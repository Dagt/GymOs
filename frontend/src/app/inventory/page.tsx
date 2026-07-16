"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  createInventoryMovement,
  getInventoryMovements,
  getInventoryStock,
  getLowInventoryStock,
  getStoredSession,
  type InventoryMovement,
  type InventoryMovementPayload,
  type InventoryMovementType,
  type InventoryStock,
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
        const [loadedStock, loadedLowStock, loadedMovements] = await Promise.all([
          getInventoryStock(stored.accessToken),
          getLowInventoryStock(stored.accessToken),
          getInventoryMovements(stored.accessToken),
        ]);
        if (!active) {
          return;
        }
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
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible registrar movimiento.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]">
        <p className="rounded-md border border-[#d8ddd2] bg-white px-4 py-3 text-sm text-[#42514a]">
          Cargando inventario...
        </p>
      </main>
    );
  }

  if (!session) {
    return (
      <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]">
        <section className="w-full max-w-md rounded-lg border border-[#d8ddd2] bg-white p-6 shadow-sm">
          <p className="text-sm font-semibold uppercase tracking-wide text-[#51735f]">GymOS</p>
          <h1 className="mt-3 text-2xl font-semibold">Necesitas iniciar sesión</h1>
          <Link
            className="mt-5 inline-flex h-11 items-center justify-center rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30]"
            href="/login"
          >
            Iniciar sesion
          </Link>
        </section>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-[#f6f7f2] text-[#111815]">
      <header className="border-b border-[#d8ddd2] bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="text-sm font-semibold uppercase tracking-wide text-[#51735f]">Operacion</p>
            <h1 className="mt-1 text-3xl font-semibold">Inventario</h1>
            <p className="mt-1 text-sm text-[#5d6b63]">
              Existencias, movimientos, ajustes y alertas.
            </p>
          </div>
          <Link
            className="inline-flex h-10 items-center justify-center rounded-md border border-[#cdd5ca] bg-white px-4 text-sm font-semibold text-[#42514a] transition hover:border-[#51735f]"
            href="/reception"
          >
            Recepcion
          </Link>
        </div>
      </header>

      <section className="mx-auto grid max-w-7xl gap-6 px-6 py-6 lg:grid-cols-[1fr_1.25fr]">
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
            <h2 className="text-xl font-semibold">Movimiento</h2>
            <p className="mt-1 text-sm text-[#5d6b63]">
              {selectedStock ? selectedStock.productName : "Selecciona producto"}
            </p>
            <form className="mt-5 grid gap-4" onSubmit={submitMovement}>
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
                className="h-11 rounded-md bg-[#13251c] px-5 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
                disabled={saving || !selectedProductId || !reason.trim()}
                type="submit"
              >
                {saving ? "Registrando..." : "Registrar movimiento"}
              </button>
            </form>
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
    </main>
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
