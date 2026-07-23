"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
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
import type { BookingNoteDialog, BookingNotice } from "./components";
import {
  createBooking,
  createResource,
  deleteResource,
  getBranches,
  getBookings,
  getCurrentOrganization,
  getPlans,
  getResources,
  getStoredSession,
  searchClients,
  transitionBooking,
  updateResource,
  type BranchSummary,
  type BookingSummary,
  type ClientSummary,
  type OrganizationSummary,
  type PlanSummary,
  type ResourceSummary,
  type StoredSession,
} from "@/lib/api";

export function useBookingsController() {
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
  const [editingResourceId, setEditingResourceId] = useState<string | null>(null);

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

    return () => {
      active = false;
    };
  }, [monthDates]);

  useEffect(() => {
    if (!session || clientQuery.trim().length < 2) {
      return;
    }
    let active = true;
    const timer = window.setTimeout(() => {
      searchClients(session.accessToken, clientQuery.trim(), "ACTIVE")
        .then((results) => {
          if (active) setClientResults(results.slice(0, 8));
        })
        .catch(() => {
          if (active) setClientResults([]);
        });
    }, 250);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
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

  const visibleClientResults = clientQuery.trim().length < 2 ? [] : clientResults;
  const activeResource = resources.find((resource) => resource.id === resourceId) ?? null;
  const activeBranch = branches.find((branch) => branch.id === session?.user.branchId) ?? null;
  const selectedClient = visibleClientResults.find((client) => client.id === clientId) ?? null;
  const roleNames = session?.user.roles.map((role) => role.toLowerCase()) ?? [];
  const isTenantOwner = roleNames.some((role) => role.includes("dueno") || role.includes("dueño"));
  const canManageResources = Boolean(session?.user.permissions.includes("resource.manage") && (session.user.platformAdmin || isTenantOwner));
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
      setNotice({ title: "Reserva creada", message: "La reserva se registro correctamente.", tone: "success" });
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
      setNotice({ title: "Configuracion incompleta", message: "Selecciona al menos un dia operativo para el recurso.", tone: "error" });
      return;
    }
    if (enabledDays.some((day) => timeToMinutes(day.opensAt) >= timeToMinutes(day.closesAt))) {
      setNotice({ title: "Horario invalido", message: "Cada dia operativo debe cerrar despues de abrir.", tone: "error" });
      return;
    }
    setSaving(true);
    try {
      const payload = {
        name: resourceName,
        resourceType: "ROOM",
        capacity: 1,
        slotMinutes: durationMinutes,
        opensAt: minutesToTime(Math.min(...enabledDays.map((day) => timeToMinutes(day.opensAt)))),
        closesAt: minutesToTime(Math.max(...enabledDays.map((day) => timeToMinutes(day.closesAt)))),
        notes: buildResourceScheduleNotes(enabledDays),
        allowedPlanIds: resourceAllowedPlanIds,
      };
      const saved = editingResourceId
        ? await updateResource(session.accessToken, editingResourceId, payload)
        : await createResource(session.accessToken, payload);
      await refresh();
      setResourceId(saved.id);
      resetResourceForm();
      setNotice({
        title: editingResourceId ? "Recurso actualizado" : "Recurso creado",
        message: editingResourceId ? "Los cambios del recurso quedaron guardados." : "El recurso ya esta disponible para reservar.",
        tone: "success",
      });
      setShowResourceDrawer(false);
    } catch (error) {
      showError(error, "No fue posible crear el recurso.");
    } finally {
      setSaving(false);
    }
  }

  async function deleteReservableResource(targetResourceId: string) {
    if (!session) return;
    const target = resources.find((resource) => resource.id === targetResourceId);
    const confirmed = window.confirm(`Quieres eliminar ${target?.name ?? "este recurso"}? Solo se permite si no tiene reservas vigentes.`);
    if (!confirmed) return;
    setSaving(true);
    try {
      await deleteResource(session.accessToken, targetResourceId);
      await refresh();
      setResourceId((current) => current === targetResourceId ? "" : current);
      setNotice({ title: "Recurso eliminado", message: "El recurso ya no aparecera en la agenda.", tone: "success" });
    } catch (error) {
      showError(error, "No fue posible eliminar el recurso.");
    } finally {
      setSaving(false);
    }
  }

  function openCreateResourceDrawer() {
    resetResourceForm();
    setShowResourceDrawer(true);
  }

  function openEditResourceDrawer(targetResourceId: string) {
    const target = resources.find((resource) => resource.id === targetResourceId);
    if (!target) return;
    setEditingResourceId(target.id);
    setResourceName(target.name);
    setDurationUnit("MINUTES");
    setDurationValue(target.slotMinutes);
    setResourceAllowedPlanIds(target.allowedPlanIds ?? []);
    setResourceSchedule(weekdays.map((day) => {
      const hours = resourceHoursForDay(target, day.key);
      return {
        ...day,
        enabled: Boolean(hours),
        opensAt: hours?.opensAt ?? day.opensAt,
        closesAt: hours?.closesAt ?? day.closesAt,
      };
    }));
    setShowResourceDrawer(true);
  }

  function resetResourceForm() {
    setEditingResourceId(null);
    setResourceName("");
    setDurationValue(60);
    setDurationUnit("MINUTES");
    setResourceSchedule(weekdays.map((day) => ({ ...day })));
    setResourceAllowedPlanIds([]);
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

  function updateClientQuery(value: string) {
    setClientQuery(value);
    setClientId("");
  }

  function selectClient(client: ClientSummary) {
    setClientId(client.id);
    setClientQuery(`${client.firstName} ${client.lastName}`);
  }

  function openNoteDialog(selectedBooking: BookingSummary) {
    setNoteDialog({
      clientName: selectedBooking.clientName,
      note: selectedBooking.notes?.trim() || "Esta reserva no tiene una nota registrada.",
    });
  }

  return {
    activeBookings,
    activeBranch,
    activeResource,
    calendarDate,
    canManageResources,
    clientQuery,
    clientResults: visibleClientResults,
    computedEnd,
    durationUnit,
    durationValue,
    editingResourceId,
    formatDateTime,
    goToToday,
    isValidTimeInput,
    loading,
    minimumBookingTime,
    monthDates,
    moveCalendarMonth,
    noteDialog,
    notes,
    notice,
    openNoteDialog,
    openCreateResourceDrawer,
    openEditResourceDrawer,
    openResourceCount,
    organization,
    plans,
    resourceAllowedPlanIds,
    resourceColor,
    resourceHoursForDay,
    resourceId,
    resourceName,
    resourceSchedule,
    resources,
    saving,
    selectCalendarDate,
    selectClient,
    selectedClient,
    selectedDateBookings,
    selectedDateIsPast,
    selectedDayResourceSummary,
    selectedStartIsPast,
    selectedWeekday,
    session,
    setDurationUnit,
    setDurationValue,
    setNoteDialog,
    setNotes,
    setNotice,
    setResourceAllowedPlanIds,
    setResourceId,
    setResourceName,
    setShowBookingDrawer,
    setShowResourceDrawer,
    setStartTime,
    showBookingDrawer,
    showResourceDrawer,
    startDate,
    startTime,
    submitBooking,
    submitResource,
    today,
    updateClientQuery,
    updateResourceDay,
    weekdays,
    cancelBooking,
    deleteReservableResource,
  };
}

async function getCalendarBookings(accessToken: string, dates: Array<{ date: string }>) {
  const lists = await Promise.all(dates.map((day) => getBookings(accessToken, day.date)));
  return lists.flat();
}
