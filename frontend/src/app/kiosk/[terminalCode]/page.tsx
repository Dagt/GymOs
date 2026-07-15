"use client";

import { useParams } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import {
  changeMembershipPlanFromKiosk,
  createKioskSession,
  endKioskSession,
  getKioskMembershipOptions,
  getKioskSummary,
  renewMembershipFromKiosk,
  verifyKioskSession,
  type KioskMembership,
  type KioskPlanOption,
  type KioskSessionResponse,
  type KioskSummaryResponse,
} from "@/lib/api";

export default function KioskPage() {
  const params = useParams<{ terminalCode: string }>();
  const [session, setSession] = useState<KioskSessionResponse | null>(null);
  const [summary, setSummary] = useState<KioskSummaryResponse | null>(null);
  const [plans, setPlans] = useState<KioskPlanOption[]>([]);
  const [identifier, setIdentifier] = useState("");
  const [birthDate, setBirthDate] = useState("");
  const [targetPlanId, setTargetPlanId] = useState("");
  const [reference, setReference] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [workingMembershipId, setWorkingMembershipId] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function start() {
      setLoading(true);
      setError(null);
      try {
        const created = await createKioskSession(params.terminalCode);
        if (active) {
          setSession(created);
        }
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible iniciar kiosco.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void start();
    return () => {
      active = false;
    };
  }, [params.terminalCode]);

  async function verify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    setError(null);
    setMessage(null);
    try {
      const nextSummary = await verifyKioskSession(session.sessionId, session.sessionToken, identifier, birthDate);
      const options = await getKioskMembershipOptions(session.sessionId, session.sessionToken);
      setSummary(nextSummary);
      setPlans(options);
      setTargetPlanId(options[0]?.id ?? "");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible verificar.");
    }
  }

  async function renew(membership: KioskMembership) {
    if (!session) {
      return;
    }
    setWorkingMembershipId(membership.id);
    setError(null);
    setMessage(null);
    try {
      const response = await renewMembershipFromKiosk(session.sessionId, session.sessionToken, membership.id, {
        method: "CASH",
        reference,
        validateNow: true,
        notes: "Renovacion desde kiosco",
        idempotencyKey: operationKey("kiosk-renewal", session.sessionId, membership.id),
      });
      setSummary(await getKioskSummary(session.sessionId, session.sessionToken));
      setMessage(response.membership ? `Renovada hasta ${formatDay(response.membership.endsOn)}.` : "Pago pendiente.");
      setReference("");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible renovar.");
    } finally {
      setWorkingMembershipId(null);
    }
  }

  async function changePlan(membership: KioskMembership) {
    if (!session || !targetPlanId) {
      return;
    }
    setWorkingMembershipId(membership.id);
    setError(null);
    setMessage(null);
    try {
      const response = await changeMembershipPlanFromKiosk(session.sessionId, session.sessionToken, membership.id, {
        targetPlanId,
        method: "CASH",
        reference,
        validateNow: true,
        notes: "Cambio de plan desde kiosco",
        idempotencyKey: operationKey("kiosk-plan-change", session.sessionId, membership.id, targetPlanId),
      });
      setSummary(await getKioskSummary(session.sessionId, session.sessionToken));
      setMessage(response.membership ? `Nuevo plan activo: ${response.membership.planName}.` : "Solicitud registrada.");
      setReference("");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cambiar plan.");
    } finally {
      setWorkingMembershipId(null);
    }
  }

  async function end() {
    if (session) {
      await endKioskSession(session.sessionId, session.sessionToken).catch(() => undefined);
    }
    setSummary(null);
    setIdentifier("");
    setBirthDate("");
    setReference("");
    setMessage("Sesion cerrada.");
  }

  if (loading) {
    return (
      <main className="grid min-h-screen place-items-center bg-[#101916] px-6 text-white">
        <p className="text-lg font-semibold">Iniciando kiosco...</p>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-[#101916] text-white">
      <section className="mx-auto grid min-h-screen max-w-5xl content-center gap-6 px-6 py-8">
        <header className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="text-sm font-semibold uppercase tracking-wide text-[#93d2a8]">GymOS Kiosco</p>
            <h1 className="mt-2 text-4xl font-semibold">{session?.terminalName ?? params.terminalCode}</h1>
          </div>
          <p className="text-sm text-[#c6d5cd]">Expira: {session ? formatTime(session.expiresAt) : "N/A"}</p>
        </header>

        {error ? (
          <p className="rounded-md border border-[#8b3939] bg-[#2a1616] px-4 py-3 text-sm text-[#ffd0d0]">
            {error}
          </p>
        ) : null}
        {message ? (
          <p className="rounded-md border border-[#3b7650] bg-[#12291d] px-4 py-3 text-sm text-[#c8f2d6]">
            {message}
          </p>
        ) : null}

        {!summary ? (
          <form className="rounded-lg border border-[#2f473c] bg-[#17231f] p-6" onSubmit={verify}>
            <h2 className="text-2xl font-semibold">Verifica tu identidad</h2>
            <p className="mt-2 text-sm text-[#c6d5cd]">Ingresa tus datos registrados en recepcion.</p>
            <input
              className="mt-5 h-14 w-full rounded-md border border-[#3e5a4d] bg-[#101916] px-4 text-lg outline-none transition focus:border-[#93d2a8]"
              onChange={(event) => setIdentifier(event.target.value)}
              placeholder="email o telefono"
              required
              value={identifier}
            />
            <input
              className="mt-4 h-14 w-full rounded-md border border-[#3e5a4d] bg-[#101916] px-4 text-lg outline-none transition focus:border-[#93d2a8]"
              onChange={(event) => setBirthDate(event.target.value)}
              required
              type="date"
              value={birthDate}
            />
            <button
              className="mt-5 h-14 w-full rounded-md bg-[#93d2a8] px-5 text-base font-semibold text-[#101916] transition hover:bg-[#b5e6c5]"
              type="submit"
            >
              Continuar
            </button>
          </form>
        ) : (
          <section className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
            <div className="rounded-lg border border-[#2f473c] bg-[#17231f] p-6">
              <h2 className="text-2xl font-semibold">{summary.clientName}</h2>
              <label className="mt-5 grid gap-2 text-sm font-medium text-[#c6d5cd]">
                Referencia de pago
                <input
                  className="h-12 rounded-md border border-[#3e5a4d] bg-[#101916] px-3 text-base text-white"
                  onChange={(event) => setReference(event.target.value)}
                  value={reference}
                />
              </label>
              <label className="mt-4 grid gap-2 text-sm font-medium text-[#c6d5cd]">
                Plan para cambio
                <select
                  className="h-12 rounded-md border border-[#3e5a4d] bg-[#101916] px-3 text-base text-white"
                  onChange={(event) => setTargetPlanId(event.target.value)}
                  value={targetPlanId}
                >
                  {plans.map((plan) => (
                    <option key={plan.id} value={plan.id}>
                      {plan.name} - {formatMoney(plan.priceCents, plan.currency)}
                    </option>
                  ))}
                </select>
              </label>

              <div className="mt-5 grid gap-4">
                {summary.memberships.length === 0 ? (
                  <p className="text-sm text-[#c6d5cd]">No hay membresias registradas.</p>
                ) : null}
                {summary.memberships.map((membership) => (
                  <article className="rounded-md border border-[#3e5a4d] p-4" key={membership.id}>
                    <p className="text-lg font-semibold">{membership.planName}</p>
                    <p className="mt-1 text-sm text-[#c6d5cd]">
                      {membership.status} - {formatDay(membership.startsOn)} a {formatDay(membership.endsOn)}
                    </p>
                    <div className="mt-4 grid gap-2 sm:grid-cols-2">
                      <button
                        className="h-12 rounded-md bg-[#93d2a8] px-4 text-sm font-semibold text-[#101916] transition hover:bg-[#b5e6c5] disabled:opacity-50"
                        disabled={workingMembershipId !== null || membership.status !== "ACTIVE"}
                        onClick={() => renew(membership)}
                        type="button"
                      >
                        {workingMembershipId === membership.id ? "Procesando..." : "Renovar"}
                      </button>
                      <button
                        className="h-12 rounded-md border border-[#93d2a8] px-4 text-sm font-semibold text-[#dff5e7] transition hover:bg-[#1f3329] disabled:opacity-50"
                        disabled={
                          workingMembershipId !== null
                          || membership.status !== "ACTIVE"
                          || !targetPlanId
                          || targetPlanId === membership.planId
                        }
                        onClick={() => changePlan(membership)}
                        type="button"
                      >
                        Cambiar plan
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            </div>

            <aside className="rounded-lg border border-[#2f473c] bg-[#17231f] p-6">
              <h2 className="text-2xl font-semibold">Pagos pendientes</h2>
              <div className="mt-5 grid gap-3">
                {summary.pendingPayments.length === 0 ? (
                  <p className="text-sm text-[#c6d5cd]">Sin pagos pendientes.</p>
                ) : null}
                {summary.pendingPayments.map((payment) => (
                  <article className="rounded-md border border-[#3e5a4d] p-4" key={payment.id}>
                    <p className="font-semibold">{payment.saleNumber}</p>
                    <p className="mt-1 text-sm text-[#c6d5cd]">
                      {payment.method} - {formatMoney(payment.amountCents, payment.currency)}
                    </p>
                  </article>
                ))}
              </div>
              <button
                className="mt-6 h-12 w-full rounded-md border border-[#6f8580] px-4 text-sm font-semibold text-[#dff5e7] transition hover:bg-[#1f3329]"
                onClick={end}
                type="button"
              >
                Cerrar sesion
              </button>
            </aside>
          </section>
        )}
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

function formatDay(value: string) {
  return new Intl.DateTimeFormat("es-MX", {
    dateStyle: "medium",
    timeZone: "UTC",
  }).format(new Date(`${value}T00:00:00Z`));
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function operationKey(...parts: string[]) {
  const random = typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : `${Date.now()}`;
  return [...parts, random].join(":");
}
