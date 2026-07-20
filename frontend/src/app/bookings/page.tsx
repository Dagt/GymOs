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
type Notice = { title: string; message: string; tone: "error" | "success" };

type ResourceDay = {
  key: WeekdayKey;
  label: string;
  shortLabel: string;
  enabled: boolean;
  opensAt: string;
  closesAt: string;
};

type StoredSchedule = {
  schedule?: Array<{ day: WeekdayKey; opensAt: string; closesAt: string }>;
  description?: string | null;
};

const resourceColors = ["#166534", "#0f766e", "#1d4ed8", "#7c3aed", "#be123c", "#b45309"];
const bookingActions: Array<"confirm" | "check-in" | "complete" | "cancel"> = ["confirm", "check-in", "complete", "cancel"];
const weekdays: ResourceDay[] = [
  { key: "MONDAY", label: "Lunes", shortLabel: "L", enabled: true, opensAt: "06:00", closesAt: "22:00" },
  { key: "TUESDAY", label: "Martes", shortLabel: "M", enabled: true, opensAt: "06:00", closesAt: "22:00" },
  { key: "WEDNESDAY", label: "Miércoles", shortLabel: "M", enabled: true, opensAt: "06:00", closesAt: "22:00" },
  { key: "THURSDAY", label: "Jueves", shortLabel: "J", enabled: true, opensAt: "06:00", closesAt: "22:00" },
  { key: "FRIDAY", label: "Viernes", shortLabel: "V", enabled: true, opensAt: "06:00", closesAt: "22:00" },
  { key: "SATURDAY", label: "Sábado", shortLabel: "S", enabled: false, opensAt: "08:00", closesAt: "14:00" },
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
  const [notice, setNotice] = useState<Notice | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const today = toDateInput(new Date());
  const activeBookings = useMemo(() => bookings.filter((booking) => booking.status !== "CANCELLED"), [bookings]);
  const monthDates = useMemo(() => buildMonthDates(calendarDate), [calendarDate]);
  const selectedDateBookings = useMemo(
    () => activeBookings
      .filter((booking) => bookingDate(booking.startsAt) === startDate)
      .sort((first, second) => first.startsAt.localeCompare(second.startsAt)),
    [activeBookings, startDate],
  );

  useEffect(() => {
    let active = true;
    async function load() {
      const stored = getStoredSession();
      if (!active) return;
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
          getCalendarBookings(stored.accessToken, monthDates),
          getPlans(stored.accessToken),
        ]);
        if (!active) return;
        setOrganization(loadedOrganization);
        setBranches(loadedBranches);
        setResources(loadedResources);
        setBookings(loadedBookings);
        setPlans(loadedPlans);
        setResourceId((current) => current || loadedResources[0]?.id || "");
      } catch (exception) {
        if (active) showError(exception, "No fue posible cargar las reservas.");
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => { active = false; };
  }, [monthDates]);

  useEffect(() => {
    if (!session) return;
    const query = clientQuery.trim();
    if (query.length < 2) return;
    let active = true;
    const timer = window.setTimeout(() => {
      searchClients(session.accessToken, query, "ACTIVE")
        .then((results) => { if (active) setClientResults(results.slice(0, 8)); })
        .catch(() => { if (active) setClientResults([]); });
    }, 250);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [clientQuery, session]);

  useEffect(() => {
    if (!notice) return;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setNotice(null);
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [notice]);

  const activeResource = useMemo(() => resources.find((resource) => resource.id === resourceId) ?? null, [resources, resourceId]);
  const activeBranch = branches.find((branch) => branch.id === session?.user.branchId) ?? null;
  const selectedClient = clientResults.find((client) => client.id === clientId) ?? null;
  const isReceptionUser = session?.user.console === "RECEPTION" || session?.user.roles.some((role) => role.toLowerCase().includes("recepcion"));
  const canManageResources = Boolean(session?.user.permissions.includes("resource.manage") && !isReceptionUser);
  const durationMinutes = Math.max(1, durationUnit === "HOURS" ? durationValue * 60 : durationValue);
  const selectedDateIsPast = isPastDate(startDate);
  const selectedStartIsPast = isPastDateTime(startDate, startTime);
  const computedEnd = activeResource && startDate && isValidTimeInput(startTime)
    ? addMinutes(`${startDate}T${startTime}`, activeResource.slotMinutes)
    : "";

  async function refresh(stored = session, dates = monthDates) {
    if (!stored) return;
    const [loadedResources, loadedBookings] = await Promise.all([
      getResources(stored.accessToken),
      getCalendarBookings(stored.accessToken, dates),
    ]);
    setResources(loadedResources);
    setBookings(loadedBookings);
    setResourceId((current) => current || loadedResources[0]?.id || "");
  }

  function showError(exception: unknown, fallback: string) {
    const raw = exception instanceof Error ? exception.message : fallback;
    setNotice({ title: "No se pudo completar la reserva", message: friendlyBookingError(raw, fallback), tone: "error" });
  }

  async function submitBooking(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !activeResource || selectedDateIsPast || !clientId || !isValidTimeInput(startTime) || !computedEnd) return;
    if (selectedStartIsPast) {
      setNotice({ title: "Horario no disponible", message: "Selecciona una hora posterior a la hora actual.", tone: "error" });
      return;
    }
    setSaving(true);
    try {
      const startsAt = new Date(`${startDate}T${startTime}`).toISOString();
      const endsAt = new Date(computedEnd).toISOString();
      await createBooking(session.accessToken, { resourceId, clientId, startsAt, endsAt, notes });
      const nextMonth = buildMonthDates(startDate);
      setCalendarDate(startDate);
      await refresh(session, nextMonth);
      setNotice({ title: "Reserva creada", message: "La reserva se registró correctamente.", tone: "success" });
      setNotes("");
      setStartTime("");
    } catch (exception) {
      showError(exception, "No fue posible crear la reserva.");
    } finally {
      setSaving(false);
    }
  }

  async function submitResource(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) return;
    const enabledDays = resourceSchedule.filter((day) => day.enabled);
    if (enabledDays.length === 0) {
      setNotice({ title: "Configuración incompleta", message: "Selecciona al menos un día operativo para el recurso.", tone: "error" });
      return;
    }
    if (enabledDays.some((day) => timeToMinutes(day.opensAt) >= timeToMinutes(day.closesAt))) {
      setNotice({ title: "Horario inválido", message: "Cada día operativo debe cerrar después de abrir.", tone: "error" });
      return;
    }
    setSaving(true);
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
      setNotice({ title: "Recurso creado", message: "El recurso ya está disponible para reservar.", tone: "success" });
    } catch (exception) {
      showError(exception, "No fue posible crear el recurso.");
    } finally {
      setSaving(false);
    }
  }

  async function runAction(bookingId: string, action: "confirm" | "check-in" | "complete" | "cancel") {
    if (!session) return;
    try {
      await transitionBooking(session.accessToken, bookingId, action);
      await refresh();
      setNotice({ title: "Reserva actualizada", message: action === "cancel" ? "La reserva fue cancelada y ya no aparece en la agenda." : "El estado de la reserva se actualizó.", tone: "success" });
    } catch (exception) {
      showError(exception, "No fue posible actualizar la reserva.");
    }
  }

  function selectCalendarDate(value: string) {
    setCalendarDate(value);
    setStartDate(value);
    if (value === today && startTime && isPastDateTime(value, startTime)) setStartTime("");
  }

  function goToToday() {
    const value = toDateInput(new Date());
    setCalendarDate(value);
    setStartDate(value);
    setStartTime("");
  }

  if (loading) return <LoadingScreen text="Cargando reservas..." />;
  if (!session) return <AuthRequiredCard message="Necesitas iniciar sesión para administrar reservas." title="Inicia sesión" />;

  return (
    <AppShell
      branchName={activeBranch?.name}
      eyebrow="Operación"
      mode={activeBranch ? "operation" : undefined}
      organizationName={organization?.brandName || organization?.tradeName || session.user.brandName || session.user.organizationName}
      session={session}
      subtitle="Agenda mensual con detalle diario y reservaciones incluidas en membresías."
      title="Recursos y reservas"
    >
      {notice ? <NoticeModal notice={notice} onClose={() => setNotice(null)} /> : null}
      <section className="grid gap-6 xl:grid-cols-[410px_minmax(0,1fr)]">
        <aside className="grid content-start gap-5">
          {selectedDateIsPast ? (
            <p className="rounded-md border border-[#d8ddd2] bg-[#f8faf8] px-3 py-2 text-sm text-[#5d6b63]">
              Este día ya pasó. Puedes consultar el detalle histórico, pero no crear nuevas reservas.
            </p>
          ) : null}

          <form className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" onSubmit={submitBooking}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="text-xl font-semibold">Nueva reserva</h2>
                <p className="mt-1 text-sm text-[#5d6b63]">Busca al socio, elige recurso y captura el inicio.</p>
              </div>
              {activeResource ? <span className="rounded-md bg-[var(--brand-surface)] px-3 py-2 text-xs font-semibold text-[var(--brand-primary)]">{activeResource.slotMinutes} min</span> : null}
            </div>

            <label className="mt-4 grid gap-2 text-sm font-medium">
              Recurso
              <select className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setResourceId(event.target.value)} value={resourceId}>
                {resources.map((resource) => <option key={resource.id} value={resource.id}>{resource.name}</option>)}
              </select>
            </label>

            <p className="mt-3 rounded-md border border-[#d8ddd2] bg-[#f8faf8] px-3 py-2 text-xs text-[#5d6b63]">
              {activeResource?.allowedPlanNames ? <>Reservable por: <strong>{activeResource.allowedPlanNames}</strong></> : "Reservable por cualquier socio activo."}
            </p>

            <label className="mt-4 grid gap-2 text-sm font-medium">
              Buscar socio
              <input
                className="h-11 rounded-md border border-[#cdd5ca] px-3"
                onChange={(event) => {
                  setClientQuery(event.target.value);
                  setClientId("");
                  if (event.target.value.trim().length < 2) setClientResults([]);
                }}
                placeholder="Nombre, teléfono o email"
                value={clientQuery}
              />
            </label>
            <div className="mt-2 grid gap-2">
              {clientQuery.trim().length >= 2 && clientResults.map((client) => (
                <button
                  className={`rounded-md border px-3 py-2 text-left text-sm ${client.id === clientId ? "border-[var(--brand-primary)] bg-[var(--brand-surface)]" : "border-[#d8ddd2] bg-white"}`}
                  key={client.id}
                  onClick={() => { setClientId(client.id); setClientQuery(`${client.firstName} ${client.lastName}`); }}
                  type="button"
                >
                  <strong className="block">{client.firstName} {client.lastName}</strong>
                  <span className="text-xs text-[#647067]">{client.phone || client.email || "Socio activo"}</span>
                </button>
              ))}
              {clientQuery.trim().length >= 2 && clientResults.length === 0 && !clientId ? <p className="rounded-md border border-[#d8ddd2] bg-[#f8faf8] px-3 py-2 text-xs text-[#647067]">Sin coincidencias.</p> : null}
              {selectedClient ? <p className="text-xs font-semibold text-[var(--brand-primary)]">Seleccionado: {selectedClient.firstName} {selectedClient.lastName}</p> : null}
            </div>

            <div className="mt-4 grid gap-3 sm:grid-cols-[1fr_0.8fr]">
              <label className="grid gap-2 text-sm font-medium">
                Día de inicio
                <input className="h-11 rounded-md border border-[#cdd5ca] px-3" min={today} onChange={(event) => selectCalendarDate(event.target.value)} required type="date" value={startDate} />
              </label>
              <label className="grid gap-2 text-sm font-medium">
                Hora
                <input className={`h-11 rounded-md border px-3 ${selectedStartIsPast ? "border-red-500 bg-red-50" : "border-[#cdd5ca]"}`} min={startDate === today ? currentTimeInput() : undefined} onChange={(event) => setStartTime(event.target.value)} required step={300} type="time" value={startTime} />
              </label>
            </div>
            {selectedStartIsPast ? <p className="mt-2 text-sm font-medium text-red-700">Selecciona una hora posterior a la hora actual.</p> : null}
            <div className="mt-3 rounded-md border border-[#d8ddd2] bg-[#f8faf8] px-3 py-2 text-sm">
              <span className="block text-xs font-semibold uppercase text-[#647067]">Fin calculado</span>
              <strong>{computedEnd ? formatDateTime(computedEnd) : "Selecciona recurso, día y hora"}</strong>
            </div>
            <label className="mt-4 grid gap-2 text-sm font-medium">
              Notas
              <textarea className="min-h-20 rounded-md border border-[#cdd5ca] px-3 py-2" onChange={(event) => setNotes(event.target.value)} value={notes} />
            </label>
            <button className="mt-5 h-11 w-full rounded-md bg-[var(--brand-primary)] px-4 text-sm font-semibold text-white shadow-sm disabled:opacity-60" disabled={saving || selectedDateIsPast || selectedStartIsPast || !resourceId || !clientId || !isValidTimeInput(startTime)} type="submit">
              Crear reserva
            </button>
          </form>

          {canManageResources ? (
            <form className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" onSubmit={submitResource}>
              <h2 className="text-xl font-semibold">Crear recurso</h2>
              <input className="mt-4 h-11 w-full rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setResourceName(event.target.value)} placeholder="Nombre del recurso" required value={resourceName} />
              <div className="mt-4 grid gap-3 sm:grid-cols-[1fr_130px]">
                <label className="grid gap-2 text-sm font-medium">Duración<input className="h-11 rounded-md border border-[#cdd5ca] px-3" min={1} onChange={(event) => setDurationValue(Number(event.target.value))} type="number" value={durationValue} /></label>
                <label className="grid gap-2 text-sm font-medium">Unidad<select className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => setDurationUnit(event.target.value as "MINUTES" | "HOURS")} value={durationUnit}><option value="MINUTES">Minutos</option><option value="HOURS">Horas</option></select></label>
              </div>
              <fieldset className="mt-5 grid gap-2">
                <legend className="text-sm font-semibold">Días y horarios operativos</legend>
                {resourceSchedule.map((day) => (
                  <div className="grid grid-cols-[36px_1fr_92px_92px] items-center gap-2 rounded-md border border-[#d8ddd2] px-3 py-2 text-sm" key={day.key}>
                    <input aria-label={day.label} checked={day.enabled} className="h-4 w-4 accent-[var(--brand-primary)]" onChange={(event) => updateResourceDay(day.key, { enabled: event.target.checked })} type="checkbox" />
                    <span><strong className="block">{day.shortLabel}</strong><span className="text-xs text-[#647067]">{day.label}</span></span>
                    <input className="h-10 rounded-md border border-[#cdd5ca] px-2" disabled={!day.enabled} onChange={(event) => updateResourceDay(day.key, { opensAt: event.target.value })} type="time" value={day.opensAt} />
                    <input className="h-10 rounded-md border border-[#cdd5ca] px-2" disabled={!day.enabled} onChange={(event) => updateResourceDay(day.key, { closesAt: event.target.value })} type="time" value={day.closesAt} />
                  </div>
                ))}
              </fieldset>
              <fieldset className="mt-5 grid gap-2">
                <legend className="text-sm font-semibold">Membresías que pueden reservar</legend>
                <p className="text-xs text-[#6f7a73]">Sin seleccionar planes, el recurso queda abierto para cualquier socio activo.</p>
                {plans.map((plan) => (
                  <label className="flex items-start gap-2 rounded-md border border-[#d8ddd2] px-3 py-2 text-sm" key={plan.id}>
                    <input className="mt-1 h-4 w-4 accent-[var(--brand-primary)]" checked={resourceAllowedPlanIds.includes(plan.id)} onChange={(event) => setResourceAllowedPlanIds((current) => event.target.checked ? [...current, plan.id] : current.filter((planId) => planId !== plan.id))} type="checkbox" />
                    <span><strong className="block">{plan.name}</strong><span className="text-xs text-[#6f7a73]">{plan.multiBranchAccess ? "Multi-sucursal" : "Sucursal de venta"}</span></span>
                  </label>
                ))}
              </fieldset>
              <button className="mt-5 h-11 w-full rounded-md bg-[var(--brand-primary)] px-4 text-sm font-semibold text-white shadow-sm disabled:opacity-60" disabled={saving} type="submit">Crear recurso</button>
            </form>
          ) : null}
        </aside>

        <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div><h2 className="text-xl font-semibold">Agenda mensual</h2><p className="mt-1 text-sm text-[#5d6b63]">Selecciona un día para ver sus reservas por recurso.</p></div>
            <div className="flex flex-wrap items-center gap-2">
              <button className="h-10 rounded-md border border-[var(--brand-primary)] px-3 text-sm font-semibold text-[var(--brand-primary)]" onClick={() => moveCalendarMonth(-1)} type="button">Anterior</button>
              <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => selectCalendarDate(event.target.value)} type="date" value={calendarDate} />
              <button className="h-10 rounded-md bg-[var(--brand-primary)] px-4 text-sm font-semibold text-white shadow-sm" onClick={goToToday} type="button">Hoy</button>
              <button className="h-10 rounded-md border border-[var(--brand-primary)] px-3 text-sm font-semibold text-[var(--brand-primary)]" onClick={() => moveCalendarMonth(1)} type="button">Siguiente</button>
            </div>
          </div>
          <div className="mt-4 rounded-md border border-[#d8ddd2] bg-[#f8faf8] px-3 py-2 text-sm font-semibold text-[#34443a]">{formatMonthLabel(calendarDate)}</div>
          <div className="mt-5 flex flex-wrap gap-2">
            {resources.map((resource, index) => (
              <button className={`inline-flex items-center gap-2 rounded-md border px-3 py-2 text-sm ${resource.id === resourceId ? "border-[var(--brand-primary)] bg-[var(--brand-surface)]" : "border-[#d8ddd2]"}`} key={resource.id} onClick={() => setResourceId(resource.id)} type="button">
                <span className="h-3 w-3 rounded-full" style={{ backgroundColor: resourceColor(index) }} />{resource.name}
              </button>
            ))}
          </div>

          {resources.length === 0 ? <p className="mt-6 rounded-md border border-[#d8ddd2] bg-[#f8faf8] px-3 py-6 text-center text-sm text-[#5d6b63]">No hay recursos reservables en esta sucursal.</p> : (
            <div className="mt-5 grid gap-5">
              <div className="grid grid-cols-7 overflow-hidden rounded-lg border border-[#d8ddd2]">
                {weekdays.map((day) => <div className="border-b border-r border-[#d8ddd2] bg-[#f8faf8] px-3 py-2 text-center text-xs font-semibold uppercase text-[#647067] last:border-r-0" key={day.key}>{day.shortLabel}</div>)}
                {monthDates.map((day) => {
                  const dayBookings = activeBookings.filter((booking) => bookingDate(booking.startsAt) === day.date);
                  const activeResourceCount = new Set(dayBookings.map((booking) => booking.resourceId)).size;
                  const isSelected = startDate === day.date;
                  const isToday = today === day.date;
                  return (
                    <button
                      aria-current={isToday ? "date" : undefined}
                      className={`relative min-h-24 border-r border-t p-2 text-left transition last:border-r-0 ${day.inCurrentMonth ? "bg-white" : "bg-[#f6f3ef] text-[#9aa39d]"} ${isSelected ? "z-10 border-[var(--brand-primary)] bg-[var(--brand-surface)] ring-4 ring-[var(--brand-primary)] ring-offset-[-4px]" : isToday ? "border-2 border-[#1d4ed8] bg-[#eff6ff] shadow-inner" : "border-[#d8ddd2] hover:bg-[var(--brand-surface)]"}`}
                      key={day.date}
                      onClick={() => selectCalendarDate(day.date)}
                      type="button"
                    >
                      <div className="flex items-center justify-between gap-1">
                        <span className={`inline-flex h-8 min-w-8 items-center justify-center rounded-full px-2 text-sm font-bold ${isSelected ? "bg-[var(--brand-primary)] text-white" : isToday ? "bg-[#1d4ed8] text-white" : ""}`}>{day.dayOfMonth}</span>
                        {isToday ? <span className="rounded-full bg-[#dbeafe] px-2 py-0.5 text-[10px] font-bold uppercase text-[#1d4ed8]">Hoy</span> : null}
                      </div>
                      {dayBookings.length > 0 ? <span className="mt-2 block rounded-md bg-white/80 px-2 py-1 text-xs font-semibold text-[var(--brand-primary)]">{dayBookings.length} reserva{dayBookings.length === 1 ? "" : "s"}</span> : <span className="mt-2 block text-xs text-[#8a948d]">Libre</span>}
                      {activeResourceCount > 0 ? <span className="mt-2 flex gap-1">{resources.slice(0, 4).map((resource, index) => dayBookings.some((booking) => booking.resourceId === resource.id) ? <span className="h-2 w-2 rounded-full" key={resource.id} style={{ backgroundColor: resourceColor(index) }} /> : null)}</span> : null}
                    </button>
                  );
                })}
              </div>

              <section className="rounded-lg border border-[#d8ddd2] bg-[#fbfcfa] p-4">
                <div><h3 className="text-lg font-semibold">{formatLongDate(startDate)}</h3><p className="text-sm text-[#5d6b63]">Detalle del día seleccionado.</p></div>
                <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                  {resources.map((resource, index) => {
                    const resourceBookings = selectedDateBookings.filter((booking) => booking.resourceId === resource.id);
                    const hours = resourceHoursForDay(resource, dateToWeekday(startDate));
                    const suggestedTime = hours ? suggestedStartTime(startDate, hours.opensAt, hours.closesAt) : null;
                    return (
                      <article className="rounded-md border border-[#d8ddd2] bg-white p-3" key={resource.id}>
                        <div className="flex items-center justify-between gap-2">
                          <div className="min-w-0"><p className="truncate text-sm font-semibold"><span className="mr-2 inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: resourceColor(index) }} />{resource.name}</p><p className="mt-0.5 text-xs text-[#647067]">{hours ? `${hours.opensAt} - ${hours.closesAt}` : "Cerrado"}</p></div>
                          {hours ? <button className="rounded-md border border-[var(--brand-primary)] px-2 py-1 text-xs font-semibold text-[var(--brand-primary)] disabled:cursor-not-allowed disabled:opacity-50" disabled={selectedDateIsPast || !suggestedTime} onClick={() => { setResourceId(resource.id); setStartTime(suggestedTime ?? ""); }} type="button">Reservar</button> : null}
                        </div>
                        <div className="mt-3 grid gap-2">
                          {resourceBookings.length === 0 ? <p className="rounded-md bg-[#f8faf8] px-2 py-2 text-xs text-[#647067]">Sin reservas</p> : resourceBookings.map((booking) => <BookingCard booking={booking} color={resourceColor(index)} key={booking.id} onRunAction={runAction} />)}
                        </div>
                      </article>
                    );
                  })}
                </div>
              </section>
            </div>
          )}
        </section>
      </section>
    </AppShell>
  );

  function updateResourceDay(key: WeekdayKey, patch: Partial<ResourceDay>) {
    setResourceSchedule((current) => current.map((day) => day.key === key ? { ...day, ...patch } : day));
  }

  function moveCalendarMonth(months: number) {
    const date = parseDateInput(calendarDate);
    date.setMonth(date.getMonth() + months);
    selectCalendarDate(toDateInput(date));
  }
}

function NoticeModal({ notice, onClose }: { notice: Notice; onClose: () => void }) {
  const isError = notice.tone === "error";
  return (
    <div aria-modal="true" className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm" onClick={onClose} role="dialog">
      <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl" onClick={(event) => event.stopPropagation()}>
        <div className={`mx-auto flex h-14 w-14 items-center justify-center rounded-full text-2xl font-bold ${isError ? "bg-red-100 text-red-700" : "bg-green-100 text-green-700"}`}>{isError ? "!" : "✓"}</div>
        <h2 className="mt-4 text-center text-xl font-bold text-[#243229]">{notice.title}</h2>
        <p className="mt-2 text-center text-sm leading-6 text-[#5d6b63]">{notice.message}</p>
        <button autoFocus className={`mt-6 h-11 w-full rounded-md px-4 text-sm font-semibold text-white ${isError ? "bg-red-700" : "bg-[var(--brand-primary)]"}`} onClick={onClose} type="button">Aceptar</button>
      </div>
    </div>
  );
}

function BookingCard({ booking, color, onRunAction }: { booking: BookingSummary; color: string; onRunAction: (bookingId: string, action: "confirm" | "check-in" | "complete" | "cancel") => void }) {
  return (
    <article className="rounded-md px-3 py-2 text-white shadow-sm" style={{ backgroundColor: color }}>
      <p className="text-[11px] font-semibold uppercase opacity-80">{booking.status}</p>
      <p className="mt-1 truncate text-sm font-semibold">{booking.clientName}</p>
      <p className="text-xs opacity-90">{formatTime(booking.startsAt)} - {formatTime(booking.endsAt)}</p>
      <div className="mt-2 flex flex-wrap gap-1">{bookingActions.map((action) => <ActionButton action={action} booking={booking} key={action} onRun={onRunAction} />)}</div>
    </article>
  );
}

function ActionButton({ action, booking, onRun }: { action: "confirm" | "check-in" | "complete" | "cancel"; booking: BookingSummary; onRun: (bookingId: string, action: "confirm" | "check-in" | "complete" | "cancel") => void }) {
  const labels = { confirm: "Confirmar", "check-in": "Check-in", complete: "Completar", cancel: "Cancelar" };
  const disabled = booking.status === "COMPLETED" || booking.status === "CANCELLED";
  return <button className="h-7 rounded border border-white/40 px-2 text-[11px] font-semibold text-white disabled:opacity-50" disabled={disabled} onClick={() => onRun(booking.id, action)} type="button">{labels[action]}</button>;
}

async function getCalendarBookings(accessToken: string, dates: Array<{ date: string }>) {
  const lists = await Promise.all(dates.map((day) => getBookings(accessToken, day.date)));
  return lists.flat();
}

function buildMonthDates(anchor: string) {
  const month = parseDateInput(anchor);
  const firstOfMonth = new Date(month.getFullYear(), month.getMonth(), 1);
  const gridStart = new Date(firstOfMonth);
  const mondayOffset = (firstOfMonth.getDay() + 6) % 7;
  gridStart.setDate(firstOfMonth.getDate() - mondayOffset);
  return Array.from({ length: 42 }, (_, index) => {
    const weekday = weekdays[index % 7];
    const date = new Date(gridStart);
    date.setDate(gridStart.getDate() + index);
    return { date: toDateInput(date), dayOfMonth: date.getDate(), inCurrentMonth: date.getMonth() === month.getMonth(), label: weekday.label, weekday: weekday.key };
  });
}

function resourceHoursForDay(resource: ResourceSummary, weekday: WeekdayKey) {
  const schedule = parseResourceSchedule(resource);
  const scheduleDay = schedule?.find((day) => day.day === weekday);
  if (scheduleDay) return { opensAt: normalizeTimeInput(scheduleDay.opensAt), closesAt: normalizeTimeInput(scheduleDay.closesAt) };
  if (schedule) return null;
  return { opensAt: normalizeTimeInput(resource.opensAt || "06:00"), closesAt: normalizeTimeInput(resource.closesAt || "22:00") };
}

function parseResourceSchedule(resource: ResourceSummary) {
  if (!resource.notes) return null;
  try {
    const parsed = JSON.parse(resource.notes) as StoredSchedule;
    if (!Array.isArray(parsed.schedule)) return null;
    return parsed.schedule.filter((day) => day.day && day.opensAt && day.closesAt);
  } catch {
    return null;
  }
}

function friendlyBookingError(raw: string, fallback: string) {
  const normalized = raw.toLowerCase();
  if (normalized.includes("booking_conflict") || normalized.includes("no tiene disponibilidad") || normalized.includes("conflict")) return "El recurso ya está ocupado en ese horario. Elige otra hora o un recurso diferente.";
  if (normalized.includes("booking_plan_not_allowed") || normalized.includes("membresia permitida") || normalized.includes("membresía permitida")) return "El socio no tiene una membresía válida para reservar este recurso. Selecciona otro recurso o revisa su plan.";
  if (normalized.includes("booking_outside_resource_hours") || normalized.includes("fuera del horario")) return "La hora seleccionada está fuera del horario operativo del recurso.";
  if (normalized.includes("booking_start_in_past") || normalized.includes("hora actual") || normalized.includes("pasado")) return "No es posible crear una reserva en una hora que ya pasó.";
  if (normalized.includes("booking_range_invalid")) return "Revisa la fecha y la hora de la reserva.";
  if (normalized.includes("client_not_found")) return "El socio no está disponible o no se encuentra activo.";
  const clean = raw
    .replace(/\[[A-Z0-9_ -]+\]/g, "")
    .replace(/\b[A-Z][A-Z0-9_]{3,}\b/g, "")
    .replace(/https?:\/\/\S+/g, "")
    .replace(/\s+/g, " ")
    .trim();
  return clean && clean.length <= 180 ? clean : fallback;
}

function suggestedStartTime(date: string, opensAt: string, closesAt: string) {
  if (date !== toDateInput(new Date())) return opensAt;
  const now = new Date();
  const rounded = new Date(now);
  rounded.setSeconds(0, 0);
  rounded.setMinutes(Math.ceil(rounded.getMinutes() / 5) * 5);
  const candidate = currentTimeInput(rounded);
  const resolved = timeToMinutes(candidate) > timeToMinutes(opensAt) ? candidate : opensAt;
  return timeToMinutes(resolved) < timeToMinutes(closesAt) ? resolved : null;
}

function isPastDateTime(date: string, time: string) {
  if (!date || !isValidTimeInput(time)) return false;
  return new Date(`${date}T${time}`).getTime() <= Date.now();
}

function currentTimeInput(date = new Date()) {
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

function addMinutes(localDateTime: string, minutes: number) {
  const date = new Date(localDateTime);
  if (Number.isNaN(date.getTime())) return "";
  date.setMinutes(date.getMinutes() + minutes);
  return toLocalDateTimeInput(date);
}

function formatTime(value: string) { return new Intl.DateTimeFormat("es-MX", { hour: "2-digit", minute: "2-digit" }).format(new Date(value)); }
function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Hora inválida";
  return new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", timeStyle: "short" }).format(date);
}
function formatLongDate(value: string) { return new Intl.DateTimeFormat("es-MX", { dateStyle: "full" }).format(parseDateInput(value)); }
function formatMonthLabel(value: string) { return new Intl.DateTimeFormat("es-MX", { month: "long", year: "numeric" }).format(parseDateInput(value)); }
function bookingDate(value: string) { return toDateInput(new Date(value)); }
function resourceColor(index: number) { return resourceColors[index % resourceColors.length]; }
function parseDateInput(value: string) { return new Date(`${value}T00:00:00`); }
function isPastDate(value: string) { return parseDateInput(value).getTime() < parseDateInput(toDateInput(new Date())).getTime(); }
function dateToWeekday(value: string): WeekdayKey { return weekdays[(parseDateInput(value).getDay() + 6) % 7].key; }
function isValidTimeInput(value: string) { return /^([01]\d|2[0-3]):[0-5]\d$/.test(normalizeTimeInput(value)); }
function normalizeTimeInput(value: string) { const match = value.match(/^([01]\d|2[0-3]):([0-5]\d)(?::[0-5]\d)?$/); return match ? `${match[1]}:${match[2]}` : value; }
function timeToMinutes(value: string) { const [hours, minutes] = value.split(":").map(Number); return hours * 60 + minutes; }
function minutesToTime(value: number) { const hours = Math.floor(value / 60).toString().padStart(2, "0"); const minutes = (value % 60).toString().padStart(2, "0"); return `${hours}:${minutes}`; }
function toDateInput(date: Date) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`; }
function toLocalDateTimeInput(date: Date) { return `${toDateInput(date)}T${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`; }
