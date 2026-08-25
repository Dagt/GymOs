import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { dirname, extname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const sourceRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const sourceExtensions = new Set([".css", ".ts", ".tsx"]);
const mojibakePattern = /[\u00c2\u00c3\ufffd]|\u00e2(?:\u0080|\u20ac)/u;

test("frontend source does not contain common UTF-8 mojibake markers", () => {
  const offenders = sourceFiles(sourceRoot)
    .filter((file) => mojibakePattern.test(readFileSync(file, "utf8")))
    .map((file) => file.slice(sourceRoot.length + 1).replaceAll("\\", "/"));

  assert.deepEqual(offenders, []);
});

function sourceFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) return sourceFiles(path);
    return sourceExtensions.has(extname(entry.name)) ? [path] : [];
  });
}
