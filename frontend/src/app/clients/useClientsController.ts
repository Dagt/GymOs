"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { type FieldErrorMap, normalizePhone } from "@/components/FormFields";
import {
  createClient,
  createSale,
  getCurrentUser,
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

  const permissions = useMemo(() => new Set(session?.user.permissions ?? []), [session]);
  const canCreateClient = permissions.has("client.create");
  const canCreateMembershipSale = permissions.has("sale.create") && permissions.has("catalog.read");

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
        const canLoadPlans = storedSession.user.permissions.includes("sale.create")
          && storedSession.user.permissions.includes("catalog.read");
        const activePlans = canLoadPlans
          ? await getPlans(storedSession.accessToken).then((loaded) => loaded.filter((plan) => plan.status === "ACTIVE"))
          : [];
        if (!active) return;
        setClients(results);
        setPlans(activePlans);
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
    if (!storedSession.user.permissions.includes("client.create")) {
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
      // Validate the access token immediately before the write. apiFetch refreshes
      // an expired token and getStoredSession then exposes the rotated credentials.
      await getCurrentUser(storedSession.accessToken);
      activeSession = getStoredSession() ?? storedSession;
      setSession(activeSession);
    } catch (exception) {
      setError(prependStep("No fue posible validar la sesión antes del alta", exception));
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
      if (!activeSession.user.permissions.includes("sale.create")) {
        setMessage("Socio registrado. La membresía inicial no se creó porque tu perfil no puede registrar ventas.");
      } else {
        try {
          await createInitialMembershipSale(activeSession.accessToken, clientId, selectedPlan, form);
          setMessage("Socio registrado con membresía inicial.");
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
    setForm((current) => ({ ...current, [field]: value }));
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

function createInitialMembershipSale(
  accessToken: string,
  clientId: string,
  plan: PlanSummary,
  form: ClientRegistrationForm,
) {
  return createSale(accessToken, {
    clientId,
    items: [{ productId: plan.productId, quantity: 1, discountCents: 0 }],
    discountCents: 0,
    notes: "Alta inicial de socio",
    payment: {
      method: form.paymentMethod,
      amountCents: plan.priceCents,
      reference: form.paymentReference || null,
      receiptObjectKey: null,
      validateNow: form.validateNow,
      notes: "Pago de membresía inicial",
    },
    idempotencyKey: `client-initial-membership:${clientId}:${plan.id}`,
  });
}

function prependStep(step: string, exception: unknown) {
  const detail = exception instanceof Error ? exception.message : "Ocurrió un error inesperado.";
  return `${step}. ${detail}`;
}
