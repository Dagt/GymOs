"use client";

import Image from "next/image";
import { useParams } from "next/navigation";
import { CSSProperties, FormEvent, useEffect, useMemo, useRef, useState } from "react";
import {
  clearSession,
  createKioskPaymentIntent,
  createKioskQuote,
  createKioskSession,
  endKioskSession,
  getKioskMembershipOptions,
  getStoredSession,
  updateKioskContactData,
  verifyKioskSession,
  type KioskMembership,
  type KioskPaymentIntent,
  type KioskPlanOption,
  type KioskQuote,
  type KioskQuoteOperation,
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
import {
  createKioskWellnessAppointment,
  getKioskWellnessAvailability,
  getKioskWellnessProfessionals,
  getKioskWellnessServices,
  type KioskWellnessAvailability,
  type KioskWellnessProfessional,
  type KioskWellnessService,
} from "@/lib/kiosk-wellness";
import {
  getKioskAutopayMandates,
  getKioskAutopayReadiness,
  revokeKioskAutopayMandate,
  startKioskAutopaySetup,
  type KioskAutopayMandate,
} from "@/lib/kiosk-autopay";
import { formatMexicanDateInput, parseMexicanDate, toDateInput } from "@/lib/date-time";
import { consumeKioskLaunch } from "@/lib/kiosk-launch";

const VERIFIED_INACTIVITY_MS = 3 * 60 * 1000;

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
  const [quote, setQuote] = useState<KioskQuote | null>(null);
  const [paymentIntent, setPaymentIntent] = useState<KioskPaymentIntent | null>(null);

  const [resources, setResources] = useState<KioskBookingResource[]>([]);
  const [resourceId, setResourceId] = useState("");
  const [bookingDate, setBookingDate] = useState(() => toDateInput(new Date()));
  const [slots, setSlots] = useState<KioskAvailabilitySlot[]>([]);
  const [selectedSlot, setSelectedSlot] = useState<KioskAvailabilitySlot | null>(null);
  const [bookingNotes, setBookingNotes] = useState("");
  const [bookingBusy, setBookingBusy] = useState(false);

  const [wellnessProfessionals, setWellnessProfessionals] = useState<KioskWellnessProfessional[]>([]);
  const [wellnessProfessionalId, setWellnessProfessionalId] = useState("");
  const [wellnessServices, setWellnessServices] = useState<KioskWellnessService[]>([]);
  const [wellnessServiceId, setWellnessServiceId] = useState("");
  const [wellnessDate, setWellnessDate] = useState(() => toDateInput(new Date()));
  const [wellnessSlots, setWellnessSlots] = useState<KioskWellnessAvailability[]>([]);
  const [wellnessSlot, setWellnessSlot] = useState<KioskWellnessAvailability | null>(null);
  const [wellnessNotes, setWellnessNotes] = useState("");
  const [wellnessBusy, setWellnessBusy] = useState(false);

  const [autopayReady, setAutopayReady] = useState(false);
  const [autopayMandates, setAutopayMandates] = useState<KioskAutopayMandate[]>([]);
  const [autopayConsent, setAutopayConsent] = useState(false);
  const [autopayBusy, setAutopayBusy] = useState(false);

  const operationKeys = useRef(new Map<string, string>());
  const birthDate = useMemo(() => parseMexicanDate(birthDateDisplay) ?? "", [birthDateDisplay]);

  useEffect(() => {
    let active = true;
    async function start() {
      setLoading(true);
      setError(null);
      try {
        let created: KioskSessionResponse;
        if (params.terminalCode === "reception") {
          clearSession();
          created = consumeKioskLaunch(window.location.hash) as KioskSessionResponse;
          window.history.replaceState(null, "", window.location.pathname);
          if (!created) throw new Error("Abre el modo kiosco desde la pantalla de recepción.");
        } else {
          const stored = getStoredSession();
          created = await createKioskSession(params.terminalCode, {
            organizationId: stored?.user.organizationId,
            branchId: stored?.user.branchId,
          });
        }
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
        session.sessionId, session.sessionToken, identifier, birthDate,
      );
      const [options, bookingResources, professionals, readiness, mandates] = await Promise.all([
        nextSummary.capabilities.membershipPlanChangeAllowed
          ? getKioskMembershipOptions(session.sessionId, session.sessionToken)
          : Promise.resolve([]),
        nextSummary.capabilities.resourceBookingAllowed
          ? getKioskBookingResources(session.sessionId, session.sessionToken)
          : Promise.resolve([]),
        nextSummary.capabilities.wellnessBookingAllowed
          ? getKioskWellnessProfessionals(session.sessionId, session.sessionToken)
          : Promise.resolve([]),
        nextSummary.capabilities.autopayAllowed
          ? getKioskAutopayReadiness(session.sessionId, session.sessionToken)
          : Promise.resolve({ ready: false }),
        nextSummary.capabilities.autopayAllowed
          ? getKioskAutopayMandates(session.sessionId, session.sessionToken)
          : Promise.resolve([]),
      ]);
      setSummary(nextSummary);
      setContactEmail(nextSummary.profile?.email ?? "");
      setContactPhone(nextSummary.profile?.phone ?? "");
      setPlans(options);
      setTargetPlanId(options[0]?.id ?? "");
      setResources(bookingResources);
      setResourceId(bookingResources[0]?.id ?? "");
      setWellnessProfessionals(professionals);
      setWellnessProfessionalId(professionals[0]?.id ?? "");
      setAutopayReady(readiness.ready);
      setAutopayMandates(mandates);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible verificar.");
    }
  }

  useEffect(() => {
    if (!session || !summary?.capabilities.resourceBookingAllowed || !resourceId || !bookingDate) {
      setSlots([]); setSelectedSlot(null); return;
    }
    let active = true;
    getKioskBookingAvailability(session.sessionId, session.sessionToken, resourceId, bookingDate)
      .then((items) => {
        if (!active) return;
        setSlots(items.filter((item) => item.available && new Date(item.startsAt).getTime() > Date.now()));
        setSelectedSlot(null);
      })
      .catch((exception) => { if (active) setError(exception instanceof Error ? exception.message : "No fue posible consultar disponibilidad."); });
    return () => { active = false; };
  }, [bookingDate, resourceId, session, summary]);

  useEffect(() => {
    if (!session || !wellnessProfessionalId) {
      setWellnessServices([]); setWellnessServiceId(""); return;
    }
    let active = true;
    getKioskWellnessServices(session.sessionId, session.sessionToken, wellnessProfessionalId)
      .then((items) => {
        if (!active) return;
        setWellnessServices(items);
        setWellnessServiceId(items[0]?.id ?? "");
      })
      .catch((exception) => { if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar servicios de bienestar."); });
    return () => { active = false; };
  }, [session, wellnessProfessionalId]);

  useEffect(() => {
    if (!session || !wellnessProfessionalId || !wellnessServiceId || !wellnessDate) {
      setWellnessSlots([]); setWellnessSlot(null); return;
    }
    let active = true;
    getKioskWellnessAvailability(
      session.sessionId, session.sessionToken, wellnessProfessionalId, wellnessServiceId, wellnessDate,
    ).then((items) => {
      if (!active) return;
      setWellnessSlots(items.filter((item) => item.available && new Date(item.startsAt).getTime() > Date.now()));
      setWellnessSlot(null);
    }).catch((exception) => { if (active) setError(exception instanceof Error ? exception.message : "No fue posible consultar la agenda de bienestar."); });
    return () => { active = false; };
  }, [session, wellnessDate, wellnessProfessionalId, wellnessServiceId]);

  useEffect(() => {
    if (!session || !summary) return;
    let timeout = window.setTimeout(closeForInactivity, VERIFIED_INACTIVITY_MS);
    const reset = () => {
      window.clearTimeout(timeout);
      timeout = window.setTimeout(closeForInactivity, VERIFIED_INACTIVITY_MS);
    };
    const events: Array<keyof WindowEventMap> = ["pointerdown", "keydown", "touchstart"];
    events.forEach((eventName) => window.addEventListener(eventName, reset, { passive: true }));
    async function closeForInactivity() {
      await endKioskSession(session.sessionId, session.sessionToken).catch(() => undefined);
      window.location.replace("/kiosk/closed?reason=inactive");
    }
    return () => {
      window.clearTimeout(timeout);
      events.forEach((eventName) => window.removeEventListener(eventName, reset));
    };
  }, [session, summary]);

  async function reserve() {
    if (!session || !selectedSlot) return;
    setBookingBusy(true); setError(null); setMessage(null);
    try {
      await createKioskBooking(session.sessionId, session.sessionToken, {
        resourceId, startsAt: selectedSlot.startsAt, endsAt: selectedSlot.endsAt,
        notes: bookingNotes.trim() || undefined,
      });
      setMessage("Tu reserva fue creada correctamente.");
      setBookingNotes("");
      const items = await getKioskBookingAvailability(session.sessionId, session.sessionToken, resourceId, bookingDate);
      setSlots(items.filter((item) => item.available && new Date(item.startsAt).getTime() > Date.now()));
      setSelectedSlot(null);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear la reserva.");
    } finally { setBookingBusy(false); }
  }

  async function reserveWellness() {
    if (!session || !wellnessSlot || !wellnessServiceId) return;
    const selectedService = wellnessServices.find((item) => item.id === wellnessServiceId);
    if (!selectedService) return;
    if (selectedService.priceCents > 0) {
      setError("Los servicios con costo se habilitarán cuando exista una pasarela configurada.");
      return;
    }
    setWellnessBusy(true); setError(null); setMessage(null);
    try {
      const appointment = await createKioskWellnessAppointment(session.sessionId, session.sessionToken, {
        professionalId: wellnessProfessionalId,
        serviceId: wellnessServiceId,
        startsAt: wellnessSlot.startsAt,
        notes: wellnessNotes.trim() || undefined,
      });
      setMessage(`Cita ${appointment.status.toLowerCase()} con ${appointment.professionalName}.`);
      setWellnessNotes(""); setWellnessSlot(null);
      const items = await getKioskWellnessAvailability(
        session.sessionId, session.sessionToken, wellnessProfessionalId, wellnessServiceId, wellnessDate,
      );
      setWellnessSlots(items.filter((item) => item.available && new Date(item.startsAt).getTime() > Date.now()));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear la cita.");
    } finally { setWellnessBusy(false); }
  }

  async function requestQuote(membership: KioskMembership, operationType: KioskQuoteOperation) {
    if (!session) return;
    setWorkingMembershipId(membership.id); setError(null); setMessage(null); setPaymentIntent(null);
    try {
      const created = await createKioskQuote(session.sessionId, session.sessionToken, {
        operationType,
        membershipId: membership.id,
        planId: operationType === "PLAN_CHANGE" ? targetPlanId : membership.planId,
      });
      setQuote(created); setMessage("Revisa el importe antes de continuar al pago.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible generar la cotización.");
    } finally { setWorkingMembershipId(null); }
  }

  async function requestEnrollmentQuote() {
    if (!session || !targetPlanId) return;
    setWorkingMembershipId("enrollment"); setError(null); setMessage(null); setPaymentIntent(null);
    try {
      const created = await createKioskQuote(session.sessionId, session.sessionToken, {
        operationType: "ENROLLMENT", planId: targetPlanId,
      });
      setQuote(created); setMessage("Revisa el importe de tu nueva membresía.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible generar la cotización.");
    } finally { setWorkingMembershipId(null); }
  }

  async function continueToPayment() {
    if (!session || !quote) return;
    setWorkingMembershipId(quote.membershipId ?? "enrollment"); setError(null);
    try {
      const intent = await createKioskPaymentIntent(session.sessionId, session.sessionToken, {
        quoteId: quote.id,
        idempotencyKey: stableOperationKey(operationKeys.current, "kiosk-checkout", session.sessionId, quote.id),
        successUrl: `${window.location.origin}/kiosk/payment-result?status=success`,
        cancelUrl: `${window.location.origin}/kiosk/payment-result?status=cancelled`,
      });
      setPaymentIntent(intent);
      if (intent.checkoutUrl) { window.location.assign(intent.checkoutUrl); return; }
      if (!intent.clientSecret) throw new Error("La pasarela no devolvió una sesión de pago utilizable.");
      setMessage("La intención de pago fue creada. Falta montar el componente alojado del proveedor.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible iniciar el pago.");
    } finally { setWorkingMembershipId(null); }
  }

  async function startAutopay(membershipId: string) {
    if (!session || !autopayConsent) return;
    setAutopayBusy(true); setError(null);
    try {
      const setup = await startKioskAutopaySetup(session.sessionId, session.sessionToken, {
        membershipId,
        consentAccepted: true,
        idempotencyKey: stableOperationKey(operationKeys.current, "kiosk-autopay", session.sessionId, membershipId),
        successUrl: `${window.location.origin}/kiosk/payment-result?status=autopay-success`,
        cancelUrl: `${window.location.origin}/kiosk/payment-result?status=autopay-cancelled`,
      });
      setAutopayMandates(await getKioskAutopayMandates(session.sessionId, session.sessionToken));
      if (setup.checkoutUrl) window.location.assign(setup.checkoutUrl);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible iniciar la domiciliación.");
    } finally { setAutopayBusy(false); }
  }

  async function revokeAutopay(mandateId: string) {
    if (!session) return;
    setAutopayBusy(true); setError(null);
    try {
      await revokeKioskAutopayMandate(session.sessionId, session.sessionToken, mandateId, "Revocada por el socio");
      setAutopayMandates(await getKioskAutopayMandates(session.sessionId, session.sessionToken));
      setMessage("La domiciliación fue revocada.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible revocar la domiciliación.");
    } finally { setAutopayBusy(false); }
  }

  async function updateContact(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !summary?.capabilities.profileUpdateAllowed) return;
    setContactBusy(true); setError(null); setMessage(null);
    try {
      const profile = await updateKioskContactData(session.sessionId, session.sessionToken, {
        email: contactEmail.trim() || null, phone: contactPhone.trim() || null,
      });
      setSummary({ ...summary, clientName: profile.displayName, profile });
      setContactEmail(profile.email ?? ""); setContactPhone(profile.phone ?? "");
      setMessage("Tus datos de contacto se actualizaron correctamente.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar tus datos.");
    } finally { setContactBusy(false); }
  }

  async function end() {
    if (session) await endKioskSession(session.sessionId, session.sessionToken).catch(() => undefined);
    operationKeys.current.clear();
    window.location.replace("/kiosk/closed");
  }

  if (loading) return <main className="kiosk-screen grid place-items-center px-6 text-white"><p className="kiosk-loading">Preparando pantalla de socios...</p></main>;
  if (!session) return <main className="kiosk-screen grid place-items-center px-6 text-white"><section className="kiosk-card kiosk-verify text-center"><h1>No fue posible iniciar el kiosco</h1><p className="kiosk-muted mt-3">{error ?? "Vuelve a abrir el autoservicio desde recepción."}</p><button className="kiosk-secondary-action mt-5" onClick={() => window.close()} type="button">Cerrar pestaña</button></section></main>;

  return (
    <main className="kiosk-screen text-white" style={{
      "--brand-primary": session.primaryColor ?? undefined,
      "--brand-accent": session.accentColor ?? undefined,
      "--brand-surface": session.surfaceColor ?? undefined,
    } as CSSProperties}>
      <section className="kiosk-shell">
        <header className="kiosk-header">
          <div className="kiosk-brand-row">
            {session.logoUrl ? <Image alt={session.brandName ?? session.organizationName ?? "Gimnasio"} className="kiosk-logo" height={72} src={session.logoUrl} unoptimized width={72} /> : <span className="kiosk-logo-fallback">{(session.brandName ?? session.organizationName ?? "G").slice(0, 1)}</span>}
            <div><p>Autoservicio</p><h1>{session.brandName ?? session.organizationName ?? session.terminalName ?? "GymOS"}</h1><span>Membresías, reservas y datos personales en una sesión segura.</span></div>
          </div>
          <aside><strong>Sesión segura</strong><span>Expira {formatTime(session.expiresAt)}</span></aside>
        </header>

        {error ? <p className="kiosk-notice error">{error}</p> : null}
        {message ? <p className="kiosk-notice ok">{message}</p> : null}

        {!summary ? (
          <form className="kiosk-card kiosk-verify" onSubmit={verify}>
            <div className="kiosk-step"><span>1</span><div><h2>Verifica tu identidad</h2><p>Usa los datos registrados en recepción.</p></div></div>
            <input className="kiosk-input" onChange={(event) => setIdentifier(event.target.value)} placeholder="Correo o teléfono" required value={identifier} />
            <label className="kiosk-field">Fecha de nacimiento<input aria-label="Fecha de nacimiento" autoComplete="bday" className="kiosk-input compact" inputMode="numeric" maxLength={10} onChange={(event) => setBirthDateDisplay(formatMexicanDateInput(event.target.value))} placeholder="DD/MM/AAAA" required type="text" value={birthDateDisplay} /><small>Formato: día/mes/año, por ejemplo 25/08/1990.</small></label>
            <button className="kiosk-primary-action" type="submit">Continuar de forma segura</button>
          </form>
        ) : (
          <section className="grid gap-5">
            {quote ? <section className="kiosk-card" aria-live="polite"><div className="kiosk-step"><span>2</span><div><h2>Confirma tu cotización</h2><p>El importe fue calculado por GymOS y vence {formatTime(quote.expiresAt)}.</p></div></div><div className="mt-4 grid gap-2 text-sm"><p className="flex justify-between"><span>Plan</span><strong>{quote.planName}</strong></p><p className="flex justify-between"><span>Precio de lista</span><strong>{formatMoney(quote.listPriceCents, quote.currency)}</strong></p><p className="flex justify-between"><span>Descuentos</span><strong>-{formatMoney(quote.discountCents, quote.currency)}</strong></p><p className="flex justify-between border-t border-white/20 pt-3 text-lg"><span>Total</span><strong>{formatMoney(quote.totalCents, quote.currency)}</strong></p></div>{parseBenefits(quote.benefitsJson).length > 0 ? <div className="kiosk-feature-note mt-4">{parseBenefits(quote.benefitsJson).map((benefit) => <span key={benefit.code}>{benefit.name}: -{formatMoney(benefit.discountCents, quote.currency)}</span>)}</div> : null}{!summary.capabilities.onlineCardPaymentsReady ? <p className="kiosk-feature-note mt-4">La cotización está disponible, pero el proveedor de pagos aún no está configurado.</p> : null}<div className="mt-4 flex flex-wrap gap-3"><button className="kiosk-primary-action small" disabled={!summary.capabilities.onlineCardPaymentsReady || workingMembershipId !== null} onClick={continueToPayment} type="button">Continuar al pago</button><button className="kiosk-secondary-action" onClick={() => { setQuote(null); setPaymentIntent(null); }} type="button">Cancelar</button></div>{paymentIntent ? <p className="kiosk-muted mt-3">Estado de pago: {paymentIntent.status}</p> : null}</section> : null}

            {summary.capabilities.resourceBookingAllowed ? <section className="kiosk-card"><div className="kiosk-step"><span>3</span><div><h2>Reserva un recurso</h2><p>Sólo ves horarios disponibles; nunca los datos de otros socios.</p></div></div><div className="grid gap-3 md:grid-cols-2"><label className="kiosk-field">Recurso<select className="kiosk-input compact" onChange={(event) => setResourceId(event.target.value)} value={resourceId}>{resources.map((resource) => <option key={resource.id} value={resource.id}>{resource.name}</option>)}</select></label><label className="kiosk-field">Día<input className="kiosk-input compact" min={toDateInput(new Date())} onChange={(event) => setBookingDate(event.target.value)} type="date" value={bookingDate} /></label></div><div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">{slots.map((slot) => <button className={selectedSlot?.startsAt === slot.startsAt ? "kiosk-primary-action small" : "kiosk-secondary-action"} key={slot.startsAt} onClick={() => setSelectedSlot(slot)} type="button">{formatSlot(slot.startsAt)}</button>)}{slots.length === 0 ? <p className="kiosk-muted col-span-full">No hay horarios disponibles para este día.</p> : null}</div><label className="kiosk-field mt-4">Nota opcional<textarea className="kiosk-input" onChange={(event) => setBookingNotes(event.target.value)} value={bookingNotes} /></label><button className="kiosk-primary-action mt-4" disabled={!selectedSlot || bookingBusy} onClick={reserve} type="button">{bookingBusy ? "Reservando..." : "Confirmar reserva"}</button></section> : null}

            {summary.capabilities.wellnessBookingAllowed ? <section className="kiosk-card"><div className="kiosk-step"><span>4</span><div><h2>Profesionales de bienestar</h2><p>Los servicios gratuitos se confirman aquí; los servicios con costo esperarán la pasarela.</p></div></div><div className="grid gap-3 md:grid-cols-3"><label className="kiosk-field">Profesional<select className="kiosk-input compact" onChange={(event) => setWellnessProfessionalId(event.target.value)} value={wellnessProfessionalId}>{wellnessProfessionals.map((item) => <option key={item.id} value={item.id}>{item.displayName}</option>)}</select></label><label className="kiosk-field">Servicio<select className="kiosk-input compact" onChange={(event) => setWellnessServiceId(event.target.value)} value={wellnessServiceId}>{wellnessServices.map((item) => <option key={item.id} value={item.id}>{item.name} · {formatMoney(item.priceCents, item.currency)}</option>)}</select></label><label className="kiosk-field">Día<input className="kiosk-input compact" min={toDateInput(new Date())} onChange={(event) => setWellnessDate(event.target.value)} type="date" value={wellnessDate} /></label></div><div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">{wellnessSlots.map((slot) => <button className={wellnessSlot?.startsAt === slot.startsAt ? "kiosk-primary-action small" : "kiosk-secondary-action"} key={slot.startsAt} onClick={() => setWellnessSlot(slot)} type="button">{formatSlot(slot.startsAt)}</button>)}{wellnessSlots.length === 0 ? <p className="kiosk-muted col-span-full">No hay horarios disponibles.</p> : null}</div><label className="kiosk-field mt-4">Nota opcional<textarea className="kiosk-input" onChange={(event) => setWellnessNotes(event.target.value)} value={wellnessNotes} /></label><button className="kiosk-primary-action mt-4" disabled={!wellnessSlot || wellnessBusy} onClick={reserveWellness} type="button">{wellnessBusy ? "Agendando..." : "Confirmar cita"}</button></section> : null}

            <section className="kiosk-content-grid"><div className="kiosk-card"><div className="kiosk-member-header"><span>Hola</span><h2>{summary.clientName}</h2></div>{summary.capabilities.membershipPlanChangeAllowed ? <label className="kiosk-field">Plan<select className="kiosk-input compact" onChange={(event) => setTargetPlanId(event.target.value)} value={targetPlanId}>{plans.map((plan) => <option key={plan.id} value={plan.id}>{plan.name} - {formatMoney(plan.priceCents, plan.currency)}</option>)}</select></label> : null}<div className="kiosk-membership-list">{summary.memberships.length === 0 ? <div><p className="kiosk-muted">No hay membresías registradas.</p>{summary.capabilities.membershipPlanChangeAllowed ? <button className="kiosk-primary-action small mt-3" disabled={!targetPlanId || workingMembershipId !== null} onClick={requestEnrollmentQuote} type="button">Cotizar primera membresía</button> : null}</div> : null}{summary.memberships.map((membership) => <article className="kiosk-membership-card" key={membership.id}><div><span>{membership.status}</span><h3>{membership.planName}</h3></div><p>{membership.status} - {formatDay(membership.startsOn)} a {formatDay(membership.endsOn)}</p><div className="kiosk-action-grid">{summary.capabilities.membershipRenewalAllowed ? <button className="kiosk-primary-action small" disabled={workingMembershipId !== null || membership.status === "CANCELLED"} onClick={() => requestQuote(membership, "RENEWAL")} type="button">{workingMembershipId === membership.id ? "Cotizando..." : "Cotizar renovación"}</button> : null}{summary.capabilities.membershipPlanChangeAllowed ? <button className="kiosk-secondary-action" disabled={workingMembershipId !== null || membership.status !== "ACTIVE" || !targetPlanId || targetPlanId === membership.planId} onClick={() => requestQuote(membership, "PLAN_CHANGE")} type="button">Cotizar cambio</button> : null}</div></article>)}</div></div><aside className="kiosk-card kiosk-side-card"><div className="kiosk-step"><span>5</span><div><h2>Mis datos</h2><p>Actualiza el contacto que usa tu gimnasio.</p></div></div>{summary.capabilities.profileUpdateAllowed ? <form onSubmit={updateContact}><label className="kiosk-field">Correo<input className="kiosk-input compact" onChange={(event) => setContactEmail(event.target.value)} type="email" value={contactEmail} /></label><label className="kiosk-field">Teléfono en formato +52<input className="kiosk-input compact" onChange={(event) => setContactPhone(event.target.value)} pattern="\+52\d{10}" placeholder="+521234567890" value={contactPhone} /></label><button className="kiosk-secondary-action mt-4 w-full" disabled={contactBusy || (!contactEmail.trim() && !contactPhone.trim())} type="submit">{contactBusy ? "Guardando..." : "Guardar datos"}</button></form> : <p className="kiosk-muted mt-4">Esta sucursal no permite actualizar datos desde autoservicio.</p>}{summary.capabilities.autopayAllowed ? <div className="kiosk-feature-note"><strong>Domiciliación</strong>{autopayMandates.filter((item) => item.status !== "REVOKED").map((item) => <span key={item.id}>{item.status}<button className="kiosk-secondary-action mt-2" disabled={autopayBusy} onClick={() => revokeAutopay(item.id)} type="button">Revocar</button></span>)}{autopayReady && summary.memberships[0] ? <><label className="mt-2 flex items-start gap-2"><input checked={autopayConsent} onChange={(event) => setAutopayConsent(event.target.checked)} type="checkbox" /><span>Autorizo el almacenamiento tokenizado y cargos recurrentes conforme al consentimiento mostrado.</span></label><button className="kiosk-primary-action small mt-3" disabled={!autopayConsent || autopayBusy} onClick={() => startAutopay(summary.memberships[0].id)} type="button">Configurar domiciliación</button></> : <span>La estructura de consentimiento está lista; la tokenización se habilitará al configurar proveedor.</span>}</div> : null}<button className="kiosk-end-action" onClick={end} type="button">Cerrar sesión</button></aside></section>
          </section>
        )}
      </section>
    </main>
  );
}

type QuoteBenefit = { code: string; name: string; discountCents: number };
function parseBenefits(value: string): QuoteBenefit[] { try { const parsed = JSON.parse(value) as unknown; return Array.isArray(parsed) ? parsed as QuoteBenefit[] : []; } catch { return []; } }
function formatMoney(cents: number, currency: string) { return new Intl.NumberFormat("es-MX", { style: "currency", currency }).format(cents / 100); }
function formatDay(value: string) { return new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", timeZone: "UTC" }).format(new Date(`${value}T00:00:00Z`)); }
function formatTime(value: string) { return new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)); }
function formatSlot(value: string) { return new Intl.DateTimeFormat("es-MX", { hour: "2-digit", minute: "2-digit" }).format(new Date(value)); }
function stableOperationKey(keys: Map<string, string>, ...parts: string[]) { const operation = parts.join(":"); const existing = keys.get(operation); if (existing) return existing; const random = typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID() : `${Date.now()}`; const created = [...parts, random].join(":"); keys.set(operation, created); return created; }
