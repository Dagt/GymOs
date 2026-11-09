"use client";

import Image from "next/image";
import Link from "next/link";
import { CircleAlert, CircleCheck, UserPlus, UserRound, X } from "lucide-react";
import { CSSProperties, FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  changeMembershipPlanFromKiosk,
  clearSession,
  enrollMembershipFromKiosk,
  getKioskMembershipQuote,
  getKioskAutopayMandate,
  getKioskAutopayTerms,
  getKioskMembershipOptions,
  getKioskCurrentVisitPackage,
  getKioskVisitPackageOptions,
  getKioskVisitPackageQuote,
  getKioskSummary,
  purchaseVisitPackageFromKiosk,
  renewMembershipFromKiosk,
  registerKioskClient,
  resetKioskSession,
  recoverKioskCheckout,
  revokeKioskAutopay,
  setupKioskAutopay,
  updateKioskContactData,
  verifyKioskSession,
  type KioskMembership,
  type KioskMembershipQuote,
  type KioskAutopayMandate,
  type KioskAutopayTerms,
  type KioskPlanOption,
  type KioskSessionResponse,
  type KioskSummaryResponse,
  type KioskVisitPackageEntitlement,
  type KioskVisitPackagePlan,
  type KioskVisitPackageQuote,
} from "@/lib/api";
import { normalizePhone } from "@/components/FormFields";
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
import {
  KIOSK_BROWSER_SESSION_KEY,
  writeKioskAutopayReturn,
  writeKioskPaymentReturn,
  clearKioskBrowserState,
  clearKioskAutopayReturn,
  clearKioskPaymentReturn,
} from "@/lib/kiosk-autopay-return";
import { consumeKioskLaunch } from "@/lib/kiosk-launch";

const KIOSK_OPERATION_KEYS = "gymos.kioskOperationKeys";
const EMPTY_REGISTRATION = {
  firstName: "",
  lastName: "",
  maternalLastName: "",
  gender: "",
  phone: "",
  email: "",
};
type KioskPanel = "memberships" | "resources" | "wellness" | "payments" | "profile" | "autopay";
type MembershipCheckoutKind = "enrollment" | "renewal" | "change";
type MembershipCheckoutReview = {
  kind: MembershipCheckoutKind;
  membership: KioskMembership | null;
  quote: KioskMembershipQuote;
};
type KioskEntryMode = "verify" | "register";
type KioskCommercialOfferType = "MEMBERSHIP" | "VISIT_PACKAGE";

export default function KioskPage() {
  const [session, setSession] = useState<KioskSessionResponse | null>(null);
  const [summary, setSummary] = useState<KioskSummaryResponse | null>(null);
  const [plans, setPlans] = useState<KioskPlanOption[]>([]);
  const [visitPackagePlans, setVisitPackagePlans] = useState<KioskVisitPackagePlan[]>([]);
  const [currentVisitPackage, setCurrentVisitPackage] = useState<KioskVisitPackageEntitlement | null>(null);
  const [selectedVisitPackagePlanId, setSelectedVisitPackagePlanId] = useState("");
  const [selectedCommercialOfferType, setSelectedCommercialOfferType] = useState<KioskCommercialOfferType | null>(null);
  const [visitPackageQuote, setVisitPackageQuote] = useState<KioskVisitPackageQuote | null>(null);
  const [visitPackageBusy, setVisitPackageBusy] = useState(false);
  const [visitPackageError, setVisitPackageError] = useState<string | null>(null);
  const [identifier, setIdentifier] = useState("");
  const [birthDateDisplay, setBirthDateDisplay] = useState("");
  const [entryMode, setEntryMode] = useState<KioskEntryMode>("verify");
  const [entrySwitchBusy, setEntrySwitchBusy] = useState(false);
  const [verificationBusy, setVerificationBusy] = useState(false);
  const [registrationBusy, setRegistrationBusy] = useState(false);
  const [registrationBirthDateDisplay, setRegistrationBirthDateDisplay] = useState("");
  const [registration, setRegistration] = useState({ ...EMPTY_REGISTRATION });
  const [targetPlanId, setTargetPlanId] = useState("");
  const [contactEmail, setContactEmail] = useState("");
  const [contactPhone, setContactPhone] = useState("");
  const [contactBusy, setContactBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [workingMembershipId, setWorkingMembershipId] = useState<string | null>(null);
  const [membershipReview, setMembershipReview] = useState<MembershipCheckoutReview | null>(null);
  const [membershipError, setMembershipError] = useState<string | null>(null);
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
  const [activePanel, setActivePanel] = useState<KioskPanel>("memberships");
  const operationKeys = useRef(new Map<string, string>());
  const membershipCheckoutInFlight = useRef(false);
  const recoveringCheckout = useRef(false);
  const [checkoutRecoveryBusy, setCheckoutRecoveryBusy] = useState(false);
  const checkoutRef = useRef<HTMLElement | null>(null);

  const birthDate = useMemo(() => parseMexicanDate(birthDateDisplay) ?? "", [birthDateDisplay]);
  const registrationBirthDate = useMemo(
    () => parseMexicanDate(registrationBirthDateDisplay) ?? "",
    [registrationBirthDateDisplay],
  );
  const activeMembership = useMemo(
    () => summary?.memberships.find((membership) => membership.status === "ACTIVE") ?? null,
    [summary],
  );
  const lastExpiredMembership = useMemo(
    () => summary?.memberships.find((membership) => membership.status === "EXPIRED") ?? null,
    [summary],
  );
  const previousMemberships = useMemo(
    () => summary?.memberships.filter((membership) => membership.id !== activeMembership?.id) ?? [],
    [activeMembership, summary],
  );
  const changePlans = useMemo(
    () => plans.filter((plan) => plan.id !== activeMembership?.planId),
    [activeMembership, plans],
  );
  const commercialMembershipPlans = useMemo(() => {
    if (!activeMembership) {
      return summary?.capabilities.membershipEnrollmentAllowed ? plans : [];
    }
    return activeMembership.managedAtCurrentBranch && summary?.capabilities.membershipPlanChangeAllowed
      ? changePlans
      : [];
  }, [activeMembership, changePlans, plans, summary]);
  const selectedTargetPlan = useMemo(
    () => plans.find((plan) => plan.id === targetPlanId) ?? null,
    [plans, targetPlanId],
  );
  const visiblePaymentActivity = useMemo(
    () => collapseRepeatedPendingPayments(summary?.paymentActivity ?? []),
    [summary],
  );
  const hiddenPendingPaymentCount = (summary?.paymentActivity.length ?? 0) - visiblePaymentActivity.length;

  useEffect(() => {
    if (!membershipReview && !visitPackageQuote) return;
    const frame = window.requestAnimationFrame(() => {
      checkoutRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [membershipReview, visitPackageQuote]);

  useEffect(() => {
    let active = true;
    async function start() {
      setLoading(true);
      setError(null);
      try {
        const resumed = readBrowserKioskSession();
        if (resumed) {
          const reset = await resetKioskSession(resumed.sessionId, resumed.sessionToken);
          clearKioskAutopayReturn(window.sessionStorage);
          clearKioskPaymentReturn(window.sessionStorage);
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
    const reloadRestoredPage = (event: PageTransitionEvent) => {
      if (event.persisted) window.location.reload();
    };
    window.addEventListener("pageshow", reloadRestoredPage);
    return () => {
      active = false;
      window.removeEventListener("pageshow", reloadRestoredPage);
    };
  }, []);

  async function verify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !birthDate) {
      setError("Captura una fecha de nacimiento válida en formato día/mes/año.");
      return;
    }
    setError(null);
    setMessage(null);
    setVerificationBusy(true);
    try {
      const nextSummary = await verifyKioskSession(
        session.sessionId,
        session.sessionToken,
        identifier,
        birthDate,
      );
      if (readBrowserKioskSession()?.sessionToken === session.sessionToken) await openMemberDashboard(nextSummary, session.sessionToken);
    } catch (exception) {
      setError(friendlyVerificationError(exception));
    } finally {
      setVerificationBusy(false);
    }
  }

  async function switchEntryMode(nextMode: KioskEntryMode) {
    if (!session || nextMode === entryMode || entrySwitchBusy) return;
    setEntrySwitchBusy(true);
    setError(null);
    setMessage(null);
    try {
      // Cambiar entre identificación y alta significa que otra persona puede estar
      // usando el kiosco. El backend también debe olvidar cualquier identidad que
      // haya quedado asociada aunque la respuesta anterior se haya interrumpido.
      const reset = await resetKioskSession(session.sessionId, session.sessionToken);
      writeBrowserKioskSession(reset);
      clearKioskAutopayReturn(window.sessionStorage);
      clearKioskPaymentReturn(window.sessionStorage);
      setSession(reset);
      setSummary(null);
      setIdentifier("");
      setBirthDateDisplay("");
      setRegistration({ ...EMPTY_REGISTRATION });
      setRegistrationBirthDateDisplay("");
      setEntryMode(nextMode);
    } catch (exception) {
      setError(friendlySessionResetError(exception));
    } finally {
      setEntrySwitchBusy(false);
    }
  }

  async function register(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !registrationBirthDate) {
      setError("Captura una fecha de nacimiento válida en formato día/mes/año.");
      return;
    }
    if (!registration.email.trim() && !registration.phone.trim()) {
      setError("Captura al menos un correo o teléfono.");
      return;
    }
    setRegistrationBusy(true);
    setError(null);
    setMessage(null);
    try {
      const nextSummary = await registerKioskClient(session.sessionId, session.sessionToken, {
        firstName: registration.firstName.trim(),
        lastName: registration.lastName.trim(),
        maternalLastName: registration.maternalLastName.trim() || null,
        birthDate: registrationBirthDate,
        gender: registration.gender ? registration.gender as "MALE" | "FEMALE" : null,
        phone: normalizePhone(registration.phone),
        email: registration.email.trim() || null,
      });
      if (readBrowserKioskSession()?.sessionToken === session.sessionToken) await openMemberDashboard(nextSummary, session.sessionToken);
      setMessage(nextSummary.memberships.length === 0
        ? nextSummary.capabilities.membershipEnrollmentAllowed && nextSummary.capabilities.visitPackagePurchaseAllowed
          ? "Tu registro quedó listo. Ahora puedes elegir una membresía o un paquete de visitas."
          : nextSummary.capabilities.membershipEnrollmentAllowed
            ? "Tu registro quedó listo. Ahora puedes elegir tu primera membresía."
            : nextSummary.capabilities.visitPackagePurchaseAllowed
              ? "Tu registro quedó listo. Ahora puedes elegir un paquete de visitas."
              : "Tu registro quedó listo. Solicita apoyo en recepción para contratar."
        : "Tu expediente fue reactivado correctamente.");
    } catch (exception) {
      setError(friendlyRegistrationError(exception));
    } finally {
      setRegistrationBusy(false);
    }
  }

  async function openMemberDashboard(nextSummary: KioskSummaryResponse, sessionToken: string) {
    if (!session) return;
    // La identidad ya fue verificada. Una consulta secundaria del dashboard no debe
    // convertir ese éxito en un mensaje falso de registro o datos incorrectos.
    const [membershipResult, packageOptionsResult, openPackageResult, resourcesResult, professionalsResult] = await Promise.allSettled([
      nextSummary.capabilities.membershipPlanChangeAllowed
        || nextSummary.capabilities.membershipEnrollmentAllowed
        ? getKioskMembershipOptions(session.sessionId, session.sessionToken)
        : Promise.resolve([]),
      nextSummary.capabilities.visitPackagePurchaseAllowed
        ? getKioskVisitPackageOptions(session.sessionId, session.sessionToken)
        : Promise.resolve([]),
      getKioskCurrentVisitPackage(session.sessionId, session.sessionToken),
      nextSummary.capabilities.resourceBookingAllowed
        ? getKioskBookingResources(session.sessionId, session.sessionToken, bookingDate)
        : Promise.resolve([]),
      nextSummary.capabilities.wellnessBookingAllowed
        ? getKioskWellnessProfessionals(session.sessionId, session.sessionToken)
        : Promise.resolve([]),
    ]);
    const options = settledValue(membershipResult, []);
    // If the balance cannot be read, do not offer another package: the backend
    // still enforces exclusivity, and the UI must not suggest a potentially invalid purchase.
    const packageOptions = openPackageResult.status === "rejected"
      ? []
      : settledValue(packageOptionsResult, []);
    const openPackage = settledValue(openPackageResult, undefined);
    const bookingResources = settledValue(resourcesResult, []);
    const professionals = settledValue(professionalsResult, []);
    if (readBrowserKioskSession()?.sessionToken !== sessionToken) return;
    setSummary(nextSummary);
    setActivePanel("memberships");
    setMembershipReview(null);
    setVisitPackageQuote(null);
    setMembershipError(null);
    setContactEmail("");
    setContactPhone("");
    setPlans(options);
    setVisitPackagePlans(packageOptions);
    setCurrentVisitPackage(openPackage ?? null);
    setSelectedVisitPackagePlanId(packageOptions[0]?.id ?? "");
    setVisitPackageError(null);
    const currentMembership = nextSummary.memberships.find((membership) => membership.status === "ACTIVE");
    const currentPlanId = currentMembership?.planId;
    const alternativePlan = options.find((plan) => plan.id !== currentPlanId);
    setTargetPlanId(alternativePlan?.id ?? options[0]?.id ?? "");
    setSelectedCommercialOfferType(
      !currentPlanId && options.length > 0
        ? "MEMBERSHIP"
        : currentMembership?.managedAtCurrentBranch
            && nextSummary.capabilities.membershipPlanChangeAllowed
            && alternativePlan
          ? "MEMBERSHIP"
        : !openPackage && packageOptions.length > 0
          ? "VISIT_PACKAGE"
          : null,
    );
    setResources(bookingResources);
    setResourceId(bookingResources[0]?.id ?? "");
    setWellnessProfessionals(professionals);
    setWellnessProfessionalId(professionals[0]?.id ?? "");
    if (membershipResult.status === "rejected") {
      setMembershipError(`No pudimos cargar las opciones de membresía. Intenta recargar o solicita apoyo en recepción.${supportReference(membershipResult.reason)}`);
    }
    const packageFailure = [packageOptionsResult, openPackageResult]
      .find((result): result is PromiseRejectedResult => result.status === "rejected");
    if (packageFailure) {
      setVisitPackageError(`No pudimos cargar los paquetes de visitas en este momento.${supportReference(packageFailure.reason)}`);
    }
  }

  useEffect(() => {
    if (!session || !summary?.capabilities.resourceBookingAllowed || !bookingDate) {
      setResources([]);
      setResourceId("");
      return;
    }
    let active = true;
    getKioskBookingResources(session.sessionId, session.sessionToken, bookingDate)
      .then((items) => {
        if (!active) return;
        setResources(items);
        setResourceId((current) => items.some((item) => item.id === current) ? current : (items[0]?.id ?? ""));
      })
      .catch((exception) => {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible cargar los recursos disponibles.");
      });
    return () => { active = false; };
  }, [bookingDate, session, summary]);

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
      if (readBrowserKioskSession()?.sessionToken !== session.sessionToken) return;
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
      if (readBrowserKioskSession()?.sessionToken !== session.sessionToken) return;
      setMessage(`Tu cita con ${appointment.professionalName} fue creada correctamente.`);
      setWellnessNotes("");
      const items = await getKioskWellnessAvailability(
        session.sessionId,
        session.sessionToken,
        wellnessProfessionalId,
        wellnessServiceId,
        wellnessDate,
      );
      if (readBrowserKioskSession()?.sessionToken !== session.sessionToken) return;
      setWellnessSlots(items.filter((item) => item.available && new Date(item.startsAt).getTime() > Date.now()));
      setWellnessSlot(null);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear la cita wellness.");
    } finally {
      setWellnessBusy(false);
    }
  }

  async function reviewMembershipCheckout(
    kind: MembershipCheckoutKind,
    planId: string,
    membership: KioskMembership | null = null,
  ) {
    if (!session || !summary || !planId) return;
    setWorkingMembershipId(membership?.id ?? "enrollment");
    setMembershipReview(null);
    setVisitPackageQuote(null);
    setMembershipError(null);
    setError(null);
    setMessage(null);
    try {
      const quote = await getKioskMembershipQuote(
        session.sessionId,
        session.sessionToken,
        planId,
        kind === "renewal" ? "RENEWAL" : kind === "change" ? "PLAN_CHANGE" : "ENROLLMENT",
        membership?.id,
      );
      if (readBrowserKioskSession()?.sessionToken !== session.sessionToken) return;
      setMembershipReview({ kind, membership, quote });
    } catch (exception) {
      setMembershipError(membershipQuoteError(exception));
    } finally {
      setWorkingMembershipId(null);
    }
  }

  async function completeMembershipCheckout() {
    if (!session || !membershipReview || !summary?.capabilities.onlineCardPaymentsReady || membershipCheckoutInFlight.current) return;
    membershipCheckoutInFlight.current = true;
    const { kind, membership, quote } = membershipReview;
    const workingId = membership?.id ?? "enrollment";
    setWorkingMembershipId(workingId);
    setMembershipError(null);
    setError(null);
    setMessage(null);
    try {
      const response = kind === "enrollment"
        ? await enrollMembershipFromKiosk(session.sessionId, session.sessionToken, {
          planId: quote.planId,
          idempotencyKey: stableOperationKey(
            operationKeys.current,
            "kiosk-enrollment",
            session.sessionId,
            quote.planId,
          ),
        })
        : kind === "renewal" && membership
          ? await renewMembershipFromKiosk(session.sessionId, session.sessionToken, membership.id, {
            idempotencyKey: stableOperationKey(
              operationKeys.current,
              "kiosk-renewal",
              session.sessionId,
              membership.id,
            ),
          })
          : kind === "change" && membership
            ? await changeMembershipPlanFromKiosk(session.sessionId, session.sessionToken, membership.id, {
              targetPlanId: quote.planId,
              idempotencyKey: stableOperationKey(
                operationKeys.current,
                "kiosk-plan-change",
                session.sessionId,
                membership.id,
                quote.planId,
              ),
            })
            : null;
      if (!response || readBrowserKioskSession()?.sessionToken !== session.sessionToken) return;
      if (response.checkoutUrl) {
        if (response.paymentId) {
          writeKioskPaymentReturn(window.sessionStorage, {
            sessionId: session.sessionId,
            paymentId: response.paymentId,
            saleId: response.saleId,
            providerSessionId: response.providerSessionId ?? null,
            returnPath: window.location.pathname,
            startedAt: new Date().toISOString(),
          });
        }
        window.location.assign(response.checkoutUrl);
        return;
      }
      await refreshPaymentHistory(session);
      setMembershipReview(null);
      if (kind === "renewal") {
        setMessage(response.membership ? `Renovada hasta ${formatDay(response.membership.endsOn)}.` : "Pago pendiente.");
      } else if (kind === "change") {
        setMessage(response.membership ? `Nuevo plan activo: ${response.membership.planName}.` : "Solicitud registrada.");
      } else {
        setMessage("La contratación quedó pendiente de confirmación del pago.");
      }
    } catch (exception) {
      setMembershipError(membershipCheckoutError(exception));
      void refreshPaymentHistory(session);
    } finally {
      membershipCheckoutInFlight.current = false;
      setWorkingMembershipId(null);
    }
  }

  async function refreshPaymentHistory(currentSession: KioskSessionResponse) {
    try {
      const next = await getKioskSummary(currentSession.sessionId, currentSession.sessionToken, AbortSignal.timeout(8_000));
      if (readBrowserKioskSession()?.sessionToken === currentSession.sessionToken) setSummary(next);
    } catch {
      // The member can re-identify to see the current pending payment.
    }
  }

  async function continueCheckout(paymentId: string) {
    if (!session || recoveringCheckout.current) return;
    recoveringCheckout.current = true;
    setCheckoutRecoveryBusy(true);
    setError(null);
    try {
      const checkout = await recoverKioskCheckout(session.sessionId, session.sessionToken, paymentId);
      if (readBrowserKioskSession()?.sessionToken !== session.sessionToken) return;
      writeKioskPaymentReturn(window.sessionStorage, {
        sessionId: session.sessionId,
        saleId: checkout.saleId,
        paymentId: checkout.paymentId,
        providerSessionId: checkout.providerSessionId,
        returnPath: window.location.pathname,
        startedAt: new Date().toISOString(),
      });
      window.location.assign(checkout.checkoutUrl);
    } catch {
      setError("No pudimos continuar el pago. Consulta su estado antes de iniciar otro cobro.");
    } finally {
      recoveringCheckout.current = false;
      setCheckoutRecoveryBusy(false);
    }
  }

  async function reviewVisitPackage() {
    if (!session || !selectedVisitPackagePlanId || currentVisitPackage) return;
    setVisitPackageBusy(true);
    setVisitPackageQuote(null);
    setMembershipReview(null);
    setVisitPackageError(null);
    setError(null);
    setMessage(null);
    try {
      const quote = await getKioskVisitPackageQuote(
        session.sessionId,
        session.sessionToken,
        selectedVisitPackagePlanId,
      );
      if (readBrowserKioskSession()?.sessionToken !== session.sessionToken) return;
      setVisitPackageQuote(quote);
    } catch (exception) {
      setVisitPackageError(visitPackageOperationError(exception, "quote"));
    } finally {
      setVisitPackageBusy(false);
    }
  }

  async function completeVisitPackageCheckout() {
    if (!session || !visitPackageQuote || !summary?.capabilities.onlineCardPaymentsReady || visitPackageBusy) return;
    setVisitPackageBusy(true);
    setVisitPackageError(null);
    setError(null);
    setMessage(null);
    try {
      const response = await purchaseVisitPackageFromKiosk(
        session.sessionId,
        session.sessionToken,
        visitPackageQuote.packagePlanId,
        stableOperationKey(
          operationKeys.current,
          "kiosk-visit-package",
          session.sessionId,
          visitPackageQuote.packagePlanId,
        ),
      );
      if (readBrowserKioskSession()?.sessionToken !== session.sessionToken) return;
      if (response.checkoutUrl) {
        writeKioskPaymentReturn(window.sessionStorage, {
          sessionId: session.sessionId,
          paymentId: response.paymentId,
          saleId: response.saleId,
          providerSessionId: response.providerSessionId,
          returnPath: window.location.pathname,
          startedAt: new Date().toISOString(),
        });
        window.location.assign(response.checkoutUrl);
        return;
      }
      const current = await getKioskCurrentVisitPackage(
        session.sessionId,
        session.sessionToken,
      );
      if (readBrowserKioskSession()?.sessionToken !== session.sessionToken) return;
      setCurrentVisitPackage(current ?? null);
      setVisitPackageQuote(null);
      setMessage(response.entitlementStatus === "ACTIVE"
        ? "Tu paquete de visitas quedó activo."
        : "La compra quedó pendiente de confirmación del pago.");
    } catch (exception) {
      setVisitPackageError(visitPackageOperationError(exception, "checkout"));
      void refreshPaymentHistory(session);
    } finally {
      setVisitPackageBusy(false);
    }
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
      if (readBrowserKioskSession()?.sessionToken !== session.sessionToken) return;
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
    setActivePanel("autopay");
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
      if (readBrowserKioskSession()?.sessionToken !== session.sessionToken) return;
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
      if (readBrowserKioskSession()?.sessionToken !== session.sessionToken) return;
      if (response.checkoutUrl) {
        writeKioskAutopayReturn(window.sessionStorage, {
          sessionId: session.sessionId,
          membershipId: autopayMembership.id,
          mandateId: response.mandateId,
          providerSessionId: response.providerSessionId,
          returnPath: window.location.pathname,
          startedAt: new Date().toISOString(),
        });
        window.location.assign(response.checkoutUrl);
        return;
      }
      setMessage("La autorización quedó registrada y está siendo verificada con Stripe.");
      const mandate = await getKioskAutopayMandate(
        session.sessionId,
        session.sessionToken,
        autopayMembership.id,
      );
      if (readBrowserKioskSession()?.sessionToken !== session.sessionToken) return;
      setAutopayMandate(mandate ?? null);
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
      if (readBrowserKioskSession()?.sessionToken !== session.sessionToken) return;
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
    setSession(null);
    clearBrowserKioskSession();
    setSummary(null); setIdentifier(""); setBirthDateDisplay(""); setEntryMode("verify");
    setPlans([]); setTargetPlanId(""); setMembershipReview(null); setMembershipError(null);
    setVisitPackagePlans([]); setCurrentVisitPackage(null); setSelectedVisitPackagePlanId(""); setSelectedCommercialOfferType(null);
    setVisitPackageQuote(null); setVisitPackageError(null); setVisitPackageBusy(false);
    setRegistration({ ...EMPTY_REGISTRATION }); setRegistrationBirthDateDisplay("");
    setContactEmail(""); setContactPhone(""); setResources([]); setSlots([]); setSelectedSlot(null); setBookingNotes(""); setMessage(messageText);
    setError(null);
    setWellnessProfessionals([]); setWellnessProfessionalId(""); setWellnessServices([]); setWellnessServiceId("");
    setWellnessSlots([]); setWellnessSlot(null); setWellnessNotes("");
    setAutopayMembership(null); setAutopayMandate(null); setAutopayTerms(null); setAutopayAccepted(false);
    setActivePanel("memberships");
    operationKeys.current.clear();
    clearPersistedOperationKeys();
    try {
      const reset = await resetKioskSession(session.sessionId, session.sessionToken);
      writeBrowserKioskSession(reset);
      setSession(reset);
    } catch {
      setError("La sesión se cerró en esta pantalla. Pide a recepción que abra el kiosco nuevamente.");
    }
  }, [session]);

  useEffect(() => {
    if (!message) return;
    const timer = window.setTimeout(() => setMessage(null), 4_000);
    return () => window.clearTimeout(timer);
  }, [message]);

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
            <div><p>Autoservicio</p><h1>{session?.brandName ?? session?.organizationName ?? session?.terminalName ?? "GymFort"}</h1><span>Membresías, reservas y datos personales en una sesión segura.</span></div>
          </div>
          <aside>
            <strong>{session ? "Sesión segura" : "Modo kiosco"}</strong>
            <span>{session ? `Expira ${formatTime(session.expiresAt)}` : "Acceso desde recepción"}</span>
          </aside>
        </header>

        {error && session && summary ? (
          <div className="kiosk-feedback error" role="alert">
            <CircleAlert aria-hidden="true" size={18} />
            <p>{error}</p>
            <button aria-label="Cerrar mensaje" onClick={() => setError(null)} type="button"><X aria-hidden="true" size={17} /></button>
          </div>
        ) : null}
        {message ? (
          <div className="kiosk-feedback ok" role="status">
            <CircleCheck aria-hidden="true" size={18} />
            <p>{message}</p>
            <button aria-label="Cerrar mensaje" onClick={() => setMessage(null)} type="button"><X aria-hidden="true" size={17} /></button>
          </div>
        ) : null}

        {!session ? (
          <section className="kiosk-card kiosk-verify text-center">
            <h2 className="text-3xl font-black">Abre esta pantalla desde recepción</h2>
            <p className="kiosk-muted mt-3">Inicia sesión, elige la sucursal y usa “Abrir kiosco”. No necesitas registrar esta computadora ni capturar un identificador de terminal.</p>
            <Link className="kiosk-primary-action inline-flex items-center justify-center" href="/login">Ir a iniciar sesión</Link>
          </section>
        ) : !summary ? (
          <section className="kiosk-card kiosk-verify">
            {session.capabilities.clientRegistrationAllowed ? (
              <div className="kiosk-entry-selector">
                <p>¿Cómo quieres continuar?</p>
                <div className="kiosk-entry-options" role="tablist" aria-label="Acceso al kiosco">
                  <button aria-label="Ya soy socio" aria-selected={entryMode === "verify"} className={`kiosk-entry-option ${entryMode === "verify" ? "active" : ""}`} disabled={entrySwitchBusy} onClick={() => void switchEntryMode("verify")} role="tab" type="button">
                    <span className="kiosk-entry-icon"><UserRound aria-hidden="true" size={18} /></span>
                    <span><strong>Ya soy socio</strong><small>Entrar con mis datos</small></span>
                  </button>
                  <button aria-label="Quiero registrarme" aria-selected={entryMode === "register"} className={`kiosk-entry-option ${entryMode === "register" ? "active" : ""}`} disabled={entrySwitchBusy} onClick={() => void switchEntryMode("register")} role="tab" type="button">
                    <span className="kiosk-entry-icon"><UserPlus aria-hidden="true" size={18} /></span>
                    <span><strong>Quiero registrarme</strong><small>Crear un expediente nuevo</small></span>
                  </button>
                </div>
              </div>
            ) : null}
            {error ? (
              <div className="kiosk-feedback kiosk-inline-feedback error" role="alert">
                <CircleAlert aria-hidden="true" size={18} />
                <p>{error}</p>
                <button aria-label="Cerrar mensaje" onClick={() => setError(null)} type="button"><X aria-hidden="true" size={17} /></button>
              </div>
            ) : null}
            {entryMode === "verify" ? (
              <form className="grid gap-4" onSubmit={verify}>
                <div className="kiosk-step"><span>1</span><div><h2>Verifica tu identidad</h2><p>No necesitas tener una membresía activa para entrar.</p></div></div>
                <input autoComplete="username" className="kiosk-input" onChange={(event) => setIdentifier(event.target.value)} placeholder="Correo o teléfono (10 dígitos)" required value={identifier} />
                <label className="kiosk-field">
                  Fecha de nacimiento
                  <input aria-label="Fecha de nacimiento en formato día mes año" autoComplete="bday" className="kiosk-input compact" inputMode="numeric" maxLength={10} onChange={(event) => setBirthDateDisplay(formatMexicanDateInput(event.target.value))} placeholder="DD/MM/AAAA" required type="text" value={birthDateDisplay} />
                  <small>Formato: día/mes/año, por ejemplo 25/08/1990.</small>
                </label>
                <button className="kiosk-primary-action" disabled={verificationBusy || entrySwitchBusy} type="submit">{verificationBusy ? "Verificando..." : "Continuar de forma segura"}</button>
              </form>
            ) : (
              <form className="grid gap-4" onSubmit={register}>
                <div className="kiosk-step"><span>1</span><div><h2>Crea tu expediente</h2><p>Después podrás revisar las opciones disponibles en este gimnasio.</p></div></div>
                <div className="grid gap-3 sm:grid-cols-2">
                  <label className="kiosk-field">Nombre<input autoComplete="given-name" className="kiosk-input compact" maxLength={120} onChange={(event) => setRegistration({ ...registration, firstName: event.target.value })} required value={registration.firstName} /></label>
                  <label className="kiosk-field">Apellido paterno<input autoComplete="family-name" className="kiosk-input compact" maxLength={120} onChange={(event) => setRegistration({ ...registration, lastName: event.target.value })} required value={registration.lastName} /></label>
                  <label className="kiosk-field">Apellido materno<input className="kiosk-input compact" maxLength={120} onChange={(event) => setRegistration({ ...registration, maternalLastName: event.target.value })} value={registration.maternalLastName} /></label>
                  <label className="kiosk-field">Género<select className="kiosk-input compact" onChange={(event) => setRegistration({ ...registration, gender: event.target.value })} value={registration.gender}><option value="">Prefiero no indicar</option><option value="FEMALE">Mujer</option><option value="MALE">Hombre</option></select></label>
                  <label className="kiosk-field">Teléfono<input autoComplete="tel" className="kiosk-input compact" inputMode="tel" maxLength={20} onChange={(event) => setRegistration({ ...registration, phone: event.target.value })} placeholder="10 dígitos" value={registration.phone} /></label>
                  <label className="kiosk-field">Correo<input autoComplete="email" className="kiosk-input compact" maxLength={180} onChange={(event) => setRegistration({ ...registration, email: event.target.value })} type="email" value={registration.email} /></label>
                </div>
                <label className="kiosk-field">Fecha de nacimiento<input autoComplete="bday" className="kiosk-input compact" inputMode="numeric" maxLength={10} onChange={(event) => setRegistrationBirthDateDisplay(formatMexicanDateInput(event.target.value))} placeholder="DD/MM/AAAA" required value={registrationBirthDateDisplay} /><small>Necesitarás esta fecha junto con tu correo o teléfono para volver a entrar.</small></label>
                <button className="kiosk-primary-action" disabled={registrationBusy} type="submit">{registrationBusy ? "Registrando..." : "Crear expediente y continuar"}</button>
              </form>
            )}
          </section>
        ) : (
          <section className="grid gap-5">
            <section className="kiosk-dashboard-hero">
              <div>
                <p>Sesión del socio</p>
                <h2>Hola, {summary.clientName}</h2>
                <span>Elige una opción. Sólo verás funciones habilitadas para esta sucursal.</span>
              </div>
              <button className="kiosk-end-action" onClick={() => void resetClientSession()} type="button">
                Terminar sesión y borrar mis datos
              </button>
            </section>

            <nav aria-label="Opciones de autoservicio" className="kiosk-panel-navigation">
              <KioskPanelButton active={activePanel === "memberships"} description="Estado, compra o renovación" label="Membresías" onClick={() => setActivePanel("memberships")} />
              {summary.capabilities.resourceBookingAllowed ? <KioskPanelButton active={activePanel === "resources"} description="Espacios incluidos en tu plan" label="Reservar" onClick={() => setActivePanel("resources")} /> : null}
              {summary.capabilities.wellnessBookingAllowed ? <KioskPanelButton active={activePanel === "wellness"} description="Citas gratuitas disponibles" label="Wellness" onClick={() => setActivePanel("wellness")} /> : null}
              {summary.paymentActivity.length > 0 ? <KioskPanelButton active={activePanel === "payments"} description="Cargos y comprobantes" label="Mis pagos" onClick={() => setActivePanel("payments")} /> : null}
              {summary.capabilities.profileUpdateAllowed ? <KioskPanelButton active={activePanel === "profile"} description="Correo y teléfono" label="Mis datos" onClick={() => setActivePanel("profile")} /> : null}
              {summary.capabilities.autopayAllowed && (activeMembership?.autopayManagedAtCurrentBranch ?? activeMembership?.managedAtCurrentBranch) ? (
                <KioskPanelButton
                  active={activePanel === "autopay"}
                  description="Tarjeta para renovación"
                  label="Domiciliación"
                  onClick={() => {
                    const membership = summary.memberships.find((item) => item.status === "ACTIVE") ?? summary.memberships[0];
                    if (membership) void openAutopay(membership);
                    else setActivePanel("autopay");
                  }}
                />
              ) : null}
            </nav>

            {activePanel === "resources" && summary.capabilities.resourceBookingAllowed ? <section className="kiosk-card">
              <div className="kiosk-step"><span>R</span><div><h2>Reserva un recurso</h2><p>Sólo aparecen recursos incluidos en tu membresía para el día seleccionado.</p></div></div>
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

            {activePanel === "wellness" && summary.capabilities.wellnessBookingAllowed ? (
              <section className="kiosk-card">
                <div className="kiosk-step">
                  <span>W</span>
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

            {activePanel === "payments" && summary.paymentActivity.length > 0 ? (
              <section className="kiosk-card">
                <div className="kiosk-step"><span>$</span><div><h2>Mis pagos con tarjeta</h2><p>Consulta cargos pendientes, rechazados y comprobantes emitidos por Stripe.</p></div></div>
                {hiddenPendingPaymentCount > 0 ? (
                  <p className="kiosk-feature-note mt-4">Se agruparon {hiddenPendingPaymentCount} intentos pendientes repetidos. No representan cargos completados.</p>
                ) : null}
                <div className="mt-4 grid gap-3">
                  {visiblePaymentActivity.map((payment) => (
                    <article className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-[var(--brand-line)] p-3" key={payment.id}>
                      <div>
                        <strong className="block">{payment.saleNumber} · {formatMoney(payment.amountCents, payment.currency)}</strong>
                        <span className={paymentStatusTone(payment.status)}>{paymentStatusLabel(payment.status)}</span>
                        <small className="ml-2 text-[var(--brand-muted)]">{formatTime(payment.providerUpdatedAt ?? payment.receivedAt)}</small>
                      </div>
                      <div className="flex gap-2">
                        {payment.receiptUrl ? <a className="kiosk-secondary-action" href={payment.receiptUrl} rel="noreferrer" target="_blank">Ver comprobante</a> : null}
                        {payment.checkoutRecoverable && summary.capabilities.onlineCardPaymentsReady ? (
                          <button className="kiosk-primary-action small" type="button" disabled={checkoutRecoveryBusy} onClick={() => void continueCheckout(payment.id)}>
                            {checkoutRecoveryBusy ? "Consultando…" : "Continuar pago"}
                          </button>
                        ) : null}
                      </div>
                    </article>
                  ))}
                </div>
              </section>
            ) : null}

            {activePanel === "memberships" ? <section className="kiosk-content-grid kiosk-content-grid-single">
              <div className="kiosk-card">
                <div className="kiosk-member-header">
                  <span>{activeMembership || currentVisitPackage ? "Tus servicios" : "Comienza aquí"}</span>
                  <h2>{activeMembership || currentVisitPackage
                    ? "Servicios activos"
                    : summary.capabilities.membershipEnrollmentAllowed && summary.capabilities.visitPackagePurchaseAllowed
                      ? "Elige una opción"
                      : summary.capabilities.membershipEnrollmentAllowed
                        ? "Contrata una membresía"
                        : summary.capabilities.visitPackagePurchaseAllowed
                          ? "Elige una opción"
                          : "Membresías"}</h2>
                  <p className="kiosk-section-intro">
                    {activeMembership
                      ? activeMembership.managedAtCurrentBranch
                        ? "Consulta tu vigencia y elige exactamente qué quieres hacer. Nada cambia hasta que revises el importe y completes el pago."
                        : "Tu membresía multi-sucursal es válida aquí para acceso y reservas. Su renovación, cambio de plan y domiciliación se administran en la sucursal donde la contrataste."
                      : currentVisitPackage
                        ? "Consulta en un solo lugar la vigencia y el saldo de tus servicios. También puedes revisar las opciones disponibles para contratar una membresía."
                      : summary.capabilities.membershipEnrollmentAllowed && summary.capabilities.visitPackagePurchaseAllowed
                        ? "Elige un plan o paquete y consulta el importe final. El pago sólo se habilita cuando Stripe está listo."
                        : summary.capabilities.membershipEnrollmentAllowed
                          ? "Elige un plan y consulta el importe final. El pago sólo se habilita cuando Stripe está listo."
                          : summary.capabilities.visitPackagePurchaseAllowed
                            ? "Elige un paquete y consulta el importe final. El pago sólo se habilita cuando Stripe está listo."
                        : "Consulta aquí el estado de tus membresías y solicita apoyo en recepción para contratar."}
                  </p>
                </div>
                {!summary.capabilities.onlineCardPaymentsReady && (summary.capabilities.membershipEnrollmentAllowed || summary.capabilities.membershipRenewalAllowed || summary.capabilities.membershipPlanChangeAllowed || summary.capabilities.visitPackagePurchaseAllowed) ? (
                  <p className="kiosk-feature-note">Los pagos en línea todavía no están disponibles. Podrás contratar cuando el gimnasio termine de configurar Stripe o puedes acudir a recepción.</p>
                ) : null}

                {membershipError ? <div className="kiosk-membership-error" role="alert">{membershipError}</div> : null}
                {visitPackageError ? <div className="kiosk-membership-error" role="alert">{visitPackageError}</div> : null}

                {activeMembership || currentVisitPackage ? (
                  <section aria-label="Servicios activos" className="kiosk-active-products">
                    {activeMembership ? (
                    <article className="kiosk-current-membership kiosk-active-product-card">
                      <div className="kiosk-current-membership-heading">
                        <div><span className="kiosk-status-pill active">Activa</span><h3>{activeMembership.planName}</h3></div>
                        <strong>Vigente hasta {formatDay(activeMembership.endsOn)}</strong>
                      </div>
                      <div className="kiosk-membership-dates">
                        <span><small>Inicio</small>{formatDay(activeMembership.startsOn)}</span>
                        <span><small>Fin</small>{formatDay(activeMembership.endsOn)}</span>
                      </div>
                      {!activeMembership.managedAtCurrentBranch ? (
                        <p className="kiosk-feature-note">
                          Esta membresía pertenece a otra sucursal. Puedes usar aquí los accesos y recursos incluidos en el plan, pero los cobros y cambios se realizan únicamente en su sucursal de origen.
                        </p>
                      ) : null}
                    </article>
                    ) : null}
                    {currentVisitPackage ? (
                      <article aria-label="Saldo de visitas actual" className="kiosk-current-membership kiosk-active-product-card">
                        <div className="kiosk-current-membership-heading">
                          <div>
                            <span className={currentVisitPackage.status === "ACTIVE" ? "kiosk-status-pill active" : "kiosk-status-pill"}>
                              {currentVisitPackage.status === "ACTIVE" ? "Activo" : "Pago pendiente"}
                            </span>
                            <h3>{currentVisitPackage.packageName}</h3>
                          </div>
                          <strong>{currentVisitPackage.remainingVisits} de {currentVisitPackage.totalVisits} visitas disponibles</strong>
                        </div>
                        {currentVisitPackage.validUntil ? (
                          <p className="kiosk-muted">Vigente hasta {formatDay(currentVisitPackage.validUntil)}.</p>
                        ) : (
                          <p className="kiosk-muted">El paquete se activará cuando el pago sea confirmado.</p>
                        )}
                      </article>
                    ) : null}
                    {activeMembership?.managedAtCurrentBranch && summary.capabilities.membershipRenewalAllowed ? (
                      <div className="kiosk-renewal-bar">
                        <div>
                          <strong>Renovar el mismo plan</strong>
                          <p>Conserva {activeMembership.planName} y extiende su vigencia.</p>
                        </div>
                        <button className="kiosk-primary-action small" disabled={workingMembershipId !== null} onClick={() => void reviewMembershipCheckout("renewal", activeMembership.planId, activeMembership)} type="button">
                          {workingMembershipId === activeMembership.id ? "Calculando importe..." : `Revisar renovación de ${activeMembership.planName}`}
                        </button>
                      </div>
                    ) : null}
                  </section>
                ) : (
                  <div className="kiosk-enrollment-flow">
                    <div className="kiosk-empty-membership">
                      <strong>No tienes una membresía activa</strong>
                      <p>{summary.capabilities.membershipEnrollmentAllowed && summary.capabilities.visitPackagePurchaseAllowed
                        ? "Revisa los planes y paquetes disponibles para elegir cómo continuar."
                        : summary.capabilities.membershipEnrollmentAllowed
                          ? "Revisa las opciones disponibles para tu primera membresía."
                          : summary.capabilities.visitPackagePurchaseAllowed
                            ? "Revisa los paquetes disponibles para elegir cómo continuar."
                            : "La contratación de membresías desde el kiosco no está habilitada. Solicita apoyo en recepción."}</p>
                    </div>

                  </div>
                )}

                {!activeMembership && lastExpiredMembership?.managedAtCurrentBranch && summary.capabilities.membershipRenewalAllowed ? (
                  <article className="kiosk-membership-action-card renewal">
                    <div><h3>Renovar tu plan anterior</h3><p><strong>{lastExpiredMembership.planName}</strong> venció el {formatDay(lastExpiredMembership.endsOn)}.</p></div>
                    <button className="kiosk-primary-action small" disabled={workingMembershipId !== null} onClick={() => void reviewMembershipCheckout("renewal", lastExpiredMembership.planId, lastExpiredMembership)} type="button">
                      {workingMembershipId === lastExpiredMembership.id ? "Calculando importe..." : `Revisar renovación de ${lastExpiredMembership.planName}`}
                    </button>
                  </article>
                ) : null}

                {(commercialMembershipPlans.length > 0)
                  || (!currentVisitPackage && summary.capabilities.visitPackagePurchaseAllowed && visitPackagePlans.length > 0) ? (
                    <section className="kiosk-plan-picker" aria-label="Planes y paquetes disponibles">
                      <div>
                        <span>Opciones disponibles</span>
                        <h3>{commercialMembershipPlans.length > 0
                          && !currentVisitPackage && summary.capabilities.visitPackagePurchaseAllowed && visitPackagePlans.length > 0
                          ? activeMembership ? "Elige otro plan o paquete" : "Elige un plan o paquete"
                          : commercialMembershipPlans.length > 0
                            ? activeMembership ? "Elige otro plan" : "Elige una membresía"
                            : "Elige un paquete"}</h3>
                      </div>
                      <div aria-label="Planes y paquetes disponibles" className="kiosk-plan-options" role="radiogroup">
                        {commercialMembershipPlans.map((plan) => {
                          const selected = selectedCommercialOfferType === "MEMBERSHIP" && targetPlanId === plan.id;
                          return (
                            <button
                              aria-checked={selected}
                              className={selected ? "kiosk-plan-option selected" : "kiosk-plan-option"}
                              key={`membership-${plan.id}`}
                              onClick={() => {
                                setSelectedCommercialOfferType("MEMBERSHIP");
                                setTargetPlanId(plan.id);
                                setMembershipReview(null);
                                setMembershipError(null);
                                setVisitPackageQuote(null);
                              }}
                              role="radio"
                              type="button"
                            >
                              <strong>{plan.name}</strong>
                              <span>{formatMoney(plan.priceCents, plan.currency)}</span>
                              <small>{plan.durationDays} días</small>
                            </button>
                          );
                        })}
                        {!currentVisitPackage && summary.capabilities.visitPackagePurchaseAllowed ? visitPackagePlans.map((plan) => {
                          const selected = selectedCommercialOfferType === "VISIT_PACKAGE" && selectedVisitPackagePlanId === plan.id;
                          return (
                            <button
                              aria-checked={selected}
                              className={selected ? "kiosk-plan-option selected" : "kiosk-plan-option"}
                              key={`visit-package-${plan.id}`}
                              onClick={() => {
                                setSelectedCommercialOfferType("VISIT_PACKAGE");
                                setSelectedVisitPackagePlanId(plan.id);
                                setVisitPackageQuote(null);
                                setVisitPackageError(null);
                                setMembershipReview(null);
                              }}
                              role="radio"
                              type="button"
                            >
                              <strong>{plan.name}</strong>
                              <span>{formatMoney(plan.priceCents, plan.currency)}</span>
                              <small>{plan.totalVisits} visitas · {plan.validityDays} días</small>
                            </button>
                          );
                        }) : null}
                      </div>
                      {selectedCommercialOfferType === "MEMBERSHIP" ? (
                        <button className="kiosk-primary-action small kiosk-offer-review-action" disabled={workingMembershipId !== null || !selectedTargetPlan} onClick={() => selectedTargetPlan && void reviewMembershipCheckout(activeMembership ? "change" : "enrollment", selectedTargetPlan.id, activeMembership)} type="button">
                          {workingMembershipId === (activeMembership?.id ?? "enrollment")
                            ? "Calculando importe..."
                            : selectedTargetPlan
                              ? activeMembership ? `Revisar cambio a ${selectedTargetPlan.name}` : `Revisar contratación de ${selectedTargetPlan.name}`
                              : "Elige un plan"}
                        </button>
                      ) : selectedCommercialOfferType === "VISIT_PACKAGE" ? (
                        <button className="kiosk-primary-action small kiosk-offer-review-action" disabled={visitPackageBusy || !selectedVisitPackagePlanId} onClick={() => void reviewVisitPackage()} type="button">
                          {visitPackageBusy ? "Calculando importe..." : "Revisar paquete"}
                        </button>
                      ) : null}
                    </section>
                  ) : null}

                {!activeMembership && summary.capabilities.membershipEnrollmentAllowed && plans.length === 0
                  && !summary.capabilities.visitPackagePurchaseAllowed ? (
                    <p className="kiosk-muted kiosk-offers-empty">No hay planes disponibles actualmente. Solicita apoyo en recepción.</p>
                  ) : null}
                {!currentVisitPackage && summary.capabilities.visitPackagePurchaseAllowed && visitPackagePlans.length === 0
                  && !summary.capabilities.membershipEnrollmentAllowed ? (
                    <p className="kiosk-muted kiosk-offers-empty">No hay paquetes disponibles actualmente. Solicita apoyo en recepción.</p>
                  ) : null}
                {!activeMembership && !currentVisitPackage
                  && summary.capabilities.membershipEnrollmentAllowed && summary.capabilities.visitPackagePurchaseAllowed
                  && plans.length === 0 && visitPackagePlans.length === 0 ? (
                    <p className="kiosk-muted kiosk-offers-empty">No hay planes ni paquetes disponibles actualmente. Solicita apoyo en recepción.</p>
                  ) : null}

                {membershipReview ? (
                  <section aria-label="Revisión de membresía" className="kiosk-membership-review" ref={checkoutRef}>
                    <div>
                      <span>Último paso antes del pago</span>
                      <h3>{membershipActionTitle(membershipReview.kind)}</h3>
                      <p>Revisa el importe. Tu membresía sólo cambiará cuando Stripe confirme el pago.</p>
                    </div>
                    <dl className="kiosk-quote-breakdown">
                      <div><dt>Plan</dt><dd>{membershipReview.quote.planName}</dd></div>
                      <div><dt>Precio del plan</dt><dd>{formatMoney(membershipReview.quote.planPriceCents, membershipReview.quote.currency)}</dd></div>
                      {membershipReview.quote.registrationFeeCents > 0 ? <div><dt>{membershipReview.quote.registrationFeeLabel ?? "Inscripción"}</dt><dd>{formatMoney(membershipReview.quote.registrationFeeCents, membershipReview.quote.currency)}</dd></div> : null}
                      {membershipReview.quote.discountCents > 0 ? <div><dt>Descuentos</dt><dd>-{formatMoney(membershipReview.quote.discountCents, membershipReview.quote.currency)}</dd></div> : null}
                      <div className="total"><dt>Total a pagar</dt><dd>{formatMoney(membershipReview.quote.totalCents, membershipReview.quote.currency)}</dd></div>
                    </dl>
                    {membershipReview.quote.appliedBenefits.length > 0 ? (
                      <p className="kiosk-quote-benefits">Beneficios aplicados: {membershipReview.quote.appliedBenefits.map((benefit) => benefit.name).join(", ")}.</p>
                    ) : null}
                    <div className="kiosk-review-actions">
                      <button className="kiosk-secondary-action" disabled={workingMembershipId !== null} onClick={() => setMembershipReview(null)} type="button">Volver sin cambios</button>
                      <button className="kiosk-primary-action small" disabled={workingMembershipId !== null || !summary.capabilities.onlineCardPaymentsReady} onClick={() => void completeMembershipCheckout()} type="button">
                        {workingMembershipId !== null
                          ? "Abriendo Stripe..."
                          : summary.capabilities.onlineCardPaymentsReady
                            ? "Confirmar y continuar a Stripe"
                            : "Pago pendiente de Stripe"}
                      </button>
                    </div>
                  </section>
                ) : null}

                {visitPackageQuote ? (
                  <section aria-label="Revisión de paquete" className="kiosk-membership-review" ref={checkoutRef}>
                    <div>
                      <span>Revisa antes de pagar</span>
                      <h3>{visitPackageQuote.packageName}</h3>
                      <p>{visitPackageQuote.totalVisits} visitas para usar durante {visitPackageQuote.validityDays} días desde la activación.</p>
                    </div>
                    <dl className="kiosk-quote-breakdown">
                      <div><dt>Paquete</dt><dd>{formatMoney(visitPackageQuote.packagePriceCents, visitPackageQuote.currency)}</dd></div>
                      {visitPackageQuote.registrationFeeCents > 0 ? <div><dt>{visitPackageQuote.registrationFeeLabel ?? "Inscripción"}</dt><dd>{formatMoney(visitPackageQuote.registrationFeeCents, visitPackageQuote.currency)}</dd></div> : null}
                      <div className="total"><dt>Total a pagar</dt><dd>{formatMoney(visitPackageQuote.totalCents, visitPackageQuote.currency)}</dd></div>
                    </dl>
                    <div className="kiosk-review-actions">
                      <button className="kiosk-secondary-action" disabled={visitPackageBusy} onClick={() => setVisitPackageQuote(null)} type="button">Volver</button>
                      <button className="kiosk-primary-action small" disabled={visitPackageBusy || !summary.capabilities.onlineCardPaymentsReady} onClick={() => void completeVisitPackageCheckout()} type="button">
                        {visitPackageBusy ? "Abriendo Stripe..." : summary.capabilities.onlineCardPaymentsReady ? "Continuar a Stripe" : "Pago pendiente de Stripe"}
                      </button>
                    </div>
                  </section>
                ) : null}

                {previousMemberships.length > 0 ? (
                  <details className="kiosk-membership-history">
                    <summary>Membresías anteriores ({previousMemberships.length})</summary>
                    <div className="kiosk-membership-list">
                      {previousMemberships.map((membership) => (
                        <article className="kiosk-membership-card" key={membership.id}>
                          <div><span>{membershipStatusLabel(membership.status)}</span><h3>{membership.planName}</h3></div>
                          <p>{formatDay(membership.startsOn)} a {formatDay(membership.endsOn)}</p>
                        </article>
                      ))}
                    </div>
                  </details>
                ) : null}
              </div>
            </section> : null}

            {activePanel === "profile" && summary.capabilities.profileUpdateAllowed ? (
              <section className="kiosk-content-grid kiosk-content-grid-single">
                <aside className="kiosk-card kiosk-side-card">
                <div className="kiosk-step"><span>D</span><div><h2>Mis datos</h2><p>Actualiza el contacto que usa tu gimnasio.</p></div></div>
                {summary.capabilities.profileUpdateAllowed ? (
                  <form onSubmit={updateContact}>
                    <p className="kiosk-muted">Actual: {summary.profile?.email ?? "sin correo"} / {summary.profile?.phone ?? "sin teléfono"}</p>
                    <label className="kiosk-field">Nuevo correo<input className="kiosk-input compact" onChange={(event) => setContactEmail(event.target.value)} placeholder="Deja vacío para conservarlo" type="email" value={contactEmail} /></label>
                    <label className="kiosk-field">Nuevo teléfono en formato +52<input className="kiosk-input compact" onChange={(event) => setContactPhone(event.target.value)} pattern="\+52\d{10}" placeholder="Deja vacío para conservarlo" value={contactPhone} /></label>
                    <button className="kiosk-secondary-action mt-4 w-full" disabled={contactBusy || (!contactEmail.trim() && !contactPhone.trim())} type="submit">{contactBusy ? "Guardando..." : "Guardar datos"}</button>
                  </form>
                ) : null}
              </aside>
              </section>
            ) : null}

            {activePanel === "autopay" && summary.capabilities.autopayAllowed ? (
              <section className="kiosk-content-grid kiosk-content-grid-single">
                <aside className="kiosk-card kiosk-side-card">
                <div className="kiosk-step"><span>A</span><div><h2>Domiciliación</h2><p>Administra la tarjeta autorizada para renovar tu membresía.</p></div></div>
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
              </aside>
              </section>
            ) : null}
          </section>
        )}
      </section>
    </main>
  );
}

function KioskPanelButton({
  active,
  description,
  label,
  onClick,
}: {
  active: boolean;
  description: string;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      aria-pressed={active}
      className={active ? "kiosk-panel-button active" : "kiosk-panel-button"}
      onClick={onClick}
      type="button"
    >
      <strong>{label}</strong>
      <span>{description}</span>
    </button>
  );
}

function membershipActionTitle(kind: MembershipCheckoutKind) {
  if (kind === "renewal") return "Renovación de membresía";
  if (kind === "change") return "Cambio de plan";
  return "Contratación de membresía";
}

function membershipQuoteError(exception: unknown) {
  return `No pudimos calcular el importe final. No se realizó ningún cargo ni cambio en tu membresía. Intenta nuevamente o solicita apoyo en recepción.${supportReference(exception)}`;
}

function membershipCheckoutError(exception: unknown) {
  const rawMessage = exception instanceof Error ? exception.message : "";
  const providerDetail = rawMessage.match(/Detalle:\s*(.+?)(?=\s+\(\d{3}\s|$)/)?.[1];
  const apiMessage = rawMessage.match(/^(.+?)\s+\(4\d{2}\s/)?.[1];
  const detail = providerDetail
    ? ` Stripe indicó: ${providerDetail}`
    : apiMessage && !apiMessage.startsWith("No se pudo conectar")
      ? ` Motivo: ${apiMessage}`
      : "";
  return `No pudimos abrir el pago. No se realizó ningún cargo ni cambio en tu membresía.${detail}${supportReference(exception)}`;
}

function visitPackageOperationError(exception: unknown, phase: "quote" | "checkout") {
  const rawMessage = exception instanceof Error ? exception.message : "";
  if (rawMessage.includes("VISIT_PACKAGE_OPEN_CONFLICT")) {
    return "Ya tienes un paquete de visitas activo o un pago pendiente. Debes terminarlo, agotarlo o cancelarlo antes de contratar otro.";
  }
  if (rawMessage.includes("VISIT_PACKAGE_LEGACY_CONFLICT")) {
    return "Tu expediente conserva un paquete anterior activo. Solicita apoyo en recepción antes de contratar otro.";
  }
  if (rawMessage.includes("VISIT_PACKAGE_PLAN_NOT_AVAILABLE")) {
    return "Este paquete no está disponible en la sucursal actual.";
  }
  const action = phase === "quote" ? "calcular el importe" : "abrir el pago";
  return `No pudimos ${action} del paquete. No se realizó ningún cargo. Intenta nuevamente o solicita apoyo en recepción.${supportReference(exception)}`;
}

function collapseRepeatedPendingPayments(payments: KioskSummaryResponse["paymentActivity"]) {
  const pendingKeys = new Set<string>();
  return payments.filter((payment) => {
    if (!["OPEN", "PENDING_VALIDATION"].includes(payment.status)) return true;
    const key = `${payment.amountCents}:${payment.currency}`;
    if (pendingKeys.has(key)) return false;
    pendingKeys.add(key);
    return true;
  });
}

function supportReference(exception: unknown) {
  const rawMessage = exception instanceof Error ? exception.message : "";
  const correlationId = rawMessage.match(/CorrelationId:\s*([a-zA-Z0-9-]+)/)?.[1];
  return correlationId ? ` Referencia para soporte: ${correlationId}.` : "";
}

function friendlyVerificationError(exception: unknown) {
  const rawMessage = exception instanceof Error ? exception.message : "";
  if (rawMessage.includes("SELF_SERVICE_SESSION_BLOCKED")) {
    return "Se alcanzó el límite de intentos. Espera un momento o solicita apoyo en recepción.";
  }
  if (["SELF_SERVICE_SESSION_EXPIRED", "SELF_SERVICE_SESSION_INVALID", "SELF_SERVICE_SESSION_ENDED"]
    .some((code) => rawMessage.includes(code))) {
    return "La sesión segura terminó. Solicita en recepción que vuelvan a abrir el kiosco.";
  }
  if (rawMessage.includes("INTERNAL_ERROR") || /\(5\d{2}\s/.test(rawMessage)) {
    return `No pudimos consultar tu expediente en este momento. Intenta nuevamente o solicita apoyo en recepción.${supportReference(exception)}`;
  }
  return "No pudimos verificar tus datos. Revísalos o elige “Quiero registrarme” si aún no tienes expediente.";
}

function friendlyRegistrationError(exception: unknown) {
  const rawMessage = exception instanceof Error ? exception.message : "";
  if (rawMessage.includes("SELF_SERVICE_CLIENT_ALREADY_EXISTS")) {
    return "Ya existe un socio con esos datos. Elige “Ya soy socio” para entrar.";
  }
  if (rawMessage.includes("SELF_SERVICE_REACTIVATION_REQUIRES_RECEPTION")) {
    return "Tu expediente requiere apoyo de recepción para reactivarse.";
  }
  if (rawMessage.includes("SELF_SERVICE_SESSION_ALREADY_VERIFIED")) {
    return "La pantalla conservó una sesión anterior. Cambia a “Ya soy socio” y vuelve a “Quiero registrarme” para limpiarla.";
  }
  if (["SELF_SERVICE_SESSION_EXPIRED", "SELF_SERVICE_SESSION_INVALID", "SELF_SERVICE_SESSION_ENDED"]
    .some((code) => rawMessage.includes(code))) {
    return "La sesión segura terminó. Solicita en recepción que vuelvan a abrir el kiosco.";
  }
  return `No pudimos completar el registro. Intenta nuevamente o solicita apoyo en recepción.${supportReference(exception)}`;
}

function friendlySessionResetError(exception: unknown) {
  const rawMessage = exception instanceof Error ? exception.message : "";
  if (["SELF_SERVICE_SESSION_EXPIRED", "SELF_SERVICE_SESSION_INVALID", "SELF_SERVICE_SESSION_ENDED"]
    .some((code) => rawMessage.includes(code))) {
    return "La sesión segura terminó. Solicita en recepción que vuelvan a abrir el kiosco.";
  }
  return `No pudimos preparar la pantalla para el siguiente socio. Solicita apoyo en recepción.${supportReference(exception)}`;
}

function settledValue<T>(result: PromiseSettledResult<T>, fallback: T): T {
  return result.status === "fulfilled" ? result.value : fallback;
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
  if (persistedKey && persistedKey.length <= 160) {
    keys.set(operation, persistedKey);
    return persistedKey;
  }
  const random = typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now()}`;
  // The API caps idempotency keys at 160 characters. Keep the full operation
  // identity only as the local map key and send a compact, collision-safe key.
  const created = `${parts[0]}:${random}`;
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
  if (typeof window !== "undefined") clearKioskBrowserState(window.sessionStorage);
}

function writeBrowserKioskSession(session: KioskSessionResponse) {
  if (typeof window !== "undefined") {
    window.sessionStorage.setItem(KIOSK_BROWSER_SESSION_KEY, JSON.stringify(session));
  }
}
