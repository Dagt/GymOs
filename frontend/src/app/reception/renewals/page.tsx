"use client";

import { FormEvent, useEffect, useState } from "react";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import { MembershipQuoteSummary } from "@/components/MembershipQuoteSummary";
import {
  changeMembershipPlan,
  getClientMemberships,
  getPlans,
  getMembershipQuote,
  getStoredSession,
  renewMembership,
  searchClients,
  type ClientMembershipSummary,
  type ClientSummary,
  type PlanSummary,
  type MembershipQuote,
  type StoredSession,
} from "@/lib/api";
import { buildChangePlanPayload, buildRenewalPayload } from "./renewals-model";

export default function ReceptionRenewalsPage() {
  const [session] = useState<StoredSession | null>(() => getStoredSession());
  const [loading, setLoading] = useState(() => Boolean(session));
  const [query, setQuery] = useState("");
  const [clients, setClients] = useState<ClientSummary[]>([]);
  const [selectedClient, setSelectedClient] = useState<ClientSummary | null>(null);
  const [membership, setMembership] = useState<ClientMembershipSummary | null>(null);
  const [renewalQuote, setRenewalQuote] = useState<MembershipQuote | null>(null);
  const [changeQuote, setChangeQuote] = useState<MembershipQuote | null>(null);
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [targetPlanId, setTargetPlanId] = useState("");
  const [method, setMethod] = useState<"CASH" | "CARD_MANUAL" | "TRANSFER">("CASH");
  const [reference, setReference] = useState("");
  const [searching, setSearching] = useState(false);
  const [processing, setProcessing] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!session) return;
    let active = true;
    getPlans(session.accessToken)
      .then((loadedPlans) => {
        if (active) setPlans(loadedPlans);
      })
      .catch(() => {})
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [session]);

  useEffect(() => {
    if (!session || !selectedClient) return;
    let active = true;
    getClientMemberships(session.accessToken, selectedClient.id)
      .then((items) => {
        if (active && items.length > 0) setMembership(items[0]);
        else if (active) setMembership(null);
      })
      .catch(() => {
        if (active) setMembership(null);
      });
    return () => {
      active = false;
    };
  }, [selectedClient, session]);

  useEffect(() => {
    if (!session || !selectedClient || !membership) return;
    let active = true;
    getMembershipQuote(session.accessToken, selectedClient.id, membership.planId)
      .then((quote) => {
        if (active) setRenewalQuote(quote);
      })
      .catch(() => {
        if (active) setRenewalQuote(null);
      });
    return () => {
      active = false;
    };
  }, [membership, selectedClient, session]);

  useEffect(() => {
    if (!session || !selectedClient || !targetPlanId) return;
    let active = true;
    getMembershipQuote(session.accessToken, selectedClient.id, targetPlanId)
      .then((quote) => {
        if (active) setChangeQuote(quote);
      })
      .catch(() => {
        if (active) setChangeQuote(null);
      });
    return () => {
      active = false;
    };
  }, [selectedClient, session, targetPlanId]);

  async function handleSearch(e: FormEvent) {
    e.preventDefault();
    if (!session || !query.trim()) return;
    setSearching(true);
    setError(null);
    try {
      setClients(await searchClients(session.accessToken, query.trim()));
    } catch {
      setError("No fue posible buscar socios.");
    } finally {
      setSearching(false);
    }
  }

  async function handleRenew() {
    if (!session || !membership) return;
    setProcessing(true);
    setError(null);
    setMessage(null);
    try {
      const payload = buildRenewalPayload({ method, reference });
      const res = await renewMembership(session.accessToken, membership.id, payload);
      setMessage(`Membresía renovada con éxito por ${formatMoney(res.totalCents, res.currency)}. Venta ${res.saleNumber} registrada.`);
      setMembership(res.membership || null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Error al procesar la renovación.");
    } finally {
      setProcessing(false);
    }
  }

  async function handleChangePlan() {
    if (!session || !membership || !targetPlanId) return;
    setProcessing(true);
    setError(null);
    setMessage(null);
    try {
      const payload = buildChangePlanPayload({ targetPlanId, method, reference });
      const res = await changeMembershipPlan(session.accessToken, membership.id, payload);
      setMessage(`Plan cambiado con éxito por ${formatMoney(res.totalCents, res.currency)}. Venta ${res.saleNumber} registrada.`);
      setMembership(res.membership || null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Error al procesar cambio de plan.");
    } finally {
      setProcessing(false);
    }
  }

  if (loading) return <LoadingScreen text="Cargando módulo de renovaciones..." />;
  if (!session) return <AuthRequiredCard message="Inicia sesión para acceder a renovaciones." title="Acceso requerido" />;

  return (
    <AppShell
      eyebrow="Atención en Mostrador"
      session={session}
      subtitle="Módulo dedicado para la extensión de vigencias y migración de planes de membresía."
      title="Renovaciones y Cambios de Plan"
    >
      <div className="w-full max-w-[1200px] mx-auto space-y-6">
        {message ? (
          <div className="p-4 rounded-xl border-2 border-green-200 bg-green-50 text-green-800 font-bold text-sm shadow-sm flex items-center gap-2">
            <span>✅</span>
            <span>{message}</span>
          </div>
        ) : null}

        {error ? (
          <div className="p-4 rounded-xl border-2 border-red-200 bg-red-50 text-red-800 font-bold text-sm shadow-sm flex items-center gap-2">
            <span>⚠️</span>
            <span>{error}</span>
          </div>
        ) : null}

        <section className="app-card p-6 border-2 border-[var(--brand-line)] bg-white rounded-2xl shadow-sm">
          <form className="flex gap-2" onSubmit={handleSearch}>
            <input
              className="app-input flex-1 h-12 text-base font-bold"
              placeholder="Buscar socio por nombre, teléfono o correo..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
            <button className="app-button px-6" type="submit" disabled={searching}>
              {searching ? "Buscando..." : "Buscar Socio"}
            </button>
          </form>

          {clients.length > 0 && !selectedClient ? (
            <div className="mt-4 grid gap-2 max-h-60 overflow-y-auto p-2 bg-gray-50 rounded-xl border border-gray-100">
              {clients.map((c) => (
                <button
                  key={c.id}
                  type="button"
                  onClick={() => {
                    setSelectedClient(c);
                    setMembership(null);
                    setRenewalQuote(null);
                    setChangeQuote(null);
                    setClients([]);
                  }}
                  className="p-3 rounded-lg bg-white border border-gray-100 hover:border-[var(--brand-primary)] text-left flex items-center justify-between transition-colors group"
                >
                  <div>
                    <strong className="text-sm font-bold text-gray-900 group-hover:text-[var(--brand-primary)]">
                      {c.firstName} {c.lastName}
                    </strong>
                    <p className="text-xs text-gray-500">{c.phone || c.email || "Sin teléfono"}</p>
                  </div>
                  <span className="text-xs font-bold text-[var(--brand-primary)]">Seleccionar →</span>
                </button>
              ))}
            </div>
          ) : null}
        </section>

        {selectedClient ? (
          <section className="grid gap-6 md:grid-cols-2">
            {/* Client Summary */}
            <article className="app-card p-6 border-2 border-blue-100 bg-blue-50/30 rounded-2xl flex flex-col justify-between">
              <div>
                <span className="text-[0.65rem] font-black uppercase tracking-widest text-blue-800">Socio Seleccionado</span>
                <h3 className="text-xl font-black text-gray-900 mt-1">
                  {selectedClient.firstName} {selectedClient.lastName}
                </h3>
                <p className="text-xs text-gray-600 mt-1 font-medium">
                  📞 {selectedClient.phone || "Sin teléfono"} • ✉️ {selectedClient.email || "Sin correo"}
                </p>
              </div>

              <button
                type="button"
                onClick={() => {
                  setSelectedClient(null);
                  setMembership(null);
                  setRenewalQuote(null);
                  setChangeQuote(null);
                  setMessage(null);
                  setError(null);
                }}
                className="app-button-secondary text-xs mt-6 self-start"
              >
                ✕ Cambiar de socio
              </button>
            </article>

            {/* Membership & Action Details */}
            <article className="app-card p-6 border-2 border-[var(--brand-line)] bg-white rounded-2xl">
              {membership ? (
                <div className="space-y-4">
                  <div className="flex items-center justify-between pb-3 border-b border-gray-100">
                    <div>
                      <span className="text-[0.65rem] font-black uppercase text-gray-400">Plan Contratado</span>
                      <h4 className="text-lg font-black text-gray-900">{membership.planName}</h4>
                    </div>
                    <span
                      className={`text-xs font-black uppercase px-2.5 py-1 rounded-md ${
                        membership.status === "ACTIVE" ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"
                      }`}
                    >
                      {membership.status === "ACTIVE" ? "Activa" : "Vencida"}
                    </span>
                  </div>

                  <p className="text-xs font-bold text-gray-600">
                    Vigencia: {membership.startsOn} al {membership.endsOn}
                  </p>
                  <MembershipQuoteSummary loading={false} quote={renewalQuote} />

                  <div className="grid gap-3 pt-2">
                    <label className="grid gap-1 text-xs font-bold text-gray-700">
                      Método de Pago
                      <select
                        className="app-input h-10 text-xs font-bold"
                        value={method}
                        onChange={(e) => {
                          if (e.target.value === "CASH" || e.target.value === "CARD_MANUAL" || e.target.value === "TRANSFER") {
                            setMethod(e.target.value);
                          }
                        }}
                      >
                        <option value="CASH">Efectivo</option>
                        <option value="CARD_MANUAL">Tarjeta</option>
                        <option value="TRANSFER">Transferencia</option>
                      </select>
                    </label>

                    <label className="grid gap-1 text-xs font-bold text-gray-700">
                      Referencia / Folio
                      <input
                        className="app-input h-10 text-xs"
                        placeholder="Ej. Folio de voucher o transferencia..."
                        value={reference}
                        onChange={(e) => setReference(e.target.value)}
                      />
                    </label>
                  </div>

                  <div className="flex flex-col sm:flex-row gap-3 pt-2">
                    <button
                      type="button"
                      onClick={handleRenew}
                      disabled={processing}
                      className="app-button flex-1 h-11 text-xs font-black"
                    >
                      {processing ? "Procesando..." : "🔄 Renovar Mismo Plan"}
                    </button>

                    {plans.length > 0 ? (
                      <div className="flex gap-2 flex-1">
                        <select
                          className="app-input flex-1 h-11 text-xs font-bold"
                          value={targetPlanId}
                          onChange={(e) => {
                            setTargetPlanId(e.target.value);
                            setChangeQuote(null);
                          }}
                        >
                          <option value="">🔀 Seleccionar Nuevo Plan...</option>
                          {plans.map((p) => (
                            <option key={p.id} value={p.id}>{p.name}</option>
                          ))}
                        </select>
                        <button
                          type="button"
                          onClick={handleChangePlan}
                          disabled={processing || !targetPlanId}
                          className="app-button-secondary h-11 px-4 text-xs font-black"
                        >
                          Cambiar
                        </button>
                      </div>
                    ) : null}
                    {targetPlanId ? <MembershipQuoteSummary loading={false} quote={changeQuote} /> : null}
                  </div>
                </div>
              ) : (
                <div className="py-8 text-center">
                  <p className="text-sm text-gray-500 font-bold italic">
                    Este socio no tiene membresía registrada.
                  </p>
                  <p className="text-xs text-gray-400 mt-1">
                    Puedes asignarle su primera membresía desde la ficha del socio.
                  </p>
                </div>
              )}
            </article>
          </section>
        ) : null}
      </div>
    </AppShell>
  );
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency,
  }).format(cents / 100);
}
