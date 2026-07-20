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

type WeekdayKey = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";

type ResourceDay = {
  key: WeekdayKey;
  label: string;
  shortLabel: string;
  enabled: boolean;
  opensAt: string;
  closesAt: string;
};

type StoredSchedule = {
  schedule?: Array<{
    day: WeekdayKey;
    opensAt: string;
    closesAt: string;
  }>;
  description?: string | null;
};

const resourceColors = ["#166534", "#0f766e", "#1d4ed8", "#7c3aed", "#be123c", "#b45309"];
const bookingActions: Array<"confirm" | "check-in" | "complete" | "cancel"> = ["confirm", "check-in", "complete", "cancel"];
const weekdays: ResourceDay[] = [
  { key: "MONDAY", label: "Lunes", shortLabel: "L", enabled: true, opensAt: "06:00", closesAt: "22:00" },
  { key: "TUESDAY", label: "Martes", shortLabel: "M", enabled: true, opensAt: "06:00", closesAt: "22:00" },
  { key: "WEDNESDAY", label: "Miercoles", shortLabel: "M", enabled: true, opensAt: "06:00", closesAt: "22:00" },
  { key: "THURSDAY", label: "Jueves", shortLabel: "J", enabled: true, opensAt: "06:00", closesAt: "22:00" },
  { key: "FRIDAY", label: "Viernes", shortLabel: "V", enabled: true, opensAt: "06:00", closesAt: "22:00" },
  { key: "SATURDAY", label: "Sabado", shortLabel: "S", enabled: false, opensAt: "08:00", closesAt: "14:00" },
  { key: "SUNDAY", label: "Domingo", shortLabel: "D", enabled: false, opensAt: "08:00", closesAt: "14:00" },
];

export default function BookingsPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organization, setOrganization] = useState<OrganizationSummary | null>(null);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [resources, setResources] = useState<ResourceSummary[]>([]);
  const [bookings, setBookings] = useState<BookingSummary[]>([]);
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [clientResults, setClientResults] = useState<ClientSummary[]>([]);
  const [clientQuery, setClientQuery] = useState("");
  const [calendarDate, setCalendarDate] = useState(() => toDateInput(new Date()));
  const [resourceId, setResourceId] = useState("");
  const [clientId, setClientId] = useState("");
  const [startDate, setStartDate] = useState(() => toDateInput(new Date()));
  const [startTime, setStartTime] = useState("");
  const [notes, setNotes] = useState("");
  const [resourceName, setResourceName] = useState("");
  const [durationValue, setDurationValue] = useState(60);
  const [durationUnit, setDurationUnit] = useState<"MINUTES" | "HOURS">("MINUTES");
  const [resourceSchedule, setResourceSchedule] = useState<ResourceDay[]>(() => weekdays.map((day) => ({ ...day })));
  const [resourceAllowedPlanIds, setResourceAllowedPlanIds] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const weekDates = useMemo(() => buildWeekDates(calendarDate), [calendarDate]);

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
          getWeekBookings(stored.accessToken, weekDates),
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
  }, [weekDates]);

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
  const selectedClient = clientResults.find((client) => client.id === clientId) ?? null;
  const isReceptionUser = session?.user.console === "RECEPTION"
    || session?.user.roles.some((role) => role.toLowerCase().includes("recepcion"));
  const canManageResources = Boolean(session?.user.permissions.includes("resource.manage") && !isReceptionUser);
  const durationMinutes = Math.max(1, durationUnit === "HOURS" ? durationValue * 60 : durationValue);
  const computedEnd = activeResource && startDate && startTime
    ? addMinutes(`${startDate}T${startTime}`, activeResource.slotMinutes)
    : "";

  async function refresh(stored = session, dates = weekDates) {
    if (!stored) {
      return;
    }
    const [loadedResources, loadedBookings] = await Promise.all([
      getResources(stored.accessToken),
      getWeekBookings(stored.accessToken, dates),
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
      const nextWeek = buildWeekDates(startDate);
      setCalendarDate(startDate);
      await refresh(session, nextWeek);
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
    const enabledDays = resourceSchedule.filter((day) => day.enabled);
    if (enabledDays.length === 0) {
      setError("Selecciona al menos un dia operativo para el recurso.");
      return;
    }
    if (enabledDays.some((day) => timeToMinutes(day.opensAt) >= timeToMinutes(day.closesAt))) {
      setError("Cada dia operativo debe cerrar despues de abrir.");
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const opensAt = minutesToTime(Math.min(...enabledDays.map((day) => timeToMinutes(day.opensAt))));
      const closesAt = minutesToTime(Math.max(...enabledDays.map((day) => timeToMinutes(day.closesAt))));
      const created = await createResource(session.accessToken, {
        name: resourceName,
        resourceType: "ROOM",
        capacity: 1,
        slotMinutes: durationMinutes,
        opensAt,
        closesAt,
        notes: JSON.stringify({
          schedule: enabledDays.map((day) => ({ day: day.key, opensAt: day.opensAt, closesAt: day.closesAt })),
          description: null,
        } satisfies StoredSchedule),
        allowedPlanIds: resourceAllowedPlanIds,
      });
      await refresh();
      setResourceId(created.id);
      setResourceName("");
      setDurationValue(60);
      setDurationUnit("MINUTES");
      setResourceSchedule(weekdays.map((day) => ({ ...day })));
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
      subtitle="Agenda semanal por recurso y reservaciones incluidas en membresias."
      title="Recursos y reservas"
    >
      <section className="grid gap-6 xl:grid-cols-[410px_minmax(0,1fr)]">
        <aside className="grid content-start gap-5">
          {error ? <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f]">{error}</p> : null}
          {message ? <p className="rounded-md border border-[#b8d9c3] bg-[#f0fbf3] px-3 py-2 text-sm text-[#2d6843]">{message}</p> : null}

          <form className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" onSubmit={submitBooking}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="text-xl font-semibold">Nueva reserva</h2>
                <p className="mt-1 text-sm text-[#5d6b63]">Busca al socio, elige recurso y captura solo el inicio.</p>
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
                  if (event.target.value.trim().length < 2) {
                    setClientResults([]);
                  }
                }}
                placeholder="Nombre, telefono o email"
                value={clientQuery}
              />
            </label>
            <div className="mt-2 grid gap-2">
              {clientQuery.trim().length >= 2 && clientResults.map((client) => (
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
              {clientQuery.trim().length >= 2 && clientResults.length === 0 && !clientId ? (
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
            <button className="mt-5 h-11 w-full rounded-md bg-[var(--brand-primary)] px-4 text-sm font-semibold text-white shadow-sm disabled:opacity-60" disabled={saving || !resourceId || !clientId || !startTime} type="submit">
              Crear reserva
            </button>
          </form>

          {canManageResources ? (
            <form className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" onSubmit={submitResource}>
              <h2 className="text-xl font-semibold">Crear recurso</h2>
              <input className="mt-4 h-11 w-full rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setResourceName(event.target.value)} placeholder="Nombre del recurso" required value={resourceName} />

              <div className="mt-4 grid gap-3 sm:grid-cols-[1fr_130px]">
                <label className="grid gap-2 text-sm font-medium">
                  Duracion
                  <input
                    className="h-11 rounded-md border border-[#cdd5ca] px-3"
                    min={1}
                    onChange={(event) => setDurationValue(Number(event.target.value))}
                    type="number"
                    value={durationValue}
                  />
                </label>
                <label className="grid gap-2 text-sm font-medium">
                  Unidad
                  <select className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setDurationUnit(event.target.value as "MINUTES" | "HOURS")} value={durationUnit}>
                    <option value="MINUTES">Minutos</option>
                    <option value="HOURS">Horas</option>
                  </select>
                </label>
              </div>

              <fieldset className="mt-5 grid gap-2">
                <legend className="text-sm font-semibold">Dias y horarios operativos</legend>
                <div className="grid gap-2">
                  {resourceSchedule.map((day) => (
                    <div className="grid grid-cols-[36px_1fr_92px_92px] items-center gap-2 rounded-md border border-[#d8ddd2] px-3 py-2 text-sm" key={day.key}>
                      <label className="flex h-8 w-8 items-center justify-center rounded-md border border-[#cdd5ca]">
                        <input
                          aria-label={day.label}
                          checked={day.enabled}
                          className="h-4 w-4 accent-[var(--brand-primary)]"
                          onChange={(event) => updateResourceDay(day.key, { enabled: event.target.checked })}
                          type="checkbox"
                        />
                      </label>
                      <span>
                        <strong className="block">{day.shortLabel}</strong>
                        <span className="text-xs text-[#647067]">{day.label}</span>
                      </span>
                      <input className="h-10 rounded-md border border-[#cdd5ca] px-2" disabled={!day.enabled} onChange={(event) => updateResourceDay(day.key, { opensAt: event.target.value })} type="time" value={day.opensAt} />
                      <input className="h-10 rounded-md border border-[#cdd5ca] px-2" disabled={!day.enabled} onChange={(event) => updateResourceDay(day.key, { closesAt: event.target.value })} type="time" value={day.closesAt} />
                    </div>
                  ))}
                </div>
              </fieldset>

              <fieldset className="mt-5 grid gap-2">
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
              <button className="mt-5 h-11 w-full rounded-md border border-[var(--brand-primary)] bg-[var(--brand-primary)] px-4 text-sm font-semibold text-white shadow-sm disabled:opacity-60" disabled={saving} type="submit">
                Crear recurso
              </button>
            </form>
          ) : null}
        </aside>

        <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <h2 className="text-xl font-semibold">Agenda semanal</h2>
              <p className="mt-1 text-sm text-[#5d6b63]">Vista compacta por dia y recurso.</p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <button className="h-10 rounded-md border border-[var(--brand-primary)] px-3 text-sm font-semibold text-[var(--brand-primary)]" onClick={() => moveCalendarWeek(-1)} type="button">Anterior</button>
              <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setCalendarDate(event.target.value)} type="date" value={calendarDate} />
              <button className="h-10 rounded-md border border-[var(--brand-primary)] px-3 text-sm font-semibold text-[var(--brand-primary)]" onClick={() => setCalendarDate(toDateInput(new Date()))} type="button">Hoy</button>
              <button className="h-10 rounded-md border border-[var(--brand-primary)] px-3 text-sm font-semibold text-[var(--brand-primary)]" onClick={() => moveCalendarWeek(1)} type="button">Siguiente</button>
            </div>
          </div>

          <div className="mt-4 rounded-md border border-[#d8ddd2] bg-[#f8faf8] px-3 py-2 text-sm font-semibold text-[#34443a]">
            Semana del {formatShortDate(weekDates[0].date)} al {formatShortDate(weekDates[6].date)}
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
            <div className="mt-5 grid gap-3 lg:grid-cols-7">
              {weekDates.map((day) => (
                <section className="min-h-[260px] rounded-lg border border-[#d8ddd2] bg-[#fbfcfa] p-3" key={day.date}>
                  <button
                    className={`w-full rounded-md border px-2 py-2 text-left ${startDate === day.date ? "border-[var(--brand-primary)] bg-[var(--brand-surface)]" : "border-transparent"}`}
                    onClick={() => setStartDate(day.date)}
                    type="button"
                  >
                    <span className="block text-xs font-semibold uppercase text-[#647067]">{day.label}</span>
                    <strong className="text-lg">{day.dayOfMonth}</strong>
                  </button>
                  <div className="mt-3 grid gap-3">
                    {resources.map((resource, index) => {
                      const dayBookings = bookings
                        .filter((booking) => booking.resourceId === resource.id && bookingDate(booking.startsAt) === day.date)
                        .sort((first, second) => first.startsAt.localeCompare(second.startsAt));
                      const hours = resourceHoursForDay(resource, day.weekday);
                      return (
                        <article className="rounded-md border border-[#d8ddd2] bg-white p-2" key={`${day.date}-${resource.id}`}>
                          <div className="flex items-center justify-between gap-2">
                            <div className="min-w-0">
                              <p className="truncate text-sm font-semibold">
                                <span className="mr-2 inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: resourceColor(index) }} />
                                {resource.name}
                              </p>
                              <p className="mt-0.5 text-xs text-[#647067]">{hours ? `${hours.opensAt} - ${hours.closesAt}` : "Cerrado"}</p>
                            </div>
                            {hours ? (
                              <button
                                className="rounded-md border border-[var(--brand-primary)] px-2 py-1 text-xs font-semibold text-[var(--brand-primary)]"
                                onClick={() => {
                                  setResourceId(resource.id);
                                  setStartDate(day.date);
                                  setStartTime(hours.opensAt);
                                }}
                                type="button"
                              >
                                Reservar
                              </button>
                            ) : null}
                          </div>
                          <div className="mt-2 grid gap-2">
                            {dayBookings.length === 0 ? (
                              <p className="rounded-md bg-[#f8faf8] px-2 py-2 text-xs text-[#647067]">Sin reservas</p>
                            ) : (
                              dayBookings.map((booking) => (
                                <BookingCard booking={booking} color={resourceColor(index)} key={booking.id} onRunAction={runAction} />
                              ))
                            )}
                          </div>
                        </article>
                      );
                    })}
                  </div>
                </section>
              ))}
            </div>
          )}
        </section>
      </section>
    </AppShell>
  );

  function updateResourceDay(key: WeekdayKey, patch: Partial<ResourceDay>) {
    setResourceSchedule((current) => current.map((day) => day.key === key ? { ...day, ...patch } : day));
  }

  function moveCalendarWeek(weeks: number) {
    const date = parseDateInput(calendarDate);
    date.setDate(date.getDate() + weeks * 7);
    const value = toDateInput(date);
    setCalendarDate(value);
    setStartDate(value);
  }
}

function BookingCard({
  booking,
  color,
  onRunAction,
}: {
  booking: BookingSummary;
  color: string;
  onRunAction: (bookingId: string, action: "confirm" | "check-in" | "complete" | "cancel") => void;
}) {
  return (
    <article className="rounded-md px-3 py-2 text-white shadow-sm" style={{ backgroundColor: color }}>
      <p className="text-[11px] font-semibold uppercase opacity-80">{booking.status}</p>
      <p className="mt-1 truncate text-sm font-semibold">{booking.clientName}</p>
      <p className="text-xs opacity-90">{formatTime(booking.startsAt)} - {formatTime(booking.endsAt)}</p>
      <div className="mt-2 flex flex-wrap gap-1">
        {bookingActions.map((action) => (
          <ActionButton action={action} booking={booking} key={action} onRun={onRunAction} />
        ))}
      </div>
    </article>
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

async function getWeekBookings(accessToken: string, dates: Array<{ date: string }>) {
  const lists = await Promise.all(dates.map((day) => getBookings(accessToken, day.date)));
  return lists.flat();
}

function buildWeekDates(anchor: string) {
  const start = parseDateInput(anchor);
  const mondayOffset = (start.getDay() + 6) % 7;
  start.setDate(start.getDate() - mondayOffset);
  return weekdays.map((weekday, index) => {
    const date = new Date(start);
    date.setDate(start.getDate() + index);
    return {
      date: toDateInput(date),
      dayOfMonth: date.getDate(),
      label: weekday.label,
      weekday: weekday.key,
    };
  });
}

function resourceHoursForDay(resource: ResourceSummary, weekday: WeekdayKey) {
  const schedule = parseResourceSchedule(resource);
  const scheduleDay = schedule?.find((day) => day.day === weekday);
  if (scheduleDay) {
    return { opensAt: scheduleDay.opensAt, closesAt: scheduleDay.closesAt };
  }
  if (schedule) {
    return null;
  }
  return { opensAt: resource.opensAt || "06:00", closesAt: resource.closesAt || "22:00" };
}

function parseResourceSchedule(resource: ResourceSummary) {
  if (!resource.notes) {
    return null;
  }
  try {
    const parsed = JSON.parse(resource.notes) as StoredSchedule;
    if (!Array.isArray(parsed.schedule)) {
      return null;
    }
    return parsed.schedule.filter((day) => day.day && day.opensAt && day.closesAt);
  } catch {
    return null;
  }
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

function formatShortDate(value: string) {
  return new Intl.DateTimeFormat("es-MX", { day: "2-digit", month: "short" }).format(parseDateInput(value));
}

function bookingDate(value: string) {
  return toDateInput(new Date(value));
}

function resourceColor(index: number) {
  return resourceColors[index % resourceColors.length];
}

function parseDateInput(value: string) {
  return new Date(`${value}T00:00:00`);
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
