"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { AppNotice } from "@/components/AppNotice";
import { MembershipQuoteSummary } from "@/components/MembershipQuoteSummary";
import { ReceptionPointRecovery, useReceptionPoint } from "@/components/ReceptionPointRecovery";
import { runSaleWrite } from "@/lib/sale-write";
import { VisitPackageCard } from "@/components/VisitPackageCard";
import { currentMembership } from "@/app/clients/[clientId]/client-membership-helpers";
import { resolveClientMembershipCapabilities } from "@/app/clients/[clientId]/client-membership-permissions";
import { contractErrorMessage } from "@/lib/contract-error";
import {
  changeMembershipPlan,
  enrollMembership,
  getClientMemberships,
  getMembershipQuote,
  getPaymentConfiguration,
  getPlans,
  renewMembership,
  type ClientMembershipSummary,
  type ClientSummary,
  type MembershipQuote,
  type PlanSummary,
  type RenewMembershipPayload,
  type StoredSession,
} from "@/lib/api";

const operationLabels = {
  enroll: "Asignar membresía",
  renew: "Renovar membresía",
  change: "Cambiar plan",
};
const clientStatus = { ACTIVE: "Activo", INACTIVE: "Inactivo", SUSPENDED: "Suspendido" };
type Operation = keyof typeof operationLabels;
type PaymentMethod = RenewMembershipPayload["method"];

export function ReceptionMemberOperations({ client, session, onBusyChange, onClear }: {
  client: ClientSummary;
  session: StoredSession;
  onBusyChange: (busy: boolean) => void;
  onClear: () => void;
}) {
  const token = session.accessToken;
  const point = useReceptionPoint(session, client.id, "membership");
  const clientName = [client.firstName, client.lastName, client.maternalLastName].filter(Boolean).join(" ");
  const capabilities = resolveClientMembershipCapabilities(session.user);
  const canReadPaymentSettings = session.user.permissions.some((permission) =>
    permission === "payment.settings.read" || permission === "payment.settings.manage");
  const [membership, setMembership] = useState<ClientMembershipSummary | null>();
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [manualCardEnabled, setManualCardEnabled] = useState(false);
  const [pointEnabled, setPointEnabled] = useState(false);
  const [operation, setOperation] = useState<Operation | null>(null);
  const [selectedPlanId, setSelectedPlanId] = useState("");
  const [quote, setQuote] = useState<MembershipQuote | null>(null);
  const [quoteError, setQuoteError] = useState<string | null>(null);
  const [method, setMethod] = useState<PaymentMethod>("CASH");
  const [reference, setReference] = useState("");
  const [processing, setProcessing] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const inFlight = useRef(false);
  const targetPlanId = operation === "renew" ? membership?.planId : selectedPlanId;
  const visibleQuote = quote?.planId === targetPlanId ? quote : null;
  const eligiblePlans = plans.filter((plan) => operation !== "change" || plan.id !== membership?.planId);
  const durationDays = plans.find((plan) => plan.id === targetPlanId)?.durationDays;
  const usesReference = method === "TRANSFER" || method === "CARD_MANUAL";

  useEffect(() => {
    let active = true;
    Promise.all([
      getClientMemberships(token, client.id),
      capabilities.canManage ? getPlans(token) : Promise.resolve([]),
      canReadPaymentSettings ? getPaymentConfiguration(token) : Promise.resolve(null),
    ]).then(([memberships, availablePlans, payment]) => {
      if (!active) return;
      setMembership(currentMembership(memberships));
      setPlans(availablePlans);
      setManualCardEnabled(payment?.receptionManualTerminalEnabled ?? false);
      setPointEnabled(capabilities.canEnroll && (payment?.mercadoPagoReady ?? false));
    }).catch((exception) => {
      if (active) setError(contractErrorMessage(exception, "No fue posible cargar las contrataciones del socio."));
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [token, client.id, canReadPaymentSettings, capabilities.canEnroll, capabilities.canManage]);

  useEffect(() => {
    if (!operation || !targetPlanId) return;
    let active = true;
    getMembershipQuote(token, client.id, targetPlanId)
      .then((result) => { if (active) setQuote(result); })
      .catch((exception) => {
        if (active) setQuoteError(contractErrorMessage(exception, "No fue posible calcular la cotización."));
      });
    return () => { active = false; };
  }, [token, client.id, operation, targetPlanId]);

  function selectOperation(next: Operation | null) {
    setOperation(next);
    setSelectedPlanId("");
    setQuote(null);
    setQuoteError(null);
    setError(null);
    setMessage(null);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (inFlight.current || point.pending || !operation || !targetPlanId || !visibleQuote || !actionAllowed(operation)) return;
    inFlight.current = true;
    setProcessing(true);
    onBusyChange(true);
    setError(null);
    const paymentReference = usesReference ? reference.trim() || null : null;
    try {
      const payment = {
        method,
        reference: paymentReference,
        validateNow: method !== "CARD_MERCADO_PAGO",
        notes: `${operationLabels[operation]} desde recepción`,
      };
      const write = (idempotencyKey: string) => operation === "enroll"
        ? enrollMembership(token, { ...payment, idempotencyKey, clientId: client.id, planId: targetPlanId })
        : operation === "change" && membership
          ? changeMembershipPlan(token, membership.id, { ...payment, idempotencyKey, targetPlanId })
          : renewMembership(token, membership!.id, { ...payment, idempotencyKey });
      const result = await runSaleWrite(session, `reception-membership:${client.id}`,
        { ...payment, operation, membershipId: membership?.id, targetPlanId }, write);
      selectOperation(null);
      setReference("");
      setMethod("CASH");
      if (method === "CARD_MERCADO_PAGO" && result.saleStatus === "PENDING_PAYMENT") {
        await sendPoint(result);
        return;
      }
      setMessage(result.saleStatus === "PAID"
        ? `Operación confirmada. Venta ${result.saleNumber}.`
        : `Venta ${result.saleNumber} registrada. La membresía se actualizará al confirmar el pago.`);
      try {
        setMembership(currentMembership(await getClientMemberships(token, client.id)));
      } catch {
        setMembership(undefined);
        setError("Vuelve a seleccionar al socio para consultar la membresía actualizada.");
      }
    } catch (exception) {
      setError(contractErrorMessage(exception, "No fue posible completar la operación de membresía."));
    } finally {
      inFlight.current = false;
      setProcessing(false);
      onBusyChange(false);
    }
  }

  async function sendPoint(sale: Parameters<typeof point.send>[0]) {
    setError(null);
    onBusyChange(true);
    try {
      const message = await point.send(sale);
      if (message) {
        setMessage(message);
        try {
          setMembership(currentMembership(await getClientMemberships(token, client.id)));
        } catch {
          setMembership(undefined);
          setError("La venta está registrada. Vuelve a seleccionar al socio para actualizar la membresía.");
        }
      }
    } catch (exception) {
      setError(contractErrorMessage(exception, "Consulta el estado de la venta en el historial."));
    } finally {
      onBusyChange(false);
    }
  }

  function actionAllowed(action: Operation) {
    if (point.pending || loading || membership === undefined) return false;
    if (action === "enroll") return capabilities.canEnroll && !membership;
    if (action === "renew") return capabilities.canRenew && Boolean(membership);
    return capabilities.canChange && membership?.status === "ACTIVE";
  }

  return (
    <>
      <section className="app-card grid gap-4 p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="app-eyebrow">Socio seleccionado</p>
            <h2 className="app-card-title">{clientName}</h2>
            <p className="text-sm text-[var(--brand-muted)]">Estado: {clientStatus[client.status]}</p>
          </div>
          <button className="app-button-secondary" disabled={processing || point.sending} onClick={onClear} type="button">Cambiar socio</button>
        </div>
        <div className="rounded-lg bg-[var(--brand-surface)] p-4">
          <h3 className="app-eyebrow">Membresía actual</h3>
          {loading ? <p>Cargando membresía…</p> : membership ? (
            <>
              <p className="font-semibold">{membership.planName} · {membership.status === "ACTIVE" ? "Activa" : "Vencida"}</p>
              <p className="text-sm">Vigencia: {membership.startsOn} a {membership.endsOn}</p>
            </>
          ) : <p>{membership === null ? "El socio no tiene membresía." : "Membresía no disponible."}</p>}
        </div>
        {message ? <AppNotice message={message} tone="success" /> : null}
        {error ? <AppNotice message={error} tone="error" /> : null}
        {point.pending && capabilities.canEnroll ? <ReceptionPointRecovery sale={point.pending} sending={point.sending}
          canReadSale={session.user.permissions.includes("sale.read")}
          onRetry={() => { if (point.pending) void sendPoint(point.pending); }} /> : null}
        {capabilities.canManage ? (
          <div className="flex flex-wrap gap-2" aria-label="Operaciones de membresía">
            {(Object.keys(operationLabels) as Operation[]).map((action) => (
              <button
                aria-pressed={operation === action}
                className={operation === action ? "app-button" : "app-button-secondary disabled:cursor-not-allowed disabled:opacity-50"}
                disabled={processing || operation === action || !actionAllowed(action)}
                key={action}
                onClick={() => selectOperation(action)}
                type="button"
              >{operationLabels[action]}</button>
            ))}
          </div>
        ) : null}
        {operation ? (
          <form onSubmit={submit}>
            <fieldset className="grid gap-3 rounded-lg border border-[var(--brand-line)] p-4" disabled={processing}>
              <legend className="px-1 font-semibold">{operationLabels[operation]}</legend>
              {operation !== "renew" ? (
                <label className="form-field">
                  <span>Plan</span>
                  <select className="app-input" value={selectedPlanId} onChange={(event) => {
                    setSelectedPlanId(event.target.value);
                    setQuote(null);
                    setQuoteError(null);
                    setError(null);
                  }}>
                    <option value="">Selecciona un plan</option>
                    {eligiblePlans.filter((plan) => plan.status === "ACTIVE").map((plan) =>
                      <option key={plan.id} value={plan.id}>{plan.name}</option>)}
                  </select>
                </label>
              ) : <p className="font-semibold">{membership?.planName}</p>}
              {durationDays ? <p className="text-sm">Vigencia del plan: {durationDays} días</p> : null}
              {quoteError ? <AppNotice message={quoteError} tone="error" /> : null}
              <MembershipQuoteSummary loading={Boolean(targetPlanId && !visibleQuote && !quoteError)} quote={visibleQuote} />
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="form-field">
                  <span>Método de pago</span>
                  <select className="app-input" value={method} onChange={(event) => {
                    setMethod(event.target.value as PaymentMethod);
                    setReference("");
                  }}>
                    <option value="CASH">Efectivo</option>
                    <option value="TRANSFER">Transferencia</option>
                    {manualCardEnabled ? <option value="CARD_MANUAL">Tarjeta manual</option> : null}
                    {pointEnabled ? <option value="CARD_MERCADO_PAGO">Mercado Pago Point</option> : null}
                  </select>
                </label>
                {usesReference ? (
                  <label className="form-field">
                    <span>Referencia (opcional)</span>
                    <input className="app-input" maxLength={160} value={reference} onChange={(event) => setReference(event.target.value)} />
                  </label>
                ) : null}
              </div>
              <button className="app-button" disabled={processing || !visibleQuote} type="submit">
                {processing ? "Procesando…" : "Confirmar operación"}
              </button>
            </fieldset>
          </form>
        ) : null}
      </section>
      <VisitPackageCard clientId={client.id} clientName={clientName} session={session} source="reception" />
    </>
  );
}
