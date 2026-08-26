"use client";

import { useEffect, useId, useState } from "react";
import { Pagination } from "@/components/Pagination";
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
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const list = usePaginatedQuery<InventoryStock, InventoryStockSearchParams>(accessToken, searchInventoryStock, { sort: "name,asc" }, 10, open);
  const setFilters = list.setFilters;
  useEffect(() => {
    const timer = window.setTimeout(() => setFilters({ q: query.trim(), sort: "name,asc" }), 250);
    return () => window.clearTimeout(timer);
  }, [query, setFilters]);

  return (
    <fieldset className="grid min-w-0 gap-2" disabled={disabled}>
      <legend className="mb-1 text-xs font-semibold text-[#536158]">Producto</legend>
      <button aria-controls={id} aria-expanded={open} className="app-input h-auto min-h-9 text-left text-xs break-words" onClick={() => setOpen(!open)} type="button">
        {selected ? `${selected.productName} · ${selected.sku}` : allowClear ? "Todos los productos" : "Seleccionar producto"}
      </button>
      {open ? (
        <div className="grid gap-2 rounded-xl border border-[var(--brand-line)] bg-white p-3" id={id}>
          <input aria-label="Buscar producto en todo el inventario" className="app-input" maxLength={200} onChange={(event) => setQuery(event.target.value)} placeholder="Nombre, SKU o código de barras" value={query} />
          {allowClear ? <button className="app-button-secondary" onClick={() => { onChange(null); setOpen(false); }} type="button">Todos los productos</button> : null}
          {list.error ? <p className="text-xs text-red-700" role="alert">{list.error}</p> : null}
          <Pagination label="opciones de producto" page={list.page} size={list.size} totalElements={list.total} loading={list.loading} onPageChange={list.setPage} />
          {!list.loading && list.items.length === 0 && !list.error ? <p className="text-xs text-[var(--brand-muted)]">No hay coincidencias.</p> : null}
          <ul className="grid gap-1">
            {list.items.map((product) => (
              <li key={product.productId}>
                <button className="grid w-full min-w-0 gap-1 rounded-lg p-2 text-left text-xs break-words hover:bg-[var(--brand-surface)] focus-visible:outline-2" onClick={() => { onChange(product); setOpen(false); }} type="button">
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
