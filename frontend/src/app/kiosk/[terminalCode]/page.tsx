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
      <main className="kiosk-screen grid place-items-center px-6 text-white">
        <p className="kiosk-loading">Preparando pantalla de socios...</p>
      </main>
    );
  }

  return (
    <main className="kiosk-screen text-white">
      <section className="kiosk-shell">
        <header className="kiosk-header">
          <div>
            <p>Autoservicio</p>
            <h1>{session?.terminalName ?? params.terminalCode}</h1>
            <span>Renueva, revisa pagos o cambia tu membresía sin pasar por mostrador.</span>
          </div>
          <aside>
            <strong>Sesión segura</strong>
            <span>Expira {session ? formatTime(session.expiresAt) : "N/A"}</span>
          </aside>
        </header>

        {error ? (
          <p className="kiosk-notice error">
            {error}
          </p>
        ) : null}
        {message ? (
          <p className="kiosk-notice ok">
            {message}
          </p>
        ) : null}

        {!summary ? (
          <form className="kiosk-card kiosk-verify" onSubmit={verify}>
            <div className="kiosk-step">
              <span>1</span>
              <div>
                <h2>Verifica tu identidad</h2>
                <p>Usa los datos que registraste en recepción. Tu información se muestra sólo después de verificarte.</p>
              </div>
            </div>
            <input
              className="kiosk-input"
              onChange={(event) => setIdentifier(event.target.value)}
              placeholder="Correo o teléfono"
              required
              value={identifier}
            />
            <input
              className="kiosk-input"
              onChange={(event) => setBirthDate(event.target.value)}
              required
              type="date"
              value={birthDate}
            />
            <button
              className="kiosk-primary-action"
              type="submit"
            >
              Continuar de forma segura
            </button>
          </form>
        ) : (
          <section className="kiosk-content-grid">
            <div className="kiosk-card">
              <div className="kiosk-member-header">
                <span>Hola</span>
                <h2>{summary.clientName}</h2>
              </div>
              <label className="kiosk-field">
                Referencia de pago
                <input
                  className="kiosk-input compact"
                  onChange={(event) => setReference(event.target.value)}
                  placeholder="Opcional"
                  value={reference}
                />
              </label>
              <label className="kiosk-field">
                Nuevo plan
                <select
                  className="kiosk-input compact"
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

              <div className="kiosk-membership-list">
                {summary.memberships.length === 0 ? (
                  <p className="kiosk-muted">No hay membresías registradas.</p>
                ) : null}
                {summary.memberships.map((membership) => (
                  <article className="kiosk-membership-card" key={membership.id}>
                    <div>
                      <span>{membership.status}</span>
                      <h3>{membership.planName}</h3>
                    </div>
                    <p>
                      {membership.status} - {formatDay(membership.startsOn)} a {formatDay(membership.endsOn)}
                    </p>
                    <div className="kiosk-action-grid">
                      <button
                        className="kiosk-primary-action small"
                        disabled={workingMembershipId !== null || membership.status !== "ACTIVE"}
                        onClick={() => renew(membership)}
                        type="button"
                      >
                        {workingMembershipId === membership.id ? "Procesando..." : "Renovar"}
                      </button>
                      <button
                        className="kiosk-secondary-action"
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

            <aside className="kiosk-card kiosk-side-card">
              <div className="kiosk-step">
                <span>2</span>
                <div>
                  <h2>Pagos pendientes</h2>
                  <p>Consulta lo que requiere revisión o pago en mostrador.</p>
                </div>
              </div>
              <div className="kiosk-payment-list">
                {summary.pendingPayments.length === 0 ? (
                  <p className="kiosk-muted">No tienes pagos pendientes.</p>
                ) : null}
                {summary.pendingPayments.map((payment) => (
                  <article className="kiosk-payment-card" key={payment.id}>
                    <strong>{payment.saleNumber}</strong>
                    <span>
                      {payment.method} - {formatMoney(payment.amountCents, payment.currency)}
                    </span>
                  </article>
                ))}
              </div>
              <button
                className="kiosk-end-action"
                onClick={end}
                type="button"
              >
                Cerrar sesión
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
