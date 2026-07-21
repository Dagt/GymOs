"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  DateField,
  EmailField,
  FieldErrorMap,
  FormError,
  GenderField,
  PhoneField,
  TextField,
  normalizePhone,
  validateEmail,
  validateGender,
  validateIsoDate,
  validatePhone,
  validateRequired,
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

type ClientRegistrationForm = ClientPayload & {
  planId: string;
  paymentMethod: "CASH" | "TRANSFER" | "CARD_MANUAL";
  paymentReference: string;
  validateNow: boolean;
};

const emptyForm: ClientRegistrationForm = {
  firstName: "",
  lastName: "",
  maternalLastName: "",
  birthDate: "",
  gender: "",
  phone: "",
  email: "",
  photoReference: "",
  notes: "",
  planId: "",
  paymentMethod: "CASH",
  paymentReference: "",
  validateNow: true,
};

export default function ClientsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [clients, setClients] = useState<ClientSummary[]>([]);
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [query, setQuery] = useState("");
  const [form, setForm] = useState<ClientRegistrationForm>(emptyForm);
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
        const [results, loadedPlans] = await Promise.all([
          searchClients(storedSession.accessToken, ""),
          getPlans(storedSession.accessToken),
        ]);
        if (!active) {
          return;
        }
        const activePlans = loadedPlans.filter((plan) => plan.status === "ACTIVE");
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
      const normalizedClient: ClientPayload = {
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
      const response = await createClient(session.accessToken, normalizedClient);
      const selectedPlan = plans.find((plan) => plan.id === form.planId);
      if (selectedPlan) {
        await createSale(session.accessToken, {
          clientId: response.client.id,
          items: [{ productId: selectedPlan.productId, quantity: 1, discountCents: 0 }],
          discountCents: 0,
          notes: "Alta inicial de socio",
          payment: {
            method: form.paymentMethod,
            amountCents: selectedPlan.priceCents,
            reference: form.paymentReference || null,
            receiptObjectKey: null,
            validateNow: form.validateNow,
            notes: "Pago de membresia inicial",
          },
          idempotencyKey: `client-initial-membership:${response.client.id}:${selectedPlan.id}`,
        });
      }
      setCreatedClientId(response.client.id);
      setForm({ ...emptyForm, planId: plans[0]?.id || "" });
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
      <form className="admin-panel flex w-full gap-2" onSubmit={submitSearch}>
        <input
          className="app-input min-w-0 flex-1"
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Nombre, telefono o email"
          value={query}
        />
        <button className="app-button" type="submit">
          Buscar
        </button>
      </form>

      {error ? <FormError message={error} /> : null}
      {message ? <p className="form-message success">{message}</p> : null}

      <section className="grid gap-6">
        <section className="catalog-toolbar app-card p-5">
          <div>
            <p className="app-eyebrow">Recepcion de socios</p>
            <h2 className="app-card-title">Busqueda y alta en el mismo flujo</h2>
            <p className="app-card-copy">
              La lista queda disponible para consulta rapida. El registro completo con membresia inicial se abre en un panel lateral.
            </p>
          </div>
          <button className="app-button" onClick={() => setShowCreateClient(true)} type="button">
            Registrar socio
          </button>
        </section>

        <section className="app-card p-5">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="app-eyebrow">Busqueda</p>
              <h2 className="mt-2 text-xl font-semibold">Socios registrados</h2>
            </div>
            <span className="text-sm text-[#6f7a73]">{clients.length} socios</span>
          </div>
          <div className="mt-5 grid gap-3">
            {loading ? <p className="text-sm text-[#5d6b63]">Cargando socios...</p> : null}
            {!loading && clients.length === 0 ? (
              <p className="text-sm text-[#5d6b63]">No hay socios para mostrar.</p>
            ) : null}
            {clients.map((client) => (
              <Link
                className="rounded-md border border-[#d8ddd2] px-4 py-3 transition hover:border-[var(--brand-primary)] hover:bg-[#f6f7f2]"
                href={`/clients/${client.id}`}
                key={client.id}
              >
                <span className="block font-semibold">{clientFullName(client)}</span>
                <span className="mt-1 block text-sm text-[#5d6b63]">
                  {client.phone ?? "Sin telefono"} - {client.email ?? "Sin email"} - {client.status}
                </span>
              </Link>
            ))}
          </div>
        </section>
      </section>

      <AppDrawer
        description="Captura datos personales, membresia inicial y pago de entrada desde recepcion."
        onClose={() => setShowCreateClient(false)}
        open={showCreateClient}
        title="Registrar socio"
      >
        <form className="grid gap-4" onSubmit={submitCreate}>
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField error={fieldErrors.firstName} label="Nombre" name="firstName" value={form.firstName} onChange={(value) => updateForm("firstName", value)} />
            <TextField error={fieldErrors.lastName} label="Apellido paterno" name="lastName" value={form.lastName} onChange={(value) => updateForm("lastName", value)} />
            <TextField error={fieldErrors.maternalLastName} label="Apellido materno" name="maternalLastName" required={false} value={form.maternalLastName ?? ""} onChange={(value) => updateForm("maternalLastName", value)} />
            <PhoneField error={fieldErrors.phone} label="Telefono" name="phone" required value={form.phone ?? ""} onChange={(value) => updateForm("phone", value)} />
            <EmailField error={fieldErrors.email} label="Email" name="email" value={form.email ?? ""} onChange={(value) => updateForm("email", value)} />
            <DateField error={fieldErrors.birthDate} label="Fecha de nacimiento" name="birthDate" value={form.birthDate ?? ""} onChange={(value) => updateForm("birthDate", value)} />
            <GenderField error={fieldErrors.gender} label="Genero" name="gender" value={form.gender ?? ""} onChange={(value) => updateForm("gender", value)} />
          </div>

          <label className="form-field" htmlFor="planId">
            <span>Plan inicial{plans.length > 0 ? <b>*</b> : null}</span>
            <select className="app-input" id="planId" onChange={(event) => updateForm("planId", event.target.value)} value={form.planId}>
              {plans.length === 0 ? <option value="">No hay planes activos</option> : null}
              {plans.map((plan) => (
                <option key={plan.id} value={plan.id}>
                  {plan.name} - {formatMoney(plan.priceCents, plan.currency)}
                </option>
              ))}
            </select>
            {fieldErrors.planId ? <small className="form-field-error">{fieldErrors.planId}</small> : null}
          </label>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="form-field" htmlFor="paymentMethod">
              <span>Metodo de pago</span>
              <select className="app-input" id="paymentMethod" onChange={(event) => updateForm("paymentMethod", event.target.value)} value={form.paymentMethod}>
                <option value="CASH">Efectivo</option>
                <option value="TRANSFER">Transferencia</option>
                <option value="CARD_MANUAL">Tarjeta manual</option>
              </select>
            </label>
            <TextField label="Referencia de pago" name="paymentReference" required={false} value={form.paymentReference} onChange={(value) => updateForm("paymentReference", value)} />
          </div>

          <label className="flex items-center gap-2 text-sm font-medium text-[#42514a]">
            <input checked={form.validateNow} onChange={(event) => updateForm("validateNow", event.target.checked)} type="checkbox" />
            Validar pago ahora
          </label>

          <label className="form-field" htmlFor="notes">
            <span>Notas</span>
            <textarea
              className="app-input min-h-24 py-2"
              id="notes"
              onChange={(event) => updateForm("notes", event.target.value)}
              value={form.notes ?? ""}
            />
          </label>

          {createdClientId ? (
            <Link className="text-sm font-semibold text-[var(--brand-primary)]" href={`/clients/${createdClientId}`}>
              Socio registrado. Abrir ficha.
            </Link>
          ) : null}
          <button className="form-primary-button" disabled={saving} type="submit">
            {saving ? "Guardando..." : "Registrar socio"}
          </button>
        </form>
      </AppDrawer>
    </AppShell>
  );
}

function validateClientForm(form: ClientRegistrationForm, requiresPlan: boolean) {
  const errors: FieldErrorMap = {};
  addError(errors, "firstName", validateRequired(form.firstName, "Nombre"));
  addError(errors, "lastName", validateRequired(form.lastName, "Apellido paterno"));
  addError(errors, "phone", validatePhone(form.phone ?? "", true));
  addError(errors, "email", validateEmail(form.email ?? "", false));
  addError(errors, "birthDate", validateIsoDate(form.birthDate ?? "", "Fecha de nacimiento", false));
  addError(errors, "gender", validateGender(form.gender ?? "", false));
  if (requiresPlan && !form.planId) {
    errors.planId = "Selecciona la membresia inicial.";
  }
  return errors;
}

function addError(errors: FieldErrorMap, key: string, value: string) {
  if (value) {
    errors[key] = value;
  }
}

function clientFullName(client: ClientSummary) {
  return `${client.firstName} ${client.lastName} ${client.maternalLastName ?? ""}`.trim();
}

function AuthRequired() {
  return (
    <AuthRequiredCard
      message="La lista de socios contiene informacion sensible y requiere una cuenta autorizada."
      title="Inicia sesion para consultar socios"
    />
  );
}

function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency,
  }).format(cents / 100);
}
