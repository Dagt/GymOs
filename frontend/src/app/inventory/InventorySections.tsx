import { type FormEvent } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import {
  type InventoryMovement,
  type InventoryMovementType,
  type InventoryStock,
  type InventoryStockSearchParams,
} from "@/lib/api";

type ManualMovementType = Exclude<InventoryMovementType, "SALE">;

export const movementTypes: Array<{ value: ManualMovementType; label: string }> = [
  { value: "PURCHASE", label: "Compra" },
  { value: "ADJUSTMENT_IN", label: "Ajuste entrada" },
  { value: "ADJUSTMENT_OUT", label: "Ajuste salida" },
  { value: "WASTE", label: "Merma" },
  { value: "RETURN", label: "Devolucion" },
  { value: "INITIAL", label: "Inicial" },
];

export function InventoryStatusMessages({ error, message }: { error: string | null; message: string | null }) {
  return (
    <>
      {error ? <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f]" role="alert">{error}</p> : null}
      {message ? <p className="rounded-md border border-[#bfd6c7] bg-[#f0f7f2] px-3 py-2 text-sm text-[#31543d]">{message}</p> : null}
    </>
  );
}

export function InventoryStockPanel({ loading, page, query, selectedProductId, sort, stock, total, totalPages,
  onPageChange, onQueryChange, onRefresh, onSelectProduct, onSortChange }: {
  loading: boolean;
  page: number;
  query: string;
  selectedProductId: string;
  sort: NonNullable<InventoryStockSearchParams["sort"]>;
  stock: InventoryStock[];
  total: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  onQueryChange: (value: string) => void;
  onRefresh: () => void;
  onSelectProduct: (item: InventoryStock) => void;
  onSortChange: (value: NonNullable<InventoryStockSearchParams["sort"]>) => void;
}) {
  return (
    <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div><h2 className="text-xl font-semibold">Existencias</h2><p className="mt-1 text-sm text-[#5d6b63]">{total} productos fisicos</p></div>
        <button className="app-button-secondary" onClick={onRefresh} type="button">Actualizar</button>
      </div>
      <div className="mt-4 grid gap-3 sm:grid-cols-[1fr_auto]">
        <label className="grid gap-1 text-sm"><span className="font-medium">Buscar</span>
          <input className="h-10 rounded-md border border-[#cdd5ca] px-3" placeholder="Nombre, SKU o codigo"
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
      {!loading && stock.length === 0 ? <p className="mt-4 text-sm text-[#5d6b63]">No hay productos para esta busqueda.</p> : null}
      {!loading ? <div className="mt-4 grid gap-3">
        {stock.map((item) => (
          <button className={`rounded-md border p-4 text-left transition ${item.productId === selectedProductId ? "border-[#51735f] bg-[#eef7f0]" : "border-[#d8ddd2] bg-white hover:border-[#51735f]"}`}
            key={item.productId} onClick={() => onSelectProduct(item)} type="button">
            <div className="flex items-start justify-between gap-3">
              <div><p className="font-semibold">{item.productName}</p><p className="mt-1 text-xs text-[#6f7a73]">{item.sku}</p></div>
              <span className={`rounded-md px-2 py-1 text-xs font-semibold ${item.lowStock ? "bg-[#fff4f4] text-[#8c2f2f]" : "bg-[#eef7f0] text-[#31543d]"}`}>{item.quantityOnHand}</span>
            </div>
            <p className="mt-3 text-xs text-[#6f7a73]">Minimo {item.reorderPoint} · Costo {item.averageCostCents == null ? "N/A" : formatMoney(item.averageCostCents, item.currency)}</p>
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

export function InventoryMovementSummary({ selectedStock, onOpenDrawer }: { selectedStock: InventoryStock | null; onOpenDrawer: () => void }) {
  return (
    <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <div className="admin-panel-header">
        <div><p>Movimiento</p><h2>{selectedStock ? selectedStock.productName : "Selecciona producto"}</h2></div>
        <button className="app-button" disabled={!selectedStock} onClick={onOpenDrawer} type="button">Registrar movimiento</button>
      </div>
      {selectedStock ? (
        <dl className="mt-5 grid gap-3 sm:grid-cols-3">
          <Metric label="Existencia" value={String(selectedStock.quantityOnHand)} />
          <Metric label="Minimo" value={String(selectedStock.reorderPoint)} />
          <Metric label="Costo promedio" value={selectedStock.averageCostCents == null ? "N/A" : formatMoney(selectedStock.averageCostCents, selectedStock.currency)} />
        </dl>
      ) : <p className="mt-4 text-sm text-[#5d6b63]">Selecciona un producto para registrar entradas, ajustes o mermas.</p>}
    </section>
  );
}

export function InventoryMovementsTable({ loading, movements, page, totalPages, onPageChange }: {
  loading: boolean;
  movements: InventoryMovement[];
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}) {
  return (
    <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <h2 className="text-xl font-semibold">Movimientos recientes</h2>
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
              <td className="py-3 pr-4">{movement.movementType}</td><td className={`py-3 pr-4 font-semibold ${movement.quantityDelta < 0 ? "text-[#8c2f2f]" : "text-[#31543d]"}`}>{movement.quantityDelta > 0 ? "+" : ""}{movement.quantityDelta}</td>
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
    <AppDrawer description="Registra entradas, ajustes, mermas o devoluciones para el producto seleccionado." onClose={onClose} open={open} title="Registrar movimiento">
      <form className="grid gap-4" onSubmit={onSubmit}>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Producto"><select className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm" onChange={(event) => onProductChange(event.target.value)} value={selectedProductId}>{stock.map((item) => <option key={item.productId} value={item.productId}>{item.productName}</option>)}</select></Field>
          <Field label="Tipo"><select className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm" onChange={(event) => onMovementTypeChange(event.target.value as ManualMovementType)} value={movementType}>{movementTypes.map((type) => <option key={type.value} value={type.value}>{type.label}</option>)}</select></Field>
          <Field label="Cantidad"><input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" min={1} onChange={(event) => onQuantityChange(Number(event.target.value))} type="number" value={quantity} /></Field>
          <Field label="Costo unitario"><input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" min={0} onChange={(event) => onUnitCostChange(event.target.value)} step="0.01" type="number" value={unitCostCents} /></Field>
          <Field label="Minimo"><input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" min={0} onChange={(event) => onReorderPointChange(event.target.value)} type="number" value={reorderPoint} /></Field>
        </div>
        <Field label="Motivo"><textarea className="min-h-24 rounded-md border border-[#cdd5ca] px-3 py-2 text-sm" onChange={(event) => onReasonChange(event.target.value)} value={reason} /></Field>
        <button className="form-primary-button" disabled={saving || !selectedProductId || !reason.trim()} type="submit">{saving ? "Registrando..." : "Registrar movimiento"}</button>
      </form>
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
