"use client";

import { useEffect, useState } from "react";
import {
  getPaymentWebhookHistory,
  getPaymentOperations,
  getRecurringChargeHistory,
  getRecurringMandateHistory,
  type PaymentHistoryPage,
  type PaymentWebhookHistory,
  type PaymentOperations,
  type RecurringChargeHistory,
  type RecurringMandateHistory,
} from "@/lib/api";

type HistoryKind = "charges" | "mandates" | "webhooks";
type HistoryItem = RecurringChargeHistory | RecurringMandateHistory | PaymentWebhookHistory;

export function PaymentOperationsHistoryCard({ accessToken }: { accessToken: string }) {
  const [kind, setKind] = useState<HistoryKind>("charges");
  const [history, setHistory] = useState<PaymentHistoryPage<HistoryItem> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [operations, setOperations] = useState<PaymentOperations | null>(null);

  async function fetchHistory(nextKind: HistoryKind, page = 0) {
    return nextKind === "charges"
      ? await getRecurringChargeHistory(accessToken, page)
      : nextKind === "mandates"
        ? await getRecurringMandateHistory(accessToken, page)
        : await getPaymentWebhookHistory(accessToken, page);
  }

  async function load(nextKind: HistoryKind, page = 0) {
    setLoading(true);
    setError(null);
    try {
      const response = await fetchHistory(nextKind, page);
      setHistory(response);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cargar el historial de cobros.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    let active = true;
    async function loadInitial() {
      try {
        const [response, operationStatus] = await Promise.all([
          fetchHistory(kind),
          getPaymentOperations(accessToken),
        ]);
        if (active) {
          setHistory(response);
          setOperations(operationStatus);
        }
      } catch (exception) {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar el historial de cobros.");
      } finally {
        if (active) setLoading(false);
      }
    }
    void loadInitial();
    return () => {
      active = false;
    };
    // The request is intentionally restarted only when the selected source or session changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accessToken, kind]);

  function select(nextKind: HistoryKind) {
    setKind(nextKind);
    setHistory(null);
    setLoading(true);
    setError(null);
  }

  return (
    <section className="app-card grid scroll-mt-6 gap-4 p-5" id="payment-operations">
      <div>
        <p className="app-eyebrow">Operación de pagos</p>
        <h2 className="text-xl font-semibold">Historial de domiciliación y webhooks</h2>
        <p className="mt-2 text-sm text-[var(--brand-muted)]">
          Información aislada para este gimnasio. Los datos completos de tarjeta y los payloads del proveedor no se muestran.
        </p>
      </div>
      {operations ? (
        <div className={`rounded-md border p-4 ${operations.alertLevel === "CRITICAL" ? "border-[#e5b7b7] bg-[#fff7f7]" : "border-[var(--brand-line)] bg-[var(--brand-surface)]"}`}>
          <strong>{operations.alertLevel === "HEALTHY" ? "Procesamiento saludable" : "Pagos requieren atención"}</strong>
          <p className="mt-1 text-sm text-[var(--brand-muted)]">
            {operations.pastDueMandates} mandatos vencidos · {operations.retryCharges} cargos en reintento · {operations.failedCharges} cargos fallidos · {operations.failedWebhooks} webhooks fallidos · {operations.staleOperations} operaciones estancadas
          </p>
        </div>
      ) : null}
      <div aria-label="Tipo de historial de pagos" className="flex flex-wrap gap-2" role="navigation">
        <HistoryTab active={kind === "charges"} label="Cargos" onClick={() => select("charges")} />
        <HistoryTab active={kind === "mandates"} label="Consentimientos" onClick={() => select("mandates")} />
        <HistoryTab active={kind === "webhooks"} label="Webhooks" onClick={() => select("webhooks")} />
      </div>
      {error ? <p className="form-message error">{error}</p> : null}
      {loading ? <p className="text-sm text-[var(--brand-muted)]">Cargando historial...</p> : null}
      {!loading && history?.items.length === 0 ? (
        <p className="rounded-md border border-[var(--brand-line)] bg-[var(--brand-surface)] p-3 text-sm text-[var(--brand-muted)]">
          No hay registros para mostrar.
        </p>
      ) : null}
      {!loading && history && history.items.length > 0 ? (
        <div className="overflow-x-auto">
          {kind === "charges" ? <ChargeTable items={history.items as RecurringChargeHistory[]} /> : null}
          {kind === "mandates" ? <MandateTable items={history.items as RecurringMandateHistory[]} /> : null}
          {kind === "webhooks" ? <WebhookTable items={history.items as PaymentWebhookHistory[]} /> : null}
        </div>
      ) : null}
      {history ? (
        <div className="flex items-center justify-between gap-3 text-sm">
          <button className="app-button-secondary" disabled={loading || history.page === 0} onClick={() => void load(kind, history.page - 1)} type="button">Anterior</button>
          <span className="text-[var(--brand-muted)]">
            {history.totalElements} registros · Página {history.totalPages === 0 ? 0 : history.page + 1} de {history.totalPages}
          </span>
          <button className="app-button-secondary" disabled={loading || history.page + 1 >= history.totalPages} onClick={() => void load(kind, history.page + 1)} type="button">Siguiente</button>
        </div>
      ) : null}
    </section>
  );
}

function HistoryTab({ active, label, onClick }: { active: boolean; label: string; onClick: () => void }) {
  return <button aria-pressed={active} className={active ? "app-button" : "app-button-secondary"} onClick={onClick} type="button">{label}</button>;
}

function ChargeTable({ items }: { items: RecurringChargeHistory[] }) {
  return <table className="min-w-full text-left text-sm"><thead className="border-b border-[var(--brand-line)] text-xs uppercase text-[var(--brand-muted)]"><tr><th className="px-3 py-2">Vencimiento</th><th className="px-3 py-2">Estado</th><th className="px-3 py-2">Monto</th><th className="px-3 py-2">Intento</th><th className="px-3 py-2">Error</th></tr></thead><tbody>{items.map((item) => <tr className="border-b border-[var(--brand-line)]/70" key={item.id}><td className="px-3 py-3">{formatDate(item.dueOn)}</td><td className="px-3 py-3"><Status value={item.status} /></td><td className="px-3 py-3">{formatMoney(item.amountCents, item.currency)}</td><td className="px-3 py-3">{item.attemptNumber}</td><td className="max-w-md px-3 py-3 text-[#8c2f2f]">{item.lastError ?? "—"}</td></tr>)}</tbody></table>;
}

function MandateTable({ items }: { items: RecurringMandateHistory[] }) {
  return <table className="min-w-full text-left text-sm"><thead className="border-b border-[var(--brand-line)] text-xs uppercase text-[var(--brand-muted)]"><tr><th className="px-3 py-2">Consentimiento</th><th className="px-3 py-2">Estado</th><th className="px-3 py-2">Tarjeta</th><th className="px-3 py-2">Próximo cobro</th><th className="px-3 py-2">Fallos</th></tr></thead><tbody>{items.map((item) => <tr className="border-b border-[var(--brand-line)]/70" key={item.id}><td className="px-3 py-3">{formatDateTime(item.consentedAt)}<small className="block text-[var(--brand-muted)]">{item.termsVersion}</small></td><td className="px-3 py-3"><Status value={item.status} /></td><td className="px-3 py-3">{item.lastFour ? `${item.brand ?? "Tarjeta"} •••• ${item.lastFour}` : "Pendiente"}</td><td className="px-3 py-3">{formatDate(item.nextChargeOn)}</td><td className="px-3 py-3">{item.consecutiveFailures}</td></tr>)}</tbody></table>;
}

function WebhookTable({ items }: { items: PaymentWebhookHistory[] }) {
  return <table className="min-w-full text-left text-sm"><thead className="border-b border-[var(--brand-line)] text-xs uppercase text-[var(--brand-muted)]"><tr><th className="px-3 py-2">Recibido</th><th className="px-3 py-2">Proveedor</th><th className="px-3 py-2">Evento</th><th className="px-3 py-2">Estado</th><th className="px-3 py-2">Error</th></tr></thead><tbody>{items.map((item) => <tr className="border-b border-[var(--brand-line)]/70" key={item.id}><td className="px-3 py-3">{formatDateTime(item.receivedAt)}</td><td className="px-3 py-3">{item.provider}</td><td className="max-w-72 px-3 py-3"><span>{item.eventType ?? "Sin tipo"}</span><small className="block truncate text-[var(--brand-muted)]" title={item.externalEventId}>{item.externalEventId}</small></td><td className="px-3 py-3"><Status value={item.status} /></td><td className="max-w-md px-3 py-3 text-[#8c2f2f]">{item.errorMessage ?? "—"}</td></tr>)}</tbody></table>;
}

function Status({ value }: { value: string }) {
  const tone = ["SUCCEEDED", "COMPLETED", "ACTIVE"].includes(value)
    ? "bg-[#eef7f0] text-[#31543d]"
    : ["FAILED", "PAST_DUE"].includes(value)
      ? "bg-[#fff0f0] text-[#8c2f2f]"
      : "bg-[#eef1f3] text-[#42514a]";
  return <span className={`rounded-md px-2 py-1 text-xs font-semibold ${tone}`}>{paymentStatusLabel(value)}</span>;
}

function paymentStatusLabel(value: string) {
  return ({
    ACTIVE: "Activo",
    CANCELLED: "Cancelado",
    COMPLETED: "Completado",
    FAILED: "Fallido",
    PAST_DUE: "Pago vencido",
    PAUSED: "Pausado",
    PENDING: "Pendiente",
    PENDING_SETUP: "Captura pendiente",
    PROCESSED: "Procesado",
    RECEIVED: "Recibido",
    RETRY_SCHEDULED: "Reintento programado",
    REVOKED: "Revocado",
    SUCCEEDED: "Cobrado",
  } as Record<string, string>)[value] ?? value;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", timeZone: "UTC" }).format(new Date(`${value}T00:00:00Z`));
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
}

function formatMoney(amountCents: number | null, currency: string | null) {
  if (amountCents === null || !currency) return "Pendiente";
  return new Intl.NumberFormat("es-MX", { style: "currency", currency }).format(amountCents / 100);
}
