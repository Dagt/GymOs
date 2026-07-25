"use client";

import { type FormEvent, useState } from "react";
import { AppDrawer } from "@/components/AppDrawer";
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

type ManualMovementType = Exclude<InventoryMovementType, "SALE">;

import { MANUAL_MOVEMENT_TYPES } from "@/features/inventory/inventory-utils";

export const movementTypes = MANUAL_MOVEMENT_TYPES;

export function InventoryStatusMessages({ error, message }: { error: string | null; message: string | null }) {
  return (
    <>
      {error ? <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f]" role="alert">{error}</p> : null}
      {message ? <p className="rounded-md border border-[#bfd6c7] bg-[#f0f7f2] px-3 py-2 text-sm text-[#31543d]">{message}</p> : null}
    </>
  );
}

export function InventorySummaryHeader({
  branchSettings,
  saving,
  summary,
  onToggleAllowNegativeStock,
}: {
  branchSettings: InventoryBranchSettings | null;
  saving: boolean;
  summary: InventorySummary | null;
  onToggleAllowNegativeStock: (allow: boolean) => void;
}) {
  if (!summary) return null;

  return (
    <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-[#e4e8df] pb-4">
        <div>
          <h2 className="text-lg font-semibold text-[#1d2720]">Resumen Operativo de Inventario</h2>
          <p className="text-xs text-[#5d6b63]">Métricas generales de existencias y valuación de la sucursal activa.</p>
        </div>
        <div className="flex items-center gap-3 bg-[#f7f9f6] px-3 py-2 rounded-md border border-[#d8ddd2]">
          <span className="text-xs font-medium text-[#42514a]">Permitir Stock Negativo:</span>
          <button
            className={`px-3 py-1 rounded text-xs font-semibold transition ${
              branchSettings?.allowNegativeStock
                ? "bg-[#31543d] text-white hover:bg-[#254231]"
                : "bg-[#eef1eb] text-[#5d6b63] hover:bg-[#dfe4dc]"
            }`}
            disabled={saving}
            onClick={() => onToggleAllowNegativeStock(!branchSettings?.allowNegativeStock)}
            type="button"
          >
            {branchSettings?.allowNegativeStock ? "HABILITADO" : "BLOQUEADO"}
          </button>
        </div>
      </div>

      <dl className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-md border border-[#e4e8df] bg-[#fafbf9] p-3">
          <dt className="text-xs font-medium text-[#6f7a73]">Valuación Total Estimada</dt>
          <dd className="mt-1 text-xl font-bold text-[#1d2720]">
            {formatMoney(summary.totalValuationCents, "MXN")}
          </dd>
        </div>
        <div className="rounded-md border border-[#e4e8df] bg-[#fafbf9] p-3">
          <dt className="text-xs font-medium text-[#6f7a73]">Productos en Control</dt>
          <dd className="mt-1 text-xl font-bold text-[#1d2720]">{summary.totalTrackedProducts}</dd>
        </div>
        <div className="rounded-md border border-[#e4e8df] bg-[#fafbf9] p-3">
          <dt className="text-xs font-medium text-[#6f7a73]">Stock Bajo Mínimo</dt>
          <dd className={`mt-1 text-xl font-bold ${summary.lowStockCount > 0 ? "text-[#8c2f2f]" : "text-[#31543d]"}`}>
            {summary.lowStockCount}
          </dd>
        </div>
        <div className="rounded-md border border-[#e4e8df] bg-[#fafbf9] p-3">
          <dt className="text-xs font-medium text-[#6f7a73]">Agotados / Sin Stock</dt>
          <dd className={`mt-1 text-xl font-bold ${summary.outOfStockCount > 0 ? "text-[#8c2f2f]" : "text-[#31543d]"}`}>
            {summary.outOfStockCount}
          </dd>
        </div>
      </dl>
    </section>
  );
}

export function InventoryStockPanel({ loading, page, query, selectedProductId, sort, stock, total, totalPages,
  onExportCsv, onPageChange, onQueryChange, onRefresh, onSelectProduct, onSortChange }: {
  loading: boolean;
  page: number;
  query: string;
  selectedProductId: string;
  sort: NonNullable<InventoryStockSearchParams["sort"]>;
  stock: InventoryStock[];
  total: number;
  totalPages: number;
  onExportCsv: () => void;
  onPageChange: (page: number) => void;
  onQueryChange: (value: string) => void;
  onRefresh: () => void;
  onSelectProduct: (item: InventoryStock) => void;
  onSortChange: (value: NonNullable<InventoryStockSearchParams["sort"]>) => void;
}) {
  return (
    <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div><h2 className="text-xl font-semibold">Existencias</h2><p className="mt-1 text-sm text-[#5d6b63]">{total} productos físicos</p></div>
        <div className="flex gap-2">
          <button className="app-button-secondary" onClick={onExportCsv} type="button">Exportar CSV</button>
          <button className="app-button-secondary" onClick={onRefresh} type="button">Actualizar</button>
        </div>
      </div>
      <div className="mt-4 grid gap-3 sm:grid-cols-[1fr_auto]">
        <label className="grid gap-1 text-sm"><span className="font-medium">Buscar</span>
          <input className="h-10 rounded-md border border-[#cdd5ca] px-3" placeholder="Nombre, SKU o código"
            value={query} onChange={(event) => onQueryChange(event.target.value)} />
        </label>
        <label className="grid gap-1 text-sm"><span className="font-medium">Ordenar</span>
          <select className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3" value={sort}
            onChange={(event) => onSortChange(event.target.value as NonNullable<InventoryStockSearchParams["sort"]>)}>
            <option value="name,asc">Nombre A-Z</option><option value="name,desc">Nombre Z-A</option>
            <option value="quantity,asc">Menor existencia</option><option value="quantity,desc">Mayor existencia</option>
            <option value="updatedAt,desc">Actualizados recientemente</option>
          </select>
        </label>
      </div>
      {loading ? <div className="mt-4 grid gap-3" role="status" aria-label="Cargando existencias">{[0, 1, 2].map((row) => <div className="h-20 animate-pulse rounded-md bg-[#eef1eb]" key={row} />)}</div> : null}
      {!loading && stock.length === 0 ? <p className="mt-4 text-sm text-[#5d6b63]">No hay productos para esta búsqueda.</p> : null}
      {!loading ? <div className="mt-4 grid gap-3">
        {stock.map((item) => (
          <button className={`rounded-md border p-4 text-left transition ${item.productId === selectedProductId ? "border-[#51735f] bg-[#eef7f0]" : "border-[#d8ddd2] bg-white hover:border-[#51735f]"}`}
            key={item.productId} onClick={() => onSelectProduct(item)} type="button">
            <div className="flex items-start justify-between gap-3">
              <div><p className="font-semibold">{item.productName}</p><p className="mt-1 text-xs text-[#6f7a73]">{item.sku}</p></div>
              <span className={`rounded-md px-2 py-1 text-xs font-semibold ${item.quantityOnHand < 0 ? "bg-[#fff4f4] text-[#8c2f2f]" : item.lowStock ? "bg-[#fff8e6] text-[#8a6300]" : "bg-[#eef7f0] text-[#31543d]"}`}>{item.quantityOnHand}</span>
            </div>
            <div className="mt-3 flex flex-wrap justify-between items-center text-xs text-[#6f7a73]">
              <span>Mínimo {item.reorderPoint} · Costo {item.averageCostCents == null ? "N/A" : formatMoney(item.averageCostCents, item.currency)}</span>
              {item.suggestedReorderQuantity > 0 ? (
                <span className="font-semibold text-[#8a6300]">Sugerido pedir: +{item.suggestedReorderQuantity}</span>
              ) : null}
            </div>
          </button>
        ))}
      </div> : null}
      <Pagination page={page} totalPages={totalPages} onPageChange={onPageChange} label="existencias" />
    </section>
  );
}

export function InventoryAlertsPanel({ lowStock }: { lowStock: InventoryStock[] }) {
  return (
    <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <h2 className="text-xl font-semibold">Alertas</h2>
      {lowStock.length === 0 ? <p className="mt-3 text-sm text-[#5d6b63]">Sin productos bajo minimo.</p> : (
        <div className="mt-4 grid gap-2">{lowStock.map((item) => (
          <div className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm" key={item.productId}>
            <span className="font-semibold">{item.productName}</span><span className="ml-2 text-[#8c2f2f]">{item.quantityOnHand} / {item.reorderPoint}</span>
          </div>
        ))}</div>
      )}
    </section>
  );
}

export function InventoryMovementSummary({
  selectedStock,
  onOpenDrawer,
  onOpenPhysicalCountDrawer,
  onOpenTransferDrawer,
}: {
  selectedStock: InventoryStock | null;
  onOpenDrawer: () => void;
  onOpenPhysicalCountDrawer: () => void;
  onOpenTransferDrawer: () => void;
}) {
  return (
    <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-xs uppercase tracking-wide text-[#6f7a73]">Acciones de Inventario</p>
          <h2 className="text-xl font-semibold text-[#1d2720]">
            {selectedStock ? selectedStock.productName : "Selecciona producto"}
          </h2>
        </div>
        <div className="flex flex-wrap gap-2">
          <button className="app-button-secondary" onClick={onOpenPhysicalCountDrawer} type="button">
            Conteo Físico
          </button>
          <button className="app-button-secondary" onClick={onOpenTransferDrawer} type="button">
            Transferencia
          </button>
          <button className="app-button" disabled={!selectedStock} onClick={onOpenDrawer} type="button">
            Registrar Movimiento
          </button>
        </div>
      </div>
      {selectedStock ? (
        <dl className="mt-5 grid gap-3 sm:grid-cols-3">
          <Metric label="Existencia" value={String(selectedStock.quantityOnHand)} />
          <Metric label="Mínimo" value={String(selectedStock.reorderPoint)} />
          <Metric label="Costo promedio" value={selectedStock.averageCostCents == null ? "N/A" : formatMoney(selectedStock.averageCostCents, selectedStock.currency)} />
        </dl>
      ) : <p className="mt-4 text-sm text-[#5d6b63]">Selecciona un producto para registrar entradas, consumo interno, ajustes o mermas.</p>}
    </section>
  );
}

export function InventoryMovementsTable({ loading, movements, page, totalPages, onExportCsv, onPageChange }: {
  loading: boolean;
  movements: InventoryMovement[];
  page: number;
  totalPages: number;
  onExportCsv: () => void;
  onPageChange: (page: number) => void;
}) {
  return (
    <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-xl font-semibold">Movimientos recientes</h2>
        <button className="app-button-secondary" onClick={onExportCsv} type="button">Exportar CSV</button>
      </div>
      {loading ? <div className="mt-4 h-24 animate-pulse rounded-md bg-[#eef1eb]" role="status" aria-label="Cargando movimientos" /> : null}
      {!loading ? <div className="mt-4 overflow-x-auto">
        <table className="w-full min-w-[760px] border-collapse text-sm">
          <thead><tr className="border-b border-[#e4e8df] text-left text-xs uppercase tracking-wide text-[#6f7a73]">
            <th className="py-3 pr-4">Fecha</th><th className="py-3 pr-4">Producto</th><th className="py-3 pr-4">Tipo</th>
            <th className="py-3 pr-4">Cantidad</th><th className="py-3 pr-4">Existencia</th><th className="py-3">Motivo</th>
          </tr></thead>
          <tbody>{movements.map((movement) => (
            <tr className="border-b border-[#eef0eb]" key={movement.id}>
              <td className="py-3 pr-4">{formatDateTime(movement.occurredAt)}</td><td className="py-3 pr-4 font-medium">{movement.productName}</td>
              <td className="py-3 pr-4"><span className="rounded bg-[#f0f4ef] px-2 py-0.5 text-xs font-semibold text-[#31543d]">{movement.movementType}</span></td><td className={`py-3 pr-4 font-semibold ${movement.quantityDelta < 0 ? "text-[#8c2f2f]" : "text-[#31543d]"}`}>{movement.quantityDelta > 0 ? "+" : ""}{movement.quantityDelta}</td>
              <td className="py-3 pr-4">{movement.quantityBefore} → {movement.quantityAfter}</td><td className="py-3 text-[#5d6b63]">{movement.reason}</td>
            </tr>
          ))}</tbody>
        </table>
      </div> : null}
      <Pagination page={page} totalPages={totalPages} onPageChange={onPageChange} label="movimientos" />
    </section>
  );
}

export function InventoryMovementDrawer({ movementType, open, quantity, reason, reorderPoint, saving,
  selectedProductId, stock, unitCostCents, onClose, onMovementTypeChange, onProductChange,
  onQuantityChange, onReasonChange, onReorderPointChange, onSubmit, onUnitCostChange }: {
  movementType: ManualMovementType; open: boolean; quantity: number; reason: string; reorderPoint: string;
  saving: boolean; selectedProductId: string; stock: InventoryStock[]; unitCostCents: string;
  onClose: () => void; onMovementTypeChange: (value: ManualMovementType) => void;
  onProductChange: (value: string) => void; onQuantityChange: (value: number) => void;
  onReasonChange: (value: string) => void; onReorderPointChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void; onUnitCostChange: (value: string) => void;
}) {
  return (
    <AppDrawer description="Registra entradas, consumo interno, ajustes, mermas o devoluciones para el producto seleccionado." onClose={onClose} open={open} title="Registrar movimiento">
      <form className="grid gap-4" onSubmit={onSubmit}>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Producto"><select className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm" onChange={(event) => onProductChange(event.target.value)} value={selectedProductId}>{stock.map((item) => <option key={item.productId} value={item.productId}>{item.productName}</option>)}</select></Field>
          <Field label="Tipo"><select className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm" onChange={(event) => onMovementTypeChange(event.target.value as ManualMovementType)} value={movementType}>{movementTypes.map((type) => <option key={type.value} value={type.value}>{type.label}</option>)}</select></Field>
          <Field label="Cantidad"><input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" min={1} onChange={(event) => onQuantityChange(Number(event.target.value))} type="number" value={quantity} /></Field>
          <Field label="Costo unitario"><input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" min={0} onChange={(event) => onUnitCostChange(event.target.value)} step="0.01" type="number" value={unitCostCents} /></Field>
          <Field label="Mínimo"><input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" min={0} onChange={(event) => onReorderPointChange(event.target.value)} type="number" value={reorderPoint} /></Field>
        </div>
        <Field label="Motivo"><textarea className="min-h-24 rounded-md border border-[#cdd5ca] px-3 py-2 text-sm" onChange={(event) => onReasonChange(event.target.value)} value={reason} /></Field>
        <button className="form-primary-button" disabled={saving || !selectedProductId || !reason.trim()} type="submit">{saving ? "Registrando..." : "Registrar movimiento"}</button>
      </form>
    </AppDrawer>
  );
}

export function InventoryPhysicalCountDrawer({
  counts,
  open,
  saving,
  onApproveCount,
  onCancelCount,
  onClose,
  onSaveCountItem,
  onStartCount,
}: {
  counts: InventoryPhysicalCount[];
  open: boolean;
  saving: boolean;
  onApproveCount: (countId: string) => void;
  onCancelCount: (countId: string) => void;
  onClose: () => void;
  onSaveCountItem: (countId: string, productId: string, countedQty: number | null, notes?: string) => void;
  onStartCount: (notes?: string) => void;
}) {
  const [notes, setNotes] = useState("");
  const activeCount = counts.find((c) => c.status === "IN_PROGRESS" || c.status === "PENDING_APPROVAL") ?? null;

  return (
    <AppDrawer description="Inicia una auditoría de existencias físicas para ajustar automáticamente las diferencias del sistema." onClose={onClose} open={open} title="Auditoría / Conteo Físico">
      <div className="grid gap-6">
        {!activeCount ? (
          <div className="rounded-md border border-[#d8ddd2] bg-[#fdfefc] p-4 grid gap-3">
            <h3 className="font-semibold text-sm text-[#1d2720]">Iniciar nueva sesión de conteo</h3>
            <label className="grid gap-1 text-sm"><span className="font-medium text-[#42514a]">Notas / Referencia</span>
              <input className="h-10 rounded-md border border-[#cdd5ca] px-3" placeholder="Ej. Conteo mensual de cierre" value={notes} onChange={(e) => setNotes(e.target.value)} />
            </label>
            <button
              className="app-button"
              disabled={saving}
              onClick={() => { onStartCount(notes); setNotes(""); }}
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
                <p className="text-xs text-[#5d6b63]">Creado: {formatDateTime(activeCount.createdAt)}</p>
              </div>
              <span className="rounded bg-[#eef7f0] px-2 py-1 text-xs font-semibold text-[#31543d]">{activeCount.status}</span>
            </div>

            <div className="max-h-60 overflow-y-auto grid gap-2">
              {activeCount.items.map((item) => (
                <div className="rounded border border-[#e4e8df] p-3 grid sm:grid-cols-[1fr_auto] gap-2 items-center text-sm" key={item.id}>
                  <div>
                    <p className="font-medium">{item.productName}</p>
                    <p className="text-xs text-[#6f7a73]">Sistema: {item.systemQuantity} | Varianza: {item.varianceQuantity ?? 0}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <input
                      className="w-20 h-9 rounded border border-[#cdd5ca] px-2 text-right"
                      defaultValue={item.countedQuantity ?? ""}
                      onBlur={(e) => {
                        const val = e.target.value.trim() !== "" ? Number(e.target.value) : null;
                        onSaveCountItem(activeCount.id, item.productId, val);
                      }}
                      placeholder="Contado"
                      type="number"
                    />
                  </div>
                </div>
              ))}
            </div>

            <div className="flex gap-2 justify-end pt-2">
              <button className="app-button-secondary" disabled={saving} onClick={() => onCancelCount(activeCount.id)} type="button">
                Cancelar Conteo
              </button>
              <button className="app-button" disabled={saving} onClick={() => onApproveCount(activeCount.id)} type="button">
                Aprobar y Ajustar Stock
              </button>
            </div>
          </div>
        )}

        <div>
          <h4 className="text-sm font-semibold text-[#1d2720] mb-2">Historial de Conteos</h4>
          <div className="grid gap-2 max-h-48 overflow-y-auto">
            {counts.map((c) => (
              <div className="rounded border border-[#eef0eb] p-2 text-xs flex justify-between items-center" key={c.id}>
                <div>
                  <span className="font-medium">{formatDateTime(c.createdAt)}</span>
                  <p className="text-[#6f7a73]">{c.notes || "Sin notas"}</p>
                </div>
                <span className="font-semibold text-[#31543d]">{c.status}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </AppDrawer>
  );
}

export function InventoryTransferDrawer({
  branches,
  open,
  saving,
  stock,
  transfers,
  onCancelTransfer,
  onClose,
  onCreateTransfer,
  onReceiveTransfer,
  onShipTransfer,
}: {
  branches: BranchSummary[];
  open: boolean;
  saving: boolean;
  stock: InventoryStock[];
  transfers: InventoryTransfer[];
  onCancelTransfer: (transferId: string) => void;
  onClose: () => void;
  onCreateTransfer: (destBranchId: string, notes: string, items: Array<{ productId: string; requestedQuantity: number }>) => void;
  onReceiveTransfer: (transferId: string) => void;
  onShipTransfer: (transferId: string) => void;
}) {
  const [destBranchId, setDestBranchId] = useState("");
  const [notes, setNotes] = useState("");
  const [selectedProductId, setSelectedProductId] = useState("");
  const [requestedQty, setRequestedQty] = useState(1);
  const [items, setItems] = useState<Array<{ productId: string; productName: string; requestedQuantity: number }>>([]);

  function addItem() {
    if (!selectedProductId || requestedQty <= 0) return;
    const prod = stock.find((s) => s.productId === selectedProductId);
    if (!prod) return;
    setItems((prev) => [...prev.filter((i) => i.productId !== selectedProductId), { productId: selectedProductId, productName: prod.productName, requestedQuantity: requestedQty }]);
  }

  function submitTransfer() {
    if (!destBranchId || items.length === 0) return;
    onCreateTransfer(destBranchId, notes, items.map((i) => ({ productId: i.productId, requestedQuantity: i.requestedQuantity })));
    setItems([]); setNotes("");
  }

  return (
    <AppDrawer description="Envía o recibe mercancía entre sucursales de la misma organización." onClose={onClose} open={open} title="Transferencia entre Sucursales">
      <div className="grid gap-6">
        <div className="rounded-md border border-[#d8ddd2] bg-[#fdfefc] p-4 grid gap-3 text-sm">
          <h3 className="font-semibold text-[#1d2720]">Solicitar Transferencia</h3>
          <Field label="Sucursal Destino">
            <select className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3" value={destBranchId} onChange={(e) => setDestBranchId(e.target.value)}>
              <option value="">-- Seleccionar sucursal --</option>
              {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select>
          </Field>
          <div className="grid sm:grid-cols-[1fr_auto_auto] gap-2 items-end">
            <Field label="Producto">
              <select className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3" value={selectedProductId} onChange={(e) => setSelectedProductId(e.target.value)}>
                <option value="">-- Producto --</option>
                {stock.map((s) => <option key={s.productId} value={s.productId}>{s.productName}</option>)}
              </select>
            </Field>
            <Field label="Cantidad">
              <input className="h-10 w-20 rounded-md border border-[#cdd5ca] px-2" min={1} value={requestedQty} onChange={(e) => setRequestedQty(Number(e.target.value))} type="number" />
            </Field>
            <button className="app-button-secondary h-10" onClick={addItem} type="button">Agregar</button>
          </div>

          {items.length > 0 && (
            <div className="grid gap-1 border-t pt-2">
              {items.map((i) => (
                <div className="flex justify-between text-xs py-1" key={i.productId}>
                  <span>{i.productName}</span>
                  <span className="font-semibold">{i.requestedQuantity} unidades</span>
                </div>
              ))}
            </div>
          )}

          <Field label="Notas">
            <input className="h-10 rounded-md border border-[#cdd5ca] px-3" placeholder="Motivo de la transferencia" value={notes} onChange={(e) => setNotes(e.target.value)} />
          </Field>
          <button className="app-button" disabled={saving || !destBranchId || items.length === 0} onClick={submitTransfer} type="button">
            {saving ? "Procesando..." : "Crear Solicitud de Transferencia"}
          </button>
        </div>

        <div>
          <h4 className="text-sm font-semibold text-[#1d2720] mb-2">Transferencias Activas</h4>
          <div className="grid gap-2 max-h-60 overflow-y-auto">
            {transfers.map((t) => (
              <div className="rounded border border-[#e4e8df] p-3 text-xs grid gap-2" key={t.id}>
                <div className="flex justify-between items-center">
                  <span className="font-semibold">{t.originBranchName} → {t.destinationBranchName}</span>
                  <span className="rounded bg-[#f0f4ef] px-2 py-0.5 font-semibold text-[#31543d]">{t.status}</span>
                </div>
                <p className="text-[#5d6b63]">{t.notes || "Sin observaciones"}</p>
                <div className="flex gap-2 justify-end">
                  {t.status === "REQUESTED" && (
                    <button className="app-button-secondary py-1 text-xs" disabled={saving} onClick={() => onShipTransfer(t.id)} type="button">Enviar</button>
                  )}
                  {t.status === "SHIPPED" && (
                    <button className="app-button py-1 text-xs" disabled={saving} onClick={() => onReceiveTransfer(t.id)} type="button">Recibir</button>
                  )}
                  {t.status !== "RECEIVED" && t.status !== "CANCELLED" && (
                    <button className="app-button-secondary py-1 text-xs text-[#8c2f2f]" disabled={saving} onClick={() => onCancelTransfer(t.id)} type="button">Cancelar</button>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </AppDrawer>
  );
}

function Pagination({ label, onPageChange, page, totalPages }: { label: string; onPageChange: (page: number) => void; page: number; totalPages: number; }) {
  if (totalPages <= 1) return null;
  return <nav aria-label={`Paginacion de ${label}`} className="mt-4 flex items-center justify-between gap-3 text-sm">
    <button className="app-button-secondary" disabled={page <= 0} onClick={() => onPageChange(page - 1)} type="button">Anterior</button>
    <span>Pagina {page + 1} de {totalPages}</span>
    <button className="app-button-secondary" disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)} type="button">Siguiente</button>
  </nav>;
}
function Field({ label, children }: { label: string; children: React.ReactNode }) { return <label className="grid gap-2 text-sm font-medium text-[#42514a]">{label}{children}</label>; }
function Metric({ label, value }: { label: string; value: string }) { return <div><dt className="font-medium text-[#6f7a73]">{label}</dt><dd className="mt-1 font-semibold">{value}</dd></div>; }
function formatMoney(cents: number, currency: string) { return new Intl.NumberFormat("es-MX", { style: "currency", currency }).format(cents / 100); }
function formatDateTime(value: string) { return new Intl.DateTimeFormat("es-MX", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)); }
