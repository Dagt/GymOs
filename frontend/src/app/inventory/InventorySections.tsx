"use client";

import controls from "@/components/CommercialControls.module.css";

import { type FormEvent, useState } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { Pagination } from "@/components/Pagination";
import { InventoryProductPicker } from "@/features/inventory/components/InventoryProductPicker";
import {
  type BranchSummary,
  type InventoryBranchSettings,
  type InventoryMovement,
  type InventoryMovementType,
  type InventoryPhysicalCount,
  type InventoryStock,
  type InventoryStockSearchParams,
  type InventorySummary,
  type InventoryTransfer,
} from "@/lib/api";
import { MANUAL_MOVEMENT_TYPES } from "@/features/inventory/inventory-utils";

type ManualMovementType = Exclude<InventoryMovementType, "SALE">;
export type InventoryView = "stock" | "movements" | "audits" | "transfers";

export const movementTypes = MANUAL_MOVEMENT_TYPES;

export function InventoryToolbar({
  branchSettings,
  saving,
  onToggleAllowNegativeStock,
}: {
  branchSettings: InventoryBranchSettings | null;
  saving: boolean;
  onToggleAllowNegativeStock: (allow: boolean) => void;
}) {
  return (
    <section className="app-card flex flex-col gap-4 p-5 lg:flex-row lg:items-center lg:justify-between">
      <div>
        <p className="app-eyebrow">Operación · Sucursal activa</p>
        <h2 className="app-card-title">Control de Inventario</h2>
        <p className="app-card-copy">
          Existencias de productos, movimientos de stock, auditorías físicas y transferencias inter-sucursal.
        </p>
      </div>
      <div className="flex items-center gap-3">
        <div className="flex items-center gap-2.5 rounded-lg border border-[var(--brand-line)] bg-[#f7f9f6] px-3.5 py-2 text-xs">
          <span className="font-semibold text-[#42514a]">Stock Negativo:</span>
          <button
            type="button"
            disabled={saving}
            onClick={() => onToggleAllowNegativeStock(!branchSettings?.allowNegativeStock)}
            className={
              branchSettings?.allowNegativeStock
                ? "rounded px-2.5 py-1 font-bold text-white bg-[#31543d] hover:bg-[#254231] transition"
                : "rounded px-2.5 py-1 font-bold text-[var(--brand-muted)] bg-[#eef1eb] hover:bg-[#dfe4dc] transition"
            }
          >
            {branchSettings?.allowNegativeStock ? "HABILITADO" : "BLOQUEADO"}
          </button>
        </div>
      </div>
    </section>
  );
}

export function InventorySummaryHeader({ summary }: { summary: InventorySummary | null }) {
  if (!summary) return null;

  return (
    <section aria-label="Resumen operativo" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <article className="app-card flex flex-col justify-between p-4">
        <p className="app-eyebrow">Valuación Total</p>
        <p className="mt-1 text-2xl font-extrabold text-[#111815]">
          {formatMoney(summary.totalValuationCents, "MXN")}
        </p>
        <p className="mt-1 text-[11px] text-[#6f7a73]">Estimada al costo promedio</p>
      </article>

      <article className="app-card flex flex-col justify-between p-4">
        <p className="app-eyebrow">Productos en Control</p>
        <p className="mt-1 text-2xl font-extrabold text-[#111815]">
          {summary.totalTrackedProducts}
        </p>
        <p className="mt-1 text-[11px] text-[#6f7a73]">Artículos físicos rastreados</p>
      </article>

      <article className="app-card flex flex-col justify-between p-4">
        <p className="app-eyebrow">Stock Bajo Mínimo</p>
        <p className={`mt-1 text-2xl font-extrabold ${summary.lowStockCount > 0 ? "text-[#8c2f2f]" : "text-[#28733e]"}`}>
          {summary.lowStockCount}
        </p>
        <p className="mt-1 text-[11px] text-[#6f7a73]">Requieren reabastecimiento</p>
      </article>

      <article className="app-card flex flex-col justify-between p-4">
        <p className="app-eyebrow">Agotados / Sin Stock</p>
        <p className={`mt-1 text-2xl font-extrabold ${summary.outOfStockCount > 0 ? "text-[#8c2f2f]" : "text-[#28733e]"}`}>
          {summary.outOfStockCount}
        </p>
        <p className="mt-1 text-[11px] text-[#6f7a73]">Existencia en 0 o negativa</p>
      </article>
    </section>
  );
}

export function InventoryNavigation({
  onChange,
  view,
  lowStockCount = 0,
  activeTransfersCount = 0,
}: {
  onChange: (view: InventoryView) => void;
  view: InventoryView;
  lowStockCount?: number;
  activeTransfersCount?: number;
}) {
  return (
    <nav aria-label="Secciones de inventario" className="grid gap-2 rounded-xl border border-[var(--brand-line)] bg-white p-2 shadow-sm sm:grid-cols-2 lg:grid-cols-4">
      <button
        type="button"
        onClick={() => onChange("stock")}
        className={
          view === "stock"
            ? "app-button h-10 w-full flex items-center justify-center gap-2 text-xs font-semibold"
            : "app-button-secondary h-10 w-full flex items-center justify-center gap-2 text-xs font-semibold"
        }
      >
        <span>Existencias y Alertas</span>
        {lowStockCount > 0 && (
          <span className="rounded-full bg-[#8c2f2f] px-2 py-0.5 text-[10px] text-white font-bold">
            {lowStockCount}
          </span>
        )}
      </button>

      <button
        type="button"
        onClick={() => onChange("movements")}
        className={
          view === "movements"
            ? "app-button h-10 w-full flex items-center justify-center gap-2 text-xs font-semibold"
            : "app-button-secondary h-10 w-full flex items-center justify-center gap-2 text-xs font-semibold"
        }
      >
        <span>Historial de Movimientos</span>
      </button>

      <button
        type="button"
        onClick={() => onChange("transfers")}
        className={
          view === "transfers"
            ? "app-button h-10 w-full flex items-center justify-center gap-2 text-xs font-semibold"
            : "app-button-secondary h-10 w-full flex items-center justify-center gap-2 text-xs font-semibold"
        }
      >
        <span>Gestión de Transferencias</span>
        {activeTransfersCount > 0 && (
          <span className="rounded-full bg-[#28733e] px-2 py-0.5 text-[10px] text-white font-bold">
            {activeTransfersCount}
          </span>
        )}
      </button>

      <button
        type="button"
        onClick={() => onChange("audits")}
        className={
          view === "audits"
            ? "app-button h-10 w-full flex items-center justify-center gap-2 text-xs font-semibold"
            : "app-button-secondary h-10 w-full flex items-center justify-center gap-2 text-xs font-semibold"
        }
      >
        <span>Auditorías</span>
      </button>
    </nav>
  );
}

export function InventoryStatusMessages({ error, message }: { error: string | null; message: string | null }) {
  if (!error && !message) return null;
  return (
    <div className="space-y-2">
      {error ? (
        <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-4 py-2.5 text-xs font-semibold text-[#8c2f2f]" role="alert">
          {error}
        </p>
      ) : null}
      {message ? (
        <p className="rounded-md border border-[#bfd6c7] bg-[#f0f7f2] px-4 py-2.5 text-xs font-semibold text-[#31543d]">
          {message}
        </p>
      ) : null}
    </div>
  );
}

export function InventoryStockPanel({
  loading,
  page,
  query,
  selectedProductId,
  sort,
  stock,
  total,
  pageSize,
  onExportCsv,
  onPageChange,
  onPageSizeChange,
  onQueryChange,
  onRefresh,
  onSelectProduct,
  onSortChange,
}: {
  loading: boolean;
  page: number;
  query: string;
  selectedProductId: string;
  sort: NonNullable<InventoryStockSearchParams["sort"]>;
  stock: InventoryStock[];
  total: number;
  pageSize: number;
  onExportCsv: () => void;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onQueryChange: (value: string) => void;
  onRefresh: () => void;
  onSelectProduct: (item: InventoryStock) => void;
  onSortChange: (value: NonNullable<InventoryStockSearchParams["sort"]>) => void;
}) {
  return (
    <section className="app-card p-5 h-full flex flex-col justify-between space-y-4">
      <div>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="app-eyebrow">Catálogo físico</p>
            <h2 className="app-card-title">Existencias</h2>
            <p className="app-card-copy">{total} productos en almacén</p>
          </div>
          <div className="flex gap-2">
            <button className="app-button-secondary h-8 px-3 text-xs" disabled={loading || stock.length === 0} onClick={onExportCsv} type="button">
              Exportar página CSV
            </button>
            <button className="app-button-secondary h-8 px-3 text-xs" onClick={onRefresh} type="button">
              Actualizar
            </button>
          </div>
        </div>

        <div className="mt-4 grid gap-2 sm:grid-cols-[1fr_auto]">
          <label className="grid gap-1 text-xs font-semibold text-[#536158]">
            <span>Buscar</span>
            <input
              className="app-input h-9 px-3 text-xs"
              placeholder="Nombre, SKU o código de barras"
              value={query}
              onChange={(event) => onQueryChange(event.target.value)}
            />
          </label>
          <label className="grid gap-1 text-xs font-semibold text-[#536158]">
            <span>Ordenar por</span>
            <select
              className="app-input h-9 bg-white px-3 text-xs"
              value={sort}
              onChange={(event) => onSortChange(event.target.value as NonNullable<InventoryStockSearchParams["sort"]>)}
            >
              <option value="name,asc">Nombre A-Z</option>
              <option value="name,desc">Nombre Z-A</option>
              <option value="quantity,asc">Menor existencia</option>
              <option value="quantity,desc">Mayor existencia</option>
              <option value="updatedAt,desc">Actualizados recientemente</option>
            </select>
          </label>
        </div>

        <div className="mt-4"><Pagination page={page} size={pageSize} totalElements={total} loading={loading} onPageChange={onPageChange} onPageSizeChange={onPageSizeChange} label="existencias" /></div>

        {loading ? (
          <div className="mt-4 grid gap-2.5" role="status" aria-label="Cargando existencias">
            {[0, 1, 2].map((row) => (
              <div className="h-20 animate-pulse rounded-lg bg-[#eef1eb]" key={row} />
            ))}
          </div>
        ) : null}

        {!loading && stock.length === 0 ? (
          <p className="mt-6 rounded-lg border border-dashed border-[var(--brand-line)] p-8 text-center text-xs text-[var(--brand-muted)]">
            No hay productos que coincidan con la búsqueda.
          </p>
        ) : null}

        {!loading && stock.length > 0 ? (
          <div key={page} className="app-scroll-list mt-4 grid gap-2" aria-label="Listado de existencias" role="region" tabIndex={0}>
            {stock.map((item) => {
              const isSelected = item.productId === selectedProductId;
              return (
                <button
                  key={item.productId}
                  type="button"
                  onClick={() => onSelectProduct(item)}
                  className={`rounded-lg border p-3.5 text-left transition ${
                    isSelected
                      ? "border-[#31543d] bg-[#f0f7f2] ring-1 ring-[#31543d]"
                      : "border-[var(--brand-line)] bg-white hover:border-[#86a390]"
                  }`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="font-semibold text-sm text-[#111815]">{item.productName}</p>
                      <p className="mt-0.5 text-xs text-[#6f7a73]">SKU: {item.sku}</p>
                    </div>
                    <span
                      className={`rounded-md px-2.5 py-1 text-xs font-bold ${
                        item.quantityOnHand < 0
                          ? "bg-[#fff4f4] text-[#8c2f2f]"
                          : item.lowStock
                          ? "bg-[#fff8e6] text-[#8a6300]"
                          : "bg-[#eef7f0] text-[#28733e]"
                      }`}
                    >
                      {item.quantityOnHand} unidades
                    </span>
                  </div>
                  <div className="mt-2.5 flex flex-wrap justify-between items-center text-xs text-[#6f7a73] pt-2 border-t border-[#e8ece5]">
                    <span>
                      Mínimo: {item.reorderPoint} · Costo:{" "}
                      {item.averageCostCents == null ? "N/A" : formatMoney(item.averageCostCents, item.currency)}
                    </span>
                    {item.suggestedReorderQuantity > 0 ? (
                      <span className="font-semibold text-[#8a6300]">Sugerido: +{item.suggestedReorderQuantity}</span>
                    ) : null}
                  </div>
                </button>
              );
            })}
          </div>
        ) : null}
      </div>

    </section>
  );
}

export function InventoryMovementSummary({
  selectedStock,
  onOpenDrawer,
}: {
  selectedStock: InventoryStock | null;
  onOpenDrawer: () => void;
}) {
  return (
    <section className={`app-card p-5 space-y-4 ${controls.controls}`}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="app-eyebrow">Producto seleccionado</p>
          <h2 className="app-card-title">
            {selectedStock ? selectedStock.productName : "Ningún producto seleccionado"}
          </h2>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <button className="app-button" disabled={!selectedStock} onClick={onOpenDrawer} type="button">
            Registrar Movimiento
          </button>
        </div>
      </div>

      {selectedStock ? (
        <dl className="grid gap-3 sm:grid-cols-3 border-t border-[#e4e8df] pt-4">
          <Metric label="Existencia Actual" value={`${selectedStock.quantityOnHand} u.`} />
          <Metric label="Punto Mínimo" value={`${selectedStock.reorderPoint} u.`} />
          <Metric
            label="Costo Promedio"
            value={
              selectedStock.averageCostCents == null
                ? "N/A"
                : formatMoney(selectedStock.averageCostCents, selectedStock.currency)
            }
          />
        </dl>
      ) : (
        <p className="text-xs text-[var(--brand-muted)]">
          Selecciona un producto de la lista para registrar compras, consumo interno, mermas o ajustes.
        </p>
      )}
    </section>
  );
}

export function InventoryAlertsPanel({
  lowStock,
  page, pageSize, total, loading, onPageChange,
  onSelectProduct,
}: {
  lowStock: InventoryStock[];
  page: number;
  pageSize: number;
  total: number;
  loading: boolean;
  onPageChange: (page: number) => void;
  onSelectProduct?: (item: InventoryStock) => void;
}) {
  return (
    <section className="app-card p-5 flex-1 flex flex-col justify-start min-h-0 space-y-3">
      <div className="flex items-center justify-between border-b border-[#e4e8df] pb-3">
        <div>
          <p className="app-eyebrow">Notificaciones</p>
          <h2 className="app-card-title">Alertas de Stock Bajo</h2>
        </div>
        <span className="rounded-full bg-[#fff4f4] px-2.5 py-0.5 text-xs font-bold text-[#8c2f2f]">
          {total}
        </span>
      </div>

      <Pagination label="alertas" page={page} size={pageSize} totalElements={total} loading={loading} onPageChange={onPageChange} />
      {!loading && lowStock.length === 0 ? (
        <p className="text-xs text-[var(--brand-muted)] flex-1 flex items-center justify-center py-4">
          Sin productos bajo el nivel mínimo de reorden.
        </p>
      ) : (
        <div key={page} className="app-scroll-list app-scroll-list--short flex flex-col justify-start gap-2" aria-label="Listado de alertas de inventario" role="region" tabIndex={0}>
          {lowStock.map((item) => (
            <button type="button"
              key={item.productId}
              className={`shrink-0 h-fit rounded-lg border border-[#e5b7b7] bg-[#fff4f4] p-3 text-xs flex items-center justify-between gap-3 transition ${
                onSelectProduct ? "cursor-pointer hover:border-[#8c2f2f]" : ""
              }`}
              onClick={() => onSelectProduct?.(item)}
            >
              <div>
                <p className="font-semibold text-[#111815]">{item.productName}</p>
                <p className="text-[11px] text-[#8c2f2f]">
                  Existencia: {item.quantityOnHand} / Mínimo: {item.reorderPoint}
                </p>
              </div>
              <span className="text-[10px] font-bold text-[#8c2f2f] bg-white px-2 py-1 rounded border border-[#e5b7b7]">
                Reordenar
              </span>
            </button>
          ))}
        </div>
      )}
    </section>
  );
}

export function InventoryMovementsTable({
  accessToken,
  loading,
  movements,
  page,
  product,
  query,
  total, pageSize,
  typeFilter,
  onClearFilters,
  onExportCsv,
  onPageChange,
  onPageSizeChange,
  onProductChange,
  onQueryChange,
  onTypeFilterChange,
}: {
  accessToken: string;
  loading: boolean;
  movements: InventoryMovement[];
  page: number;
  product: InventoryStock | null;
  query: string;
  total: number;
  pageSize: number;
  typeFilter: string;
  onClearFilters: () => void;
  onExportCsv: () => void;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onProductChange: (product: InventoryStock | null) => void;
  onQueryChange: (query: string) => void;
  onTypeFilterChange: (type: string) => void;
}) {
  return (
    <section className="app-card p-5 space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#e4e8df] pb-3">
        <div>
          <p className="app-eyebrow">Bitácora general de almacén</p>
          <h2 className="app-card-title">Historial de Movimientos</h2>
          <p className="app-card-copy">Busca en el historial completo. Los filtros se aplican a todas las páginas.</p>
        </div>
        <button className="app-button-secondary h-8 px-3 text-xs" disabled={loading || movements.length === 0} onClick={onExportCsv} type="button">
          Exportar página CSV
        </button>
      </div>

      {/* Bar de Búsqueda y Filtros similar al Catálogo */}
      <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-[minmax(200px,1fr)_200px_180px_auto] lg:items-end">
        <label className="grid gap-1 text-xs font-semibold text-[#536158]">
          <span>Buscar</span>
          <input
            className="app-input h-8 min-h-8 px-2.5 text-xs"
            placeholder="Nombre de producto, SKU o motivo..."
            maxLength={200}
            value={query}
            onChange={(e) => onQueryChange(e.target.value)}
          />
        </label>

        <InventoryProductPicker accessToken={accessToken} allowClear selected={product} onChange={onProductChange} />

        <label className="grid gap-1 text-xs font-semibold text-[#536158]">
          <span>Tipo de movimiento</span>
          <select
            className="app-input h-8 min-h-8 bg-white px-2 text-xs"
            value={typeFilter}
            onChange={(e) => onTypeFilterChange(e.target.value)}
          >
            <option value="">Todos los tipos</option>
            <option value="PURCHASE">Compra / Entrada</option>
            <option value="SALE">Venta en Caja</option>
            <option value="INTERNAL_CONSUMPTION">Consumo Interno</option>
            <option value="WASTE">Merma / Pérdida</option>
            <option value="RETURN">Devolución</option>
            <option value="TRANSFER_IN">Entrada por Transferencia</option>
            <option value="TRANSFER_OUT">Salida por Transferencia</option>
            <option value="ADJUSTMENT_IN">Ajuste de Entrada</option>
            <option value="ADJUSTMENT_OUT">Ajuste de Salida</option>
            <option value="INITIAL">Carga Inicial</option>
          </select>
        </label>

        <button
          className="app-button-secondary h-8 min-h-8 px-3 text-xs"
          onClick={onClearFilters}
          type="button"
        >
          Limpiar
        </button>
      </div>

      <Pagination page={page} size={pageSize} totalElements={total} loading={loading} onPageChange={onPageChange} onPageSizeChange={onPageSizeChange} label="movimientos" />
      {loading ? (
        <div className="h-32 animate-pulse rounded-lg bg-[#eef1eb]" role="status" aria-label="Cargando movimientos" />
      ) : null}

      {!loading && movements.length === 0 ? (
        <p className="rounded-lg border border-dashed border-[var(--brand-line)] p-8 text-center text-xs text-[var(--brand-muted)]">
          No hay movimientos registrados que coincidan con los filtros seleccionados.
        </p>
      ) : null}

      {!loading && movements.length > 0 ? (
        <div key={page} className="app-scroll-list" aria-label="Historial de movimientos" role="region" tabIndex={0}>
          <table className="w-full border-collapse text-xs">
            <thead>
              <tr className="border-b border-[#e4e8df] text-left uppercase tracking-wider text-[#6f7a73] font-semibold">
                <th className="py-2.5 pr-4">Fecha / Hora</th>
                <th className="py-2.5 pr-4">Producto</th>
                <th className="py-2.5 pr-4">Tipo</th>
                <th className="py-2.5 pr-4">Cantidad</th>
                <th className="py-2.5 pr-4">Ajuste</th>
                <th className="py-2.5">Motivo / Observación</th>
              </tr>
            </thead>
            <tbody>
              {movements.map((movement) => (
                <tr className="border-b border-[#eef0eb] hover:bg-[#fafcf9] transition" key={movement.id}>
                  <td className="py-3 pr-4 text-[var(--brand-muted)]">{formatDateTime(movement.occurredAt)}</td>
                  <td className="py-3 pr-4 font-semibold text-[#111815]">{movement.productName}</td>
                  <td className="py-3 pr-4">
                    <span className="rounded bg-[#f0f4ef] px-2 py-0.5 text-[11px] font-semibold text-[#31543d]">
                      {movement.movementType}
                    </span>
                  </td>
                  <td
                    className={`py-3 pr-4 font-bold text-sm ${
                      movement.quantityDelta < 0 ? "text-[#8c2f2f]" : "text-[#28733e]"
                    }`}
                  >
                    {movement.quantityDelta > 0 ? "+" : ""}
                    {movement.quantityDelta}
                  </td>
                  <td className="py-3 pr-4 text-[#42514a]">
                    {movement.quantityBefore} → {movement.quantityAfter}
                  </td>
                  <td className="py-3 text-[var(--brand-muted)]">{movement.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}

    </section>
  );
}

export function InventoryAuditsCard({
  counts,
  activeCount, page, pageSize, total, loading, onPageChange, onPageSizeChange,
  onOpenDrawer,
}: {
  counts: InventoryPhysicalCount[];
  activeCount: InventoryPhysicalCount | null;
  page: number;
  pageSize: number;
  total: number;
  loading: boolean;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onOpenDrawer: () => void;
}) {

  return (
    <section className="app-card p-5 space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#e4e8df] pb-3">
        <div>
          <p className="app-eyebrow">Ajustes y control físico</p>
          <h2 className="app-card-title">Auditoría y Conteo Físico</h2>
          <p className="app-card-copy">Ajustes periódicos de existencias físicas para resolver diferencias del sistema.</p>
        </div>
        <button className="app-button h-8 px-3 text-xs" onClick={onOpenDrawer} type="button">
          Abrir Conteo Físico
        </button>
      </div>

      {activeCount ? (
        <div className="rounded-lg border border-[#bfd6c7] bg-[#f0f7f2] p-4 text-xs space-y-2">
          <div className="flex items-center justify-between">
            <span className="font-bold text-sm text-[#31543d]">Sesión Activa de Conteo en Curso</span>
            <span className="rounded bg-[#31543d] px-2.5 py-0.5 text-xs font-bold text-white">
              {activeCount.status}
            </span>
          </div>
          <p className="text-[var(--brand-muted)]">Iniciado el {formatDateTime(activeCount.createdAt)}</p>
          <p className="text-[#42514a] font-medium">{activeCount.notes || "Sin observaciones adicionales"}</p>
        </div>
      ) : null}

      <div>
        <p className="app-eyebrow mb-2">Historial de Conteos Físicos</p>
        <Pagination label="conteos" page={page} size={pageSize} totalElements={total} loading={loading} onPageChange={onPageChange} onPageSizeChange={onPageSizeChange} />
        {!loading && counts.length === 0 ? (
          <p className="rounded-lg border border-dashed border-[var(--brand-line)] p-6 text-center text-xs text-[var(--brand-muted)]">
            Sin historial de auditorías de conteo físico registradas.
          </p>
        ) : (
          <div key={page} className="app-scroll-list mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3" aria-label="Historial de conteos" role="region" tabIndex={0}>
            {counts.map((c) => (
              <div className="rounded-lg border border-[#eef0eb] bg-[var(--brand-surface)] p-3 text-xs flex flex-col justify-between gap-2" key={c.id}>
                <div>
                  <span className="font-bold text-[#111815]">{formatDateTime(c.createdAt)}</span>
                  <p className="text-[#6f7a73] mt-0.5">{c.notes || "Sin notas"}</p>
                </div>
                <span className="font-bold text-[#31543d] text-right">{c.status}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}

export function InventoryTransfersCard({
  transfers,
  activeTransfersCount, page, pageSize, total, loading, onPageChange, onPageSizeChange,
  onCancelTransfer,
  onOpenDrawer,
  onReceiveTransfer,
  onShipTransfer,
  saving,
}: {
  transfers: InventoryTransfer[];
  activeTransfersCount: number;
  page: number;
  pageSize: number;
  total: number;
  loading: boolean;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onCancelTransfer?: (transferId: string) => void;
  onOpenDrawer: () => void;
  onReceiveTransfer?: (transferId: string) => void;
  onShipTransfer?: (transferId: string) => void;
  saving?: boolean;
}) {

  return (
    <section className="app-card p-5 space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#e4e8df] pb-3">
        <div>
          <p className="app-eyebrow">Logística inter-sucursal</p>
          <h2 className="app-card-title">Transferencias entre Sucursales</h2>
          <p className="app-card-copy">Envíos y recepciones de mercancía con otras sucursales del gimnasio.</p>
        </div>
        <button className="app-button h-8 px-3 text-xs" onClick={onOpenDrawer} type="button">
          Crear Transferencia
        </button>
      </div>

      <div className="flex items-center gap-2">
        <span className="text-xs text-[#6f7a73]">Transferencias activas:</span>
        <span className="rounded-full bg-[#f0f4ef] px-2.5 py-0.5 text-xs font-bold text-[#31543d]">
          {activeTransfersCount}
        </span>
      </div>

      <Pagination label="transferencias" page={page} size={pageSize} totalElements={total} loading={loading || saving} onPageChange={onPageChange} onPageSizeChange={onPageSizeChange} />
      {!loading && transfers.length === 0 ? (
        <p className="rounded-lg border border-dashed border-[var(--brand-line)] p-6 text-center text-xs text-[var(--brand-muted)]">
          No hay registros de transferencias de inventario en esta sucursal.
        </p>
      ) : (
        <div key={page} className="app-scroll-list grid gap-3 sm:grid-cols-2 lg:grid-cols-2" aria-label="Historial de transferencias" role="region" tabIndex={0}>
          {transfers.map((t) => (
            <div className="rounded-lg border border-[#e4e8df] bg-[var(--brand-surface)] p-4 text-xs flex flex-col justify-between gap-3" key={t.id}>
              <div className="space-y-2">
                <div className="flex justify-between items-center border-b border-[#e8ece5] pb-2">
                  <span className="font-bold text-sm text-[#111815]">
                    {t.originBranchName} → {t.destinationBranchName}
                  </span>
                  <span className="rounded bg-[#f0f4ef] px-2.5 py-0.5 text-xs font-bold text-[#31543d]">
                    {t.status}
                  </span>
                </div>
                {t.notes ? <p className="text-[var(--brand-muted)]">{t.notes}</p> : null}

                {t.items && t.items.length > 0 ? (
                  <div className="space-y-1 bg-white p-2.5 rounded border border-[#e8ece5] text-[11px]">
                    <span className="font-semibold text-[#6f7a73] text-[10px] uppercase tracking-wide">Artículos incluidos:</span>
                    {t.items.map((item) => (
                      <div key={item.id} className="flex justify-between items-center text-[#42514a]">
                        <span>• {item.productName} ({item.sku})</span>
                        <span className="font-bold text-[#111815]">{item.shippedQuantity ?? item.requestedQuantity} unidades</span>
                      </div>
                    ))}
                  </div>
                ) : null}
              </div>

              {onShipTransfer || onReceiveTransfer || onCancelTransfer ? (
                <div className="flex gap-2 justify-end pt-2 border-t border-[#e8ece5]">
                  {t.status === "REQUESTED" && onShipTransfer && (
                    <button
                      className="app-button-secondary py-1 px-3 text-xs"
                      disabled={saving}
                      onClick={() => onShipTransfer(t.id)}
                      type="button"
                    >
                      Enviar
                    </button>
                  )}
                  {t.status === "SHIPPED" && onReceiveTransfer && (
                    <button
                      className="app-button py-1 px-3 text-xs"
                      disabled={saving}
                      onClick={() => onReceiveTransfer(t.id)}
                      type="button"
                    >
                      Recibir
                    </button>
                  )}
                  {t.status !== "RECEIVED" && t.status !== "CANCELLED" && onCancelTransfer && (
                    <button
                      className="app-button-secondary py-1 px-3 text-xs text-[#8c2f2f]"
                      disabled={saving}
                      onClick={() => onCancelTransfer(t.id)}
                      type="button"
                    >
                      Cancelar
                    </button>
                  )}
                </div>
              ) : null}
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

export function InventoryMovementDrawer({
  accessToken,
  movementType,
  open,
  quantity,
  reason,
  reorderPoint,
  saving,
  selectedProduct,
  unitCostCents,
  onClose,
  onMovementTypeChange,
  onProductChange,
  onQuantityChange,
  onReasonChange,
  onReorderPointChange,
  onSubmit,
  onUnitCostChange,
}: {
  accessToken: string;
  movementType: ManualMovementType;
  open: boolean;
  quantity: number;
  reason: string;
  reorderPoint: string;
  saving: boolean;
  selectedProduct: InventoryStock | null;
  unitCostCents: string;
  onClose: () => void;
  onMovementTypeChange: (value: ManualMovementType) => void;
  onProductChange: (value: InventoryStock) => void;
  onQuantityChange: (value: number) => void;
  onReasonChange: (value: string) => void;
  onReorderPointChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onUnitCostChange: (value: string) => void;
}) {
  return (
    <AppDrawer
      description="Registra entradas, consumo interno, ajustes, mermas o devoluciones para el producto seleccionado."
      onClose={onClose}
      open={open}
      title="Registrar movimiento"
    >
      <form className="grid gap-4" onSubmit={onSubmit}>
        <div className="grid gap-4 sm:grid-cols-2">
          <InventoryProductPicker accessToken={accessToken} disabled={saving} selected={selectedProduct} onChange={(product) => { if (product) onProductChange(product); }} />
          <Field label="Tipo">
            <select
              className="app-input"
              onChange={(event) => onMovementTypeChange(event.target.value as ManualMovementType)}
              value={movementType}
            >
              {movementTypes.map((type) => (
                <option key={type.value} value={type.value}>
                  {type.label}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Cantidad">
            <input
              className="app-input"
              min={1}
              onChange={(event) => onQuantityChange(Number(event.target.value))}
              type="number"
              value={quantity}
            />
          </Field>
          <Field label="Costo unitario ($ MXN)">
            <input
              className="app-input"
              min={0}
              onChange={(event) => onUnitCostChange(event.target.value)}
              step="0.01"
              type="number"
              value={unitCostCents}
            />
          </Field>
          <Field label="Punto Mínimo Reorden">
            <input
              className="app-input"
              min={0}
              onChange={(event) => onReorderPointChange(event.target.value)}
              type="number"
              value={reorderPoint}
            />
          </Field>
        </div>
        <Field label="Motivo u Observación">
          <textarea
            className="app-input min-h-24 py-2"
            onChange={(event) => onReasonChange(event.target.value)}
            value={reason}
          />
        </Field>
        <button
          className="form-primary-button"
          disabled={saving || !selectedProduct || !reason.trim()}
          type="submit"
        >
          {saving ? "Registrando..." : "Registrar movimiento"}
        </button>
      </form>
    </AppDrawer>
  );
}

export function InventoryPhysicalCountDrawer({
  activeCount,
  loading,
  open,
  saving,
  onApproveCount,
  onCancelCount,
  onClose,
  onSaveCountItem,
  onStartCount,
}: {
  activeCount: InventoryPhysicalCount | null;
  loading: boolean;
  open: boolean;
  saving: boolean;
  onApproveCount: (countId: string) => void;
  onCancelCount: (countId: string) => void;
  onClose: () => void;
  onSaveCountItem: (countId: string, productId: string, countedQty: number | null, notes?: string) => void;
  onStartCount: (notes?: string) => void;
}) {
  const [notes, setNotes] = useState("");

  return (
    <AppDrawer
      description="Inicia una auditoría de existencias físicas para ajustar automáticamente las diferencias del sistema."
      onClose={onClose}
      open={open}
      title="Auditoría / Conteo Físico"
    >
      <div className="grid gap-6">
        {loading && !activeCount ? <p role="status">Cargando conteo activo…</p> : !activeCount ? (
          <div className="rounded-lg border border-[var(--brand-line)] bg-[#fdfefc] p-4 grid gap-3">
            <h3 className="font-semibold text-sm text-[#111815]">Iniciar nueva sesión de conteo</h3>
            <label className="grid gap-1 text-xs font-semibold text-[#536158]">
              <span>Notas / Referencia</span>
              <input
                className="app-input"
                placeholder="Ej. Conteo mensual de cierre"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
              />
            </label>
            <button
              className="app-button"
              disabled={saving}
              onClick={() => {
                onStartCount(notes);
                setNotes("");
              }}
              type="button"
            >
              {saving ? "Iniciando..." : "Iniciar Sesión de Conteo"}
            </button>
          </div>
        ) : (
          <div className="grid gap-4">
            <div className="flex items-center justify-between border-b border-[#e4e8df] pb-3">
              <div>
                <h3 className="font-semibold text-sm">Conteo en Progreso</h3>
                <p className="text-xs text-[var(--brand-muted)]">Creado: {formatDateTime(activeCount.createdAt)}</p>
              </div>
              <span className="rounded bg-[#eef7f0] px-2 py-1 text-xs font-semibold text-[#31543d]">
                {activeCount.status}
              </span>
            </div>

            <div className="grid gap-2">
              {activeCount.items && activeCount.items.length > 0 ? (
                activeCount.items.map((item) => (
                  <div
                    className="rounded-lg border border-[#e4e8df] bg-[var(--brand-surface)] p-3 grid sm:grid-cols-[1fr_auto] gap-2 items-center text-xs"
                    key={item.id}
                  >
                    <div>
                      <p className="font-semibold text-sm text-[#111815]">{item.productName}</p>
                      <p className="text-[#6f7a73] mt-0.5">
                        SKU: {item.sku} · Sistema: <span className="font-bold text-[#111815]">{item.systemQuantity} u.</span> | Varianza:{" "}
                        <span className={`font-bold ${(item.varianceQuantity ?? 0) < 0 ? "text-[#8c2f2f]" : (item.varianceQuantity ?? 0) > 0 ? "text-[#28733e]" : "text-[var(--brand-muted)]"}`}>
                          {item.varianceQuantity ?? 0} u.
                        </span>
                      </p>
                    </div>
                    <div className="flex items-center gap-2">
                      <label className="text-[11px] font-semibold text-[#536158]">Físico:</label>
                      <input
                        aria-label={`Cantidad física de ${item.productName}`}
                        className="app-input w-24 text-right h-9 font-bold text-sm bg-white"
                        defaultValue={item.countedQuantity ?? ""}
                        onBlur={(e) => {
                          const val = e.target.value.trim() !== "" ? Number(e.target.value) : null;
                          onSaveCountItem(activeCount.id, item.productId, val);
                        }}
                        placeholder="0"
                        type="number"
                      />
                    </div>
                  </div>
                ))
              ) : (
                <p className="text-xs text-[var(--brand-muted)] p-4 text-center border border-dashed rounded-lg">
                  No hay productos registrados en el inventario para auditar.
                </p>
              )}
            </div>

            <div className="flex gap-2 justify-end pt-2">
              <button
                className="app-button-secondary"
                disabled={saving || loading}
                onClick={() => onCancelCount(activeCount.id)}
                type="button"
              >
                Cancelar Conteo
              </button>
              <button
                className="app-button"
                disabled={saving || loading}
                onClick={() => onApproveCount(activeCount.id)}
                type="button"
              >
                Aprobar y Ajustar Stock
              </button>
            </div>
          </div>
        )}
      </div>
    </AppDrawer>
  );
}

export function InventoryTransferDrawer({
  accessToken,
  activeBranchId,
  branches,
  open,
  saving,
  onClose,
  onCreateTransfer,
}: {
  accessToken: string;
  activeBranchId?: string;
  branches: BranchSummary[];
  open: boolean;
  saving: boolean;
  onClose: () => void;
  onCreateTransfer: (
    destBranchId: string,
    notes: string,
    items: Array<{ productId: string; requestedQuantity: number }>
  ) => void;
}) {
  const [destBranchId, setDestBranchId] = useState("");
  const [notes, setNotes] = useState("");
  const [selectedStockItem, setSelectedStockItem] = useState<InventoryStock | null>(null);
  const [requestedQty, setRequestedQty] = useState(1);
  const [items, setItems] = useState<Array<{ productId: string; productName: string; requestedQuantity: number }>>([]);
  const [itemError, setItemError] = useState<string | null>(null);

  const destinationBranches = activeBranchId ? branches.filter((b) => b.id !== activeBranchId) : branches;

  function addItem() {
    setItemError(null);
    if (!selectedStockItem || requestedQty <= 0) return;
    const prod = selectedStockItem;
    if (!prod) return;

    if (requestedQty > prod.quantityOnHand) {
      setItemError(`No es posible transferir ${requestedQty} unidades de "${prod.productName}". Existencia disponible: ${prod.quantityOnHand} unidades.`);
      return;
    }

    setItems((prev) => [
      ...prev.filter((i) => i.productId !== prod.productId),
      { productId: prod.productId, productName: prod.productName, requestedQuantity: requestedQty },
    ]);
  }

  function submitTransfer() {
    if (!destBranchId || items.length === 0) return;
    onCreateTransfer(
      destBranchId,
      notes,
      items.map((i) => ({ productId: i.productId, requestedQuantity: i.requestedQuantity }))
    );
    setItems([]);
    setNotes("");
    setItemError(null);
  }

  return (
    <AppDrawer
      description="Crea una solicitud de envío de mercancía a otra sucursal de tu organización."
      onClose={onClose}
      open={open}
      containFocus
      title="Crear Transferencia entre Sucursales"
    >
      <div className={`grid gap-4 ${controls.controls}`}>
        <div className="rounded-lg border border-[var(--brand-line)] bg-[#fdfefc] p-4 grid gap-3 text-xs">
          <Field label="Sucursal Destino">
            {destinationBranches.length === 0 ? (
              <p className="rounded border border-[#e5b7b7] bg-[#fff4f4] p-2.5 text-xs text-[#8c2f2f] font-medium">
                No hay otras sucursales registradas en esta organización a las cuales transferir mercancía.
              </p>
            ) : (
              <select
                className="app-input"
                value={destBranchId}
                onChange={(e) => setDestBranchId(e.target.value)}
              >
                <option value="">-- Seleccionar sucursal --</option>
                {destinationBranches.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </select>
            )}
          </Field>

          <div className="grid min-w-0 gap-3">
            <div className="min-w-0">
              <InventoryProductPicker accessToken={accessToken} disabled={saving} selected={selectedStockItem} onChange={(product) => { setSelectedStockItem(product); setItemError(null); }} />
              {selectedStockItem ? <p className="mt-1 text-xs text-[var(--brand-muted)]">Disponible: {selectedStockItem.quantityOnHand} u.</p> : null}
            </div>
            <div className="grid items-end gap-3 sm:grid-cols-[minmax(0,1fr)_auto]">
            <Field label="Cantidad">
              <input
                className="app-input text-center"
                min={1}
                max={selectedStockItem ? selectedStockItem.quantityOnHand : undefined}
                value={requestedQty}
                onChange={(e) => {
                  setRequestedQty(Number(e.target.value));
                  setItemError(null);
                }}
                type="number"
              />
            </Field>
            <button className="app-button-secondary h-10 px-3" onClick={addItem} type="button">
              Agregar
            </button>
            </div>
          </div>

          {itemError ? (
            <p className="rounded border border-[#e5b7b7] bg-[#fff4f4] px-3 py-1.5 text-xs font-semibold text-[#8c2f2f]">
              {itemError}
            </p>
          ) : null}

          {items.length > 0 && (
            <div className="grid gap-1 border-t border-[#e4e8df] pt-2">
              <p className="text-[10px] font-bold text-[#6f7a73] uppercase">Lista de transferencia:</p>
              {items.map((i) => (
                <div className="flex flex-wrap justify-between gap-2 text-sm py-2" key={i.productId}>
                  <span className="min-w-0 break-words font-semibold text-[var(--brand-ink)]">• {i.productName}</span>
                  <span className="font-bold text-[#31543d]">{i.requestedQuantity} unidades</span>
                </div>
              ))}
            </div>
          )}

          <Field label="Notas / Motivo">
            <input
              className="app-input"
              placeholder="Motivo de la transferencia"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </Field>
          <button
            className="app-button h-10"
            disabled={saving || !destBranchId || items.length === 0}
            onClick={submitTransfer}
            type="button"
          >
            {saving ? "Procesando..." : "Crear Solicitud de Transferencia"}
          </button>
        </div>
      </div>
    </AppDrawer>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="grid gap-1 text-xs font-semibold text-[#536158]">{label}{children}</label>;
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-[11px] font-semibold text-[#6f7a73] uppercase tracking-wide">{label}</dt>
      <dd className="mt-1 font-extrabold text-sm text-[#111815]">{value}</dd>
    </div>
  );
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", { style: "currency", currency }).format(cents / 100);
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
}
