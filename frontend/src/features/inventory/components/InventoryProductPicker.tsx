"use client";

import { useEffect, useId, useState, useRef } from "react";
import styles from "./InventoryProductPicker.module.css";
import { Pagination } from "@/components/Pagination";
import { DEFAULT_PAGE_SIZE } from "@/lib/pagination";
import { searchInventoryStock, type InventoryStock, type InventoryStockSearchParams } from "@/lib/api";
import { usePaginatedQuery } from "@/lib/usePaginatedQuery";

/** A product choice is independent of the current inventory list page. */
export function InventoryProductPicker({ accessToken, selected, onChange, allowClear = false, disabled = false }: {
  accessToken: string;
  selected: InventoryStock | null;
  onChange: (product: InventoryStock | null) => void;
  allowClear?: boolean;
  disabled?: boolean;
}) {
  const id = useId();
  const trigger = useRef<HTMLButtonElement>(null);
  const search = useRef<HTMLInputElement>(null);
  const results = useRef<HTMLUListElement>(null);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const list = usePaginatedQuery<InventoryStock, InventoryStockSearchParams>(accessToken, searchInventoryStock, { sort: "name,asc" }, DEFAULT_PAGE_SIZE, open);
  const setFilters = list.setFilters;
  const appliedQuery = list.filters.q ?? "";
  useEffect(() => {
    const search = query.trim();
    if (search === appliedQuery) return;
    const timer = window.setTimeout(() => setFilters({ q: search, sort: "name,asc" }), 250);
    return () => window.clearTimeout(timer);
  }, [appliedQuery, query, setFilters]);

  useEffect(() => { if (open) search.current?.focus(); }, [open]);
  function close() { setOpen(false); trigger.current?.focus(); }

  return (
    <fieldset className={`grid min-w-0 gap-2 ${styles.picker}`} disabled={disabled} onKeyDown={(event) => {
      if (event.key === "Escape" && open) { event.stopPropagation(); close(); }
      if (event.key === "ArrowDown" || event.key === "ArrowUp") {
        const buttons = Array.from(results.current?.querySelectorAll<HTMLButtonElement>("button") ?? []);
        if (!buttons.length) return;
        event.preventDefault();
        const index = buttons.indexOf(document.activeElement as HTMLButtonElement);
        buttons[(index + (event.key === "ArrowDown" ? 1 : -1) + buttons.length) % buttons.length]?.focus();
      }
    }}>
      <legend className="mb-1 text-xs font-semibold text-[#536158]">Producto</legend>
      <button ref={trigger} aria-controls={id} aria-expanded={open} className={`app-input ${styles.trigger}`} onClick={() => setOpen(!open)} type="button">
        {selected ? `${selected.productName} · ${selected.sku}` : allowClear ? "Todos los productos" : "Seleccionar producto"}
      </button>
      {open ? (
        <div className="grid gap-2 rounded-xl border border-[var(--brand-line)] bg-white p-3" id={id}>
          <input ref={search} aria-label="Buscar producto en todo el inventario" className="app-input" maxLength={200} onChange={(event) => setQuery(event.target.value)} placeholder="Nombre, SKU o código de barras" value={query} />
          {allowClear ? <button className="app-button-secondary" onClick={() => { onChange(null); close(); }} type="button">Todos los productos</button> : null}
          {list.error ? <p className="text-xs text-red-700" role="alert">{list.error}</p> : null}
          <Pagination label="opciones de producto" page={list.page} size={list.size} totalElements={list.total} loading={list.loading} onPageChange={list.setPage} />
          {!list.loading && list.items.length === 0 && !list.error ? <p className="text-xs text-[var(--brand-muted)]">No hay coincidencias.</p> : null}
          <ul ref={results} className={`app-scroll-list grid gap-1 ${styles.results}`} aria-label="Opciones de producto" tabIndex={0} key={`${list.page}-${list.filters.q ?? ""}`}>
            {list.items.map((product) => (
              <li key={product.productId}>
                <button aria-pressed={selected?.productId === product.productId} className="grid w-full min-w-0 gap-1 rounded-lg border-b border-[var(--brand-line)] p-3 text-left text-sm break-words hover:bg-[var(--brand-surface)] focus-visible:outline-2" onClick={() => { onChange(product); close(); }} type="button">
                  <span className="font-semibold">{product.productName}</span>
                  <span className="text-[var(--brand-muted)]">{product.sku} · {product.quantityOnHand} unidades</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </fieldset>
  );
}
