"use client";

import Image from "next/image";
import { useParams } from "next/navigation";
import { CSSProperties, FormEvent, useEffect, useMemo, useRef, useState } from "react";
import {
  changeMembershipPlanFromKiosk,
  createKioskSession,
  endKioskSession,
  getStoredSession,
  getKioskMembershipOptions,
  getKioskSummary,
  renewMembershipFromKiosk,
  updateKioskContactData,
  verifyKioskSession,
  type KioskMembership,
  type KioskPlanOption,
  type KioskSessionResponse,
  type KioskSummaryResponse,
} from "@/lib/api";
import { createReceptionKioskSession } from "@/lib/api/selfservice";
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
  const [contactEmail, setContactEmail] = useState("");
  const [contactPhone, setContactPhone] = useState("");
  const [contactBusy, setContactBusy] = useState(false);
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
  const operationKeys = useRef(new Map<string, string>());

  const birthDate = useMemo(() => parseMexicanDate(birthDateDisplay) ?? "", [birthDateDisplay]);

  useEffect(() => {
    let active = true;
    async function start() {
      setLoading(true);
      setError(null);
      try {
        const stored = getStoredSession();
        const created = params.terminalCode === "reception"
          ? await createReceptionSession(stored)
          : await createKioskSession(params.terminalCode, {
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
      const nextSummary = await verifyKioskSession(
        session.sessionId,
        session.sessionToken,
        identifier,
        birthDate,
      );
      const [options, bookingResources] = await Promise.all([
        nextSummary.capabilities.membershipPlanChangeAllowed
          ? getKioskMembershipOptions(session.sessionId, session.sessionToken)
          : Promise.resolve([]),
        nextSummary.capabilities.resourceBookingAllowed
          ? getKioskBookingResources(session.sessionId, session.sessionToken)
          : Promise.resolve([]),
      ]);
      setSummary(nextSummary);
      setContactEmail(nextSummary.profile?.email ?? "");
      setContactPhone(nextSummary.profile?.phone ?? "");
      setPlans(options);
      setTargetPlanId(options[0]?.id ?? "");
      setResources(bookingResources);
      setResourceId(bookingResources[0]?.id ?? "");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible verificar.");
    }
  }

  useEffect(() => {
    if (!session || !summary?.capabilities.resourceBookingAllowed || !resourceId || !bookingDate) {
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
    if (!session || !summary?.capabilities.onlineCardPaymentsReady) return;
    setWorkingMembershipId(membership.id); setError(null); setMessage(null);
    try {
      const response = await renewMembershipFromKiosk(session.sessionId, session.sessionToken, membership.id, {
        idempotencyKey: stableOperationKey(
          operationKeys.current,
          "kiosk-renewal",
          session.sessionId,
          membership.id,
        ),
      });
      setSummary(await getKioskSummary(session.sessionId, session.sessionToken));
      setMessage(response.membership ? `Renovada hasta ${formatDay(response.membership.endsOn)}.` : "Pago pendiente.");
    } catch (exception) { setError(exception instanceof Error ? exception.message : "No fue posible renovar."); }
    finally { setWorkingMembershipId(null); }
  }

  async function changePlan(membership: KioskMembership) {
    if (!session || !targetPlanId || !summary?.capabilities.onlineCardPaymentsReady) return;
    setWorkingMembershipId(membership.id); setError(null); setMessage(null);
    try {
      const response = await changeMembershipPlanFromKiosk(session.sessionId, session.sessionToken, membership.id, {
        targetPlanId,
        idempotencyKey: stableOperationKey(
          operationKeys.current,
          "kiosk-plan-change",
          session.sessionId,
          membership.id,
          targetPlanId,
        ),
      });
      setSummary(await getKioskSummary(session.sessionId, session.sessionToken));
      setMessage(response.membership ? `Nuevo plan activo: ${response.membership.planName}.` : "Solicitud registrada.");
    } catch (exception) { setError(exception instanceof Error ? exception.message : "No fue posible cambiar plan."); }
    finally { setWorkingMembershipId(null); }
  }

  async function updateContact(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !summary?.capabilities.profileUpdateAllowed) return;
    setContactBusy(true);
    setError(null);
    setMessage(null);
    try {
      const profile = await updateKioskContactData(session.sessionId, session.sessionToken, {
        email: contactEmail.trim() || null,
        phone: contactPhone.trim() || null,
      });
      setSummary({ ...summary, clientName: profile.displayName, profile });
      setContactEmail(profile.email ?? "");
      setContactPhone(profile.phone ?? "");
      setMessage("Tus datos de contacto se actualizaron correctamente.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar tus datos.");
    } finally {
      setContactBusy(false);
    }
  }

  async function end() {
    if (session) await endKioskSession(session.sessionId, session.sessionToken).catch(() => undefined);
    setSummary(null); setIdentifier(""); setBirthDateDisplay("");
    setContactEmail(""); setContactPhone(""); setResources([]); setSlots([]); setSelectedSlot(null); setMessage("Sesion cerrada.");
    operationKeys.current.clear();
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
            <div><p>Autoservicio</p><h1>{session?.brandName ?? session?.organizationName ?? session?.terminalName ?? "GymOS"}</h1><span>Membresías, reservas y datos personales en una sesión segura.</span></div>
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
            {summary.capabilities.resourceBookingAllowed ? <section className="kiosk-card">
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
            </section> : null}

            {summary.capabilities.wellnessBookingAllowed ? (
              <section className="kiosk-card">
                <div className="kiosk-step"><span>3</span><div><h2>Profesionales de bienestar</h2><p>El catálogo de profesionales, servicios y horarios se incorporará en el siguiente bloque.</p></div></div>
                <p className="kiosk-feature-note">Las consultas con costo abrirán un pago seguro con tarjeta antes de confirmar la cita.</p>
              </section>
            ) : null}

            <section className="kiosk-content-grid">
              <div className="kiosk-card">
                <div className="kiosk-member-header"><span>Hola</span><h2>{summary.clientName}</h2></div>
                {summary.capabilities.membershipPlanChangeAllowed ? <label className="kiosk-field">Nuevo plan<select className="kiosk-input compact" onChange={(event) => setTargetPlanId(event.target.value)} value={targetPlanId}>{plans.map((plan) => <option key={plan.id} value={plan.id}>{plan.name} - {formatMoney(plan.priceCents, plan.currency)}</option>)}</select></label> : null}
                {!summary.capabilities.onlineCardPaymentsReady && (summary.capabilities.membershipRenewalAllowed || summary.capabilities.membershipPlanChangeAllowed) ? (
                  <p className="kiosk-feature-note">La renovación y el cambio de plan usarán exclusivamente tarjeta de crédito o débito. Se habilitarán al conectar la pasarela de pagos.</p>
                ) : null}
                <div className="kiosk-membership-list">
                  {summary.memberships.length === 0 ? <p className="kiosk-muted">No hay membresías registradas.</p> : null}
                  {summary.memberships.map((membership) => (
                    <article className="kiosk-membership-card" key={membership.id}>
                      <div><span>{membership.status}</span><h3>{membership.planName}</h3></div>
                      <p>{membership.status} - {formatDay(membership.startsOn)} a {formatDay(membership.endsOn)}</p>
                      <div className="kiosk-action-grid">
                        {summary.capabilities.membershipRenewalAllowed ? <button className="kiosk-primary-action small" disabled={workingMembershipId !== null || !summary.capabilities.onlineCardPaymentsReady || membership.status === "CANCELLED"} onClick={() => renew(membership)} type="button">{workingMembershipId === membership.id ? "Procesando..." : "Renovar con tarjeta"}</button> : null}
                        {summary.capabilities.membershipPlanChangeAllowed ? <button className="kiosk-secondary-action" disabled={workingMembershipId !== null || !summary.capabilities.onlineCardPaymentsReady || membership.status !== "ACTIVE" || !targetPlanId || targetPlanId === membership.planId} onClick={() => changePlan(membership)} type="button">Cambiar plan</button> : null}
                      </div>
                    </article>
                  ))}
                </div>
              </div>
              <aside className="kiosk-card kiosk-side-card">
                <div className="kiosk-step"><span>4</span><div><h2>Mis datos</h2><p>Actualiza el contacto que usa tu gimnasio.</p></div></div>
                {summary.capabilities.profileUpdateAllowed ? (
                  <form onSubmit={updateContact}>
                    <label className="kiosk-field">Correo<input className="kiosk-input compact" onChange={(event) => setContactEmail(event.target.value)} type="email" value={contactEmail} /></label>
                    <label className="kiosk-field">Teléfono en formato +52<input className="kiosk-input compact" onChange={(event) => setContactPhone(event.target.value)} pattern="\+52\d{10}" placeholder="+521234567890" value={contactPhone} /></label>
                    <button className="kiosk-secondary-action mt-4 w-full" disabled={contactBusy || (!contactEmail.trim() && !contactPhone.trim())} type="submit">{contactBusy ? "Guardando..." : "Guardar datos"}</button>
                  </form>
                ) : <p className="kiosk-muted mt-4">Esta sucursal no permite actualizar datos desde autoservicio.</p>}
                {summary.capabilities.autopayAllowed ? (
                  <div className="kiosk-feature-note">
                    <strong>Domiciliación</strong>
                    <span>Podrás autorizar cobros recurrentes cuando la pasarela permita guardar un método tokenizado.</span>
                  </div>
                ) : null}
                <button className="kiosk-end-action" onClick={end} type="button">Cerrar sesión</button>
              </aside>
            </section>
          </section>
        )}
      </section>
    </main>
  );
}

async function createReceptionSession(stored: ReturnType<typeof getStoredSession>) {
  if (!stored) {
    throw new Error("Inicia sesión en recepción antes de abrir el kiosco.");
  }
  if (!stored.user.branchId) {
    throw new Error("Selecciona una sucursal antes de abrir el kiosco.");
  }
  return createReceptionKioskSession(stored.accessToken);
}

function formatMoney(cents: number, currency: string) { return new Intl.NumberFormat("es-MX", { style: "currency", currency }).format(cents / 100); }
function formatDay(value: string) { return new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", timeZone: "UTC" }).format(new Date(`${value}T00:00:00Z`)); }
function formatTime(value: string) { return new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)); }
function formatSlot(value: string) { return new Intl.DateTimeFormat("es-MX", { hour: "2-digit", minute: "2-digit" }).format(new Date(value)); }
function stableOperationKey(keys: Map<string, string>, ...parts: string[]) {
  const operation = parts.join(":");
  const existing = keys.get(operation);
  if (existing) return existing;
  const random = typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now()}`;
  const created = [...parts, random].join(":");
  keys.set(operation, created);
  return created;
}
