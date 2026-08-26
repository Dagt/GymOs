"use client";

import { useCallback, useEffect, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import type { ProductSearchParams } from "@/lib/api";
import { pageNumber, pageSize } from "@/lib/pagination";

export function useCatalogFilters() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const query = searchParams.get("q") ?? "";
  const [draft, setDraft] = useState({ query, value: query });
  // URL navigation (including Back/Clear) wins over an obsolete draft.
  const searchInput = draft.query === query ? draft.value : query;
  const paramsKey = searchParams.toString();

  const update = useCallback((key: string, value: string | null) => {
    const next = new URLSearchParams(paramsKey);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== "page") next.delete("page");
    router.replace(`${pathname}${next.size ? `?${next.toString()}` : ""}`, { scroll: false });
  }, [paramsKey, pathname, router]);

  useEffect(() => {
    if (searchInput.trim() === query) return;
    const timer = window.setTimeout(() => update("q", searchInput.trim() || null), 300);
    return () => window.clearTimeout(timer);
  }, [searchInput, query, update]);

  const filters: ProductSearchParams = {
    q: query,
    type: (searchParams.get("type") as ProductSearchParams["type"]) ?? "",
    categoryId: searchParams.get("categoryId") ?? "",
    status: (searchParams.get("status") as ProductSearchParams["status"]) ?? "",
    inventoryTracked: parseBoolean(searchParams.get("inventoryTracked")),
    includeArchived: parseBoolean(searchParams.get("includeArchived")) ?? false,
    page: pageNumber(searchParams.get("page")),
    size: pageSize(searchParams.get("size")),
    sort: searchParams.get("sort") ?? "displayOrder,asc",
  };

  function setSearchInput(value: string) {
    setDraft({ query, value });
  }

  function clear() {
    setDraft({ query: "", value: "" });
    router.replace(pathname, { scroll: false });
  }

  return { clear, filters, searchInput, setSearchInput, update };
}

function parseBoolean(value: string | null) {
  if (value === "true") return true;
  if (value === "false") return false;
  return undefined;
}
