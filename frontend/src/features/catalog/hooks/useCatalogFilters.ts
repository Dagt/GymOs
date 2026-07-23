"use client";

import { useEffect, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import type { ProductSearchParams } from "@/lib/api";

export function useCatalogFilters() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const query = searchParams.get("q") ?? "";
  const [searchInput, setSearchInput] = useState(query);
  const [debouncedQuery, setDebouncedQuery] = useState(query);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedQuery(searchInput.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  useEffect(() => {
    if (debouncedQuery !== query) update("q", debouncedQuery || null, true);
    // update is intentionally driven only by the debounced value.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedQuery]);

  const filters: ProductSearchParams = {
    q: debouncedQuery,
    type: (searchParams.get("type") as ProductSearchParams["type"]) ?? "",
    categoryId: searchParams.get("categoryId") ?? "",
    status: (searchParams.get("status") as ProductSearchParams["status"]) ?? "",
    inventoryTracked: parseBoolean(searchParams.get("inventoryTracked")),
    page: Number(searchParams.get("page") ?? "0"),
    size: Number(searchParams.get("size") ?? "25"),
    sort: searchParams.get("sort") ?? "displayOrder,asc",
  };

  function update(key: string, value: string | null, preservePage = false) {
    const next = new URLSearchParams(searchParams.toString());
    if (value) next.set(key, value);
    else next.delete(key);
    if (!preservePage && key !== "page") next.delete("page");
    router.replace(`${pathname}${next.size ? `?${next.toString()}` : ""}`, { scroll: false });
  }

  function clear() {
    setSearchInput("");
    router.replace(pathname, { scroll: false });
  }

  return { clear, filters, searchInput, setSearchInput, update };
}

function parseBoolean(value: string | null) {
  if (value === "true") return true;
  if (value === "false") return false;
  return undefined;
}
