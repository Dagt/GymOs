"use client";

import type { FormEvent } from "react";
import type { ClientSummary, PlanSummary, ResourceSummary } from "@/lib/api";

export type BookingWeekdayKey = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";
export type BookingResourceDay = {
  key: BookingWeekdayKey;
  label: string;
  shortLabel: string;
  enabled: boolean;
  opensAt: string;
  closesAt: string;
};

type DurationUnit = "MINUTES" | "HOURS";

export function BookingDrawerForm({
  activeResource,
  clientQuery,
  clientResults,
  computedEnd,
  formatDateTime,
  isValidTimeInput,
  minimumBookingTime,
  notes,
  onClientQueryChange,
  onSelectCalendarDate,
  onSelectClient,
  onSelectResource,
  onStartTimeChange,
  onSubmit,
  onNotesChange,
  resourceId,
  resources,
  saving,
  selectedClient,
  selectedDateIsPast,
  selectedStartIsPast,
  startDate,
  startTime,
  today,
}: {
  activeResource: ResourceSummary | null;
  clientQuery: string;
  clientResults: ClientSummary[];
  computedEnd: string;
  formatDateTime: (value: string) => string;
  isValidTimeInput: (value: string) => boolean;
  minimumBookingTime?: string;
  notes: string;
  onClientQueryChange: (value: string) => void;
  onNotesChange: (value: string) => void;
  onSelectCalendarDate: (value: string) => void;
  onSelectClient: (client: ClientSummary) => void;
  onSelectResource: (value: string) => void;
  onStartTimeChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  resourceId: string;
  resources: ResourceSummary[];
  saving: boolean;
  selectedClient: ClientSummary | null;
  selectedDateIsPast: boolean;
  selectedStartIsPast: boolean;
  startDate: string;
  startTime: string;
  today: string;
}) {
  return (
    <form className="grid gap-4" onSubmit={onSubmit}>
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm text-[#5d6b63]">Captura los datos de la reserva. El sistema validara disponibilidad y membresia antes de guardar.</p>
        {activeResource ? <span className="rounded-md bg-[var(--brand-surface)] px-3 py-2 text-xs font-semibold text-[var(--brand-primary)]">{activeResource.slotMinutes} min</span> : null}
      </div>
      <label className="mt-4 grid gap-2 text-sm font-medium">
        Recurso
        <select className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => onSelectResource(event.target.value)} value={resourceId}>
          {resources.map((resource) => <option key={resource.id} value={resource.id}>{resource.name}</option>)}
        </select>
      </label>
      <p className="mt-3 rounded-md border border-[#d8ddd2] bg-[#f8faf8] px-3 py-2 text-xs text-[#5d6b63]">
        {activeResource?.allowedPlanNames ? <>Reservable por: <strong>{activeResource.allowedPlanNames}</strong></> : "Reservable por cualquier socio activo."}
      </p>
      <label className="mt-4 grid gap-2 text-sm font-medium">
        Buscar socio
        <input className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => onClientQueryChange(event.target.value)} placeholder="Nombre, telefono o email" value={clientQuery} />
      </label>
      <div className="mt-2 grid gap-2">
        {clientResults.map((client) => (
          <button className={`rounded-md border px-3 py-2 text-left text-sm ${client.id === selectedClient?.id ? "border-[var(--brand-primary)] bg-[var(--brand-surface)]" : "border-[#d8ddd2] bg-white"}`} key={client.id} onClick={() => onSelectClient(client)} type="button">
            <strong className="block">{client.firstName} {client.lastName}</strong>
            <span className="text-xs text-[#647067]">{client.phone || client.email || "Socio activo"}</span>
          </button>
        ))}
        {selectedClient ? <p className="text-xs font-semibold text-[var(--brand-primary)]">Seleccionado: {selectedClient.firstName} {selectedClient.lastName}</p> : null}
      </div>
      <div className="mt-4 grid gap-3 sm:grid-cols-[1fr_0.8fr]">
        <label className="grid gap-2 text-sm font-medium">
          Dia de inicio
          <input className="h-11 rounded-md border border-[#cdd5ca] px-3" min={today} onChange={(event) => onSelectCalendarDate(event.target.value)} required type="date" value={startDate} />
        </label>
        <label className="grid gap-2 text-sm font-medium">
          Hora
          <input className={`h-11 rounded-md border px-3 ${selectedStartIsPast ? "border-red-500 bg-red-50" : "border-[#cdd5ca]"}`} min={minimumBookingTime} onChange={(event) => onStartTimeChange(event.target.value)} required step={300} type="time" value={startTime} />
        </label>
      </div>
      {selectedStartIsPast ? <p className="mt-2 text-sm font-medium text-red-700">Selecciona una hora posterior a la hora actual.</p> : null}
      <div className="mt-3 rounded-md border border-[#d8ddd2] bg-[#f8faf8] px-3 py-2 text-sm">
        <span className="block text-xs font-semibold uppercase text-[#647067]">Fin calculado</span>
        <strong>{computedEnd ? formatDateTime(computedEnd) : "Selecciona recurso, dia y hora"}</strong>
      </div>
      <label className="mt-4 grid gap-2 text-sm font-medium">
        Notas
        <textarea className="min-h-20 rounded-md border border-[#cdd5ca] px-3 py-2" onChange={(event) => onNotesChange(event.target.value)} value={notes} />
      </label>
      <button className="mt-5 h-11 w-full rounded-md bg-[var(--brand-primary)] px-4 text-sm font-semibold text-white shadow-sm disabled:opacity-60" disabled={saving || selectedDateIsPast || selectedStartIsPast || !resourceId || !selectedClient || !isValidTimeInput(startTime)} type="submit">
        Crear reserva
      </button>
    </form>
  );
}

export function ResourceDrawerForm({
  durationUnit,
  durationValue,
  onAllowedPlanIdsChange,
  onDurationUnitChange,
  onDurationValueChange,
  onResourceNameChange,
  onSubmit,
  onUpdateResourceDay,
  plans,
  resourceAllowedPlanIds,
  resourceName,
  resourceSchedule,
  saving,
}: {
  durationUnit: DurationUnit;
  durationValue: number;
  onAllowedPlanIdsChange: (ids: string[]) => void;
  onDurationUnitChange: (value: DurationUnit) => void;
  onDurationValueChange: (value: number) => void;
  onResourceNameChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onUpdateResourceDay: (key: BookingWeekdayKey, patch: Partial<BookingResourceDay>) => void;
  plans: PlanSummary[];
  resourceAllowedPlanIds: string[];
  resourceName: string;
  resourceSchedule: BookingResourceDay[];
  saving: boolean;
}) {
  return (
    <form className="grid gap-4" onSubmit={onSubmit}>
      <p className="text-sm text-[#5d6b63]">Configura una cancha, salon, area o recurso que pueda reservarse desde esta sucursal.</p>
      <input className="h-11 w-full rounded-md border border-[#cdd5ca] px-3" onChange={(event) => onResourceNameChange(event.target.value)} placeholder="Nombre del recurso" required value={resourceName} />
      <div className="mt-4 grid gap-3 sm:grid-cols-[1fr_130px]">
        <label className="grid gap-2 text-sm font-medium">
          Duracion
          <input className="h-11 rounded-md border border-[#cdd5ca] px-3" min={1} onChange={(event) => onDurationValueChange(Number(event.target.value))} type="number" value={durationValue} />
        </label>
        <label className="grid gap-2 text-sm font-medium">
          Unidad
          <select className="h-11 rounded-md border border-[#cdd5ca] px-3" onChange={(event) => onDurationUnitChange(event.target.value as DurationUnit)} value={durationUnit}>
            <option value="MINUTES">Minutos</option>
            <option value="HOURS">Horas</option>
          </select>
        </label>
      </div>
      <fieldset className="mt-5 grid gap-2">
        <legend className="text-sm font-semibold">Dias y horarios operativos</legend>
        {resourceSchedule.map((day) => (
          <div className="grid grid-cols-[36px_1fr_92px_92px] items-center gap-2 rounded-md border border-[#d8ddd2] px-3 py-2 text-sm" key={day.key}>
            <input aria-label={day.label} checked={day.enabled} className="h-4 w-4 accent-[var(--brand-primary)]" onChange={(event) => onUpdateResourceDay(day.key, { enabled: event.target.checked })} type="checkbox" />
            <span>
              <strong className="block">{day.shortLabel}</strong>
              <span className="text-xs text-[#647067]">{day.label}</span>
            </span>
            <input className="h-10 rounded-md border border-[#cdd5ca] px-2" disabled={!day.enabled} onChange={(event) => onUpdateResourceDay(day.key, { opensAt: event.target.value })} type="time" value={day.opensAt} />
            <input className="h-10 rounded-md border border-[#cdd5ca] px-2" disabled={!day.enabled} onChange={(event) => onUpdateResourceDay(day.key, { closesAt: event.target.value })} type="time" value={day.closesAt} />
          </div>
        ))}
      </fieldset>
      <fieldset className="mt-5 grid gap-2">
        <legend className="text-sm font-semibold">Membresias que pueden reservar</legend>
        <p className="text-xs text-[#6f7a73]">Sin seleccionar planes, el recurso queda abierto para cualquier socio activo.</p>
        {plans.map((plan) => (
          <label className="flex items-start gap-2 rounded-md border border-[#d8ddd2] px-3 py-2 text-sm" key={plan.id}>
            <input
              checked={resourceAllowedPlanIds.includes(plan.id)}
              className="mt-1 h-4 w-4 accent-[var(--brand-primary)]"
              onChange={(event) => onAllowedPlanIdsChange(event.target.checked ? [...resourceAllowedPlanIds, plan.id] : resourceAllowedPlanIds.filter((id) => id !== plan.id))}
              type="checkbox"
            />
            <span>
              <strong className="block">{plan.name}</strong>
              <span className="text-xs text-[#6f7a73]">{plan.multiBranchAccess ? "Multi-sucursal" : "Sucursal de venta"}</span>
            </span>
          </label>
        ))}
      </fieldset>
      <button className="mt-5 h-11 w-full rounded-md bg-[var(--brand-primary)] px-4 text-sm font-semibold text-white shadow-sm disabled:opacity-60" disabled={saving} type="submit">
        Crear recurso
      </button>
    </form>
  );
}
