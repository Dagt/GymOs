"use client";

import { pageRange } from "@/lib/pagination";

type PaginationProps = {
  label: string;
  page: number;
  size: number;
  totalElements: number;
  loading?: boolean;
  onPageChange: (page: number) => void;
  onPageSizeChange?: (size: number) => void;
};

/** Lists scroll with the page; navigation is explicit, never inferred from scrolling. */
export function Pagination({ label, page, size, totalElements, loading = false, onPageChange, onPageSizeChange }: PaginationProps) {
  const { from, to, pages } = pageRange(page, size, totalElements);
  const sizes = [...new Set([25, 50, 100, size])].sort((a, b) => a - b);

  return (
    <nav aria-label={`Paginación de ${label}`} aria-busy={loading} className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[var(--brand-line)] bg-white px-4 py-3">
      <p aria-live="polite" className="text-sm text-[var(--brand-muted)]">
        {loading ? "Cargando…" : `${from}–${to} de ${totalElements} ${label}`}
      </p>
      <div className="flex w-full flex-wrap items-center gap-3 sm:w-auto">
        {onPageSizeChange ? (
          <label className="flex items-center gap-2 text-sm text-[var(--brand-muted)]">
            Por página
            <select aria-label={`${label} por página`} className="app-input !w-auto !py-1" disabled={loading} onChange={(event) => onPageSizeChange(Number(event.target.value))} value={size}>
              {sizes.map((value) => <option key={value} value={value}>{value}</option>)}
            </select>
          </label>
        ) : null}
        <div className="flex w-full items-center justify-between gap-2 sm:w-auto">
          <button className="app-button-secondary !px-2 !py-2 text-xs sm:!px-3" disabled={loading || page <= 0} onClick={() => onPageChange(page - 1)} type="button">Anterior</button>
          <span className="text-xs whitespace-nowrap text-[var(--brand-muted)] sm:text-sm">{loading ? "Cargando…" : `Página ${page + 1} de ${Math.max(pages, page + 1)}`}</span>
          <button className="app-button-secondary !px-2 !py-2 text-xs sm:!px-3" disabled={loading || page + 1 >= pages} onClick={() => onPageChange(page + 1)} type="button">Siguiente</button>
        </div>
      </div>
    </nav>
  );
}
