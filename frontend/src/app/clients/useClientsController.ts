"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { type FieldErrorMap, normalizePhone } from "@/components/FormFields";
import {
  createClient,
  createMercadoPagoPointOrder,
  enrollMembership,
  getCurrentUser,
  getPaymentConfiguration,
  getPlans,
  getStoredSession,
  searchClients,
  type ClientPayload,
  type ClientSummary,
  type PlanSummary,
  type StoredSession,
} from "@/lib/api";
import {
  emptyClientForm,
  validateClientForm,
  type ClientRegistrationForm,
} from "./ClientHelpers";

export function useClientsController() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [clients, setClients] = useState<ClientSummary[]>([]);
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [query, setQuery] = useState("");
  const [form, setForm] = useState<ClientRegistrationForm>(emptyClientForm);
  const [fieldErrors, setFieldErrors] = useState<FieldErrorMap>({});
  const [createdClientId, setCreatedClientId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [showCreateClient, setShowCreateClient] = useState(false);
  const [manualCardEnabled, setManualCardEnabled] = useState(false);
  const [mercadoPagoEnabled, setMercadoPagoEnabled] = useState(false);

  const canCreateClient = useMemo(() => canRegisterClients(session), [session]);
  const canCreateMembershipSale = useMemo(
    () => hasPermission(session, "sale.create") && hasPermission(session, "catalog.read"),
    [session],
  );

  useEffect(() => {
    let active = true;

    async function loadInitial() {
      const storedSession = getStoredSession();
      if (!storedSession) {
        setLoading(false);
        return;
      }
      setSession(storedSession);
      setLoading(true);
      setError(null);
      try {
        const results = await searchClients(storedSession.accessToken, "");
        const refreshedSession = getStoredSession() ?? storedSession;
        const canLoadPlans = hasPermission(refreshedSession, "sale.create")
          && hasPermission(refreshedSession, "catalog.read");
        const activePlans = canLoadPlans
          ? await getPlans(refreshedSession.accessToken).then((loaded) => loaded.filter((plan) => plan.status === "ACTIVE"))
          : [];
        const paymentConfiguration = canLoadPlans && hasPermission(refreshedSession, "payment.settings.read")
          ? await getPaymentConfiguration(refreshedSession.accessToken)
          : null;
        if (!active) return;
        setSession(getStoredSession() ?? refreshedSession);
        setClients(results);
        setPlans(activePlans);
        setManualCardEnabled(Boolean(paymentConfiguration?.receptionManualTerminalEnabled));
        setMercadoPagoEnabled(Boolean(paymentConfiguration?.mercadoPagoReady));
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar socios.");
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    void loadInitial();

    return () => {
      active = false;
    };
  }, []);

  async function loadClients(
    activeSession = getStoredSession() ?? session,
    activeQuery = query,
    clearPreviousError = true,
  ) {
    if (!activeSession) return;
    setLoading(true);
    if (clearPreviousError) setError(null);
    try {
      setClients(await searchClients(activeSession.accessToken, activeQuery));
      setSession(getStoredSession() ?? activeSession);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cargar socios.");
    } finally {
      setLoading(false);
    }
  }

  async function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await loadClients();
  }

  async function submitCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const storedSession = getStoredSession();
    if (!storedSession) {
      setError("No se encontró una sesión activa. Inicia sesión nuevamente antes de registrar al socio.");
      return;
    }
    if (!canRegisterClients(storedSession)) {
      setError("Tu perfil puede consultar socios, pero no tiene permiso para registrar uno nuevo.");
      return;
    }

    const errors = validateClientForm(form, false);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSaving(true);
    setCreatedClientId(null);
    setError(null);
    setMessage(null);

    let activeSession = storedSession;
    try {
      await getCurrentUser(storedSession.accessToken);
      activeSession = getStoredSession() ?? storedSession;
      setSession(activeSession);
    } catch (exception) {
      setError(prependStep("No fue posible validar la sesión antes del alta", exception));
      setSaving(false);
      return;
    }

    if (!canRegisterClients(activeSession)) {
      setError("La sesión actual no permite registrar socios. Cierra sesión e ingresa nuevamente para actualizar los permisos.");
      setSaving(false);
      return;
    }

    let clientId: string;
    try {
      const response = await createClient(activeSession.accessToken, normalizeClientPayload(form));
      clientId = response.client.id;
      setCreatedClientId(clientId);
    } catch (exception) {
      setError(prependStep("No fue posible crear el socio", exception));
      setSaving(false);
      return;
    }

    let operationError: string | null = null;
    const selectedPlan = plans.find((plan) => plan.id === form.planId);
    if (selectedPlan) {
      if (!hasPermission(activeSession, "sale.create")) {
        setMessage("Socio registrado. La membresía inicial no se creó porque tu perfil no puede registrar ventas.");
      } else {
        try {
          await createInitialMembershipSale(activeSession.accessToken, clientId, selectedPlan, form);
          setMessage(form.paymentMethod === "CARD_MERCADO_PAGO"
            ? "Socio registrado. El cobro inicial fue enviado a Point y la membresia se activara al confirmar el webhook."
            : "Socio registrado con membresía inicial.");
        } catch (exception) {
          setMessage("El socio sí quedó registrado. Puedes abrir su ficha y agregar la membresía después.");
          operationError = prependStep("No fue posible registrar la membresía o el pago inicial", exception);
        }
      }
    } else {
      setMessage("Socio registrado sin membresía inicial.");
    }

    setForm({ ...emptyClientForm });
    await loadClients(getStoredSession() ?? activeSession, query, false);
    if (operationError) setError(operationError);
    setShowCreateClient(false);
    setSaving(false);
  }

  function updateForm(field: keyof ClientRegistrationForm, value: string | boolean) {
    setForm((current) => ({
      ...current,
      [field]: value,
      ...(field === "paymentMethod" && value === "CARD_MERCADO_PAGO" ? { validateNow: false } : {}),
    }));
  }

  return {
    canCreateClient,
    canCreateMembershipSale,
    clients,
    createdClientId,
    error,
    fieldErrors,
    form,
    loading,
    manualCardEnabled,
    mercadoPagoEnabled,
    message,
    plans,
    query,
    saving,
    session,
    setQuery,
    setShowCreateClient,
    showCreateClient,
    submitCreate,
    submitSearch,
    updateForm,
  };
}

function normalizeClientPayload(form: ClientRegistrationForm): ClientPayload {
  return {
    firstName: form.firstName.trim(),
    lastName: form.lastName.trim(),
    maternalLastName: form.maternalLastName?.trim() || null,
    birthDate: form.birthDate || null,
    gender: form.gender || null,
    phone: normalizePhone(form.phone ?? ""),
    email: form.email?.trim() || null,
    photoReference: null,
    notes: form.notes?.trim() || null,
  };
}

async function createInitialMembershipSale(
  accessToken: string,
  clientId: string,
  plan: PlanSummary,
  form: ClientRegistrationForm,
) {
  const result = await enrollMembership(accessToken, {
    clientId,
    planId: plan.id,
    method: form.paymentMethod,
    reference: form.paymentReference || null,
    validateNow: form.paymentMethod === "CARD_MERCADO_PAGO" ? false : form.validateNow,
    notes: "Alta inicial de socio",
    idempotencyKey: `client-initial-membership:${clientId}:${plan.id}`,
  });
  if (form.paymentMethod === "CARD_MERCADO_PAGO") {
    if (!result.paymentId) throw new Error("La venta inicial no devolvio el pago para enviar a Point.");
    await createMercadoPagoPointOrder(accessToken, result.paymentId);
  }
  return result;
}

function hasPermission(session: StoredSession | null, permission: string) {
  return Boolean(
    session?.user.permissions.includes(permission)
      || session?.user.permissions.includes("platform.admin")
      || session?.user.platformAdmin,
  );
}

function canRegisterClients(session: StoredSession | null) {
  return hasPermission(session, "client.create");
}

function prependStep(step: string, exception: unknown) {
  const detail = exception instanceof Error ? exception.message : "Ocurrió un error inesperado.";
  return `${step}. ${detail}`;
}
