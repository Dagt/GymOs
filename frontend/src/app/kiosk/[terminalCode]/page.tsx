"use client";

import Image from "next/image";
import Link from "next/link";
import { CSSProperties, FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  changeMembershipPlanFromKiosk,
  clearSession,
  enrollMembershipFromKiosk,
  getKioskMembershipQuote,
  getKioskAutopayMandate,
  getKioskAutopayTerms,
  getKioskMembershipOptions,
  getKioskSummary,
  renewMembershipFromKiosk,
  resetKioskSession,
  revokeKioskAutopay,
  setupKioskAutopay,
  updateKioskContactData,
  verifyKioskSession,
  type KioskMembership,
  type KioskAutopayMandate,
  type KioskAutopayTerms,
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
import {
  createKioskWellnessAppointment,
  getKioskWellnessAvailability,
  getKioskWellnessProfessionals,
  getKioskWellnessServices,
  type KioskWellnessAvailability,
  type KioskWellnessProfessional,
  type KioskWellnessService,
} from "@/lib/kiosk-wellness";
import { formatMexicanDateInput, parseMexicanDate, toDateInput } from "@/lib/date-time";
import { consumeKioskLaunch } from "@/lib/kiosk-launch";

const KIOSK_BROWSER_SESSION_KEY = "gymos.kioskBrowserSession";
const KIOSK_OPERATION_KEYS = "gymos.kioskOperationKeys";

export default function KioskPage() {
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
  const [wellnessProfessionals, setWellnessProfessionals] = useState<KioskWellnessProfessional[]>([]);
  const [wellnessProfessionalId, setWellnessProfessionalId] = useState("");
  const [wellnessServices, setWellnessServices] = useState<KioskWellnessService[]>([]);
  const [wellnessServiceId, setWellnessServiceId] = useState("");
  const [wellnessDate, setWellnessDate] = useState(() => toDateInput(new Date()));
  const [wellnessSlots, setWellnessSlots] = useState<KioskWellnessAvailability[]>([]);
  const [wellnessSlot, setWellnessSlot] = useState<KioskWellnessAvailability | null>(null);
  const [wellnessNotes, setWellnessNotes] = useState("");
  const [wellnessBusy, setWellnessBusy] = useState(false);
  const [autopayMembership, setAutopayMembership] = useState<KioskMembership | null>(null);
  const [autopayMandate, setAutopayMandate] = useState<KioskAutopayMandate | null>(null);
  const [autopayTerms, setAutopayTerms] = useState<KioskAutopayTerms | null>(null);
  const [autopayAccepted, setAutopayAccepted] = useState(false);
  const [autopayBusy, setAutopayBusy] = useState(false);
  const [failedLogoUrl, setFailedLogoUrl] = useState<string | null>(null);
  const operationKeys = useRef(new Map<string, string>());

  const birthDate = useMemo(() => parseMexicanDate(birthDateDisplay) ?? "", [birthDateDisplay]);

  useEffect(() => {
    let active = true;
    async function start() {
      setLoading(true);
      setError(null);
      try {
        const resumed = readBrowserKioskSession();
        if (resumed) {
          const reset = await resetKioskSession(resumed.sessionId, resumed.sessionToken);
          writeBrowserKioskSession(reset);
          clearPersistedOperationKeys();
          if (active) setSession(reset);
          return;
        }
        const created = consumeKioskLaunch(window.name);
        window.name = "";
        if (!created) throw new Error("Abre el modo kiosco desde la pantalla de recepción.");
        writeBrowserKioskSession(created);
        clearPersistedOperationKeys();
        clearSession();
        if (active) setSession(created);
      } catch (exception) {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible iniciar kiosco.");
      } finally {
        if (active) setLoading(false);
      }
    }
    void start();
    return () => { active = false; };
  }, []);

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
      const [options, bookingResources, professionals] = await Promise.all([
        nextSummary.capabilities.membershipPlanChangeAllowed
          || nextSummary.capabilities.membershipEnrollmentAllowed
          ? getKioskMembershipOptions(session.sessionId, session.sessionToken)
          : Promise.resolve([]),
        nextSummary.capabilities.resourceBookingAllowed
          ? getKioskBookingResources(session.sessionId, session.sessionToken)
          : Promise.resolve([]),
        nextSummary.capabilities.wellnessBookingAllowed
          ? getKioskWellnessProfessionals(session.sessionId, session.sessionToken)
          : Promise.resolve([]),
      ]);
      setSummary(nextSummary);
      setContactEmail("");
      setContactPhone("");
      setPlans(options);
      setTargetPlanId(options[0]?.id ?? "");
      setResources(bookingResources);
      setResourceId(bookingResources[0]?.id ?? "");
      setWellnessProfessionals(professionals);
      setWellnessProfessionalId(professionals[0]?.id ?? "");
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

  useEffect(() => {
    if (!session || !summary?.capabilities.wellnessBookingAllowed || !wellnessProfessionalId) {
      setWellnessServices([]);
      setWellnessServiceId("");
      return;
    }
    let active = true;
    getKioskWellnessServices(session.sessionId, session.sessionToken, wellnessProfessionalId)
      .then((items) => {
        if (!active) return;
        setWellnessServices(items);
        setWellnessServiceId(items[0]?.id ?? "");
      })
      .catch((exception) => {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar los servicios wellness.");
      });
    return () => { active = false; };
  }, [session, summary, wellnessProfessionalId]);

  useEffect(() => {
    if (!session || !wellnessProfessionalId || !wellnessServiceId || !wellnessDate) {
      setWellnessSlots([]);
      setWellnessSlot(null);
      return;
    }
    let active = true;
    getKioskWellnessAvailability(
      session.sessionId,
      session.sessionToken,
      wellnessProfessionalId,
      wellnessServiceId,
      wellnessDate,
    )
      .then((items) => {
        if (!active) return;
        setWellnessSlots(items.filter((item) => item.available && new Date(item.startsAt).getTime() > Date.now()));
        setWellnessSlot(null);
      })
      .catch((exception) => {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible consultar la agenda wellness.");
      });
    return () => { active = false; };
  }, [session, wellnessDate, wellnessProfessionalId, wellnessServiceId]);

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

  async function reserveWellness() {
    if (!session || !wellnessSlot || !wellnessServiceId) return;
    setWellnessBusy(true);
    setError(null);
    setMessage(null);
    try {
      const appointment = await createKioskWellnessAppointment(session.sessionId, session.sessionToken, {
        professionalId: wellnessProfessionalId,
        serviceId: wellnessServiceId,
        startsAt: wellnessSlot.startsAt,
        notes: wellnessNotes.trim() || undefined,
      });
      setMessage(`Tu cita con ${appointment.professionalName} fue creada correctamente.`);
      setWellnessNotes("");
      const items = await getKioskWellnessAvailability(
        session.sessionId,
        session.sessionToken,
        wellnessProfessionalId,
        wellnessServiceId,
        wellnessDate,
      );
      setWellnessSlots(items.filter((item) => item.available && new Date(item.startsAt).getTime() > Date.now()));
      setWellnessSlot(null);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear la cita wellness.");
    } finally {
      setWellnessBusy(false);
    }
  }

  async function renew(membership: KioskMembership) {
    if (!session || !summary?.capabilities.onlineCardPaymentsReady) return;
    setWorkingMembershipId(membership.id); setError(null); setMessage(null);
    try {
        if (!await confirmMembershipQuote(membership.planId, "renovación")) return;
        const response = await renewMembershipFromKiosk(session.sessionId, session.sessionToken, membership.id, {
        idempotencyKey: stableOperationKey(
          operationKeys.current,
          "kiosk-renewal",
          session.sessionId,
          membership.id,
        ),
        });
        if (response.checkoutUrl) {
          window.location.assign(response.checkoutUrl);
          return;
        }
        setSummary(await getKioskSummary(session.sessionId, session.sessionToken));
      setMessage(response.membership ? `Renovada hasta ${formatDay(response.membership.endsOn)}.` : "Pago pendiente.");
    } catch (exception) { setError(exception instanceof Error ? exception.message : "No fue posible renovar."); }
    finally { setWorkingMembershipId(null); }
  }

  async function enrollMembership() {
    if (!session || !targetPlanId || !summary?.capabilities.onlineCardPaymentsReady) return;
    setWorkingMembershipId("enrollment"); setError(null); setMessage(null);
    try {
      if (!await confirmMembershipQuote(targetPlanId, "contratación")) return;
      const response = await enrollMembershipFromKiosk(session.sessionId, session.sessionToken, {
        planId: targetPlanId,
        idempotencyKey: stableOperationKey(
          operationKeys.current,
          "kiosk-enrollment",
          session.sessionId,
          targetPlanId,
        ),
      });
      if (response.checkoutUrl) {
        window.location.assign(response.checkoutUrl);
        return;
      }
      setSummary(await getKioskSummary(session.sessionId, session.sessionToken));
      setMessage("La contratacion quedo pendiente de confirmacion del pago.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible contratar la membresía.");
    } finally {
      setWorkingMembershipId(null);
    }
  }

  async function changePlan(membership: KioskMembership) {
    if (!session || !targetPlanId || !summary?.capabilities.onlineCardPaymentsReady) return;
    setWorkingMembershipId(membership.id); setError(null); setMessage(null);
    try {
        if (!await confirmMembershipQuote(targetPlanId, "cambio de plan")) return;
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
        if (response.checkoutUrl) {
          window.location.assign(response.checkoutUrl);
          return;
        }
        setSummary(await getKioskSummary(session.sessionId, session.sessionToken));
      setMessage(response.membership ? `Nuevo plan activo: ${response.membership.planName}.` : "Solicitud registrada.");
    } catch (exception) { setError(exception instanceof Error ? exception.message : "No fue posible cambiar plan."); }
    finally { setWorkingMembershipId(null); }
  }

  async function confirmMembershipQuote(planId: string, operation: string): Promise<boolean> {
    if (!session) return false;
    const quote = await getKioskMembershipQuote(session.sessionId, session.sessionToken, planId);
    const discount = quote.discountCents > 0 ? `\nDescuento: -${formatMoney(quote.discountCents, quote.currency)}` : "";
    return window.confirm(
      `${quote.planName}\nPrecio de lista: ${formatMoney(quote.listPriceCents, quote.currency)}${discount}\nTotal final: ${formatMoney(quote.totalCents, quote.currency)}\n\n¿Confirmas la ${operation} y continuar a Stripe?`,
    );
  }

  async function updateContact(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !summary?.capabilities.profileUpdateAllowed) return;
    setContactBusy(true);
    setError(null);
    setMessage(null);
    try {
      const profile = await updateKioskContactData(session.sessionId, session.sessionToken, {
        ...(contactEmail.trim() ? { email: contactEmail.trim() } : {}),
        ...(contactPhone.trim() ? { phone: contactPhone.trim() } : {}),
      });
      setSummary({ ...summary, clientName: profile.displayName, profile });
      setContactEmail("");
      setContactPhone("");
      setMessage("Tus datos de contacto se actualizaron correctamente.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar tus datos.");
    } finally {
      setContactBusy(false);
    }
  }

  async function openAutopay(membership: KioskMembership) {
    if (!session || !summary?.capabilities.autopayAllowed) return;
    setAutopayMembership(membership);
    setAutopayMandate(null);
    setAutopayTerms(null);
    setAutopayAccepted(false);
    setAutopayBusy(true);
    setError(null);
    try {
      const [mandate, terms] = await Promise.all([
        getKioskAutopayMandate(session.sessionId, session.sessionToken, membership.id),
        summary.capabilities.recurringCardPaymentsReady
          ? getKioskAutopayTerms(session.sessionId, session.sessionToken, membership.id)
          : Promise.resolve(null),
      ]);
      setAutopayMandate(mandate ?? null);
      setAutopayTerms(terms);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible consultar la domiciliación.");
      setAutopayMembership(null);
    } finally {
      setAutopayBusy(false);
    }
  }

  async function setupAutopay() {
    if (!session || !autopayMembership || !autopayTerms || !autopayAccepted) return;
    setAutopayBusy(true);
    setError(null);
    setMessage(null);
    try {
      const replacing = Boolean(autopayMandate?.canReplacePaymentMethod);
      const response = await setupKioskAutopay(
        session.sessionId,
        session.sessionToken,
        autopayMembership.id,
        autopayTerms.termsVersion,
        stableOperationKey(
          operationKeys.current,
          replacing ? "kiosk-autopay-replace" : "kiosk-autopay",
          session.sessionId,
          autopayMembership.id,
          autopayTerms.termsVersion,
        ),
        replacing,
      );
      if (response.checkoutUrl) {
        window.location.assign(response.checkoutUrl);
        return;
      }
      setMessage("La autorización quedó registrada y está siendo verificada con Stripe.");
      setAutopayMandate(await getKioskAutopayMandate(
        session.sessionId,
        session.sessionToken,
        autopayMembership.id,
      ) ?? null);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible iniciar la domiciliación.");
    } finally {
      setAutopayBusy(false);
    }
  }

  async function revokeAutopay() {
    if (!session || !autopayMembership) return;
    setAutopayBusy(true);
    setError(null);
    setMessage(null);
    try {
      const revoked = await revokeKioskAutopay(
        session.sessionId,
        session.sessionToken,
        autopayMembership.id,
        "Revocación solicitada por el socio desde el kiosco",
      );
      setAutopayMandate(revoked);
      setAutopayAccepted(false);
      forgetStableOperationKey(
        operationKeys.current,
        "kiosk-autopay",
        session.sessionId,
        autopayMembership.id,
        autopayTerms?.termsVersion ?? revoked.termsVersion,
      );
      setMessage("La domiciliación fue revocada. No se iniciarán cargos futuros.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible revocar la domiciliación.");
    } finally {
      setAutopayBusy(false);
    }
  }

  const resetClientSession = useCallback(async (messageText = "Sesión cerrada. La pantalla está lista para el siguiente socio.") => {
    if (!session) return;
    const reset = await resetKioskSession(session.sessionId, session.sessionToken);
    writeBrowserKioskSession(reset);
    setSession(reset);
    setSummary(null); setIdentifier(""); setBirthDateDisplay("");
    setContactEmail(""); setContactPhone(""); setResources([]); setSlots([]); setSelectedSlot(null); setMessage(messageText);
    setWellnessProfessionals([]); setWellnessProfessionalId(""); setWellnessServices([]); setWellnessServiceId("");
    setWellnessSlots([]); setWellnessSlot(null); setWellnessNotes("");
    setAutopayMembership(null); setAutopayMandate(null); setAutopayTerms(null); setAutopayAccepted(false);
    operationKeys.current.clear();
    clearPersistedOperationKeys();
  }, [session]);

  useEffect(() => {
    if (!session || !summary) return;
    let timer = window.setTimeout(() => {
      void resetClientSession("La sesión se cerró por inactividad. La pantalla está lista para el siguiente socio.")
        .catch(() => setError("El modo kiosco debe ser abierto nuevamente por recepción."));
    }, 150_000);
    const refreshTimer = () => {
      window.clearTimeout(timer);
      timer = window.setTimeout(() => {
        void resetClientSession("La sesión se cerró por inactividad. La pantalla está lista para el siguiente socio.")
          .catch(() => setError("El modo kiosco debe ser abierto nuevamente por recepción."));
      }, 150_000);
    };
    const events: Array<keyof WindowEventMap> = ["pointerdown", "keydown", "touchstart"];
    events.forEach((eventName) => window.addEventListener(eventName, refreshTimer, { passive: true }));
    return () => {
      window.clearTimeout(timer);
      events.forEach((eventName) => window.removeEventListener(eventName, refreshTimer));
    };
  }, [resetClientSession, session, summary]);

  useEffect(() => {
    if (!session) return;
    const remaining = new Date(session.expiresAt).getTime() - Date.now();
    const expire = () => {
      clearBrowserKioskSession();
      clearPersistedOperationKeys();
      operationKeys.current.clear();
      setSession(null);
      setSummary(null);
      setIdentifier("");
      setBirthDateDisplay("");
      setError("La sesión segura terminó. Recepción debe abrir nuevamente el kiosco.");
    };
    if (!Number.isFinite(remaining) || remaining <= 0) {
      expire();
      return;
    }
    const timer = window.setTimeout(expire, remaining);
    return () => window.clearTimeout(timer);
  }, [session]);

  if (loading) return <main className="kiosk-screen grid place-items-center px-6 text-white"><p className="kiosk-loading">Preparando pantalla de socios...</p></main>;

  const wellnessStep = 2 + (summary?.capabilities.resourceBookingAllowed ? 1 : 0);
  const paymentStep = wellnessStep + (summary?.capabilities.wellnessBookingAllowed ? 1 : 0);
  const profileStep = paymentStep + (summary?.paymentActivity.length ? 1 : 0);

  return (
    <main className="kiosk-screen text-white" style={{
      "--brand-primary": session?.primaryColor ?? undefined,
      "--brand-accent": session?.accentColor ?? undefined,
      "--brand-surface": session?.surfaceColor ?? undefined,
    } as CSSProperties}>
      <section className="kiosk-shell">
        <header className="kiosk-header">
          <div className="kiosk-brand-row">
            {session?.logoUrl && failedLogoUrl !== session.logoUrl ? (
              <Image alt={session.brandName ?? session.organizationName ?? "Gimnasio"} className="kiosk-logo" height={72} onError={() => setFailedLogoUrl(session.logoUrl)} src={session.logoUrl} unoptimized width={72} />
            ) : (
              <span className="kiosk-logo-fallback">{(session?.brandName ?? session?.organizationName ?? "G").slice(0, 1)}</span>
            )}
            <div><p>Autoservicio</p><h1>{session?.brandName ?? session?.organizationName ?? session?.terminalName ?? "GymOS"}</h1><span>Membresías, reservas y datos personales en una sesión segura.</span></div>
          </div>
          <aside>
            <strong>{session ? "Sesión segura" : "Modo kiosco"}</strong>
            <span>{session ? `Expira ${formatTime(session.expiresAt)}` : "Acceso desde recepción"}</span>
          </aside>
        </header>

        {error && session ? <p className="kiosk-notice error">{error}</p> : null}
        {message ? <p className="kiosk-notice ok">{message}</p> : null}

        {!session ? (
          <section className="kiosk-card kiosk-verify text-center">
            <h2 className="text-3xl font-black">Abre esta pantalla desde recepción</h2>
            <p className="kiosk-muted mt-3">Inicia sesión, elige la sucursal y usa “Abrir kiosco”. No necesitas registrar esta computadora ni capturar un identificador de terminal.</p>
            <Link className="kiosk-primary-action inline-flex items-center justify-center" href="/login">Ir a iniciar sesión</Link>
          </section>
        ) : !summary ? (
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
                <div className="kiosk-step">
                  <span>{wellnessStep}</span>
                  <div>
                    <h2>Agenda una cita wellness gratuita</h2>
                    <p>El kiosco sólo muestra servicios activos sin costo. Los servicios de pago no están disponibles aquí.</p>
                  </div>
                </div>
                {wellnessProfessionals.length > 0 ? (
                  <>
                    <div className="grid gap-3 md:grid-cols-3">
                      <label className="kiosk-field">
                        Profesional
                        <select
                          className="kiosk-input compact"
                          onChange={(event) => setWellnessProfessionalId(event.target.value)}
                          value={wellnessProfessionalId}
                        >
                          {wellnessProfessionals.map((professional) => (
                            <option key={professional.id} value={professional.id}>{professional.displayName}</option>
                          ))}
                        </select>
                      </label>
                      <label className="kiosk-field">
                        Servicio gratuito
                        <select
                          className="kiosk-input compact"
                          disabled={wellnessServices.length === 0}
                          onChange={(event) => setWellnessServiceId(event.target.value)}
                          value={wellnessServiceId}
                        >
                          {wellnessServices.map((service) => (
                            <option key={service.id} value={service.id}>
                              {service.name} · {service.durationMinutes} min
                            </option>
                          ))}
                        </select>
                      </label>
                      <label className="kiosk-field">
                        Día
                        <input
                          className="kiosk-input compact"
                          min={toDateInput(new Date())}
                          onChange={(event) => setWellnessDate(event.target.value)}
                          type="date"
                          value={wellnessDate}
                        />
                      </label>
                    </div>
                    {wellnessServices.length === 0 ? (
                      <p className="kiosk-muted mt-4">Este profesional no tiene servicios gratuitos disponibles.</p>
                    ) : (
                      <>
                        <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
                          {wellnessSlots.map((slot) => (
                            <button
                              className={wellnessSlot?.startsAt === slot.startsAt ? "kiosk-primary-action small" : "kiosk-secondary-action"}
                              key={slot.startsAt}
                              onClick={() => setWellnessSlot(slot)}
                              type="button"
                            >
                              {formatSlot(slot.startsAt)}
                            </button>
                          ))}
                          {wellnessSlots.length === 0 ? (
                            <p className="kiosk-muted col-span-full">No hay horarios disponibles para este día.</p>
                          ) : null}
                        </div>
                        <label className="kiosk-field mt-4">
                          Nota opcional
                          <textarea
                            className="kiosk-input"
                            maxLength={2000}
                            onChange={(event) => setWellnessNotes(event.target.value)}
                            placeholder="Indicaciones para el profesional"
                            value={wellnessNotes}
                          />
                        </label>
                        <button
                          className="kiosk-primary-action mt-4"
                          disabled={!wellnessSlot || wellnessBusy}
                          onClick={() => void reserveWellness()}
                          type="button"
                        >
                          {wellnessBusy ? "Agendando..." : "Confirmar cita gratuita"}
                        </button>
                      </>
                    )}
                  </>
                ) : (
                  <p className="kiosk-muted">No hay profesionales wellness disponibles en esta sucursal.</p>
                )}
              </section>
            ) : null}

            {summary.paymentActivity.length > 0 ? (
              <section className="kiosk-card">
                <div className="kiosk-step"><span>{paymentStep}</span><div><h2>Mis pagos con tarjeta</h2><p>Consulta cargos pendientes, rechazados y comprobantes emitidos por Stripe.</p></div></div>
                <div className="mt-4 grid gap-3">
                  {summary.paymentActivity.map((payment) => (
                    <article className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-[var(--brand-line)] p-3" key={payment.id}>
                      <div>
                        <strong className="block">{payment.saleNumber} · {formatMoney(payment.amountCents, payment.currency)}</strong>
                        <span className={paymentStatusTone(payment.status)}>{paymentStatusLabel(payment.status)}</span>
                        <small className="ml-2 text-[var(--brand-muted)]">{formatTime(payment.providerUpdatedAt ?? payment.receivedAt)}</small>
                      </div>
                      <div className="flex gap-2">
                        {payment.receiptUrl ? <a className="kiosk-secondary-action" href={payment.receiptUrl} rel="noreferrer" target="_blank">Ver comprobante</a> : null}
                        {!payment.receiptUrl && payment.checkoutUrl && ["OPEN", "PENDING_VALIDATION"].includes(payment.status) ? <a className="kiosk-primary-action small" href={payment.checkoutUrl}>Continuar pago</a> : null}
                      </div>
                    </article>
                  ))}
                </div>
              </section>
            ) : null}

            <section className="kiosk-content-grid">
              <div className="kiosk-card">
                <div className="kiosk-member-header"><span>Hola</span><h2>{summary.clientName}</h2></div>
                {(summary.capabilities.membershipEnrollmentAllowed || summary.capabilities.membershipPlanChangeAllowed) ? <label className="kiosk-field">Plan<select className="kiosk-input compact" onChange={(event) => setTargetPlanId(event.target.value)} value={targetPlanId}>{plans.map((plan) => <option key={plan.id} value={plan.id}>{plan.name} - {formatMoney(plan.priceCents, plan.currency)}</option>)}</select></label> : null}
                {!summary.capabilities.onlineCardPaymentsReady && (summary.capabilities.membershipEnrollmentAllowed || summary.capabilities.membershipRenewalAllowed || summary.capabilities.membershipPlanChangeAllowed) ? (
                  <p className="kiosk-feature-note">La contratación, renovación y el cambio de plan usarán exclusivamente tarjeta de crédito o débito. Se habilitarán al conectar la pasarela de pagos.</p>
                ) : null}
                <div className="kiosk-membership-list">
                  {summary.memberships.length === 0 ? (
                    <div className="grid gap-3">
                      <p className="kiosk-muted">No hay membresías registradas.</p>
                      {summary.capabilities.membershipEnrollmentAllowed ? (
                        <button className="kiosk-primary-action small" disabled={workingMembershipId !== null || !summary.capabilities.onlineCardPaymentsReady || !targetPlanId} onClick={enrollMembership} type="button">
                          {workingMembershipId === "enrollment" ? "Preparando pago..." : "Contratar este plan con tarjeta"}
                        </button>
                      ) : null}
                    </div>
                  ) : null}
                  {summary.memberships.map((membership) => (
                    <article className="kiosk-membership-card" key={membership.id}>
                      <div><span>{membershipStatusLabel(membership.status)}</span><h3>{membership.planName}</h3></div>
                      <p>{membershipStatusLabel(membership.status)} · {formatDay(membership.startsOn)} a {formatDay(membership.endsOn)}</p>
                      <div className="kiosk-action-grid">
                        {summary.capabilities.membershipRenewalAllowed ? <button className="kiosk-primary-action small" disabled={workingMembershipId !== null || !summary.capabilities.onlineCardPaymentsReady || membership.status === "CANCELLED"} onClick={() => renew(membership)} type="button">{workingMembershipId === membership.id ? "Procesando..." : "Renovar con tarjeta"}</button> : null}
                        {summary.capabilities.membershipPlanChangeAllowed ? <button className="kiosk-secondary-action" disabled={workingMembershipId !== null || !summary.capabilities.onlineCardPaymentsReady || membership.status !== "ACTIVE" || !targetPlanId || targetPlanId === membership.planId} onClick={() => changePlan(membership)} type="button">Cambiar plan</button> : null}
                        {summary.capabilities.autopayAllowed && membership.status === "ACTIVE" ? <button className="kiosk-secondary-action" disabled={autopayBusy} onClick={() => void openAutopay(membership)} type="button">Administrar domiciliación</button> : null}
                      </div>
                    </article>
                  ))}
                </div>
              </div>
              <aside className="kiosk-card kiosk-side-card">
                <div className="kiosk-step"><span>{profileStep}</span><div><h2>Mis datos</h2><p>Actualiza el contacto que usa tu gimnasio.</p></div></div>
                {summary.capabilities.profileUpdateAllowed ? (
                  <form onSubmit={updateContact}>
                    <p className="kiosk-muted">Actual: {summary.profile?.email ?? "sin correo"} / {summary.profile?.phone ?? "sin teléfono"}</p>
                    <label className="kiosk-field">Nuevo correo<input className="kiosk-input compact" onChange={(event) => setContactEmail(event.target.value)} placeholder="Deja vacío para conservarlo" type="email" value={contactEmail} /></label>
                    <label className="kiosk-field">Nuevo teléfono en formato +52<input className="kiosk-input compact" onChange={(event) => setContactPhone(event.target.value)} pattern="\+52\d{10}" placeholder="Deja vacío para conservarlo" value={contactPhone} /></label>
                    <button className="kiosk-secondary-action mt-4 w-full" disabled={contactBusy || (!contactEmail.trim() && !contactPhone.trim())} type="submit">{contactBusy ? "Guardando..." : "Guardar datos"}</button>
                  </form>
                ) : <p className="kiosk-muted mt-4">Esta sucursal no permite actualizar datos desde el kiosco.</p>}
                {summary.capabilities.autopayAllowed ? (
                  <div className="kiosk-feature-note grid gap-3">
                    <strong>Domiciliación con Stripe</strong>
                    {!autopayMembership ? <span>Selecciona “Administrar domiciliación” en tu membresía activa.</span> : null}
                    {autopayMembership ? <span className="font-semibold">{autopayMembership.planName}</span> : null}
                    {autopayBusy ? <span>Consultando autorización segura...</span> : null}
                    {autopayMembership && !autopayBusy && autopayMandate && ["ACTIVE", "PAST_DUE", "PAUSED"].includes(autopayMandate.status) ? (
                      <div className="grid gap-2">
                        <span>{autopayStatusLabel(autopayMandate.status)}</span>
                        <span>{autopayMandate.brand?.toUpperCase() ?? "Tarjeta"} •••• {autopayMandate.lastFour ?? "----"}</span>
                        <span>Próximo cargo: {formatDay(autopayMandate.nextChargeOn)}</span>
                        {autopayMandate.lastError ? <span className="text-[#8c2f2f]">{autopayMandate.lastError}</span> : null}
                        {autopayMandate.recoveryMessage ? <span className="text-[#8c2f2f]">{autopayMandate.recoveryMessage}</span> : null}
                        {autopayMandate.canReplacePaymentMethod && summary.capabilities.recurringCardPaymentsReady && autopayTerms ? (
                          <label className="kiosk-consent-option">
                            <input checked={autopayAccepted} onChange={(event) => setAutopayAccepted(event.target.checked)} type="checkbox" />
                            <span>Acepto nuevamente los términos para sustituir el método de pago.</span>
                          </label>
                        ) : null}
                        {autopayMandate.canReplacePaymentMethod && summary.capabilities.recurringCardPaymentsReady ? (
                          <button className="kiosk-primary-action small" disabled={!autopayAccepted || autopayBusy} onClick={() => void setupAutopay()} type="button">Actualizar tarjeta en Stripe</button>
                        ) : null}
                        <button className="kiosk-secondary-action" disabled={autopayBusy} onClick={() => void revokeAutopay()} type="button">Revocar autorización</button>
                      </div>
                    ) : null}
                    {autopayMembership && !autopayBusy && (!autopayMandate || ["PENDING_SETUP", "REVOKED", "REPLACED"].includes(autopayMandate.status)) ? (
                      summary.capabilities.recurringCardPaymentsReady && autopayTerms ? (
                        <div className="grid gap-3">
                          {autopayMandate?.status === "PENDING_SETUP" ? <span>La captura de tarjeta está pendiente. Puedes continuarla.</span> : null}
                          <span>{autopayTerms.consentText}</span>
                          <span>Importe actual de referencia: {formatMoney(autopayTerms.currentPriceCents, autopayTerms.currency)}</span>
                          <label className="flex items-start gap-2 text-sm">
                            <input checked={autopayAccepted} onChange={(event) => setAutopayAccepted(event.target.checked)} type="checkbox" />
                            <span>Acepto expresamente esta autorización y sus cargos recurrentes.</span>
                          </label>
                          <button className="kiosk-primary-action small" disabled={!autopayAccepted || autopayBusy} onClick={() => void setupAutopay()} type="button">Continuar a captura segura de tarjeta</button>
                          {autopayMandate?.status === "PENDING_SETUP" ? <button className="kiosk-secondary-action" disabled={autopayBusy} onClick={() => void revokeAutopay()} type="button">Cancelar autorización pendiente</button> : null}
                        </div>
                      ) : <span>El gimnasio aún no ha habilitado los cobros recurrentes con Stripe.</span>
                    ) : null}
                  </div>
                ) : null}
                <button className="kiosk-end-action" onClick={() => void resetClientSession()} type="button">Terminar y borrar mis datos</button>
              </aside>
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
function autopayStatusLabel(status: KioskAutopayMandate["status"]) {
  return ({ ACTIVE: "Domiciliación activa", PAST_DUE: "Pago pendiente", PAUSED: "Domiciliación pausada" } as Record<string, string>)[status] ?? status;
}
function paymentStatusLabel(status: string) {
  return ({ COMPLETED: "Pagado", REJECTED: "Rechazado", OPEN: "Pendiente de completar", EXPIRED: "Sesión vencida", FAILED: "No completado", PENDING_VALIDATION: "Confirmando con Stripe" } as Record<string, string>)[status] ?? status;
}
function paymentStatusTone(status: string) {
  return ["REJECTED", "FAILED", "EXPIRED", "CANCELLED"].includes(status) ? "text-sm font-semibold text-[#8c2f2f]" : "text-sm font-semibold text-[var(--brand-primary)]";
}
function membershipStatusLabel(status: string) {
  return ({ ACTIVE: "Activa", CANCELLED: "Cancelada", EXPIRED: "Vencida", PENDING: "Pendiente", SUSPENDED: "Suspendida" } as Record<string, string>)[status] ?? status;
}
function stableOperationKey(keys: Map<string, string>, ...parts: string[]) {
  const operation = parts.join(":");
  const existing = keys.get(operation);
  if (existing) return existing;
  const persisted = readPersistedOperationKeys();
  const persistedKey = persisted[operation];
  if (persistedKey) {
    keys.set(operation, persistedKey);
    return persistedKey;
  }
  const random = typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now()}`;
  const created = [...parts, random].join(":");
  keys.set(operation, created);
  persisted[operation] = created;
  if (typeof window !== "undefined") {
    window.sessionStorage.setItem(KIOSK_OPERATION_KEYS, JSON.stringify(persisted));
  }
  return created;
}

function readPersistedOperationKeys(): Record<string, string> {
  if (typeof window === "undefined") return {};
  const value = window.sessionStorage.getItem(KIOSK_OPERATION_KEYS);
  if (!value) return {};
  try {
    return JSON.parse(value) as Record<string, string>;
  } catch {
    clearPersistedOperationKeys();
    return {};
  }
}

function clearPersistedOperationKeys() {
  if (typeof window !== "undefined") window.sessionStorage.removeItem(KIOSK_OPERATION_KEYS);
}

function forgetStableOperationKey(keys: Map<string, string>, ...parts: string[]) {
  const operation = parts.join(":");
  keys.delete(operation);
  const persisted = readPersistedOperationKeys();
  delete persisted[operation];
  if (typeof window !== "undefined") {
    window.sessionStorage.setItem(KIOSK_OPERATION_KEYS, JSON.stringify(persisted));
  }
}

function readBrowserKioskSession(): KioskSessionResponse | null {
  if (typeof window === "undefined") return null;
  const value = window.sessionStorage.getItem(KIOSK_BROWSER_SESSION_KEY);
  if (!value) return null;
  try {
    const session = JSON.parse(value) as KioskSessionResponse;
    if (!session.expiresAt || new Date(session.expiresAt).getTime() <= Date.now()) {
      clearBrowserKioskSession();
      clearPersistedOperationKeys();
      return null;
    }
    return session;
  } catch {
    clearBrowserKioskSession();
    return null;
  }
}

function clearBrowserKioskSession() {
  if (typeof window !== "undefined") window.sessionStorage.removeItem(KIOSK_BROWSER_SESSION_KEY);
}

function writeBrowserKioskSession(session: KioskSessionResponse) {
  if (typeof window !== "undefined") {
    window.sessionStorage.setItem(KIOSK_BROWSER_SESSION_KEY, JSON.stringify(session));
  }
}
