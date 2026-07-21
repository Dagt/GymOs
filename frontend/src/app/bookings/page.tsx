"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { AppDrawer } from "@/components/AppDrawer";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  BookingDayDetail,
  BookingNoteModal,
  BookingNoticeModal,
  BookingSummaryPanel,
  BookingsMonthCalendar,
  type BookingNoteDialog,
  type BookingNotice,
} from "./components";
import { BookingDrawerForm, ResourceDrawerForm } from "./forms";
import {
  addMinutes,
  bookingDate,
  buildMonthDates,
  buildResourceScheduleNotes,
  currentTimeInput,
  dateToWeekday,
  friendlyBookingError,
  formatDateTime,
  isPastDate,
  isPastDateTime,
  isValidTimeInput,
  minutesToTime,
  parseDateInput,
  resourceColor,
  resourceHoursForDay,
  timeToMinutes,
  toDateInput,
  weekdays,
  type ResourceDay,
  type WeekdayKey,
} from "./booking-utils";
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
  const [notice, setNotice] = useState<BookingNotice | null>(null);
  const [noteDialog, setNoteDialog] = useState<BookingNoteDialog | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [showBookingDrawer, setShowBookingDrawer] = useState(false);
  const [showResourceDrawer, setShowResourceDrawer] = useState(false);

  const today = toDateInput(new Date());
  const minimumBookingTime = startDate === today ? currentTimeInput() : undefined;
  const monthDates = useMemo(() => buildMonthDates(calendarDate), [calendarDate]);
  const activeBookings = useMemo(() => bookings.filter((booking) => booking.status !== "CANCELLED"), [bookings]);
  const selectedDateBookings = useMemo(
    () => activeBookings.filter((booking) => bookingDate(booking.startsAt) === startDate).sort((a, b) => a.startsAt.localeCompare(b.startsAt)),
    [activeBookings, startDate],
  );

  useEffect(() => {
    let active = true;
    async function load() {
      const stored = getStoredSession();
      if (!stored) {
        if (active) setLoading(false);
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
      } catch (error) {
        if (active) showError(error, "No fue posible cargar las reservas.");
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => { active = false; };
  }, [monthDates]);

  useEffect(() => {
    if (!session || clientQuery.trim().length < 2) {
      setClientResults([]);
      return;
    }
    let active = true;
    const timer = window.setTimeout(() => {
      searchClients(session.accessToken, clientQuery.trim(), "ACTIVE")
        .then((results) => { if (active) setClientResults(results.slice(0, 8)); })
        .catch(() => { if (active) setClientResults([]); });
    }, 250);
    return () => { active = false; window.clearTimeout(timer); };
  }, [clientQuery, session]);

  useEffect(() => {
    if (!notice && !noteDialog) return;
    const close = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      setNotice(null);
      setNoteDialog(null);
    };
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, [notice, noteDialog]);

  const activeResource = resources.find((resource) => resource.id === resourceId) ?? null;
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
  const selectedWeekday = dateToWeekday(startDate);
  const selectedDayResourceSummary = resources.map((resource, index) => {
    const resourceBookings = selectedDateBookings.filter((booking) => booking.resourceId === resource.id);
    return {
      bookingCount: resourceBookings.length,
      color: resourceColor(index),
      hours: resourceHoursForDay(resource, selectedWeekday),
      id: resource.id,
      name: resource.name,
      slotMinutes: resource.slotMinutes,
    };
  });
  const openResourceCount = selectedDayResourceSummary.filter((resource) => resource.hours).length;

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

  function showError(error: unknown, fallback: string) {
    const raw = error instanceof Error ? error.message : fallback;
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
      await createBooking(session.accessToken, {
        resourceId,
        clientId,
        startsAt: new Date(`${startDate}T${startTime}`).toISOString(),
        endsAt: new Date(computedEnd).toISOString(),
        notes,
      });
      const dates = buildMonthDates(startDate);
      setCalendarDate(startDate);
      await refresh(session, dates);
      setNotice({ title: "Reserva creada", message: "La reserva se registró correctamente.", tone: "success" });
      setNotes("");
      setStartTime("");
      setShowBookingDrawer(false);
    } catch (error) {
      showError(error, "No fue posible crear la reserva.");
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
      const created = await createResource(session.accessToken, {
        name: resourceName,
        resourceType: "ROOM",
        capacity: 1,
        slotMinutes: durationMinutes,
        opensAt: minutesToTime(Math.min(...enabledDays.map((day) => timeToMinutes(day.opensAt)))),
        closesAt: minutesToTime(Math.max(...enabledDays.map((day) => timeToMinutes(day.closesAt)))),
        notes: buildResourceScheduleNotes(enabledDays),
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
      setShowResourceDrawer(false);
    } catch (error) {
      showError(error, "No fue posible crear el recurso.");
    } finally {
      setSaving(false);
    }
  }

  async function cancelBooking(bookingId: string) {
    if (!session) return;
    try {
      await transitionBooking(session.accessToken, bookingId, "cancel");
      await refresh();
      setNotice({ title: "Reserva cancelada", message: "La reserva fue cancelada y ya no aparece en la agenda.", tone: "success" });
    } catch (error) {
      showError(error, "No fue posible cancelar la reserva.");
    }
  }

  function selectCalendarDate(value: string) {
    setCalendarDate(value);
    setStartDate(value);
    if (value === today && startTime && isPastDateTime(value, startTime)) setStartTime("");
  }

  function goToToday() {
    selectCalendarDate(toDateInput(new Date()));
    setStartTime("");
  }

  function moveCalendarMonth(months: number) {
    const date = parseDateInput(calendarDate);
    date.setMonth(date.getMonth() + months);
    selectCalendarDate(toDateInput(date));
  }

  function updateResourceDay(key: WeekdayKey, patch: Partial<ResourceDay>) {
    setResourceSchedule((current) => current.map((day) => day.key === key ? { ...day, ...patch } : day));
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
      {notice ? <BookingNoticeModal notice={notice} onClose={() => setNotice(null)} /> : null}
      {noteDialog ? <BookingNoteModal dialog={noteDialog} onClose={() => setNoteDialog(null)} /> : null}
      <section className={`grid gap-6 xl:grid-cols-[410px_minmax(0,1fr)] ${canManageResources ? "" : "bookings-operational-layout"}`}>
        <aside className="grid content-start gap-5">
          {selectedDateIsPast ? <p className="rounded-md border border-[#d8ddd2] bg-[#f8faf8] px-3 py-2 text-sm text-[#5d6b63]">Este día ya pasó. Puedes consultar el detalle histórico, pero no crear nuevas reservas.</p> : null}

          <section className="app-card p-5">
            <p className="app-eyebrow">Acciones</p>
            <h2 className="app-card-title">Agenda del dia</h2>
            <p className="app-card-copy">
              Selecciona un dia en el calendario y abre solo la accion que necesitas.
            </p>
            <div className="mt-5 grid gap-3">
              <button className="app-button" disabled={selectedDateIsPast || resources.length === 0} onClick={() => setShowBookingDrawer(true)} type="button">
                Nueva reserva
              </button>
              {canManageResources ? (
                <button className="admin-secondary-link" onClick={() => setShowResourceDrawer(true)} type="button">
                  Crear recurso
                </button>
              ) : null}
            </div>
          </section>

          <BookingSummaryPanel openResourceCount={openResourceCount} resources={selectedDayResourceSummary} selectedDate={startDate} />

          <AppDrawer
            description="Busca al socio, elige el recurso y confirma el horario de inicio."
            onClose={() => setShowBookingDrawer(false)}
            open={showBookingDrawer}
            title="Nueva reserva"
          >
          <BookingDrawerForm
            activeResource={activeResource}
            clientQuery={clientQuery}
            clientResults={clientResults}
            computedEnd={computedEnd}
            formatDateTime={formatDateTime}
            isValidTimeInput={isValidTimeInput}
            minimumBookingTime={minimumBookingTime}
            notes={notes}
            onClientQueryChange={(value) => { setClientQuery(value); setClientId(""); }}
            onNotesChange={setNotes}
            onSelectCalendarDate={selectCalendarDate}
            onSelectClient={(client) => { setClientId(client.id); setClientQuery(`${client.firstName} ${client.lastName}`); }}
            onSelectResource={setResourceId}
            onStartTimeChange={setStartTime}
            onSubmit={submitBooking}
            resourceId={resourceId}
            resources={resources}
            saving={saving}
            selectedClient={selectedClient}
            selectedDateIsPast={selectedDateIsPast}
            selectedStartIsPast={selectedStartIsPast}
            startDate={startDate}
            startTime={startTime}
            today={today}
          />
          </AppDrawer>

          {canManageResources ? (
            <AppDrawer
              description="Define horarios, duracion y membresias permitidas para un recurso reservable."
              onClose={() => setShowResourceDrawer(false)}
              open={showResourceDrawer}
              title="Crear recurso"
            >
            <ResourceDrawerForm
              durationUnit={durationUnit}
              durationValue={durationValue}
              onAllowedPlanIdsChange={setResourceAllowedPlanIds}
              onDurationUnitChange={setDurationUnit}
              onDurationValueChange={setDurationValue}
              onResourceNameChange={setResourceName}
              onSubmit={submitResource}
              onUpdateResourceDay={updateResourceDay}
              plans={plans}
              resourceAllowedPlanIds={resourceAllowedPlanIds}
              resourceName={resourceName}
              resourceSchedule={resourceSchedule}
              saving={saving}
            />
            </AppDrawer>
          ) : null}
        </aside>

        <BookingsMonthCalendar
          activeBookings={activeBookings}
          calendarDate={calendarDate}
          className={canManageResources ? "" : "bookings-operational-agenda"}
          getResourceColor={resourceColor}
          monthDates={monthDates}
          onGoToToday={goToToday}
          onMoveMonth={moveCalendarMonth}
          onSelectDate={selectCalendarDate}
          onSelectResource={setResourceId}
          resourceId={resourceId}
          resources={resources}
          selectedDate={startDate}
          today={today}
          weekdays={weekdays}
        />
        {resources.length > 0 ? (
          <BookingDayDetail
            bookings={selectedDateBookings}
            className="bookings-full-width-detail"
            getResourceColor={resourceColor}
            getResourceHours={(resource) => resourceHoursForDay(resource, selectedWeekday)}
            onCancel={cancelBooking}
            onShowNote={(selectedBooking) => setNoteDialog({
              clientName: selectedBooking.clientName,
              note: selectedBooking.notes?.trim() || "Esta reserva no tiene una nota registrada.",
            })}
            resources={resources}
            selectedDate={startDate}
          />
        ) : null}
      </section>
    </AppShell>
  );
}

async function getCalendarBookings(accessToken: string, dates: Array<{ date: string }>) {
  const lists = await Promise.all(dates.map((day) => getBookings(accessToken, day.date)));
  return lists.flat();
}
