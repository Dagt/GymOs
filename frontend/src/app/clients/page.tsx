"use client";

import { FormEvent, useEffect, useState } from "react";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  type FieldErrorMap,
  FormError,
  normalizePhone,
} from "@/components/FormFields";
import {
  createClient,
  createSale,
  getPlans,
  getStoredSession,
  searchClients,
  type ClientPayload,
  type ClientSummary,
  type PlanSummary,
  type StoredSession,
} from "@/lib/api";
import {
  ClientList,
  ClientSearchBar,
  ClientToolbar,
  CreateClientDrawer,
} from "./ClientSections";
import {
  emptyClientForm,
  validateClientForm,
  type ClientRegistrationForm,
} from "./ClientHelpers";

export default function ClientsPage() {
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
        const [results, activePlans] = await loadClientsAndPlans(storedSession.accessToken, "");
        if (!active) {
          return;
        }
        setClients(results);
        setPlans(activePlans);
        setForm((current) => ({ ...current, planId: current.planId || activePlans[0]?.id || "" }));
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar socios.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadInitial();

    return () => {
      active = false;
    };
  }, []);

  async function loadClients(activeSession = session, activeQuery = query) {
    if (!activeSession) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setClients(await searchClients(activeSession.accessToken, activeQuery));
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
    if (!session) {
      return;
    }
    const errors = validateClientForm(form, plans.length > 0);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    setSaving(true);
    setCreatedClientId(null);
    setError(null);
    setMessage(null);
    try {
      const normalizedClient = normalizeClientPayload(form);
      const response = await createClient(session.accessToken, normalizedClient);
      const selectedPlan = plans.find((plan) => plan.id === form.planId);
      if (selectedPlan) {
        await createInitialMembershipSale(session.accessToken, response.client.id, selectedPlan, form);
      }
      setCreatedClientId(response.client.id);
      setForm({ ...emptyClientForm, planId: plans[0]?.id || "" });
      await loadClients(session, query);
      setShowCreateClient(false);
      setMessage(selectedPlan ? "Socio registrado con membresia inicial." : "Socio registrado sin membresia inicial.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible registrar socio.");
    } finally {
      setSaving(false);
    }
  }

  function updateForm(field: keyof ClientRegistrationForm, value: string | boolean) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  if (loading && !session) {
    return <LoadingScreen text="Cargando socios..." />;
  }

  if (!session) {
    return <AuthRequired />;
  }

  return (
    <AppShell
      actions={[]}
      eyebrow="Socios"
      session={session}
      subtitle="Registro completo de socios, membresia inicial y busqueda para recepcion."
      title="Socios"
    >
      <ClientSearchBar query={query} onQueryChange={setQuery} onSubmit={submitSearch} />

      {error ? <FormError message={error} /> : null}
      {message ? <p className="form-message success">{message}</p> : null}

      <section className="grid gap-6">
        <ClientToolbar onCreateClient={() => setShowCreateClient(true)} />
        <ClientList clients={clients} loading={loading} />
      </section>

      <CreateClientDrawer
        createdClientId={createdClientId}
        errors={fieldErrors}
        form={form}
        open={showCreateClient}
        plans={plans}
        saving={saving}
        onClose={() => setShowCreateClient(false)}
        onFormChange={updateForm}
        onSubmit={submitCreate}
      />
    </AppShell>
  );
}

function AuthRequired() {
  return (
    <AuthRequiredCard
      message="La lista de socios contiene informacion sensible y requiere una cuenta autorizada."
      title="Inicia sesion para consultar socios"
    />
  );
}

async function loadClientsAndPlans(accessToken: string, query: string) {
  const [results, loadedPlans] = await Promise.all([
    searchClients(accessToken, query),
    getPlans(accessToken),
  ]);
  return [results, loadedPlans.filter((plan) => plan.status === "ACTIVE")] as const;
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
      notes: "Pago de membresia inicial",
    },
    idempotencyKey: `client-initial-membership:${clientId}:${plan.id}`,
  });
}
