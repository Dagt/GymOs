"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { createMercadoPagoPointOrder, type StoredSession } from "@/lib/api";
import { ApiResponseError } from "@/lib/api/client";
import { contractErrorMessage } from "@/lib/contract-error";

type PointSale = { saleId: string; saleNumber: string; paymentId: string | null };
const pendingSales = new Map<string, PointSale>();

export function useReceptionPoint(session: StoredSession, clientId: string, operation: "membership" | "package") {
  const { organizationId, branchId, userId } = session.user;
  const key = `gymfort:reception-point:${organizationId}:${branchId}:${userId}:${clientId}:${operation}`;
  const [pending, setPending] = useState<PointSale | null>(() => {
    try {
      return pendingSales.get(key) ?? JSON.parse(sessionStorage.getItem(key) ?? "null");
    } catch {
      return pendingSales.get(key) ?? null;
    }
  });
  const [sending, setSending] = useState(false);
  const inFlight = useRef(false);

  async function send({ saleId, saleNumber, paymentId }: PointSale) {
    if (inFlight.current) return null;
    inFlight.current = true;
    setSending(true);
    const receipt = { saleId, saleNumber, paymentId };
    pendingSales.set(key, receipt);
    setPending(receipt);
    try {
      sessionStorage.setItem(key, JSON.stringify(receipt));
    } catch {
      // Keep the receipt in memory when browser storage is unavailable.
    }
    try {
      if (!paymentId) throw new Error("El API no devolvió el identificador del pago.");
      let message = `Venta ${saleNumber} registrada. Espera la confirmación del pago en Point.`;
      try {
        await createMercadoPagoPointOrder(session.accessToken, paymentId);
        return message;
      } catch (error) {
        const code = error instanceof ApiResponseError ? error.payload?.code : undefined;
        if (code === "PAYMENT_ALREADY_COMPLETED") {
          message = `El servidor confirmó el pago de la venta ${saleNumber}. Consulta el saldo actualizado.`;
        } else if (code === "PAYMENT_ALREADY_REJECTED") {
          message = `El pago de la venta ${saleNumber} quedó cerrado. Consulta su estado.`;
        } else {
          throw error;
        }
      }
      pendingSales.delete(key);
      setPending(null);
      try { sessionStorage.removeItem(key); } catch { /* Storage may be unavailable. */ }
      return message;
    } catch (error) {
      throw new Error(`La venta ${saleNumber} ya está registrada. Falta confirmar el envío a Point. ${contractErrorMessage(error, "No fue posible contactar la terminal.")}`);
    } finally {
      inFlight.current = false;
      setSending(false);
    }
  }

  return { pending, sending, send };
}

export function ReceptionPointRecovery({ sale, sending, canReadSale, onRetry }: {
  sale: PointSale;
  sending: boolean;
  canReadSale: boolean;
  onRetry: () => void;
}) {
  return (
    <div className="grid gap-2 rounded-lg border border-[var(--brand-line)] p-3 text-sm">
      <p>Venta {sale.saleNumber} registrada. Consulta el estado del pago o recupera el envío a la terminal.</p>
      {sale.paymentId ? (
        <button className="app-button-secondary" disabled={sending} onClick={onRetry} type="button">
          {sending ? "Enviando a Point…" : "Recuperar envío a Point"}
        </button>
      ) : null}
      {canReadSale ? <Link className="underline" href={`/sales/${sale.saleId}`}>Consultar venta</Link> : null}
    </div>
  );
}
