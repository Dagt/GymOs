"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  createInventoryMovement,
  getBranches,
  getCurrentOrganization,
  getInventoryMovements,
  getInventoryStock,
  getLowInventoryStock,
  getStoredSession,
  type BranchSummary,
  type InventoryMovement,
  type InventoryMovementPayload,
  type InventoryMovementType,
  type InventoryStock,
  type OrganizationSummary,
  type StoredSession,
} from "@/lib/api";

const movementTypes: Array<{ value: InventoryMovementType; label: string }> = [
  { value: "PURCHASE", label: "Compra" },
  { value: "ADJUSTMENT_IN", label: "Ajuste entrada" },
  { value: "ADJUSTMENT_OUT", label: "Ajuste salida" },
  { value: "WASTE", label: "Merma" },
  { value: "RETURN", label: "Devolucion" },
  { value: "INITIAL", label: "Inicial" },
  { value: "SALE", label: "Venta manual" },
];

export default function InventoryPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organization, setOrganization] = useState<OrganizationSummary | null>(null);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [stock, setStock] = useState<InventoryStock[]>([]);
  const [lowStock, setLowStock] = useState<InventoryStock[]>([]);
  const [movements, setMovements] = useState<InventoryMovement[]>([]);
  const [selectedProductId, setSelectedProductId] = useState("");
  const [movementType, setMovementType] = useState<InventoryMovementType>("PURCHASE");
  const [quantity, setQuantity] = useState(1);
  const [unitCostCents, setUnitCostCents] = useState("");
  const [reorderPoint, setReorderPoint] = useState("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [showMovementDrawer, setShowMovementDrawer] = useState(false);

  useEffect(() => {
    let active = true;

    async function load() {
      const stored = getStoredSession();
      if (!active) {
        return;
      }
      if (!stored) {
        setLoading(false);
        return;
      }
      setSession(stored);
      try {
        const [loadedOrganization, loadedBranches, loadedStock, loadedLowStock, loadedMovements] = await Promise.all([
          getCurrentOrganization(stored.accessToken).catch(() => null),
          getBranches(stored.accessToken),
          getInventoryStock(stored.accessToken),
          getLowInventoryStock(stored.accessToken),
          getInventoryMovements(stored.accessToken),
        ]);
        if (!active) {
          return;
        }
        setOrganization(loadedOrganization);
        setBranches(loadedBranches);
        setStock(loadedStock);
        setLowStock(loadedLowStock);
        setMovements(loadedMovements);
        setSelectedProductId(loadedStock[0]?.productId ?? "");
        setReorderPoint(String(loadedStock[0]?.reorderPoint ?? 0));
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar inventario.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void load();

    return () => {
      active = false;
    };
  }, []);

  const selectedStock = useMemo(
    () => stock.find((item) => item.productId === selectedProductId) ?? null,
    [selectedProductId, stock],
  );
  const activeBranch = branches.find((branch) => branch.id === session?.user.branchId) ?? null;

  async function refresh(activeSession = session, productId = selectedProductId) {
    if (!activeSession) {
      return;
    }
    const [nextStock, nextLowStock, nextMovements] = await Promise.all([
      getInventoryStock(activeSession.accessToken),
      getLowInventoryStock(activeSession.accessToken),
      getInventoryMovements(activeSession.accessToken, productId || undefined),
    ]);
    setStock(nextStock);
    setLowStock(nextLowStock);
    setMovements(nextMovements);
  }

  async function submitMovement(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !selectedProductId) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const payload: InventoryMovementPayload = {
        productId: selectedProductId,
        movementType,
        quantity,
        unitCostCents: unitCostCents.trim() ? Math.round(Number(unitCostCents) * 100) : null,
        reorderPoint: reorderPoint.trim() ? Number(reorderPoint) : null,
        reason,
      };
      const updated = await createInventoryMovement(session.accessToken, payload);
      setReason("");
      setUnitCostCents("");
      setReorderPoint(String(updated.reorderPoint));
      setMessage(`${updated.productName}: existencia actual ${updated.quantityOnHand}.`);
      await refresh(session, selectedProductId);
      setShowMovementDrawer(false);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible registrar movimiento.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <LoadingScreen text="Cargando inventario..." />;
  }

  if (!session) {
    return (
      <AuthRequiredCard
        message="Necesitas iniciar sesion para administrar inventario."
        title="Inicia sesion"
      />
    );
  }

  return (
    <AppShell
      branchName={activeBranch?.name}
      eyebrow="Operacion"
      mode={activeBranch ? "operation" : undefined}
      organizationName={organization?.brandName || organization?.tradeName}
      session={session}
      subtitle="Existencias, movimientos, ajustes y alertas."
      title="Inventario"
    >
      <section className="grid gap-6 lg:grid-cols-[1fr_1.25fr]">
        <aside className="grid gap-6">
          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="text-xl font-semibold">Existencias</h2>
                <p className="mt-1 text-sm text-[#5d6b63]">{stock.length} productos fisicos</p>
              </div>
              <button
                className="h-10 rounded-md border border-[#cdd5ca] bg-white px-4 text-sm font-semibold text-[#42514a] transition hover:border-[#51735f]"
                onClick={() => refresh()}
                type="button"
              >
                Actualizar
              </button>
            </div>
            <div className="mt-4 grid gap-3">
              {stock.map((item) => (
                <button
                  className={`rounded-md border p-4 text-left transition ${
                    item.productId === selectedProductId
                      ? "border-[#51735f] bg-[#eef7f0]"
                      : "border-[#d8ddd2] bg-white hover:border-[#51735f]"
                  }`}
                  key={item.productId}
                  onClick={() => {
                    setSelectedProductId(item.productId);
                    setReorderPoint(String(item.reorderPoint));
                    void refresh(session, item.productId);
                  }}
                  type="button"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="font-semibold">{item.productName}</p>
                      <p className="mt-1 text-xs text-[#6f7a73]">{item.sku}</p>
                    </div>
                    <span className={`rounded-md px-2 py-1 text-xs font-semibold ${
                      item.lowStock ? "bg-[#fff4f4] text-[#8c2f2f]" : "bg-[#eef7f0] text-[#31543d]"
                    }`}>
                      {item.quantityOnHand}
                    </span>
                  </div>
                  <p className="mt-3 text-xs text-[#6f7a73]">
                    Minimo {item.reorderPoint} · Costo {item.averageCostCents == null ? "N/A" : formatMoney(item.averageCostCents, item.currency)}
                  </p>
                </button>
              ))}
            </div>
          </section>

          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Alertas</h2>
            {lowStock.length === 0 ? (
              <p className="mt-3 text-sm text-[#5d6b63]">Sin productos bajo minimo.</p>
            ) : (
              <div className="mt-4 grid gap-2">
                {lowStock.map((item) => (
                  <div className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm" key={item.productId}>
                    <span className="font-semibold">{item.productName}</span>
                    <span className="ml-2 text-[#8c2f2f]">{item.quantityOnHand} / {item.reorderPoint}</span>
                  </div>
                ))}
              </div>
            )}
          </section>
        </aside>

        <section className="grid gap-6">
          {error ? (
            <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f]">
              {error}
            </p>
          ) : null}
          {message ? (
            <p className="rounded-md border border-[#bfd6c7] bg-[#f0f7f2] px-3 py-2 text-sm text-[#31543d]">
              {message}
            </p>
          ) : null}

          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <div className="admin-panel-header">
              <div>
                <p>Movimiento</p>
                <h2>{selectedStock ? selectedStock.productName : "Selecciona producto"}</h2>
              </div>
              <button className="app-button" disabled={!selectedProductId} onClick={() => setShowMovementDrawer(true)} type="button">
                Registrar movimiento
              </button>
            </div>
            {selectedStock ? (
              <dl className="mt-5 grid gap-3 sm:grid-cols-3">
                <Metric label="Existencia" value={String(selectedStock.quantityOnHand)} />
                <Metric label="Minimo" value={String(selectedStock.reorderPoint)} />
                <Metric label="Costo promedio" value={selectedStock.averageCostCents == null ? "N/A" : formatMoney(selectedStock.averageCostCents, selectedStock.currency)} />
              </dl>
            ) : (
              <p className="mt-4 text-sm text-[#5d6b63]">Selecciona un producto para registrar entradas, ajustes o mermas.</p>
            )}
          </section>

          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Movimientos recientes</h2>
            <div className="mt-4 overflow-x-auto">
              <table className="w-full min-w-[760px] border-collapse text-sm">
                <thead>
                  <tr className="border-b border-[#e4e8df] text-left text-xs uppercase tracking-wide text-[#6f7a73]">
                    <th className="py-3 pr-4">Fecha</th>
                    <th className="py-3 pr-4">Producto</th>
                    <th className="py-3 pr-4">Tipo</th>
                    <th className="py-3 pr-4">Cantidad</th>
                    <th className="py-3 pr-4">Existencia</th>
                    <th className="py-3">Motivo</th>
                  </tr>
                </thead>
                <tbody>
                  {movements.map((movement) => (
                    <tr className="border-b border-[#eef0eb]" key={movement.id}>
                      <td className="py-3 pr-4">{formatDateTime(movement.occurredAt)}</td>
                      <td className="py-3 pr-4 font-medium">{movement.productName}</td>
                      <td className="py-3 pr-4">{movement.movementType}</td>
                      <td className={`py-3 pr-4 font-semibold ${movement.quantityDelta < 0 ? "text-[#8c2f2f]" : "text-[#31543d]"}`}>
                        {movement.quantityDelta > 0 ? "+" : ""}{movement.quantityDelta}
                      </td>
                      <td className="py-3 pr-4">{movement.quantityBefore} → {movement.quantityAfter}</td>
                      <td className="py-3 text-[#5d6b63]">{movement.reason}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </section>
      </section>

      <AppDrawer
        description="Registra entradas, ajustes, mermas o devoluciones para el producto seleccionado."
        onClose={() => setShowMovementDrawer(false)}
        open={showMovementDrawer}
        title="Registrar movimiento"
      >
        <form className="grid gap-4" onSubmit={submitMovement}>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Producto
              <select
                className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm"
                onChange={(event) => {
                  const nextId = event.target.value;
                  setSelectedProductId(nextId);
                  const nextStock = stock.find((item) => item.productId === nextId);
                  setReorderPoint(String(nextStock?.reorderPoint ?? 0));
                }}
                value={selectedProductId}
              >
                {stock.map((item) => (
                  <option key={item.productId} value={item.productId}>
                    {item.productName}
                  </option>
                ))}
              </select>
            </label>
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Tipo
              <select
                className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm"
                onChange={(event) => setMovementType(event.target.value as InventoryMovementType)}
                value={movementType}
              >
                {movementTypes.map((type) => (
                  <option key={type.value} value={type.value}>{type.label}</option>
                ))}
              </select>
            </label>
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Cantidad
              <input
                className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm"
                min={1}
                onChange={(event) => setQuantity(Number(event.target.value))}
                type="number"
                value={quantity}
              />
            </label>
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Costo unitario
              <input
                className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm"
                min={0}
                onChange={(event) => setUnitCostCents(event.target.value)}
                step="0.01"
                type="number"
                value={unitCostCents}
              />
            </label>
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Minimo
              <input
                className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm"
                min={0}
                onChange={(event) => setReorderPoint(event.target.value)}
                type="number"
                value={reorderPoint}
              />
            </label>
          </div>
          <label className="grid gap-2 text-sm font-medium text-[#42514a]">
            Motivo
            <textarea
              className="min-h-24 rounded-md border border-[#cdd5ca] px-3 py-2 text-sm"
              onChange={(event) => setReason(event.target.value)}
              value={reason}
            />
          </label>
          <button
            className="form-primary-button"
            disabled={saving || !selectedProductId || !reason.trim()}
            type="submit"
          >
            {saving ? "Registrando..." : "Registrar movimiento"}
          </button>
        </form>
      </AppDrawer>
    </AppShell>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="font-medium text-[#6f7a73]">{label}</dt>
      <dd className="mt-1 font-semibold">{value}</dd>
    </div>
  );
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency,
  }).format(cents / 100);
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
