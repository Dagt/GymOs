"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import {
  getClient,
  getClientMemberships,
  getStoredSession,
  renewMembership,
  updateClient,
  type ClientMembershipSummary,
  type ClientDetail,
  type ClientDetailResponse,
  type ClientUpdatePayload,
  type RenewMembershipPayload,
  type StoredSession,
} from "@/lib/api";

export default function ClientDetailPage() {
  const params = useParams<{ clientId: string }>();
  const [session, setSession] = useState<StoredSession | null>(null);
  const [data, setData] = useState<ClientDetailResponse | null>(null);
  const [memberships, setMemberships] = useState<ClientMembershipSummary[]>([]);
  const [form, setForm] = useState<ClientUpdatePayload | null>(null);
  const [renewMethod, setRenewMethod] = useState<RenewMembershipPayload["method"]>("CASH");
  const [renewReference, setRenewReference] = useState("");
  const [renewValidateNow, setRenewValidateNow] = useState(true);
  const [renewingMembershipId, setRenewingMembershipId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
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
        const [response, loadedMemberships] = await Promise.all([
          getClient(storedSession.accessToken, params.clientId),
          getClientMemberships(storedSession.accessToken, params.clientId),
        ]);
        if (!active) {
          return;
        }
        setData(response);
        setMemberships(loadedMemberships);
        setForm(toForm(response.client));
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar cliente.");
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
  }, [params.clientId]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !form) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const response = await updateClient(session.accessToken, params.clientId, form);
      setData(response);
      setForm(toForm(response.client));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar cliente.");
    } finally {
      setSaving(false);
    }
  }

  async function renew(activeMembership: ClientMembershipSummary) {
    if (!session) {
      return;
    }
    setRenewingMembershipId(activeMembership.id);
    setError(null);
    setMessage(null);
    try {
      const response = await renewMembership(session.accessToken, activeMembership.id, {
        method: renewMethod,
        reference: renewReference,
        validateNow: renewValidateNow,
        notes: "Renovacion desde ficha de cliente",
      });
      setMemberships(await getClientMemberships(session.accessToken, params.clientId));
      setRenewReference("");
      setMessage(
        response.membership
          ? `Membresia renovada desde ${formatDay(response.membership.startsOn)} hasta ${formatDay(response.membership.endsOn)}.`
          : `Venta ${response.saleNumber} creada con pago pendiente.`,
      );
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible renovar membresia.");
    } finally {
      setRenewingMembershipId(null);
    }
  }

  function updateField(field: keyof ClientUpdatePayload, value: string) {
    setForm((current) => (current ? { ...current, [field]: value } : current));
  }

  if (!session) {
    return <AuthRequired />;
  }

  if (loading || !form || !data) {
    return (
      <main className="grid min-h-screen place-items-center bg-[#f6f7f2] px-6 text-[#111815]">
        <p className="rounded-md border border-[#d8ddd2] bg-white px-4 py-3 text-sm text-[#42514a]">
          Cargando ficha...
        </p>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-[#f6f7f2] text-[#111815]">
      <header className="border-b border-[#d8ddd2] bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-4 px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <Link className="text-sm font-semibold text-[#51735f]" href="/clients">
              Clientes
            </Link>
            <h1 className="mt-1 text-3xl font-semibold">
              {data.client.firstName} {data.client.lastName}
            </h1>
            <p className="mt-1 text-sm text-[#5d6b63]">
              Cliente desde {formatDate(data.client.joinedAt)} - {data.client.status}
            </p>
          </div>
          <Link
            className="inline-flex h-11 items-center justify-center rounded-md border border-[#cdd5ca] bg-white px-4 text-sm font-semibold text-[#42514a] transition hover:border-[#51735f]"
            href="/reception"
          >
            Recepcion
          </Link>
        </div>
      </header>

      <section className="mx-auto grid max-w-7xl gap-6 px-6 py-6 lg:grid-cols-[1.2fr_0.8fr]">
        <section className="grid gap-6">
          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Ficha base</h2>
            <form className="mt-5 grid gap-4" onSubmit={submit}>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Nombre" value={form.firstName ?? ""} onChange={(value) => updateField("firstName", value)} />
                <Field label="Apellido" value={form.lastName ?? ""} onChange={(value) => updateField("lastName", value)} />
                <Field label="Telefono" value={form.phone ?? ""} onChange={(value) => updateField("phone", value)} />
                <Field label="Email" type="email" value={form.email ?? ""} onChange={(value) => updateField("email", value)} />
                <Field label="Fecha de nacimiento" type="date" value={form.birthDate ?? ""} onChange={(value) => updateField("birthDate", value)} />
                <Field label="Genero" value={form.gender ?? ""} onChange={(value) => updateField("gender", value)} />
              </div>
              <label className="grid gap-2 text-sm font-medium text-[#42514a]">
                Estado
                <select
                  className="h-11 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm outline-none transition focus:border-[#51735f]"
                  onChange={(event) => updateField("status", event.target.value)}
                  value={form.status}
                >
                  <option value="ACTIVE">ACTIVE</option>
                  <option value="INACTIVE">INACTIVE</option>
                  <option value="SUSPENDED">SUSPENDED</option>
                </select>
              </label>
              <label className="grid gap-2 text-sm font-medium text-[#42514a]">
                Notas
                <textarea
                  className="min-h-28 rounded-md border border-[#cdd5ca] px-3 py-2 text-sm outline-none transition focus:border-[#51735f]"
                  onChange={(event) => updateField("notes", event.target.value)}
                  value={form.notes ?? ""}
                />
              </label>
              {error ? (
                <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f]">
                  {error}
                </p>
              ) : null}
              <button
                className="h-11 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
                disabled={saving}
                type="submit"
              >
                {saving ? "Guardando..." : "Guardar cambios"}
              </button>
            </form>
          </section>

          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <h2 className="text-xl font-semibold">Membresias</h2>
                <p className="mt-1 text-sm text-[#5d6b63]">Planes activos e historicos del cliente.</p>
              </div>
              <Link className="text-sm font-semibold text-[#51735f]" href="/pos">
                Nueva venta
              </Link>
            </div>
            <div className="mt-4 grid gap-3 rounded-md border border-[#d8ddd2] bg-[#f6f7f2] p-4">
              <div className="grid gap-3 sm:grid-cols-2">
                <label className="grid gap-2 text-sm font-medium text-[#42514a]">
                  Metodo
                  <select
                    className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm"
                    onChange={(event) => setRenewMethod(event.target.value as RenewMembershipPayload["method"])}
                    value={renewMethod}
                  >
                    <option value="CASH">Efectivo</option>
                    <option value="TRANSFER">Transferencia</option>
                    <option value="CARD_MANUAL">Tarjeta manual</option>
                  </select>
                </label>
                <label className="grid gap-2 text-sm font-medium text-[#42514a]">
                  Referencia
                  <input
                    className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm"
                    onChange={(event) => setRenewReference(event.target.value)}
                    value={renewReference}
                  />
                </label>
              </div>
              <label className="flex items-center gap-2 text-sm font-medium text-[#42514a]">
                <input
                  checked={renewValidateNow}
                  onChange={(event) => setRenewValidateNow(event.target.checked)}
                  type="checkbox"
                />
                Validar pago ahora
              </label>
            </div>
            {message ? (
              <p className="mt-4 rounded-md border border-[#bad9c4] bg-[#eff8f1] px-3 py-2 text-sm text-[#31563c]">
                {message}
              </p>
            ) : null}
            <div className="mt-4 grid gap-3">
              {memberships.length === 0 ? (
                <p className="text-sm text-[#5d6b63]">Sin membresias registradas.</p>
              ) : null}
              {memberships.map((membership) => (
                <article className="rounded-md border border-[#d8ddd2] px-4 py-3" key={membership.id}>
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                    <div>
                      <p className="font-semibold">{membership.planName}</p>
                      <p className="mt-1 text-sm text-[#5d6b63]">
                        {membershipStatusLabel(membership.status)} - {formatDay(membership.startsOn)} a {formatDay(membership.endsOn)}
                      </p>
                      <p className="mt-1 text-xs text-[#6f7a73]">
                        Gracia hasta {formatDay(membership.graceEndsOn)}
                      </p>
                    </div>
                    <Link className="text-sm font-semibold text-[#51735f]" href={`/sales/${membership.saleId}`}>
                      {membership.saleNumber}
                    </Link>
                  </div>
                  <button
                    className="mt-3 h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60"
                    disabled={renewingMembershipId !== null || membership.status !== "ACTIVE"}
                    onClick={() => renew(membership)}
                    type="button"
                  >
                    {renewingMembershipId === membership.id ? "Renovando..." : "Renovar"}
                  </button>
                </article>
              ))}
            </div>
          </section>
        </section>

        <aside className="grid gap-6">
          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Resumen</h2>
            <dl className="mt-4 grid gap-3 text-sm">
              <Metric label="Telefono" value={data.client.phone ?? "Sin telefono"} />
              <Metric label="Email" value={data.client.email ?? "Sin email"} />
              <Metric label="Actualizado" value={formatDate(data.client.updatedAt)} />
            </dl>
          </section>

          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Timeline</h2>
            <div className="mt-4 grid gap-3">
              {data.timeline.length === 0 ? (
                <p className="text-sm text-[#5d6b63]">Sin eventos registrados.</p>
              ) : null}
              {data.timeline.map((entry) => (
                <article className="border-l-2 border-[#51735f] pl-3" key={`${entry.action}-${entry.occurredAt}`}>
                  <p className="text-sm font-semibold">{entry.action}</p>
                  <p className="mt-1 text-xs text-[#6f7a73]">
                    {formatDate(entry.occurredAt)} - {entry.origin ?? "Sistema"}
                  </p>
                </article>
              ))}
            </div>
          </section>
        </aside>
      </section>
    </main>
  );
}

function toForm(client: ClientDetail): ClientUpdatePayload {
  return {
    firstName: client.firstName,
    lastName: client.lastName,
    birthDate: client.birthDate ?? "",
    gender: client.gender ?? "",
    phone: client.phone ?? "",
    email: client.email ?? "",
    photoReference: client.photoReference ?? "",
    status: client.status,
    notes: client.notes ?? "",
  };
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

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="font-medium text-[#6f7a73]">{label}</dt>
      <dd className="mt-1 font-semibold">{value}</dd>
    </div>
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

function formatDate(value: string) {
  return new Intl.DateTimeFormat("es-MX", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function formatDay(value: string) {
  return new Intl.DateTimeFormat("es-MX", {
    dateStyle: "medium",
    timeZone: "UTC",
  }).format(new Date(`${value}T00:00:00Z`));
}

function membershipStatusLabel(status: string) {
  const labels: Record<string, string> = {
    ACTIVE: "Activa",
    EXPIRED: "Expirada",
    CANCELLED: "Cancelada",
  };
  return labels[status] ?? status;
}
