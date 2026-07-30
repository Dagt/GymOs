"use client";

import { FormEvent, useEffect, useState } from "react";
import { MembershipQuoteSummary } from "@/components/MembershipQuoteSummary";
import {
  enrollMembership,
  getClientMemberships,
  getPlans,
  getMembershipQuote,
  renewMembership,
  searchClients,
  type ClientMembershipSummary,
  type ClientSummary,
  type PlanSummary,
  type MembershipQuote,
  type StoredSession,
} from "@/lib/api";

export function ReceptionRenewalWidget({ session }: { session: StoredSession }) {
  const [query, setQuery] = useState("");
  const [clients, setClients] = useState<ClientSummary[]>([]);
  const [selectedClient, setSelectedClient] = useState<ClientSummary | null>(null);
  const [membership, setMembership] = useState<ClientMembershipSummary | null>(null);
  const [quote, setQuote] = useState<MembershipQuote | null>(null);
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [selectedPlanId, setSelectedPlanId] = useState("");
  const [method, setMethod] = useState<"CASH" | "CARD_MANUAL" | "TRANSFER">("CASH");
  const [loading, setLoading] = useState(false);
  const [processing, setProcessing] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getPlans(session.accessToken).then(setPlans).catch(() => {});
  }, [session]);

  useEffect(() => {
    if (!selectedClient) {
      return;
    }
    let active = true;
    getClientMemberships(session.accessToken, selectedClient.id)
      .then((items) => {
        if (active && items.length > 0) setMembership(items[0]);
        else if (active) setMembership(null);
      })
      .catch(() => {
        if (active) setMembership(null);
      });
    return () => { active = false; };
  }, [selectedClient, session]);

  useEffect(() => {
    if (!selectedClient) return;
    const planId = membership?.planId ?? selectedPlanId;
    if (!planId) return;
    let active = true;
    getMembershipQuote(session.accessToken, selectedClient.id, planId)
      .then((nextQuote) => {
        if (active) setQuote(nextQuote);
      })
      .catch(() => {
        if (active) setQuote(null);
      });
    return () => {
      active = false;
    };
  }, [membership, selectedClient, selectedPlanId, session.accessToken]);

  async function handleSearch(e: FormEvent) {
    e.preventDefault();
    if (!query.trim()) return;
    setLoading(true);
    setError(null);
    setSelectedClient(null);
    setMembership(null);
    try {
      setClients(await searchClients(session.accessToken, query.trim()));
    } catch {
      setError("Error buscando socio.");
    } finally {
      setLoading(false);
    }
  }

  async function handleRenew() {
    if (!membership) return;
    setProcessing(true);
    setError(null);
    setMessage(null);
    try {
      const res = await renewMembership(session.accessToken, membership.id, {
        method,
        reference: null,
        validateNow: true,
        notes: "Renovación rápida desde inicio de recepción",
      });
      setMessage(`Membresía renovada por ${formatMoney(res.totalCents, res.currency)}. Venta ${res.saleNumber} registrada.`);
      setMembership(res.membership || null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Error al renovar.");
    } finally {
      setProcessing(false);
    }
  }

  async function handleAssignInitialPlan() {
    if (!selectedClient || !selectedPlanId) return;
    setProcessing(true);
    setError(null);
    setMessage(null);
    try {
      const plan = plans.find((p) => p.id === selectedPlanId);
      if (!plan) return;
      const res = await enrollMembership(session.accessToken, {
        clientId: selectedClient.id,
        planId: plan.id,
        method,
        reference: null,
        validateNow: true,
        notes: "Venta de membresía inicial desde recepción",
      });
      setMessage(`Membresía inicial asignada por ${formatMoney(res.totalCents, res.currency)}. Venta ${res.saleNumber} creada.`);
      const updatedMemberships = await getClientMemberships(session.accessToken, selectedClient.id).catch(() => []);
      if (updatedMemberships.length > 0) setMembership(updatedMemberships[0]);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Error al asignar membresía inicial.");
    } finally {
      setProcessing(false);
    }
  }

  return (
    <section className="app-card p-6 border-2 border-[var(--brand-line)] bg-white rounded-2xl shadow-sm">
      <div className="flex items-center justify-between mb-4">
        <div>
          <p className="app-eyebrow">Atención en Mostrador</p>
          <h2 className="app-card-title">Renovación y Asignación de Membresía</h2>
        </div>
        <span className="text-xl">🔄</span>
      </div>

      {message ? (
        <div className="p-3 mb-4 text-xs font-bold text-green-700 bg-green-50 rounded-xl border border-green-200">
          ✅ {message}
        </div>
      ) : null}

      {error ? (
        <div className="p-3 mb-4 text-xs font-bold text-red-700 bg-red-50 rounded-xl border border-red-200">
          ⚠️ {error}
        </div>
      ) : null}

      <form className="flex gap-2 mb-4" onSubmit={handleSearch}>
        <input
          className="app-input flex-1 h-11 text-sm font-bold"
          placeholder="Buscar socio por nombre, teléfono o email..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button className="app-button px-5 text-xs" type="submit" disabled={loading}>
          {loading ? "Buscando..." : "Buscar Socio"}
        </button>
      </form>

      {clients.length > 0 && !selectedClient ? (
        <div className="grid gap-2 max-h-40 overflow-y-auto mb-4 p-2 bg-gray-50 rounded-xl border border-gray-100">
          {clients.map((c) => (
            <button
              key={c.id}
              type="button"
              onClick={() => { setSelectedClient(c); setQuote(null); setClients([]); }}
              className="p-2.5 rounded-lg bg-white border border-gray-100 hover:border-[var(--brand-primary)] text-left flex items-center justify-between transition-colors"
            >
              <strong className="text-sm text-gray-900">{c.firstName} {c.lastName}</strong>
              <span className="text-xs text-gray-500">{c.phone || c.email || "Socio"}</span>
            </button>
          ))}
        </div>
      ) : null}

      {selectedClient ? (
        <div className="p-4 rounded-xl border-2 border-blue-100 bg-blue-50/40 flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <strong className="text-base font-black text-gray-900">
              Socio: {selectedClient.firstName} {selectedClient.lastName}
            </strong>
            <button
              type="button"
              onClick={() => { setSelectedClient(null); setMembership(null); setQuote(null); setMessage(null); setError(null); }}
              className="text-xs font-bold text-gray-500 hover:text-gray-900"
            >
              ✕ Cambiar socio
            </button>
          </div>

          {membership ? (
            <div className="p-3 bg-white rounded-lg border border-blue-100 flex flex-col gap-2">
              <div className="flex items-center justify-between">
                <span className="text-sm font-black text-gray-900">{membership.planName}</span>
                <span className={`text-[0.65rem] font-black uppercase px-2 py-0.5 rounded ${membership.status === "ACTIVE" ? "bg-green-100 text-green-700" : "bg-red-100 text-red-700"}`}>
                  {membership.status === "ACTIVE" ? "Activa" : "Vencida"}
                </span>
              </div>
              <p className="text-xs text-gray-500 font-medium">
                Vigencia: {membership.startsOn} a {membership.endsOn}
              </p>
              <MembershipQuoteSummary loading={false} quote={quote} />

              <div className="flex items-center gap-2 pt-2">
                <label className="text-xs font-bold text-gray-600 flex items-center gap-1">
                  Método de Pago:
                  <select
                    className="app-input text-xs h-8 px-2 py-0"
                    value={method}
                    onChange={(e) => setMethod(e.target.value as "CASH" | "CARD_MANUAL" | "TRANSFER")}
                  >
                    <option value="CASH">Efectivo</option>
                    <option value="CARD_MANUAL">Tarjeta</option>
                    <option value="TRANSFER">Transferencia</option>
                  </select>
                </label>
              </div>

              <button
                type="button"
                onClick={handleRenew}
                disabled={processing}
                className="app-button text-xs h-9 mt-2"
              >
                {processing ? "Procesando..." : "🔄 Renovar Mismo Plan"}
              </button>
            </div>
          ) : (
            <div className="p-3 bg-white rounded-lg border border-amber-200 bg-amber-50/50 flex flex-col gap-3">
              <p className="text-xs font-bold text-amber-900">
                El socio no tiene membresía registrada. Asígnale su primer plan aquí:
              </p>
              <div className="flex flex-col sm:flex-row gap-2">
                <select
                  className="app-input text-xs h-9 flex-1"
                  value={selectedPlanId}
                  onChange={(e) => {
                    setSelectedPlanId(e.target.value);
                    setQuote(null);
                  }}
                >
                  <option value="">Selecciona un Plan del Catálogo...</option>
                  {plans.map((p) => (
                    <option key={p.id} value={p.id}>{p.name}</option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={handleAssignInitialPlan}
                  disabled={processing || !selectedPlanId}
                  className="app-button text-xs h-9 px-4"
                >
                  + Asignar Membresía Inicial
                </button>
              </div>
              <MembershipQuoteSummary loading={false} quote={quote} />
            </div>
          )}
        </div>
      ) : null}
    </section>
  );
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency,
  }).format(cents / 100);
}
