"use client";

import type { BookingSummary, ResourceSummary } from "@/lib/api";
import { bookingDate, formatLongDate, formatMonthLabel } from "./booking-utils";

export type BookingNotice = { title: string; message: string; tone: "error" | "success" };
export type BookingNoteDialog = { clientName: string; note: string };

type ResourceDaySummary = {
  bookingCount: number;
  color: string;
  hours: { opensAt: string; closesAt: string } | null;
  id: string;
  name: string;
  slotMinutes: number;
};

type MonthDate = {
  date: string;
  dayOfMonth: number;
  inCurrentMonth: boolean;
};

type WeekdayLabel = {
  key: string;
  shortLabel: string;
};

export function BookingNoticeModal({ notice, onClose }: { notice: BookingNotice; onClose: () => void }) {
  const isError = notice.tone === "error";
  return (
    <div aria-modal="true" className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm" onClick={onClose} role="dialog">
      <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl" onClick={(event) => event.stopPropagation()}>
        <div className={`mx-auto flex h-14 w-14 items-center justify-center rounded-full text-2xl font-bold ${isError ? "bg-red-100 text-red-700" : "bg-green-100 text-green-700"}`}>
          {isError ? "!" : "OK"}
        </div>
        <h2 className="mt-4 text-center text-xl font-bold text-[#243229]">{notice.title}</h2>
        <p className="mt-2 text-center text-sm leading-6 text-[#5d6b63]">{notice.message}</p>
        <button autoFocus className={`mt-6 h-11 w-full rounded-md px-4 text-sm font-semibold text-white ${isError ? "bg-red-700" : "bg-[var(--brand-primary)]"}`} onClick={onClose} type="button">
          Aceptar
        </button>
      </div>
    </div>
  );
}

export function BookingNoteModal({ dialog, onClose }: { dialog: BookingNoteDialog; onClose: () => void }) {
  return (
    <div aria-modal="true" className="fixed inset-0 z-[120] flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm" onClick={onClose} role="dialog">
      <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl" onClick={(event) => event.stopPropagation()}>
        <p className="text-center text-xs font-semibold uppercase tracking-[0.14em] text-[var(--brand-primary)]">Nota de la reserva</p>
        <h2 className="mt-2 text-center text-xl font-bold text-[#243229]">{dialog.clientName}</h2>
        <p className="mt-4 whitespace-pre-wrap rounded-lg border border-[#d8ddd2] bg-[#f8faf8] p-4 text-sm leading-6 text-[#243229]">{dialog.note}</p>
        <button autoFocus className="mt-6 h-10 w-full rounded-md bg-[var(--brand-primary)] px-4 text-sm font-semibold text-white" onClick={onClose} type="button">
          Cerrar
        </button>
      </div>
    </div>
  );
}

export function BookingSummaryPanel({
  canManageResources = false,
  onDeleteResource,
  onEditResource,
  openResourceCount,
  resources,
  selectedDate,
}: {
  canManageResources?: boolean;
  onDeleteResource?: (resourceId: string) => void;
  onEditResource?: (resourceId: string) => void;
  openResourceCount: number;
  resources: ResourceDaySummary[];
  selectedDate: string;
}) {
  return (
    <section className="app-card p-5">
      <p className="app-eyebrow">Resumen</p>
      <h2 className="app-card-title">{formatLongDate(selectedDate)}</h2>
      <div className="mt-4 grid gap-3">
        <article className="rounded-md border border-[#d8ddd2] bg-white px-3 py-3">
          <span className="text-xs font-semibold uppercase text-[#647067]">Operacion del dia</span>
          <strong className="mt-1 block text-lg">
            {openResourceCount} de {resources.length} recursos abiertos
          </strong>
          <p className="mt-1 text-xs text-[#647067]">Resumen rapido de disponibilidad por recurso.</p>
        </article>
        <div className="bookings-resource-summary-scroll">
          {resources.length === 0 ? (
            <p className="col-span-2 rounded-md border border-[#d8ddd2] bg-white px-3 py-3 text-sm text-[#647067]">No hay recursos configurados.</p>
          ) : null}
          {resources.map((resource) => (
            <article className="bookings-resource-summary-card" key={resource.id}>
              <div className="flex items-start justify-between gap-2">
                <span className="mt-1 h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: resource.color }} />
                <strong className="min-w-0 flex-1 truncate text-xs">{resource.name}</strong>
                <span className="shrink-0 rounded-full bg-[var(--brand-surface)] px-1.5 py-0.5 text-[10px] font-semibold text-[var(--brand-primary)]">
                  {resource.bookingCount}
                </span>
              </div>
              <p className="mt-1 truncate text-[11px] text-[#647067]">
                {resource.hours ? `${resource.hours.opensAt} - ${resource.hours.closesAt}` : "Cerrado"}
              </p>
              <p className="mt-0.5 text-[11px] text-[#647067]">{resource.slotMinutes} min</p>
              {canManageResources ? (
                <div className="mt-2 grid grid-cols-2 gap-1">
                  <button className="resource-mini-action" onClick={() => onEditResource?.(resource.id)} type="button">Editar</button>
                  <button className="resource-mini-action danger" onClick={() => onDeleteResource?.(resource.id)} type="button">Eliminar</button>
                </div>
              ) : null}
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

export function BookingsMonthCalendar({
  activeBookings,
  calendarDate,
  className = "",
  getResourceColor,
  monthDates,
  onGoToToday,
  onMoveMonth,
  onSelectDate,
  onSelectResource,
  resourceId,
  resources,
  selectedDate,
  today,
  weekdays,
}: {
  activeBookings: BookingSummary[];
  calendarDate: string;
  className?: string;
  getResourceColor: (index: number) => string;
  monthDates: MonthDate[];
  onGoToToday: () => void;
  onMoveMonth: (months: number) => void;
  onSelectDate: (date: string) => void;
  onSelectResource: (resourceId: string) => void;
  resourceId: string;
  resources: ResourceSummary[];
  selectedDate: string;
  today: string;
  weekdays: WeekdayLabel[];
}) {
  return (
    <section className={`rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm ${className}`}>
      <div className="border-b border-[#e5e9e3] pb-5">
        <div>
          <h2 className="text-xl font-semibold">Agenda mensual</h2>
          <p className="mt-1 text-sm text-[#5d6b63]">Selecciona un dia para ver sus reservas por recurso.</p>
        </div>
        <div className="mt-4 grid gap-3 rounded-2xl border border-[#d8ddd2] bg-[#f8faf8] p-3 shadow-inner sm:grid-cols-[44px_minmax(170px,1fr)_44px] lg:grid-cols-[44px_minmax(180px,1fr)_auto_auto_44px] lg:items-center">
          <button aria-label="Mes anterior" className="h-11 w-11 rounded-xl border border-[#cdd5ca] bg-white text-xl font-bold text-[var(--brand-primary)] shadow-sm hover:bg-[var(--brand-surface)]" onClick={() => onMoveMonth(-1)} type="button">{"<"}</button>
          <div className="text-center lg:text-left">
            <p className="text-[11px] font-semibold uppercase tracking-[0.14em] text-[#647067]">Mes visible</p>
            <p className="text-base font-bold capitalize text-[#243229]">{formatMonthLabel(calendarDate)}</p>
          </div>
          <button aria-label="Mes siguiente" className="h-11 w-11 rounded-xl border border-[#cdd5ca] bg-white text-xl font-bold text-[var(--brand-primary)] shadow-sm hover:bg-[var(--brand-surface)] lg:order-5" onClick={() => onMoveMonth(1)} type="button">{">"}</button>
          <input aria-label="Seleccionar fecha" className="h-11 rounded-xl border border-[#cdd5ca] bg-white px-3 text-sm sm:col-span-2 lg:col-span-1" onChange={(event) => onSelectDate(event.target.value)} type="date" value={calendarDate} />
          <button className="h-11 rounded-xl bg-[var(--brand-primary)] px-5 text-sm font-semibold text-white shadow-sm sm:col-span-3 lg:col-span-1" onClick={onGoToToday} type="button">Ir a hoy</button>
        </div>
      </div>

      <div className="mt-5 flex flex-wrap gap-2">
        {resources.map((resource, index) => (
          <button className={`inline-flex items-center gap-2 rounded-md border px-3 py-2 text-sm ${resource.id === resourceId ? "border-[var(--brand-primary)] bg-[var(--brand-surface)]" : "border-[#d8ddd2]"}`} key={resource.id} onClick={() => onSelectResource(resource.id)} type="button">
            <span className="h-3 w-3 rounded-full" style={{ backgroundColor: getResourceColor(index) }} />
            {resource.name}
          </button>
        ))}
      </div>

      {resources.length === 0 ? (
        <p className="mt-6 rounded-md border border-[#d8ddd2] bg-[#f8faf8] px-3 py-6 text-center text-sm text-[#5d6b63]">No hay recursos reservables en esta sucursal.</p>
      ) : (
        <div className="mt-5 grid gap-5">
          <div className="grid grid-cols-7 overflow-hidden rounded-lg border border-[#d8ddd2]">
            {weekdays.map((day) => (
              <div className="border-b border-r border-[#d8ddd2] bg-[#f8faf8] px-3 py-2 text-center text-xs font-semibold uppercase text-[#647067] last:border-r-0" key={day.key}>{day.shortLabel}</div>
            ))}
            {monthDates.map((day) => {
              const dayBookings = activeBookings.filter((booking) => bookingDate(booking.startsAt) === day.date);
              const activeResourceCount = new Set(dayBookings.map((booking) => booking.resourceId)).size;
              const isSelected = selectedDate === day.date;
              const isToday = today === day.date;
              return (
                <button aria-current={isToday ? "date" : undefined} className={`relative min-h-24 border-r border-t p-2 text-left transition last:border-r-0 ${day.inCurrentMonth ? "bg-white" : "bg-[#f6f3ef] text-[#9aa39d]"} ${isSelected ? "z-10 border-[var(--brand-primary)] bg-[var(--brand-surface)] ring-4 ring-[var(--brand-primary)] ring-offset-[-4px]" : isToday ? "border-2 border-[#1d4ed8] bg-[#eff6ff] shadow-inner" : "border-[#d8ddd2] hover:bg-[var(--brand-surface)]"}`} key={day.date} onClick={() => onSelectDate(day.date)} type="button">
                  <div className="flex items-center justify-between gap-1">
                    <span className={`inline-flex h-8 min-w-8 items-center justify-center rounded-full px-2 text-sm font-bold ${isSelected ? "bg-[var(--brand-primary)] text-white" : isToday ? "bg-[#1d4ed8] text-white" : ""}`}>{day.dayOfMonth}</span>
                    {isToday ? <span className="rounded-full bg-[#dbeafe] px-2 py-0.5 text-[10px] font-bold uppercase text-[#1d4ed8]">Hoy</span> : null}
                  </div>
                  {dayBookings.length > 0 ? (
                    <span className="mt-2 block rounded-md bg-white/80 px-2 py-1 text-xs font-semibold text-[var(--brand-primary)]">
                      {dayBookings.length} reserva{dayBookings.length === 1 ? "" : "s"}
                    </span>
                  ) : (
                    <span className="mt-2 block text-xs text-[#8a948d]">Libre</span>
                  )}
                  {activeResourceCount > 0 ? (
                    <span className="mt-2 flex gap-1">
                      {resources.slice(0, 4).map((resource, index) => (
                        dayBookings.some((booking) => booking.resourceId === resource.id)
                          ? <span className="h-2 w-2 rounded-full" key={resource.id} style={{ backgroundColor: getResourceColor(index) }} />
                          : null
                      ))}
                    </span>
                  ) : null}
                </button>
              );
            })}
          </div>
        </div>
      )}
    </section>
  );
}

export function BookingDayDetail({
  bookings,
  className = "",
  getResourceColor,
  getResourceHours,
  onCancel,
  onShowNote,
  resources,
  selectedDate,
}: {
  bookings: BookingSummary[];
  className?: string;
  getResourceColor: (index: number) => string;
  getResourceHours: (resource: ResourceSummary) => { opensAt: string; closesAt: string } | null;
  onCancel: (bookingId: string) => void;
  onShowNote: (booking: BookingSummary) => void;
  resources: ResourceSummary[];
  selectedDate: string;
}) {
  return (
    <section className={`rounded-lg border border-[#d8ddd2] bg-[#fbfcfa] p-4 ${className}`}>
      <div className="flex items-start justify-between gap-4">
        <div>
          <h3 className="text-lg font-semibold">{formatLongDate(selectedDate)}</h3>
          <p className="text-sm text-[#5d6b63]">Detalle del dia seleccionado.</p>
        </div>
        <span className="rounded-full bg-white px-3 py-1 text-xs font-semibold text-[#5d6b63] shadow-sm">
          {bookings.length} reserva{bookings.length === 1 ? "" : "s"}
        </span>
      </div>
      <p className="mt-3 text-xs text-[#647067]">Consulta todos los recursos y sus reservas del dia.</p>
      <div className="mt-4 flex snap-x gap-4 overflow-x-auto pb-3">
        {resources.map((resource, index) => {
          const resourceBookings = bookings.filter((booking) => booking.resourceId === resource.id);
          const hours = getResourceHours(resource);
          return (
            <article className="w-[290px] shrink-0 snap-start rounded-xl border border-[#d8ddd2] bg-white p-3 shadow-sm sm:w-[320px]" key={resource.id}>
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold">
                  <span className="mr-2 inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: getResourceColor(index) }} />
                  {resource.name}
                </p>
                <p className="mt-0.5 text-xs text-[#647067]">{hours ? `${hours.opensAt} - ${hours.closesAt}` : "Cerrado"}</p>
              </div>
              <div className="mt-3 max-h-[24rem] space-y-2 overflow-y-auto pr-1">
                {resourceBookings.length === 0 ? (
                  <p className="rounded-md bg-[#f8faf8] px-2 py-3 text-center text-xs text-[#647067]">Sin reservas</p>
                ) : resourceBookings.map((booking) => (
                  <BookingCard
                    booking={booking}
                    color={getResourceColor(index)}
                    key={booking.id}
                    onCancel={onCancel}
                    onShowNote={onShowNote}
                  />
                ))}
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}

function BookingCard({
  booking,
  color,
  onCancel,
  onShowNote,
}: {
  booking: BookingSummary;
  color: string;
  onCancel: (bookingId: string) => void;
  onShowNote: (booking: BookingSummary) => void;
}) {
  const canCancel = booking.status !== "COMPLETED" && booking.status !== "CANCELLED";
  const hasNote = Boolean(booking.notes?.trim());
  return (
    <article className="rounded-md px-3 py-3 text-white shadow-sm" style={{ backgroundColor: color }}>
      <p className="text-[10px] font-semibold uppercase tracking-wide opacity-80">{bookingStatusLabel(booking.status)}</p>
      <p className="mt-1 truncate text-sm font-semibold">{booking.clientName}</p>
      <p className="text-xs opacity-90">{formatTime(booking.startsAt)} - {formatTime(booking.endsAt)}</p>
      <div className={`booking-card-actions ${canCancel ? "" : "grid-cols-1"}`}>
        {canCancel ? <button onClick={() => onCancel(booking.id)} type="button">Cancelar</button> : null}
        <button
          className={`booking-note-button ${hasNote ? "has-note" : "no-note"}`}
          disabled={!hasNote}
          onClick={() => onShowNote(booking)}
          title={hasNote ? "Ver nota de la reserva" : "Esta reserva no tiene nota"}
          type="button"
        >
          {hasNote ? "Ver nota" : "Sin nota"}
        </button>
      </div>
    </article>
  );
}

function bookingStatusLabel(status: string) {
  const labels: Record<string, string> = { RESERVED: "Reservada", CONFIRMED: "Confirmada", CHECKED_IN: "En uso", COMPLETED: "Completada", CANCELLED: "Cancelada" };
  return labels[status] ?? status;
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", { hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}
