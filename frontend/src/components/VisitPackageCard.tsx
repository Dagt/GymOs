"use client";

import Link from "next/link";
import { FormEvent, useEffect, useRef, useState } from "react";
import { ReceptionPointRecovery, useReceptionPoint } from "@/components/ReceptionPointRecovery";
import { runSaleWrite } from "@/lib/sale-write";
import { AppDrawer } from "@/components/AppDrawer";
import { AppNotice } from "@/components/AppNotice";
import { contractErrorMessage } from "@/lib/contract-error";
import {
  getCurrentVisitPackage,
  getVisitPackageHistory,
  getPaymentConfiguration,
  getVisitPackagePlans,
  purchaseVisitPackage,
  quoteVisitPackage,
  type StoredSession,
  type VisitPackageEntitlement,
  type VisitPackagePlanSummary,
  type VisitPackagePurchasePayload,
  type VisitPackageQuote,
} from "@/lib/api";

export function VisitPackageCard({
  clientId,
  clientName,
  session,
  source = "profile",
}: {
  clientId: string;
  clientName: string;
  session: StoredSession;
  source?: "profile" | "reception";
}) {
  const point = useReceptionPoint(session, clientId, "package");
  const [plans, setPlans] = useState<VisitPackagePlanSummary[]>([]);
  const [current, setCurrent] = useState<VisitPackageEntitlement | null>(null);
  const [history, setHistory] = useState<VisitPackageEntitlement[]>([]);
  const [selectedPlanId, setSelectedPlanId] = useState("");
  const [quote, setQuote] = useState<VisitPackageQuote | null>(null);
  const [method, setMethod] = useState<VisitPackagePurchasePayload["method"]>("CASH");
  const [reference, setReference] = useState("");
  const [manualCardEnabled, setManualCardEnabled] = useState(false);
  const [mercadoPagoEnabled, setMercadoPagoEnabled] = useState(false);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const inFlight = useRef(false);
  const [refreshFailed, setRefreshFailed] = useState(false);
  const visibleQuote = quote?.packagePlanId === selectedPlanId ? quote : null;
  const usesReference = method === "TRANSFER" || method === "CARD_MANUAL";
  const canSell = Boolean(session.user.branchId) && hasPermission(session, "sale.create");

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      try {
        const [loadedPlans, entitlement, packageHistory] = await Promise.all([
          canSell ? getVisitPackagePlans(session.accessToken) : Promise.resolve([]),
          getCurrentVisitPackage(session.accessToken, clientId),
          source === "profile" ? getVisitPackageHistory(session.accessToken, clientId) : Promise.resolve([]),
        ]);
        if (!active) return;
        const activePlans = loadedPlans.filter((plan) => plan.status === "ACTIVE");
        setPlans(activePlans);
        setSelectedPlanId((value) => value || activePlans[0]?.id || "");
        setCurrent(entitlement);
        setHistory(packageHistory);
        if (canSell && (hasPermission(session, "payment.settings.read") || hasPermission(session, "payment.settings.manage"))) {
          const payment = await getPaymentConfiguration(session.accessToken);
          if (!active) return;
          setManualCardEnabled(payment.receptionManualTerminalEnabled);
          setMercadoPagoEnabled(payment.mercadoPagoReady);
        }
      } catch (exception) {
        if (active) setError(contractErrorMessage(exception, "No fue posible cargar el paquete de visitas."));
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => { active = false; };
  }, [canSell, clientId, session, source]);

  useEffect(() => {
    if (!open || !selectedPlanId || current) return;
    let active = true;
    quoteVisitPackage(session.accessToken, clientId, selectedPlanId)
      .then((result) => { if (active) setQuote(result); })
      .catch((exception) => { if (active) setError(contractErrorMessage(exception, "No fue posible cotizar el paquete.")); });
    return () => { active = false; };
  }, [clientId, current, open, selectedPlanId, session.accessToken]);

  function beginPurchase() {
    setError(null);
    setMessage(null);
    setQuote(null);
    setOpen(true);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (inFlight.current || !canSell || point.pending || !visibleQuote || current || refreshFailed) return;
    inFlight.current = true;
    setSaving(true);
    setError(null);
    const paymentReference = usesReference ? reference.trim() : "";
    try {
      const payload = {
        clientId,
        packagePlanId: selectedPlanId,
        method,
        reference: paymentReference || null,
        validateNow: method !== "CARD_MERCADO_PAGO",
        notes: "Venta de paquete de visitas",
      };
      const result = await runSaleWrite(session, `reception-package:${clientId}`, payload,
        (idempotencyKey) => purchaseVisitPackage(session.accessToken, { ...payload, idempotencyKey }));
      setOpen(false);
      setReference("");
      setMethod("CASH");
      if (method === "CARD_MERCADO_PAGO" && result.saleStatus === "PENDING_PAYMENT") {
        await sendPoint(result);
        return;
      }
      setMessage(result.entitlementStatus === "ACTIVE"
        ? `Paquete activado. Venta ${result.saleNumber}.`
        : `Venta ${result.saleNumber} creada; el paquete se activará al confirmar el pago.`);
      await refreshPackage();
    } catch (exception) {
      setError(contractErrorMessage(exception, "No fue posible vender el paquete."));
    } finally {
      inFlight.current = false;
      setSaving(false);
    }
  }

  async function refreshPackage() {
    try {
      setCurrent(await getCurrentVisitPackage(session.accessToken, clientId));
      if (source === "profile") setHistory(await getVisitPackageHistory(session.accessToken, clientId));
      setRefreshFailed(false);
    } catch (exception) {
      setRefreshFailed(true);
      setError(contractErrorMessage(exception, "Recarga la página para consultar el saldo actualizado."));
    }
  }

  async function sendPoint(sale: Parameters<typeof point.send>[0]) {
    setError(null);
    try {
      const message = await point.send(sale);
      if (message) setMessage(message);
    } catch (exception) {
      setError(contractErrorMessage(exception, "Consulta el estado de la venta en el historial."));
    } finally {
      await refreshPackage();
    }
  }

  return (
    <>
      <section className="app-card grid gap-4 p-5">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <p className="app-eyebrow">Accesos consumibles</p>
            <h2 className="app-card-title">Paquete de visitas</h2>
          </div>
          {canSell && !current && !point.pending ? <button className="app-button h-9" disabled={loading || saving || refreshFailed || plans.length === 0} type="button" onClick={beginPurchase}>{source === "reception" ? "Vender paquete de visitas" : "Vender paquete"}</button> : null}
        </div>
        {error && !open ? <AppNotice message={error} tone="error" /> : null}
        {point.pending && canSell ? <ReceptionPointRecovery sale={point.pending} sending={point.sending}
          canReadSale={hasPermission(session, "sale.read")}
          onRetry={() => { if (point.pending) void sendPoint(point.pending); }} /> : null}
        {message ? <AppNotice message={message} tone="success" /> : null}
        {loading ? <p className="text-sm text-[var(--brand-muted)]">Cargando saldo...</p> : null}
        {!loading && current ? (
          <div className="rounded-xl border border-[var(--brand-line)] bg-[#fbfcfa] p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <strong>{current.packageName}</strong>
              <span className={current.status === "ACTIVE" ? "rounded-full bg-[#eaf6ed] px-2 py-1 text-xs font-semibold text-[#28733e]" : "rounded-full bg-[#fff3df] px-2 py-1 text-xs font-semibold text-[#8a5a22]"}>{statusLabel(current.status)}</span>
            </div>
            {current.status === "ACTIVE" ? (
              <p className="mt-2 text-2xl font-semibold text-[var(--brand-primary)]">{current.remainingVisits} <span className="text-sm font-normal text-[var(--brand-muted)]">de {current.totalVisits} visitas</span></p>
            ) : <p className="mt-2 text-sm text-[var(--brand-muted)]">El pago está pendiente; no consume visitas todavía.</p>}
            {current.status === "PENDING_PAYMENT" && !point.pending && hasPermission(session, "sale.read") ? (
              <Link className="mt-2 inline-block text-sm underline" href={`/sales/${current.saleId}`}>Consultar venta</Link>
            ) : null}
            {current.validUntil ? <p className="mt-1 text-xs text-[var(--brand-muted)]">Vigente hasta {formatDate(current.validUntil)}.</p> : null}
          </div>
        ) : null}
        {!loading && !refreshFailed && !current ? <p className="text-sm text-[var(--brand-muted)]">El socio no tiene un paquete pendiente ni activo.</p> : null}
        {!loading && !current && canSell && plans.length === 0 ? <p className="text-xs text-[#8a5a22]">No hay paquetes activos disponibles en esta sucursal.</p> : null}
        {!loading && history.length > 0 ? (
          <section className="grid gap-2 border-t border-[var(--brand-line)] pt-4">
            <h3 className="text-sm font-semibold text-[#111815]">Historial de paquetes</h3>
            {history.map((entitlement) => (
              <article className="rounded-lg border border-[var(--brand-line)] bg-white p-3" key={entitlement.id}>
                <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
                  <strong>{entitlement.packageName}</strong>
                  <span className="text-xs text-[var(--brand-muted)]">{statusLabel(entitlement.status)}</span>
                </div>
                <p className="mt-1 text-xs text-[var(--brand-muted)]">
                  {entitlement.remainingVisits} de {entitlement.totalVisits} visitas
                  {entitlement.validFrom ? ` · ${formatDate(entitlement.validFrom)}` : ""}
                  {entitlement.validUntil ? ` a ${formatDate(entitlement.validUntil)}` : ""}
                </p>
                {entitlement.consumptions.length > 0 ? (
                  <div className="mt-2 grid gap-1 border-t border-[var(--brand-line)] pt-2">
                    {entitlement.consumptions.slice(0, 5).map((consumption) => (
                      <p className="text-xs text-[var(--brand-muted)]" key={consumption.id}>
                        {formatDateTime(consumption.occurredAt)} · {consumption.branchName}
                        {consumption.deviceName ? ` · ${consumption.deviceName}` : ""}
                        {` · saldo ${consumption.remainingAfter}`}
                        {consumption.status === "REVERSED" ? " · revertido" : ""}
                      </p>
                    ))}
                  </div>
                ) : null}
              </article>
            ))}
          </section>
        ) : null}
      </section>

      <AppDrawer onClose={() => { if (!inFlight.current) setOpen(false); }} open={open} title="Vender paquete de visitas">
        <form onSubmit={submit}>
          <fieldset className="grid gap-4" disabled={saving}>
            <p className="text-sm">Socio: <strong>{clientName}</strong></p>
            {error ? <AppNotice message={error} tone="error" /> : null}
            <label className="form-field" htmlFor="visitPackagePlan">
              <span>Paquete</span>
              <select className="app-input" id="visitPackagePlan" value={selectedPlanId} onChange={(event) => { setError(null); setQuote(null); setSelectedPlanId(event.target.value); }}>
                {plans.map((plan) => <option key={plan.id} value={plan.id}>{plan.name} · {money(plan.priceCents, plan.currency)}</option>)}
              </select>
            </label>
            <section className="rounded-xl border border-[var(--brand-line)] bg-[#fbfcfa] p-4 text-sm">
              {!visibleQuote ? <p className="text-[var(--brand-muted)]">{error ? "Cotización no disponible." : "Calculando cotización..."}</p> : (
                <div className="grid gap-2">
                  <div className="flex justify-between gap-3"><span>{visibleQuote.totalVisits} visitas · {visibleQuote.validityDays} días</span><strong>{money(visibleQuote.packagePriceCents, visibleQuote.currency)}</strong></div>
                  {visibleQuote.registrationFeeCents > 0 ? <div className="flex justify-between gap-3"><span>{visibleQuote.registrationFeeLabel ?? "Inscripción"}</span><strong>{money(visibleQuote.registrationFeeCents, visibleQuote.currency)}</strong></div> : null}
                  <div className="flex justify-between gap-3 border-t border-[var(--brand-line)] pt-2 text-base"><strong>Total</strong><strong>{money(visibleQuote.totalCents, visibleQuote.currency)}</strong></div>
                </div>
              )}
            </section>
            <label className="form-field" htmlFor="visitPackageMethod">
              <span>Método de pago</span>
              <select className="app-input" id="visitPackageMethod" value={method} onChange={(event) => { setReference(""); setMethod(event.target.value as VisitPackagePurchasePayload["method"]); }}>
                <option value="CASH">Efectivo</option>
                <option value="TRANSFER">Transferencia</option>
                {manualCardEnabled ? <option value="CARD_MANUAL">Tarjeta manual</option> : null}
                {mercadoPagoEnabled ? <option value="CARD_MERCADO_PAGO">Mercado Pago Point</option> : null}
              </select>
            </label>
            {usesReference ? (
              <label className="form-field">
                <span>Referencia (opcional)</span>
                <input className="app-input" maxLength={160} value={reference} onChange={(event) => setReference(event.target.value)} />
              </label>
            ) : null}
            <button className="app-button h-11" disabled={saving || !visibleQuote} type="submit">{saving ? "Procesando..." : "Cobrar y crear saldo"}</button>
          </fieldset>
        </form>
      </AppDrawer>
    </>
  );
}

function hasPermission(session: StoredSession, permission: string) {
  return session.user.permissions.includes(permission)
    || session.user.permissions.includes("platform.admin")
    || session.user.platformAdmin;
}

function statusLabel(status: VisitPackageEntitlement["status"]) {
  return status === "ACTIVE" ? "Activo" : status === "PENDING_PAYMENT" ? "Pago pendiente" : status;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", timeZone: "UTC" }).format(new Date(`${value}T00:00:00Z`));
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function money(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", { style: "currency", currency }).format(cents / 100);
}
