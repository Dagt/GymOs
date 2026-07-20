"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  createBooking,
  createResource,
  getBranches,
  getBookings,
  getCurrentOrganization,
  getPlans,
  getResources,
  getStoredSession,
  searchClients,
  transitionBooking,
  type BranchSummary,
  type BookingSummary,
  type ClientSummary,
  type OrganizationSummary,
  type PlanSummary,
  type ResourceSummary,
  type StoredSession,
} from "@/lib/api";

const resourceColors = ["#166534", "#0f766e", "#1d4ed8", "#7c3aed", "#be123c", "#b45309"];
const bookingActions: Array<"confirm" | "check-in" | "complete" | "cancel"> = ["confirm", "check-in", "complete", "cancel"];

export default function BookingsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organization, setOrganization] = useState<OrganizationSummary | null>(null);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [resources, setResources] = useState<ResourceSummary[]>([]);
  const [bookings, setBookings] = useState<BookingSummary[]>([]);
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [clientResults, setClientResults] = useState<ClientSummary[]>([]);
  const [clientQuery, setClientQuery] = useState("");
  const [calendarDate, setCalendarDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [resourceId, setResourceId] = useState("");
  const [clientId, setClientId] = useState("");
  const [startDate, setStartDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [startTime, setStartTime] = useState("");
  const [notes, setNotes] = useState("");
  const [resourceName, setResourceName] = useState("");
  const [resourceType, setResourceType] = useState("ROOM");
  const [resourceCapacity, setResourceCapacity] = useState(1);
  const [resourceDuration, setResourceDuration] = useState(60);
  const [resourceOpensAt, setResourceOpensAt] = useState("06:00");
  const [resourceClosesAt, setResourceClosesAt] = useState("22:00");
  const [resourceAllowedPlanIds, setResourceAllowedPlanIds] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;

    async function load() {
      const stored = getStoredSession();
      if (!active) {
        return;
      }
      if (!stored) {
        setLoading(false);
        return;
      }
      setSession(stored);
      try {
        const [loadedOrganization, loadedBranches, loadedResources, loadedBookings, loadedPlans] = await Promise.all([
          getCurrentOrganization(stored.accessToken).catch(() => null),
          getBranches(stored.accessToken),
          getResources(stored.accessToken),
          getBookings(stored.accessToken, calendarDate),
          getPlans(stored.accessToken),
        ]);
        if (!active) {
          return;
        }
        setOrganization(loadedOrganization);
        setBranches(loadedBranches);
        setResources(loadedResources);
        setBookings(loadedBookings);
        setPlans(loadedPlans);
        setResourceId((current) => current || loadedResources[0]?.id || "");
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar reservas.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, [calendarDate]);

  useEffect(() => {
    if (!session) {
      return;
    }
    const query = clientQuery.trim();
    if (query.length < 2) {
      return;
    }
    let active = true;
    const timer = window.setTimeout(() => {
      searchClients(session.accessToken, query, "ACTIVE")
        .then((results) => {
          if (active) {
            setClientResults(results.slice(0, 8));
          }
        })
        .catch(() => {
          if (active) {
            setClientResults([]);
          }
        });
    }, 250);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [clientQuery, session]);

  const activeResource = useMemo(
    () => resources.find((resource) => resource.id === resourceId) ?? null,
    [resources, resourceId],
  );
  const activeBranch = branches.find((branch) => branch.id === session?.user.branchId) ?? null;
  const visibleClientResults = clientQuery.trim().length >= 2 ? clientResults : [];
  const selectedClient = clientResults.find((client) => client.id === clientId) ?? null;
  const isReceptionUser = session?.user.console === "RECEPTION"
    || session?.user.roles.some((role) => role.toLowerCase().includes("recepcion"));
  const canManageResources = Boolean(session?.user.permissions.includes("resource.manage") && !isReceptionUser);
  const computedEnd = activeResource && startDate && startTime
    ? addMinutes(`${startDate}T${startTime}`, activeResource.slotMinutes)
    : "";
  const calendarHours = useMemo(() => buildCalendarHours(resources), [resources]);

  async function refresh(stored = session, date = calendarDate) {
    if (!stored) {
      return;
    }
    const [loadedResources, loadedBookings] = await Promise.all([
      getResources(stored.accessToken),
      getBookings(stored.accessToken, date),
    ]);
    setResources(loadedResources);
    setBookings(loadedBookings);
    setResourceId((current) => current || loadedResources[0]?.id || "");
  }

  async function submitBooking(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !activeResource || !clientId || !startTime || !computedEnd) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const startsAt = new Date(`${startDate}T${startTime}`).toISOString();
      const endsAt = new Date(computedEnd).toISOString();
      await createBooking(session.accessToken, {
        resourceId,
        clientId,
        startsAt,
        endsAt,
        notes,
      });
      setCalendarDate(startDate);
      await refresh(session, startDate);
      setMessage("Reserva creada.");
      setNotes("");
      setStartTime("");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear reserva.");
    } finally {
      setSaving(false);
    }
  }

  async function submitResource(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const created = await createResource(session.accessToken, {
        name: resourceName,
        resourceType,
        capacity: resourceCapacity,
        slotMinutes: resourceDuration,
        opensAt: resourceOpensAt,
        closesAt: resourceClosesAt,
        allowedPlanIds: resourceAllowedPlanIds,
      });
      await refresh();
      setResourceId(created.id);
      setResourceName("");
      setResourceCapacity(1);
      setResourceDuration(60);
      setResourceAllowedPlanIds([]);
      setMessage("Recurso creado.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear recurso.");
    } finally {
      setSaving(false);
    }
  }

  async function runAction(bookingId: string, action: "confirm" | "check-in" | "complete" | "cancel") {
    if (!session) {
      return;
    }
    setError(null);
    setMessage(null);
    try {
      await transitionBooking(session.accessToken, bookingId, action);
      await refresh();
      setMessage("Reserva actualizada.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible actualizar reserva.");
    }
  }

  if (loading) {
    return <LoadingScreen text="Cargando reservas..." />;
  }

  if (!session) {
    return (
      <AuthRequiredCard
        message="Necesitas iniciar sesion para administrar reservas."
        title="Inicia sesion"
      />
    );
  }

  return (
    <AppShell
      branchName={activeBranch?.name}
      eyebrow="Operacion"
      mode={activeBranch ? "operation" : undefined}
      organizationName={organization?.brandName || organization?.tradeName || session.user.brandName || session.user.organizationName}
      session={session}
      subtitle="Agenda visual por recurso y reservaciones incluidas en membresias."
      title="Recursos y reservas"
    >
      <section className="grid gap-6 xl:grid-cols-[420px_minmax(0,1fr)]">
        <aside className="grid content-start gap-5">
          {error ? <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f]">{error}</p> : null}
          {message ? <p className="rounded-md border border-[#b8d9c3] bg-[#f0fbf3] px-3 py-2 text-sm text-[#2d6843]">{message}</p> : null}

          <form className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" onSubmit={submitBooking}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="text-xl font-semibold">Nueva reserva</h2>
                <p className="mt-1 text-sm text-[#5d6b63]">El fin se calcula con la duracion del recurso.</p>
              </div>
              {activeResource ? (
                <span className="rounded-md bg-[var(--brand-surface)] px-3 py-2 text-xs font-semibold text-[var(--brand-primary)]">
                  {activeResource.slotMinutes} min
                </span>
              ) : null}
            </div>

            <label className="mt-4 grid gap-2 text-sm font-medium">
              Recurso
              <select className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setResourceId(event.target.value)} value={resourceId}>
                {resources.map((resource) => (
                  <option key={resource.id} value={resource.id}>{resource.name}</option>
                ))}
              </select>
            </label>

            {activeResource?.allowedPlanNames ? (
              <p className="mt-3 rounded-md border border-[#d8ddd2] bg-[#f8faf8] px-3 py-2 text-xs text-[#5d6b63]">
                Reservable por: <strong>{activeResource.allowedPlanNames}</strong>
              </p>
            ) : (
              <p className="mt-3 rounded-md border border-[#d8ddd2] bg-[#f8faf8] px-3 py-2 text-xs text-[#5d6b63]">
                Reservable por cualquier socio activo.
              </p>
            )}

            <label className="mt-4 grid gap-2 text-sm font-medium">
              Buscar socio
              <input
                className="h-11 rounded-md border border-[#cdd5ca] px-3"
                onChange={(event) => {
                  setClientQuery(event.target.value);
                  setClientId("");
                }}
                placeholder="Nombre, telefono o email"
                value={clientQuery}
              />
            </label>
            <div className="mt-2 grid gap-2">
              {visibleClientResults.map((client) => (
                <button
                  className={`rounded-md border px-3 py-2 text-left text-sm ${client.id === clientId ? "border-[var(--brand-primary)] bg-[var(--brand-surface)]" : "border-[#d8ddd2] bg-white"}`}
                  key={client.id}
                  onClick={() => {
                    setClientId(client.id);
                    setClientQuery(`${client.firstName} ${client.lastName}`);
                  }}
                  type="button"
                >
                  <strong className="block">{client.firstName} {client.lastName}</strong>
                  <span className="text-xs text-[#647067]">{client.phone || client.email || "Socio activo"}</span>
                </button>
              ))}
              {clientQuery.trim().length >= 2 && visibleClientResults.length === 0 && !clientId ? (
                <p className="rounded-md border border-[#d8ddd2] bg-[#f8faf8] px-3 py-2 text-xs text-[#647067]">Sin coincidencias.</p>
              ) : null}
              {selectedClient ? (
                <p className="text-xs font-semibold text-[var(--brand-primary)]">Seleccionado: {selectedClient.firstName} {selectedClient.lastName}</p>
              ) : null}
            </div>

            <div className="mt-4 grid gap-3 sm:grid-cols-[1fr_0.8fr]">
              <label className="grid gap-2 text-sm font-medium">
                Dia de inicio
                <input className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setStartDate(event.target.value)} required type="date" value={startDate} />
              </label>
              <label className="grid gap-2 text-sm font-medium">
                Hora
                <input className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setStartTime(event.target.value)} required type="time" value={startTime} />
              </label>
            </div>
            <div className="mt-3 rounded-md border border-[#d8ddd2] bg-[#f8faf8] px-3 py-2 text-sm">
              <span className="block text-xs font-semibold uppercase text-[#647067]">Fin calculado</span>
              <strong>{computedEnd ? formatDateTime(computedEnd) : "Selecciona recurso, dia y hora"}</strong>
            </div>
            <label className="mt-4 grid gap-2 text-sm font-medium">
              Notas
              <textarea className="min-h-20 rounded-md border border-[#cdd5ca] px-3 py-2" onChange={(event) => setNotes(event.target.value)} value={notes} />
            </label>
            <button className="mt-5 h-11 w-full rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white disabled:opacity-60" disabled={saving || !resourceId || !clientId || !startTime} type="submit">
              Crear reserva
            </button>
          </form>

          {canManageResources ? (
            <form className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" onSubmit={submitResource}>
              <h2 className="text-xl font-semibold">Crear recurso</h2>
              <input className="mt-4 h-11 w-full rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setResourceName(event.target.value)} placeholder="Nombre del recurso" required value={resourceName} />
              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                <label className="grid gap-2 text-sm font-medium">
                  Tipo
                  <select className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setResourceType(event.target.value)} value={resourceType}>
                    <option value="ROOM">Sala / area</option>
                    <option value="CLASS_AREA">Area de clase</option>
                    <option value="EQUIPMENT">Equipo</option>
                  </select>
                </label>
                <label className="grid gap-2 text-sm font-medium">
                  Capacidad
                  <input className="h-11 rounded-md border border-[#cdd5ca] px-3" min={1} onChange={(event) => setResourceCapacity(Number(event.target.value))} type="number" value={resourceCapacity} />
                </label>
                <label className="grid gap-2 text-sm font-medium">
                  Duracion
                  <select className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setResourceDuration(Number(event.target.value))} value={resourceDuration}>
                    <option value={30}>30 minutos</option>
                    <option value={45}>45 minutos</option>
                    <option value={60}>60 minutos</option>
                    <option value={90}>90 minutos</option>
                    <option value={120}>120 minutos</option>
                  </select>
                </label>
                <div className="grid grid-cols-2 gap-2">
                  <label className="grid gap-2 text-sm font-medium">
                    Abre
                    <input className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setResourceOpensAt(event.target.value)} type="time" value={resourceOpensAt} />
                  </label>
                  <label className="grid gap-2 text-sm font-medium">
                    Cierra
                    <input className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setResourceClosesAt(event.target.value)} type="time" value={resourceClosesAt} />
                  </label>
                </div>
              </div>
              <fieldset className="mt-4 grid gap-2">
                <legend className="text-sm font-semibold">Membresias que pueden reservar</legend>
                <p className="text-xs text-[#6f7a73]">Sin seleccionar planes, el recurso queda abierto para cualquier socio activo.</p>
                <div className="grid gap-2">
                  {plans.map((plan) => (
                    <label className="flex items-start gap-2 rounded-md border border-[#d8ddd2] px-3 py-2 text-sm" key={plan.id}>
                      <input
                        className="mt-1 h-4 w-4 accent-[var(--brand-primary)]"
                        checked={resourceAllowedPlanIds.includes(plan.id)}
                        onChange={(event) => {
                          setResourceAllowedPlanIds((current) => (
                            event.target.checked
                              ? [...current, plan.id]
                              : current.filter((planId) => planId !== plan.id)
                          ));
                        }}
                        type="checkbox"
                      />
                      <span>
                        <strong className="block">{plan.name}</strong>
                        <span className="text-xs text-[#6f7a73]">{plan.multiBranchAccess ? "Multi-sucursal" : "Sucursal de venta"}</span>
                      </span>
                    </label>
                  ))}
                </div>
              </fieldset>
              <button className="mt-4 h-11 w-full rounded-md border border-[#51735f] px-4 text-sm font-semibold text-[#13251c]" disabled={saving} type="submit">
                Crear recurso
              </button>
            </form>
          ) : null}
        </aside>

        <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <h2 className="text-xl font-semibold">Agenda por recurso</h2>
              <p className="mt-1 text-sm text-[#5d6b63]">Reservas del dia, separadas por recurso y color.</p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <button className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm font-semibold" onClick={() => moveCalendarDate(-1)} type="button">Anterior</button>
              <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setCalendarDate(event.target.value)} type="date" value={calendarDate} />
              <button className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm font-semibold" onClick={() => setCalendarDate(new Date().toISOString().slice(0, 10))} type="button">Hoy</button>
              <button className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm font-semibold" onClick={() => moveCalendarDate(1)} type="button">Siguiente</button>
            </div>
          </div>

          <div className="mt-5 flex flex-wrap gap-2">
            {resources.map((resource, index) => (
              <button
                className={`inline-flex items-center gap-2 rounded-md border px-3 py-2 text-sm ${resource.id === resourceId ? "border-[var(--brand-primary)] bg-[var(--brand-surface)]" : "border-[#d8ddd2]"}`}
                key={resource.id}
                onClick={() => setResourceId(resource.id)}
                type="button"
              >
                <span className="h-3 w-3 rounded-full" style={{ backgroundColor: resourceColor(index) }} />
                {resource.name}
              </button>
            ))}
          </div>

          {resources.length === 0 ? (
            <p className="mt-6 rounded-md border border-[#d8ddd2] bg-[#f8faf8] px-3 py-6 text-center text-sm text-[#5d6b63]">
              No hay recursos reservables en esta sucursal.
            </p>
          ) : (
            <div className="mt-5 overflow-x-auto">
              <div className="min-w-[760px] rounded-lg border border-[#d8ddd2]">
                <div className="grid" style={{ gridTemplateColumns: `82px repeat(${resources.length}, minmax(150px, 1fr))` }}>
                  <div className="border-b border-r border-[#d8ddd2] bg-[#f8faf8] px-3 py-3 text-xs font-semibold uppercase text-[#647067]">Hora</div>
                  {resources.map((resource, index) => (
                    <div className="border-b border-r border-[#d8ddd2] bg-[#f8faf8] px-3 py-3 text-sm font-semibold last:border-r-0" key={resource.id}>
                      <span className="mr-2 inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: resourceColor(index) }} />
                      {resource.name}
                    </div>
                  ))}
                  {calendarHours.map((hour) => (
                    <TimeRow
                      bookings={bookings}
                      hour={hour}
                      key={hour}
                      resources={resources}
                      selectedDate={calendarDate}
                      onPickSlot={(pickedResourceId, pickedDateTime) => {
                        setResourceId(pickedResourceId);
                        setStartDate(pickedDateTime.slice(0, 10));
                        setStartTime(pickedDateTime.slice(11, 16));
                      }}
                      onRunAction={runAction}
                    />
                  ))}
                </div>
              </div>
            </div>
          )}
        </section>
      </section>
    </AppShell>
  );

  function moveCalendarDate(days: number) {
    const date = new Date(`${calendarDate}T00:00`);
    date.setDate(date.getDate() + days);
    const value = toDateInput(date);
    setCalendarDate(value);
    setStartDate(value);
  }
}

function TimeRow({
  bookings,
  hour,
  resources,
  selectedDate,
  onPickSlot,
  onRunAction,
}: {
  bookings: BookingSummary[];
  hour: string;
  resources: ResourceSummary[];
  selectedDate: string;
  onPickSlot: (resourceId: string, startsAt: string) => void;
  onRunAction: (bookingId: string, action: "confirm" | "check-in" | "complete" | "cancel") => void;
}) {
  return (
    <>
      <div className="min-h-24 border-r border-t border-[#d8ddd2] bg-[#fbfcfa] px-3 py-3 text-xs font-semibold text-[#647067]">
        {formatHourLabel(hour)}
      </div>
      {resources.map((resource, index) => {
        const hourBookings = bookings.filter((booking) => booking.resourceId === resource.id && startsInHour(booking.startsAt, hour));
        const disabled = !isWithinResourceHours(resource, hour);
        return (
          <div className={`min-h-24 border-r border-t border-[#d8ddd2] p-2 last:border-r-0 ${disabled ? "bg-[#f6f3ef]" : "bg-white"}`} key={`${hour}-${resource.id}`}>
            {hourBookings.length === 0 ? (
              <button
                className="h-full min-h-16 w-full rounded-md border border-dashed border-[#d8ddd2] text-xs font-semibold text-[#8a948d] transition hover:border-[var(--brand-primary)] hover:text-[var(--brand-primary)] disabled:cursor-not-allowed disabled:opacity-40"
                disabled={disabled}
                onClick={() => onPickSlot(resource.id, `${selectedDate}T${hour}`)}
                type="button"
              >
                Disponible
              </button>
            ) : (
              <div className="grid gap-2">
                {hourBookings.map((booking) => (
                  <article className="rounded-md px-3 py-2 text-white shadow-sm" key={booking.id} style={{ backgroundColor: resourceColor(index) }}>
                    <p className="text-xs font-semibold uppercase opacity-80">{booking.status}</p>
                    <p className="mt-1 text-sm font-semibold">{booking.clientName}</p>
                    <p className="text-xs opacity-90">{formatTime(booking.startsAt)} - {formatTime(booking.endsAt)}</p>
                    <div className="mt-2 flex flex-wrap gap-1">
                      {bookingActions.map((action) => (
                        <ActionButton action={action} booking={booking} key={action} onRun={onRunAction} />
                      ))}
                    </div>
                  </article>
                ))}
              </div>
            )}
          </div>
        );
      })}
    </>
  );
}

function ActionButton({ action, booking, onRun }: { action: "confirm" | "check-in" | "complete" | "cancel"; booking: BookingSummary; onRun: (bookingId: string, action: "confirm" | "check-in" | "complete" | "cancel") => void }) {
  const labels = { confirm: "Confirmar", "check-in": "Check-in", complete: "Completar", cancel: "Cancelar" };
  const disabled = booking.status === "COMPLETED" || booking.status === "CANCELLED";
  return (
    <button className="h-7 rounded border border-white/40 px-2 text-[11px] font-semibold text-white disabled:opacity-50" disabled={disabled} onClick={() => onRun(booking.id, action)} type="button">
      {labels[action]}
    </button>
  );
}

function buildCalendarHours(resources: ResourceSummary[]) {
  if (resources.length === 0) {
    return [];
  }
  const first = Math.min(...resources.map((resource) => timeToMinutes(resource.opensAt || "06:00")));
  const last = Math.max(...resources.map((resource) => timeToMinutes(resource.closesAt || "22:00")));
  const hours: string[] = [];
  for (let cursor = roundDownToHour(first); cursor < last; cursor += 60) {
    hours.push(minutesToTime(cursor));
  }
  return hours;
}

function startsInHour(value: string, hour: string) {
  const starts = new Date(value);
  const row = timeToMinutes(hour);
  const minutes = starts.getHours() * 60 + starts.getMinutes();
  return minutes >= row && minutes < row + 60;
}

function isWithinResourceHours(resource: ResourceSummary, hour: string) {
  const row = timeToMinutes(hour);
  const opens = timeToMinutes(resource.opensAt || "00:00");
  const closes = timeToMinutes(resource.closesAt || "23:59");
  return row >= opens && row < closes;
}

function addMinutes(localDateTime: string, minutes: number) {
  const date = new Date(localDateTime);
  date.setMinutes(date.getMinutes() + minutes);
  return toLocalDateTimeInput(date);
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", { hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

function formatHourLabel(value: string) {
  return new Intl.DateTimeFormat("es-MX", { hour: "2-digit", minute: "2-digit" }).format(new Date(`2026-01-01T${value}`));
}

function resourceColor(index: number) {
  return resourceColors[index % resourceColors.length];
}

function roundDownToHour(minutes: number) {
  return Math.floor(minutes / 60) * 60;
}

function timeToMinutes(value: string) {
  const [hours, minutes] = value.split(":").map(Number);
  return hours * 60 + minutes;
}

function minutesToTime(value: number) {
  const hours = Math.floor(value / 60).toString().padStart(2, "0");
  const minutes = (value % 60).toString().padStart(2, "0");
  return `${hours}:${minutes}`;
}

function toDateInput(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function toLocalDateTimeInput(date: Date) {
  return `${toDateInput(date)}T${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}
