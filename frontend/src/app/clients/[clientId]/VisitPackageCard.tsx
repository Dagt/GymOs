"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { AppNotice } from "@/components/AppNotice";
import { TextField } from "@/components/FormFields";
import {
  createMercadoPagoPointOrder,
  getCurrentVisitPackage,
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

export function VisitPackageCard({ clientId, session }: { clientId: string; session: StoredSession }) {
  const [plans, setPlans] = useState<VisitPackagePlanSummary[]>([]);
  const [current, setCurrent] = useState<VisitPackageEntitlement | null>(null);
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
  const operationKey = useRef<string | null>(null);
  const canSell = hasPermission(session, "sale.create");

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true);
      try {
        const [loadedPlans, entitlement] = await Promise.all([
          canSell ? getVisitPackagePlans(session.accessToken) : Promise.resolve([]),
          getCurrentVisitPackage(session.accessToken, clientId),
        ]);
        if (!active) return;
        const activePlans = loadedPlans.filter((plan) => plan.status === "ACTIVE");
        setPlans(activePlans);
        setSelectedPlanId((value) => value || activePlans[0]?.id || "");
        setCurrent(entitlement);
        if (canSell && hasPermission(session, "payment.settings.read")) {
          const payment = await getPaymentConfiguration(session.accessToken);
          if (!active) return;
          setManualCardEnabled(payment.receptionManualTerminalEnabled);
          setMercadoPagoEnabled(payment.mercadoPagoReady);
        }
      } catch (exception) {
        if (active) setError(errorMessage(exception, "No fue posible cargar el paquete de visitas."));
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => { active = false; };
  }, [canSell, clientId, session]);

  useEffect(() => {
    if (!open || !selectedPlanId || current) return;
    let active = true;
    quoteVisitPackage(session.accessToken, clientId, selectedPlanId)
      .then((result) => { if (active) setQuote(result); })
      .catch((exception) => { if (active) setError(errorMessage(exception, "No fue posible cotizar el paquete.")); });
    return () => { active = false; };
  }, [clientId, current, open, selectedPlanId, session.accessToken]);

  function beginPurchase() {
    operationKey.current = null;
    setReference("");
    setMethod("CASH");
    setError(null);
    setMessage(null);
    setQuote(null);
    setOpen(true);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedPlanId || current) return;
    setSaving(true);
    setError(null);
    operationKey.current ??= `visit-package:${clientId}:${selectedPlanId}:${crypto.randomUUID()}`;
    try {
      const result = await purchaseVisitPackage(session.accessToken, {
        clientId,
        packagePlanId: selectedPlanId,
        method,
        reference,
        validateNow: method === "CARD_MERCADO_PAGO" ? false : true,
        notes: "Venta desde ficha de socio",
        idempotencyKey: operationKey.current,
      });
      if (method === "CARD_MERCADO_PAGO") {
        if (!result.paymentId) throw new Error("La venta no devolvió un pago para enviar a Point.");
        await createMercadoPagoPointOrder(session.accessToken, result.paymentId);
      }
      setCurrent(await getCurrentVisitPackage(session.accessToken, clientId));
      setOpen(false);
      setMessage(result.entitlementStatus === "ACTIVE"
        ? `Paquete activado. Venta ${result.saleNumber}.`
        : `Venta ${result.saleNumber} creada; el paquete se activará al confirmar el pago.`);
    } catch (exception) {
      setError(errorMessage(exception, "No fue posible vender el paquete."));
    } finally {
      setSaving(false);
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
          {canSell && !current ? <button className="app-button h-9" disabled={loading || plans.length === 0} type="button" onClick={beginPurchase}>Vender paquete</button> : null}
        </div>
        {error && !open ? <AppNotice message={error} tone="error" /> : null}
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
            {current.validUntil ? <p className="mt-1 text-xs text-[var(--brand-muted)]">Vigente hasta {formatDate(current.validUntil)}.</p> : null}
          </div>
        ) : null}
        {!loading && !current ? <p className="text-sm text-[var(--brand-muted)]">El socio no tiene un paquete pendiente ni activo.</p> : null}
        {!loading && !current && canSell && plans.length === 0 ? <p className="text-xs text-[#8a5a22]">No hay paquetes activos disponibles en esta sucursal.</p> : null}
      </section>

      <AppDrawer onClose={() => setOpen(false)} open={open} title="Vender paquete de visitas">
        <form className="grid gap-4" onSubmit={submit}>
          {error ? <AppNotice message={error} tone="error" /> : null}
          <label className="form-field" htmlFor="visitPackagePlan">
            <span>Paquete</span>
            <select className="app-input" id="visitPackagePlan" value={selectedPlanId} onChange={(event) => { operationKey.current = null; setQuote(null); setSelectedPlanId(event.target.value); }}>
              {plans.map((plan) => <option key={plan.id} value={plan.id}>{plan.name} · {money(plan.priceCents, plan.currency)}</option>)}
            </select>
          </label>
          <section className="rounded-xl border border-[var(--brand-line)] bg-[#fbfcfa] p-4 text-sm">
            {!quote ? <p className="text-[var(--brand-muted)]">Calculando cotización...</p> : (
              <div className="grid gap-2">
                <div className="flex justify-between gap-3"><span>{quote.totalVisits} visitas · {quote.validityDays} días</span><strong>{money(quote.packagePriceCents, quote.currency)}</strong></div>
                {quote.registrationFeeCents > 0 ? <div className="flex justify-between gap-3"><span>{quote.registrationFeeLabel ?? "Inscripción"}</span><strong>{money(quote.registrationFeeCents, quote.currency)}</strong></div> : null}
                <div className="flex justify-between gap-3 border-t border-[var(--brand-line)] pt-2 text-base"><strong>Total</strong><strong>{money(quote.totalCents, quote.currency)}</strong></div>
              </div>
            )}
          </section>
          <label className="form-field" htmlFor="visitPackageMethod">
            <span>Método de pago</span>
            <select className="app-input" id="visitPackageMethod" value={method} onChange={(event) => { operationKey.current = null; setMethod(event.target.value as VisitPackagePurchasePayload["method"]); }}>
              <option value="CASH">Efectivo</option>
              <option value="TRANSFER">Transferencia</option>
              {manualCardEnabled ? <option value="CARD_MANUAL">Tarjeta manual</option> : null}
              {mercadoPagoEnabled ? <option value="CARD_MERCADO_PAGO">Mercado Pago Point</option> : null}
            </select>
          </label>
          <TextField label="Referencia" name="visitPackageReference" required={false} value={reference} onChange={(value) => { operationKey.current = null; setReference(value); }} />
          <button className="app-button h-11" disabled={saving || !quote} type="submit">{saving ? "Procesando..." : "Cobrar y crear saldo"}</button>
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

function money(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", { style: "currency", currency }).format(cents / 100);
}

function errorMessage(exception: unknown, fallback: string) {
  return exception instanceof Error ? exception.message : fallback;
}
