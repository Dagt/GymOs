"use client";

import Image from "next/image";
import { useParams } from "next/navigation";
import { CSSProperties, FormEvent, useEffect, useMemo, useState } from "react";
import {
  changeMembershipPlanFromKiosk,
  createKioskSession,
  endKioskSession,
  getStoredSession,
  getKioskMembershipOptions,
  getKioskSummary,
  renewMembershipFromKiosk,
  verifyKioskSession,
  type KioskMembership,
  type KioskPlanOption,
  type KioskSessionResponse,
  type KioskSummaryResponse,
} from "@/lib/api";
import {
  createKioskBooking,
  getKioskBookingAvailability,
  getKioskBookingResources,
  type KioskAvailabilitySlot,
  type KioskBookingResource,
} from "@/lib/kiosk-bookings";
import { formatMexicanDateInput, parseMexicanDate, toDateInput } from "@/lib/date-time";

export default function KioskPage() {
  const params = useParams<{ terminalCode: string }>();
  const [session, setSession] = useState<KioskSessionResponse | null>(null);
  const [summary, setSummary] = useState<KioskSummaryResponse | null>(null);
  const [plans, setPlans] = useState<KioskPlanOption[]>([]);
  const [identifier, setIdentifier] = useState("");
  const [birthDateDisplay, setBirthDateDisplay] = useState("");
  const [targetPlanId, setTargetPlanId] = useState("");
  const [reference, setReference] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [workingMembershipId, setWorkingMembershipId] = useState<string | null>(null);
  const [resources, setResources] = useState<KioskBookingResource[]>([]);
  const [resourceId, setResourceId] = useState("");
  const [bookingDate, setBookingDate] = useState(() => toDateInput(new Date()));
  const [slots, setSlots] = useState<KioskAvailabilitySlot[]>([]);
  const [selectedSlot, setSelectedSlot] = useState<KioskAvailabilitySlot | null>(null);
  const [bookingNotes, setBookingNotes] = useState("");
  const [bookingBusy, setBookingBusy] = useState(false);

  const birthDate = useMemo(() => parseMexicanDate(birthDateDisplay) ?? "", [birthDateDisplay]);

  useEffect(() => {
    let active = true;
    async function start() {
      setLoading(true);
      setError(null);
      try {
        const stored = getStoredSession();
        const created = await createKioskSession(params.terminalCode, {
          organizationId: stored?.user.organizationId,
          branchId: stored?.user.branchId,
        });
        if (active) setSession(created);
      } catch (exception) {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible iniciar kiosco.");
      } finally {
        if (active) setLoading(false);
      }
    }
    void start();
    return () => { active = false; };
  }, [params.terminalCode]);

  async function verify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !birthDate) {
      setError("Captura una fecha de nacimiento válida en formato día/mes/año.");
      return;
    }
    setError(null);
    setMessage(null);
    try {
      const [nextSummary, options, bookingResources] = await Promise.all([
        verifyKioskSession(session.sessionId, session.sessionToken, identifier, birthDate),
        getKioskMembershipOptions(session.sessionId, session.sessionToken),
        getKioskBookingResources(session.sessionId, session.sessionToken),
      ]);
      setSummary(nextSummary);
      setPlans(options);
      setTargetPlanId(options[0]?.id ?? "");
      setResources(bookingResources);
      setResourceId(bookingResources[0]?.id ?? "");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible verificar.");
    }
  }

  useEffect(() => {
    if (!session || !summary || !resourceId || !bookingDate) {
      setSlots([]);
      setSelectedSlot(null);
      return;
    }
    let active = true;
    getKioskBookingAvailability(session.sessionId, session.sessionToken, resourceId, bookingDate)
      .then((items) => {
        if (!active) return;
        const future = items.filter((item) => item.available && new Date(item.startsAt).getTime() > Date.now());
        setSlots(future);
        setSelectedSlot(null);
      })
      .catch((exception) => { if (active) setError(exception instanceof Error ? exception.message : "No fue posible consultar disponibilidad."); });
    return () => { active = false; };
  }, [bookingDate, resourceId, session, summary]);

  async function reserve() {
    if (!session || !selectedSlot) return;
    setBookingBusy(true);
    setError(null);
    setMessage(null);
    try {
      await createKioskBooking(session.sessionId, session.sessionToken, {
        resourceId,
        startsAt: selectedSlot.startsAt,
        endsAt: selectedSlot.endsAt,
        notes: bookingNotes.trim() || undefined,
      });
      setMessage("Tu reserva fue creada correctamente.");
      setBookingNotes("");
      const items = await getKioskBookingAvailability(session.sessionId, session.sessionToken, resourceId, bookingDate);
      setSlots(items.filter((item) => item.available && new Date(item.startsAt).getTime() > Date.now()));
      setSelectedSlot(null);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear la reserva.");
    } finally {
      setBookingBusy(false);
    }
  }

  async function renew(membership: KioskMembership) {
    if (!session) return;
    setWorkingMembershipId(membership.id); setError(null); setMessage(null);
    try {
      const response = await renewMembershipFromKiosk(session.sessionId, session.sessionToken, membership.id, {
        method: "CASH", reference, validateNow: true, notes: "Renovacion desde kiosco",
        idempotencyKey: operationKey("kiosk-renewal", session.sessionId, membership.id),
      });
      setSummary(await getKioskSummary(session.sessionId, session.sessionToken));
      setMessage(response.membership ? `Renovada hasta ${formatDay(response.membership.endsOn)}.` : "Pago pendiente.");
      setReference("");
    } catch (exception) { setError(exception instanceof Error ? exception.message : "No fue posible renovar."); }
    finally { setWorkingMembershipId(null); }
  }

  async function changePlan(membership: KioskMembership) {
    if (!session || !targetPlanId) return;
    setWorkingMembershipId(membership.id); setError(null); setMessage(null);
    try {
      const response = await changeMembershipPlanFromKiosk(session.sessionId, session.sessionToken, membership.id, {
        targetPlanId, method: "CASH", reference, validateNow: true, notes: "Cambio de plan desde kiosco",
        idempotencyKey: operationKey("kiosk-plan-change", session.sessionId, membership.id, targetPlanId),
      });
      setSummary(await getKioskSummary(session.sessionId, session.sessionToken));
      setMessage(response.membership ? `Nuevo plan activo: ${response.membership.planName}.` : "Solicitud registrada.");
      setReference("");
    } catch (exception) { setError(exception instanceof Error ? exception.message : "No fue posible cambiar plan."); }
    finally { setWorkingMembershipId(null); }
  }

  async function end() {
    if (session) await endKioskSession(session.sessionId, session.sessionToken).catch(() => undefined);
    setSummary(null); setIdentifier(""); setBirthDateDisplay("");
    setReference(""); setResources([]); setSlots([]); setSelectedSlot(null); setMessage("Sesion cerrada.");
  }

  if (loading) return <main className="kiosk-screen grid place-items-center px-6 text-white"><p className="kiosk-loading">Preparando pantalla de socios...</p></main>;

  return (
    <main className="kiosk-screen text-white" style={{
      "--brand-primary": session?.primaryColor ?? undefined,
      "--brand-accent": session?.accentColor ?? undefined,
      "--brand-surface": session?.surfaceColor ?? undefined,
    } as CSSProperties}>
      <section className="kiosk-shell">
        <header className="kiosk-header">
          <div className="kiosk-brand-row">
            {session?.logoUrl ? (
              <Image alt={session.brandName ?? session.organizationName ?? "Gimnasio"} className="kiosk-logo" height={72} src={session.logoUrl} unoptimized width={72} />
            ) : (
              <span className="kiosk-logo-fallback">{(session?.brandName ?? session?.organizationName ?? "G").slice(0, 1)}</span>
            )}
            <div><p>Autoservicio</p><h1>{session?.brandName ?? session?.organizationName ?? session?.terminalName ?? params.terminalCode}</h1><span>Consulta tu membresía y reserva recursos para ti.</span></div>
          </div>
          <aside><strong>Sesión segura</strong><span>Expira {session ? formatTime(session.expiresAt) : "N/A"}</span></aside>
        </header>

        {error ? <p className="kiosk-notice error">{error}</p> : null}
        {message ? <p className="kiosk-notice ok">{message}</p> : null}

        {!summary ? (
          <form className="kiosk-card kiosk-verify" onSubmit={verify}>
            <div className="kiosk-step"><span>1</span><div><h2>Verifica tu identidad</h2><p>Usa los datos registrados en recepción.</p></div></div>
            <input className="kiosk-input" onChange={(event) => setIdentifier(event.target.value)} placeholder="Correo o teléfono" required value={identifier} />
            <label className="kiosk-field">
              Fecha de nacimiento
              <input
                aria-label="Fecha de nacimiento en formato día mes año"
                autoComplete="bday"
                className="kiosk-input compact"
                inputMode="numeric"
                maxLength={10}
                onChange={(event) => setBirthDateDisplay(formatMexicanDateInput(event.target.value))}
                placeholder="DD/MM/AAAA"
                required
                type="text"
                value={birthDateDisplay}
              />
              <small>Formato: día/mes/año, por ejemplo 25/08/1990.</small>
            </label>
            <button className="kiosk-primary-action" type="submit">Continuar de forma segura</button>
          </form>
        ) : (
          <section className="grid gap-5">
            <section className="kiosk-card">
              <div className="kiosk-step"><span>2</span><div><h2>Reserva un recurso</h2><p>Sólo ves horarios disponibles; nunca los datos de otros socios.</p></div></div>
              <div className="grid gap-3 md:grid-cols-2">
                <label className="kiosk-field">Recurso<select className="kiosk-input compact" onChange={(event) => setResourceId(event.target.value)} value={resourceId}>{resources.map((resource) => <option key={resource.id} value={resource.id}>{resource.name}</option>)}</select></label>
                <label className="kiosk-field">Día<input className="kiosk-input compact" min={toDateInput(new Date())} onChange={(event) => setBookingDate(event.target.value)} type="date" value={bookingDate} /></label>
              </div>
              <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
                {slots.map((slot) => <button className={selectedSlot?.startsAt === slot.startsAt ? "kiosk-primary-action small" : "kiosk-secondary-action"} key={slot.startsAt} onClick={() => setSelectedSlot(slot)} type="button">{formatSlot(slot.startsAt)}</button>)}
                {slots.length === 0 ? <p className="kiosk-muted col-span-full">No hay horarios disponibles para este día.</p> : null}
              </div>
              <label className="kiosk-field mt-4">Nota opcional<textarea className="kiosk-input" onChange={(event) => setBookingNotes(event.target.value)} placeholder="Indicaciones para tu reserva" value={bookingNotes} /></label>
              <button className="kiosk-primary-action mt-4" disabled={!selectedSlot || bookingBusy} onClick={reserve} type="button">{bookingBusy ? "Reservando..." : "Confirmar reserva"}</button>
            </section>

            <section className="kiosk-content-grid">
              <div className="kiosk-card">
                <div className="kiosk-member-header"><span>Hola</span><h2>{summary.clientName}</h2></div>
                <label className="kiosk-field">Referencia de pago<input className="kiosk-input compact" onChange={(event) => setReference(event.target.value)} placeholder="Opcional" value={reference} /></label>
                <label className="kiosk-field">Nuevo plan<select className="kiosk-input compact" onChange={(event) => setTargetPlanId(event.target.value)} value={targetPlanId}>{plans.map((plan) => <option key={plan.id} value={plan.id}>{plan.name} - {formatMoney(plan.priceCents, plan.currency)}</option>)}</select></label>
                <div className="kiosk-membership-list">{summary.memberships.length === 0 ? <p className="kiosk-muted">No hay membresías registradas.</p> : null}{summary.memberships.map((membership) => <article className="kiosk-membership-card" key={membership.id}><div><span>{membership.status}</span><h3>{membership.planName}</h3></div><p>{membership.status} - {formatDay(membership.startsOn)} a {formatDay(membership.endsOn)}</p><div className="kiosk-action-grid"><button className="kiosk-primary-action small" disabled={workingMembershipId !== null || membership.status !== "ACTIVE"} onClick={() => renew(membership)} type="button">{workingMembershipId === membership.id ? "Procesando..." : "Renovar"}</button><button className="kiosk-secondary-action" disabled={workingMembershipId !== null || membership.status !== "ACTIVE" || !targetPlanId || targetPlanId === membership.planId} onClick={() => changePlan(membership)} type="button">Cambiar plan</button></div></article>)}</div>
              </div>
              <aside className="kiosk-card kiosk-side-card"><div className="kiosk-step"><span>3</span><div><h2>Pagos pendientes</h2><p>Consulta lo que requiere revisión en mostrador.</p></div></div><div className="kiosk-payment-list">{summary.pendingPayments.length === 0 ? <p className="kiosk-muted">No tienes pagos pendientes.</p> : null}{summary.pendingPayments.map((payment) => <article className="kiosk-payment-card" key={payment.id}><strong>{payment.saleNumber}</strong><span>{payment.method} - {formatMoney(payment.amountCents, payment.currency)}</span></article>)}</div><button className="kiosk-end-action" onClick={end} type="button">Cerrar sesión</button></aside>
            </section>
          </section>
        )}
      </section>
    </main>
  );
}

function formatMoney(cents: number, currency: string) { return new Intl.NumberFormat("es-MX", { style: "currency", currency }).format(cents / 100); }
function formatDay(value: string) { return new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", timeZone: "UTC" }).format(new Date(`${value}T00:00:00Z`)); }
function formatTime(value: string) { return new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)); }
function formatSlot(value: string) { return new Intl.DateTimeFormat("es-MX", { hour: "2-digit", minute: "2-digit" }).format(new Date(value)); }
function operationKey(...parts: string[]) { const random = typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : `${Date.now()}`; return [...parts, random].join(":"); }
