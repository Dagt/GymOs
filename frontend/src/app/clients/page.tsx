"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { AppShell, InlineError, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  createClient,
  getStoredSession,
  searchClients,
  type ClientPayload,
  type ClientSummary,
  type StoredSession,
} from "@/lib/api";

const emptyForm: ClientPayload = {
  firstName: "",
  lastName: "",
  birthDate: "",
  gender: "",
  phone: "",
  email: "",
  photoReference: "",
  notes: "",
};

export default function ClientsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [clients, setClients] = useState<ClientSummary[]>([]);
  const [query, setQuery] = useState("");
  const [form, setForm] = useState<ClientPayload>(emptyForm);
  const [createdClientId, setCreatedClientId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;

    async function loadInitial() {
      const storedSession = getStoredSession();
      if (!active) {
        return;
      }
      if (!storedSession) {
        setLoading(false);
        return;
      }
      setSession(storedSession);
      setLoading(true);
      setError(null);
      try {
        const results = await searchClients(storedSession.accessToken, "");
        if (active) {
          setClients(results);
        }
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar clientes.");
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
      const results = await searchClients(activeSession.accessToken, activeQuery);
      setClients(results);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible cargar clientes.");
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
    setSaving(true);
    setCreatedClientId(null);
    setError(null);
    try {
      const response = await createClient(session.accessToken, form);
      setCreatedClientId(response.client.id);
      setForm(emptyForm);
      await loadClients(session, query);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible registrar cliente.");
    } finally {
      setSaving(false);
    }
  }

  function updateForm(field: keyof ClientPayload, value: string) {
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
      actions={[
        { href: "/pos", label: "Cobrar" },
        { href: "/clients/import", label: "Importar clientes" },
      ]}
      eyebrow="Socios"
      session={session}
      subtitle="Alta, búsqueda y ficha base para operar desde recepción."
      title="Clientes"
    >
      <form className="admin-panel flex w-full gap-2" onSubmit={submitSearch}>
        <input
          className="h-11 min-w-0 flex-1 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm outline-none transition focus:border-[#51735f]"
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Nombre, teléfono o email"
          value={query}
        />
        <button className="app-button" type="submit">
          Buscar
        </button>
      </form>

      {error ? <InlineError message={error} /> : null}

      <section className="grid gap-6 lg:grid-cols-[1fr_1.3fr]">
        <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <h2 className="text-xl font-semibold">Alta rápida</h2>
          <form className="mt-5 grid gap-4" onSubmit={submitCreate}>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Nombre" value={form.firstName ?? ""} onChange={(value) => updateForm("firstName", value)} />
              <Field label="Apellido" value={form.lastName ?? ""} onChange={(value) => updateForm("lastName", value)} />
              <Field label="Teléfono" value={form.phone ?? ""} onChange={(value) => updateForm("phone", value)} />
              <Field label="Email" type="email" value={form.email ?? ""} onChange={(value) => updateForm("email", value)} />
              <Field label="Fecha de nacimiento" type="date" value={form.birthDate ?? ""} onChange={(value) => updateForm("birthDate", value)} />
              <Field label="Género" value={form.gender ?? ""} onChange={(value) => updateForm("gender", value)} />
            </div>
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Notas
              <textarea
                className="min-h-24 rounded-md border border-[#cdd5ca] px-3 py-2 text-sm outline-none transition focus:border-[#51735f]"
                onChange={(event) => updateForm("notes", event.target.value)}
                value={form.notes ?? ""}
              />
            </label>
            {createdClientId ? (
              <Link className="text-sm font-semibold text-[#51735f]" href={`/clients/${createdClientId}`}>
                Cliente registrado. Abrir ficha.
              </Link>
            ) : null}
            <button className="app-button h-11" disabled={saving} type="submit">
              {saving ? "Guardando..." : "Registrar cliente"}
            </button>
          </form>
        </section>

        <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-xl font-semibold">Resultados</h2>
            <span className="text-sm text-[#6f7a73]">{clients.length} clientes</span>
          </div>
          <div className="mt-5 grid gap-3">
            {loading ? <p className="text-sm text-[#5d6b63]">Cargando clientes...</p> : null}
            {!loading && clients.length === 0 ? (
              <p className="text-sm text-[#5d6b63]">No hay clientes para mostrar.</p>
            ) : null}
            {clients.map((client) => (
              <Link
                className="rounded-md border border-[#d8ddd2] px-4 py-3 transition hover:border-[#51735f] hover:bg-[#f6f7f2]"
                href={`/clients/${client.id}`}
                key={client.id}
              >
                <span className="block font-semibold">{client.firstName} {client.lastName}</span>
                <span className="mt-1 block text-sm text-[#5d6b63]">
                  {client.phone ?? "Sin teléfono"} - {client.email ?? "Sin email"} - {client.status}
                </span>
              </Link>
            ))}
          </div>
        </section>
      </section>
    </AppShell>
  );
}

function Field({
  label,
  value,
  onChange,
  type = "text",
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
}) {
  return (
    <label className="grid gap-2 text-sm font-medium text-[#42514a]">
      {label}
      <input
        className="h-11 rounded-md border border-[#cdd5ca] px-3 text-sm outline-none transition focus:border-[#51735f]"
        onChange={(event) => onChange(event.target.value)}
        type={type}
        value={value}
      />
    </label>
  );
}

function AuthRequired() {
  return (
    <AuthRequiredCard
      message="La lista de clientes contiene información sensible y requiere una cuenta autorizada."
      title="Inicia sesión para consultar clientes"
    />
  );
}
