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

/** Keep navigation outside the bounded list: scrolling never changes the page. */
export function Pagination({ label, page, size, totalElements, loading = false, onPageChange, onPageSizeChange }: PaginationProps) {
  const { from, to, pages } = pageRange(page, size, totalElements);
  const sizes = [...new Set([25, 50, 100, size])].sort((a, b) => a - b);

  return (
    <nav aria-label={`Paginación de ${label}`} aria-busy={loading} className="app-pagination">
      <p aria-live="polite" className="app-pagination-range">
        {loading ? "Cargando…" : `${from}–${to} de ${totalElements} ${label}`}
      </p>
      <div className="app-pagination-controls">
        {onPageSizeChange ? (
          <label className="app-pagination-size">
            Por página
            <select aria-label={`${label} por página`} disabled={loading} onChange={(event) => onPageSizeChange(Number(event.target.value))} value={size}>
              {sizes.map((value) => <option key={value} value={value}>{value}</option>)}
            </select>
          </label>
        ) : null}
        <div className="app-pagination-navigation">
          <button disabled={loading || page <= 0} onClick={() => onPageChange(page - 1)} type="button">Anterior</button>
          <span>{loading ? "Cargando…" : `Página ${page + 1} de ${Math.max(pages, page + 1)}`}</span>
          <button disabled={loading || page + 1 >= pages} onClick={() => onPageChange(page + 1)} type="button">Siguiente</button>
        </div>
      </div>
    </nav>
  );
}
