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
  searchClientsPage,
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
import { lastPage } from "@/lib/pagination";

export function useClientsController() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [clients, setClients] = useState<ClientSummary[]>([]);
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [query, setQuery] = useState("");
  const [listParams, setListParams] = useState({ query: "", page: 0, size: 25, revision: 0 });
  const [totalClients, setTotalClients] = useState(0);
  const [listError, setListError] = useState<string | null>(null);
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
        setPlans(activePlans);
        setManualCardEnabled(Boolean(paymentConfiguration?.receptionManualTerminalEnabled));
        setMercadoPagoEnabled(Boolean(paymentConfiguration?.mercadoPagoReady));
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar socios.");
        }
      }
    }

    void loadInitial();

    return () => {
      active = false;
    };
  }, []);

  const accessToken = session?.accessToken;
  useEffect(() => {
    if (!accessToken) return;
    let active = true;
    async function load() {
      await Promise.resolve();
      if (!active) return;
      setLoading(true);
      setListError(null);
      try {
        const result = await searchClientsPage(accessToken!, listParams);
        if (!active) return;
        const validPage = lastPage(result.totalElements, result.size);
        if (listParams.page > validPage) {
          setListParams((current) => ({ ...current, page: validPage }));
          return;
        }
        setClients(result.items);
        setTotalClients(result.totalElements);
      } catch (exception) {
        if (active) setListError(exception instanceof Error ? exception.message : "No fue posible cargar socios.");
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => { active = false; };
  }, [accessToken, listParams]);

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setListParams((current) => ({ ...current, query: query.trim(), page: 0, revision: current.revision + 1 }));
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
    setQuery("");
    setListParams((current) => ({ ...current, query: "", page: 0, revision: current.revision + 1 }));
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
    error: error ?? listError,
    fieldErrors,
    form,
    loading,
    page: listParams.page,
    pageSize: listParams.size,
    totalClients,
    setPage: (page: number) => setListParams((current) => ({ ...current, page })),
    setPageSize: (size: number) => setListParams((current) => ({ ...current, size, page: 0 })),
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
