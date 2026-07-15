"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
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

  if (!session) {
    return <AuthRequired />;
  }

  return (
    <main className="min-h-screen bg-[#f6f7f2] text-[#111815]">
      <header className="border-b border-[#d8ddd2] bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <Link className="text-sm font-semibold text-[#51735f]" href="/reception">
              Recepcion
            </Link>
            <h1 className="mt-1 text-3xl font-semibold">Clientes</h1>
            <p className="mt-1 text-sm text-[#5d6b63]">
              Alta, busqueda y ficha base para operar desde recepcion.
            </p>
          </div>
          <form className="flex w-full gap-2 lg:w-auto" onSubmit={submitSearch}>
            <input
              className="h-11 min-w-0 flex-1 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm outline-none transition focus:border-[#51735f] lg:w-80"
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Nombre, telefono o email"
              value={query}
            />
            <button
              className="h-11 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30]"
              type="submit"
            >
              Buscar
            </button>
          </form>
        </div>
      </header>

      <section className="mx-auto grid max-w-7xl gap-6 px-6 py-6 lg:grid-cols-[1fr_1.3fr]">
        <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <h2 className="text-xl font-semibold">Alta rapida</h2>
          <form className="mt-5 grid gap-4" onSubmit={submitCreate}>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Nombre" value={form.firstName ?? ""} onChange={(value) => updateForm("firstName", value)} />
              <Field label="Apellido" value={form.lastName ?? ""} onChange={(value) => updateForm("lastName", value)} />
              <Field label="Telefono" value={form.phone ?? ""} onChange={(value) => updateForm("phone", value)} />
              <Field label="Email" type="email" value={form.email ?? ""} onChange={(value) => updateForm("email", value)} />
              <Field label="Fecha de nacimiento" type="date" value={form.birthDate ?? ""} onChange={(value) => updateForm("birthDate", value)} />
              <Field label="Genero" value={form.gender ?? ""} onChange={(value) => updateForm("gender", value)} />
            </div>
            <label className="grid gap-2 text-sm font-medium text-[#42514a]">
              Notas
              <textarea
                className="min-h-24 rounded-md border border-[#cdd5ca] px-3 py-2 text-sm outline-none transition focus:border-[#51735f]"
                onChange={(event) => updateForm("notes", event.target.value)}
                value={form.notes ?? ""}
              />
            </label>
            {error ? (
              <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f]">
                {error}
              </p>
            ) : null}
            {createdClientId ? (
              <Link className="text-sm font-semibold text-[#51735f]" href={`/clients/${createdClientId}`}>
                Cliente registrado. Abrir ficha.
              </Link>
            ) : null}
            <button
              className="h-11 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
              disabled={saving}
              type="submit"
            >
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
                  {client.phone ?? "Sin telefono"} - {client.email ?? "Sin email"} - {client.status}
                </span>
              </Link>
            ))}
          </div>
        </section>
      </section>
    </main>
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
    <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]">
      <section className="w-full max-w-md rounded-lg border border-[#d8ddd2] bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-semibold">Necesitas iniciar sesion</h1>
        <Link
          className="mt-5 inline-flex h-11 items-center justify-center rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30]"
          href="/login"
        >
          Iniciar sesion
        </Link>
      </section>
    </main>
  );
}
