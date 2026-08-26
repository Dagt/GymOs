"use client";

import { useCallback, useEffect, useState } from "react";
import { DEFAULT_PAGE_SIZE, isPageResult, lastPage, type PageResult } from "./pagination";

/** Server-side paging with atomic filter resets and stale-response protection. */
export function usePaginatedQuery<T, F extends object>(
  accessToken: string | undefined,
  load: (token: string, params: F & { page: number; size: number }) => Promise<PageResult<T>>,
  initialFilters: F,
  initialSize = DEFAULT_PAGE_SIZE,
  enabled = true,
) {
  const [request, setRequest] = useState({ filters: initialFilters, page: 0, size: initialSize, revision: 0 });
  const [result, setResult] = useState<{ request: typeof request; token: string; data: PageResult<T> } | null>(null);
  const [failure, setFailure] = useState<{ request: typeof request; token: string; message: string } | null>(null);

  useEffect(() => {
    if (!accessToken || !enabled) return;
    let active = true;
    void load(accessToken, { ...request.filters, page: request.page, size: request.size }).then((data) => {
      if (!active) return;
      if (!isPageResult(data)) throw new Error("El servidor devolvió un listado sin paginación válida. Actualiza el backend y vuelve a intentar; no es un listado vacío.");
      const validPage = lastPage(data.totalElements, data.size);
      if (request.page > validPage) {
        setRequest((current) => current === request ? { ...current, page: validPage } : current);
        return;
      }
      setResult({ request, token: accessToken, data });
    }).catch((error: unknown) => {
      if (active) setFailure({ request, token: accessToken, message: error instanceof Error ? error.message : "No fue posible cargar la página." });
    });
    return () => { active = false; };
  }, [accessToken, enabled, load, request]);

  // A refresh may keep the same page mounted (e.g. an in-progress physical
  // count). Never carry rows across a different filter, page, size or session.
  const currentResult = result && result.token === accessToken
    && result.request.filters === request.filters
    && result.request.page === request.page
    && result.request.size === request.size ? result.data : null;
  const settled = result?.request === request && result.token === accessToken;
  const error = failure?.request === request && failure.token === accessToken ? failure.message : null;
  const setFilters = useCallback((filters: F | ((current: F) => F)) => {
    setRequest((current) => ({ ...current, page: 0, filters: typeof filters === "function" ? filters(current.filters) : filters }));
  }, []);
  const setPage = useCallback((page: number) => setRequest((current) => ({ ...current, page: Math.max(0, page) })), []);
  const setPageSize = useCallback((size: number) => setRequest((current) => ({ ...current, size: Math.min(100, Math.max(1, size)), page: 0 })), []);
  const refresh = useCallback(() => setRequest((current) => ({ ...current, revision: current.revision + 1 })), []);

  return {
    items: currentResult?.items ?? [],
    total: currentResult?.totalElements ?? 0,
    totalPages: currentResult?.totalPages ?? 0,
    page: request.page,
    size: request.size,
    filters: request.filters,
    loading: Boolean(enabled && accessToken && !settled && !error),
    error, setFilters, setPage, setPageSize, refresh,
  };
}
