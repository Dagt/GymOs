"use client";

import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { MembershipQuoteSummary } from "@/components/MembershipQuoteSummary";
import { AppNotice } from "@/components/AppNotice";
import {
  changeMembershipPlan,
  createMercadoPagoPointOrder,
  enrollMembership,
  getClientMemberships,
  getMembershipQuote,
  getPaymentConfiguration,
  getPlans,
  renewMembership,
  searchClients,
  type ClientMembershipSummary,
  type ClientSummary,
  type MembershipQuote,
  type PlanSummary,
  type StoredSession,
} from "@/lib/api";

type PaymentMethod = "CASH" | "CARD_MANUAL" | "CARD_MERCADO_PAGO" | "TRANSFER";

export function ReceptionRenewalWidget({ session }: { session: StoredSession }) {
  const [query, setQuery] = useState("");
  const [clients, setClients] = useState<ClientSummary[]>([]);
  const [selectedClient, setSelectedClient] = useState<ClientSummary | null>(null);
  const [membership, setMembership] = useState<ClientMembershipSummary | null>(null);
  const [quote, setQuote] = useState<MembershipQuote | null>(null);
  const [quoteLoading, setQuoteLoading] = useState(false);
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [selectedPlanId, setSelectedPlanId] = useState("");
  const [method, setMethod] = useState<PaymentMethod>("CASH");
  const [manualCardEnabled, setManualCardEnabled] = useState(false);
  const [mercadoPagoEnabled, setMercadoPagoEnabled] = useState(false);
  const [reference, setReference] = useState("");
  const [loading, setLoading] = useState(false);
  const [processing, setProcessing] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const membershipOperationKey = useRef<string | null>(null);

  const activePlans = useMemo(
    () => plans.filter((plan) => plan.status === "ACTIVE"),
    [plans],
  );
  const targetPlanId = selectedPlanId || membership?.planId || "";
  const visibleQuote = quote?.planId === targetPlanId ? quote : null;
  const changesPlan = Boolean(
    membership
      && selectedPlanId
      && selectedPlanId !== membership.planId,
  );

  useEffect(() => {
    Promise.all([
      getPlans(session.accessToken),
      getPaymentConfiguration(session.accessToken),
    ])
      .then(([items, paymentConfiguration]) => {
        setPlans(items.filter((plan) => plan.status === "ACTIVE"));
        setManualCardEnabled(paymentConfiguration.receptionManualTerminalEnabled);
        setMercadoPagoEnabled(paymentConfiguration.mercadoPagoReady);
      })
      .catch(() => setError("No fue posible cargar los planes disponibles."));
  }, [session.accessToken]);

  useEffect(() => {
    if (!selectedClient) return;
    let active = true;
    getClientMemberships(session.accessToken, selectedClient.id)
      .then((items) => {
        if (active) {
          const current = operationalMembership(items);
          setMembership(current);
          if (!current) setQuoteLoading(false);
        }
      })
      .catch(() => {
        if (active) {
          setQuoteLoading(false);
          setError("No fue posible consultar la membresía del socio.");
        }
      });
    return () => {
      active = false;
    };
  }, [selectedClient, session.accessToken]);

  useEffect(() => {
    if (!selectedClient || !targetPlanId) return;
    let active = true;
    getMembershipQuote(session.accessToken, selectedClient.id, targetPlanId)
      .then((nextQuote) => {
        if (active) setQuote(nextQuote);
      })
      .catch((exception) => {
        if (active) {
          setError(
            exception instanceof Error
              ? exception.message
              : "No fue posible calcular la cotización.",
          );
        }
      })
      .finally(() => {
        if (active) setQuoteLoading(false);
      });
    return () => {
      active = false;
    };
  }, [
    membership?.endsOn,
    selectedClient,
    session.accessToken,
    targetPlanId,
  ]);

  async function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!query.trim()) return;
    setLoading(true);
    setError(null);
    setMessage(null);
    setClients([]);
    setSelectedClient(null);
    try {
      setClients(await searchClients(session.accessToken, query.trim()));
    } catch {
      setError("No fue posible buscar al socio.");
    } finally {
      setLoading(false);
    }
  }

  async function handleMembershipOperation() {
    if (!selectedClient || !targetPlanId) return;
    setProcessing(true);
    setError(null);
    setMessage(null);
    try {
      const idempotencyKey = membershipOperationKey.current ?? `reception-membership:${crypto.randomUUID()}`;
      membershipOperationKey.current = idempotencyKey;
      const payment = {
        method,
        reference: reference.trim() || null,
        validateNow: method !== "CARD_MERCADO_PAGO",
        idempotencyKey,
      };
      if (!membership) {
        const result = await enrollMembership(session.accessToken, {
          ...payment,
          clientId: selectedClient.id,
          planId: targetPlanId,
          notes: "Venta de membresía inicial desde recepción",
        });
        await sendToMercadoPagoPoint(result.paymentId);
        setMessage(
          `Membresía inicial asignada por ${formatMoney(result.totalCents, result.currency)}. Venta ${result.saleNumber} creada.`,
        );
        if (method === "CARD_MERCADO_PAGO") setMessage(`Cobro de la venta ${result.saleNumber} enviado a Point. La membresia se activara al confirmar el webhook.`);
      } else if (changesPlan) {
        if (membership.status !== "ACTIVE") {
          throw new Error("Sólo una membresía activa puede cambiar de plan. Renueva primero el plan vencido.");
        }
        const result = await changeMembershipPlan(
          session.accessToken,
          membership.id,
          {
            ...payment,
            targetPlanId,
            notes: "Cambio de plan desde inicio de recepción",
          },
        );
        await sendToMercadoPagoPoint(result.paymentId);
        setMessage(
          `Plan cambiado por ${formatMoney(result.totalCents, result.currency)}. Venta ${result.saleNumber} registrada.`,
        );
        if (method === "CARD_MERCADO_PAGO") setMessage(`Cobro de la venta ${result.saleNumber} enviado a Point. El plan cambiara al confirmar el webhook.`);
      } else {
        const result = await renewMembership(session.accessToken, membership.id, {
          ...payment,
          notes: "Renovación desde inicio de recepción",
        });
        await sendToMercadoPagoPoint(result.paymentId);
        setMessage(
          `Membresía renovada por ${formatMoney(result.totalCents, result.currency)}. Venta ${result.saleNumber} registrada.`,
        );
        if (method === "CARD_MERCADO_PAGO") setMessage(`Cobro de la venta ${result.saleNumber} enviado a Point. La renovacion se aplicara al confirmar el webhook.`);
      }

      const updated = await getClientMemberships(
        session.accessToken,
        selectedClient.id,
      );
      setMembership(operationalMembership(updated));
      membershipOperationKey.current = null;
      setSelectedPlanId("");
      setReference("");
    } catch (exception) {
      setError(
        exception instanceof Error
          ? exception.message
          : "No fue posible completar la operación de membresía.",
      );
    } finally {
      setProcessing(false);
    }
  }

  function clearSelectedClient() {
    membershipOperationKey.current = null;
    setSelectedClient(null);
    setMembership(null);
    setQuote(null);
    setSelectedPlanId("");
    setReference("");
    setQuoteLoading(false);
    setMessage(null);
    setError(null);
  }

  async function sendToMercadoPagoPoint(paymentId: string | null) {
    if (method !== "CARD_MERCADO_PAGO") return;
    if (!paymentId) throw new Error("La venta se registro, pero no se encontro el pago para enviar a Point.");
    await createMercadoPagoPointOrder(session.accessToken, paymentId);
  }

  return (
    <section className="app-card p-5">
      <div className="admin-panel-header">
        <div>
          <p>Atención en mostrador</p>
          <h2>Renovar, asignar o cambiar plan</h2>
        </div>
      </div>

      <p className="mt-2 text-sm text-[var(--brand-muted)]">
        Busca al socio, revisa el precio final y registra la operación sin salir del inicio.
      </p>

      {message ? <AppNotice message={message} tone="success" /> : null}
      {error ? <AppNotice message={error} tone="error" /> : null}

      <form className="mt-4 flex flex-col gap-2 sm:flex-row" onSubmit={handleSearch}>
        <input
          className="app-input flex-1"
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Nombre, teléfono o email"
          value={query}
        />
        <button className="app-button sm:px-6" disabled={loading} type="submit">
          {loading ? "Buscando..." : "Buscar socio"}
        </button>
      </form>

      {clients.length > 0 && !selectedClient ? (
        <div className="mt-3 grid max-h-48 gap-2 overflow-y-auto rounded-md border border-[var(--brand-line)] p-2">
          {clients.map((client) => (
            <button
              className="flex items-center justify-between gap-3 rounded-md border border-transparent bg-white p-3 text-left hover:border-[var(--brand-primary)]"
              key={client.id}
              onClick={() => {
                setSelectedClient(client);
                setMembership(null);
                setSelectedPlanId("");
                setQuote(null);
                setQuoteLoading(true);
                setClients([]);
                setError(null);
              }}
              type="button"
            >
              <strong className="text-sm">
                {client.firstName} {client.lastName}
              </strong>
              <span className="text-xs text-[var(--brand-muted)]">
                {client.phone || client.email || "Socio"}
              </span>
            </button>
          ))}
        </div>
      ) : null}

      {selectedClient ? (
        <section className="mt-4 rounded-md border border-[var(--brand-line)] bg-white p-4">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <div>
              <p className="app-eyebrow">Socio seleccionado</p>
              <h3 className="mt-1 text-lg font-semibold">
                {selectedClient.firstName} {selectedClient.lastName}
              </h3>
            </div>
            <button
              className="text-xs font-semibold text-[var(--brand-muted)]"
              onClick={clearSelectedClient}
              type="button"
            >
              Cambiar socio
            </button>
          </div>

          {membership ? (
            <div className="mt-4 grid gap-4 md:grid-cols-[0.9fr_1.1fr]">
              <section className="rounded-md bg-[var(--brand-surface)] p-4">
                <p className="app-eyebrow">Membresía actual</p>
                <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
                  <strong>{membership.planName}</strong>
                  <span className="app-pill">
                    {membership.status === "ACTIVE" ? "Activa" : "Vencida"}
                  </span>
                </div>
                <p className="mt-2 text-xs text-[var(--brand-muted)]">
                  Vigencia: {membership.startsOn} a {membership.endsOn}
                </p>
              </section>

              <label className="form-field" htmlFor="receptionTargetPlan">
                <span>Operación</span>
                <select
                  className="app-input"
                  id="receptionTargetPlan"
                  onChange={(event) => {
                    setSelectedPlanId(event.target.value);
                    setQuote(null);
                    setQuoteLoading(true);
                  }}
                  value={selectedPlanId}
                >
                  <option value="">Renovar el mismo plan: {membership.planName}</option>
                  {membership.status === "ACTIVE"
                    ? activePlans
                        .filter((plan) => plan.id !== membership.planId)
                        .map((plan) => (
                          <option key={plan.id} value={plan.id}>
                            Cambiar a {plan.name}
                          </option>
                        ))
                    : null}
                </select>
                {membership.status !== "ACTIVE" ? (
                  <small>Renueva el plan vencido antes de realizar un cambio.</small>
                ) : (
                  <small>El cambio inicia hoy la vigencia completa del plan elegido.</small>
                )}
              </label>
            </div>
          ) : (
            <label className="form-field mt-4" htmlFor="receptionInitialPlan">
              <span>Plan inicial</span>
              <select
                className="app-input"
                id="receptionInitialPlan"
                onChange={(event) => {
                  setSelectedPlanId(event.target.value);
                  setQuote(null);
                  setQuoteLoading(Boolean(event.target.value));
                }}
                value={selectedPlanId}
              >
                <option value="">Selecciona un plan</option>
                {activePlans.map((plan) => (
                  <option key={plan.id} value={plan.id}>
                    {plan.name}
                  </option>
                ))}
              </select>
            </label>
          )}

          <div className="mt-4">
            <MembershipQuoteSummary loading={quoteLoading} quote={visibleQuote} />
          </div>

          <div className="mt-4 grid gap-3 md:grid-cols-2">
            <label className="form-field" htmlFor="receptionPaymentMethod">
              <span>Método de pago</span>
              <select
                className="app-input"
                id="receptionPaymentMethod"
                onChange={(event) => setMethod(event.target.value as PaymentMethod)}
                value={method}
              >
                <option value="CASH">Efectivo</option>
                {manualCardEnabled ? <option value="CARD_MANUAL">Tarjeta</option> : null}
                {mercadoPagoEnabled ? <option value="CARD_MERCADO_PAGO">Mercado Pago Point</option> : null}
                <option value="TRANSFER">Transferencia</option>
              </select>
            </label>
            <label className="form-field" htmlFor="receptionPaymentReference">
              <span>Referencia</span>
              <input
                className="app-input"
                id="receptionPaymentReference"
                onChange={(event) => setReference(event.target.value)}
                placeholder="Opcional"
                value={reference}
              />
            </label>
          </div>

          <button
            className="app-button mt-4 w-full"
            disabled={processing || !targetPlanId || !visibleQuote}
            onClick={() => void handleMembershipOperation()}
            type="button"
          >
            {processing
              ? "Procesando..."
              : !membership
                ? "Asignar membresía inicial"
                : changesPlan
                  ? "Cambiar plan"
                  : "Renovar mismo plan"}
          </button>
        </section>
      ) : null}
    </section>
  );
}

function operationalMembership(
  memberships: ClientMembershipSummary[],
): ClientMembershipSummary | null {
  return memberships.find((membership) => membership.status === "ACTIVE")
    ?? memberships.find((membership) => membership.status === "EXPIRED")
    ?? null;
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency,
  }).format(cents / 100);
}
