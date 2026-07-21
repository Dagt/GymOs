#!/usr/bin/env python3
"""Inventory oversized source files and repeated code blocks without external dependencies."""

from __future__ import annotations

import argparse
import hashlib
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
import re
import sys

SOURCE_SUFFIXES = {".java", ".ts", ".tsx", ".js", ".jsx"}
IGNORED_PARTS = {
    ".git",
    ".gradle",
    ".next",
    "build",
    "dist",
    "node_modules",
    "coverage",
}


@dataclass(frozen=True)
class SourceFile:
    path: Path
    lines: tuple[str, ...]

    @property
    def line_count(self) -> int:
        return len(self.lines)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="Repository root")
    parser.add_argument("--java-limit", type=int, default=350)
    parser.add_argument("--frontend-limit", type=int, default=300)
    parser.add_argument("--block-lines", type=int, default=12)
    parser.add_argument("--max-duplicates", type=int, default=30)
    return parser.parse_args()


def is_source(path: Path) -> bool:
    return path.suffix in SOURCE_SUFFIXES and not any(part in IGNORED_PARTS for part in path.parts)


def load_sources(root: Path) -> list[SourceFile]:
    sources: list[SourceFile] = []
    for path in root.rglob("*"):
        if not path.is_file() or not is_source(path):
            continue
        try:
            content = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        sources.append(SourceFile(path.relative_to(root), tuple(content.splitlines())))
    return sources


def normalized(line: str) -> str:
    value = line.strip()
    if not value or value.startswith(("//", "/*", "*", "import ", "package ")):
        return ""
    value = re.sub(r'"(?:\\.|[^"\\])*"', '"<string>"', value)
    value = re.sub(r"\b\d+\b", "<number>", value)
    return re.sub(r"\s+", " ", value)


def oversized(sources: list[SourceFile], java_limit: int, frontend_limit: int) -> list[SourceFile]:
    result = []
    for source in sources:
        limit = java_limit if source.path.suffix == ".java" else frontend_limit
        if source.line_count > limit:
            result.append(source)
    return sorted(result, key=lambda item: item.line_count, reverse=True)


def duplicate_blocks(sources: list[SourceFile], block_lines: int) -> list[tuple[int, list[tuple[Path, int]]]]:
    occurrences: dict[str, list[tuple[Path, int]]] = defaultdict(list)
    block_sizes: dict[str, int] = {}

    for source in sources:
        clean = [normalized(line) for line in source.lines]
        for start in range(0, len(clean) - block_lines + 1):
            block = clean[start : start + block_lines]
            if sum(bool(line) for line in block) < block_lines - 2:
                continue
            canonical = "\n".join(block)
            digest = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
            occurrences[digest].append((source.path, start + 1))
            block_sizes[digest] = len(canonical)

    duplicates = []
    for digest, locations in occurrences.items():
        distinct_files = {path for path, _ in locations}
        if len(distinct_files) < 2:
            continue
        duplicates.append((block_sizes[digest], locations))
    return sorted(duplicates, key=lambda item: (len(item[1]), item[0]), reverse=True)


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    if not (root / "backend").exists() or not (root / "frontend").exists():
        print(f"Error: {root} no parece ser la raiz de GymOs.", file=sys.stderr)
        return 2

    sources = load_sources(root)
    print("# Archivos por encima del limite")
    long_files = oversized(sources, args.java_limit, args.frontend_limit)
    if not long_files:
        print("Ninguno")
    for source in long_files:
        print(f"{source.line_count:5d}  {source.path}")

    print("\n# Bloques repetidos entre archivos")
    duplicates = duplicate_blocks(sources, args.block_lines)
    if not duplicates:
        print("Ninguno")
    for _, locations in duplicates[: args.max_duplicates]:
        print(f"Repeticiones: {len(locations)}")
        for path, line in locations[:10]:
            print(f"  {path}:{line}")
        if len(locations) > 10:
            print(f"  ... y {len(locations) - 10} mas")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
