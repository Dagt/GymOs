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
    <div aria-modal="true" className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 p-4 backdrop-blur-md animate-in fade-in duration-300" onClick={onClose} role="dialog">
      <div className="w-full max-w-md rounded-3xl bg-white p-8 shadow-2xl scale-in-95 animate-in" onClick={(event) => event.stopPropagation()}>
        <div className={`mx-auto flex h-16 w-16 items-center justify-center rounded-2xl text-2xl font-black shadow-lg ${isError ? "bg-red-50 text-red-600 shadow-red-100" : "bg-green-50 text-green-600 shadow-green-100"}`}>
          {isError ? "!" : "✓"}
        </div>
        <h2 className="mt-6 text-center text-2xl font-black text-[color:var(--brand-primary)] tracking-tight">{notice.title}</h2>
        <p className="mt-3 text-center text-sm font-medium leading-relaxed text-[color:var(--brand-muted)]">{notice.message}</p>
        <button autoFocus className={`mt-8 h-14 w-full rounded-2xl px-4 text-sm font-black text-white shadow-xl transition-all hover:brightness-110 active:scale-95 ${isError ? "bg-red-600 shadow-red-200" : "bg-[var(--brand-primary)] shadow-[color:var(--brand-primary)]/20"}`} onClick={onClose} type="button">
          ENTENDIDO
        </button>
      </div>
    </div>
  );
}

export function BookingNoteModal({ dialog, onClose }: { dialog: BookingNoteDialog; onClose: () => void }) {
  return (
    <div aria-modal="true" className="fixed inset-0 z-[120] flex items-center justify-center bg-black/60 p-4 backdrop-blur-md animate-in fade-in duration-300" onClick={onClose} role="dialog">
      <div className="w-full max-w-lg rounded-3xl bg-white p-8 shadow-2xl" onClick={(event) => event.stopPropagation()}>
        <p className="text-center text-[0.65rem] font-black uppercase tracking-[0.2em] text-[var(--brand-primary)] opacity-60 mb-2">Comentario de Reserva</p>
        <h2 className="text-center text-2xl font-black text-[color:var(--brand-primary)] mb-6">{dialog.clientName}</h2>
        <div className="whitespace-pre-wrap rounded-2xl border-2 border-dashed border-[var(--brand-line)] bg-[var(--brand-surface)] p-6 text-sm font-medium leading-relaxed text-[color:var(--brand-ink)]">
          {dialog.note}
        </div>
        <button autoFocus className="mt-8 h-14 w-full rounded-2xl bg-[var(--brand-primary)] px-4 text-sm font-black text-white shadow-xl shadow-[color:var(--brand-primary)]/20 hover:brightness-110 active:scale-95 transition-all" onClick={onClose} type="button">
          CERRAR VENTANA
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
    <section className="app-card p-6">
      <p className="text-[0.65rem] font-black uppercase tracking-widest text-[color:var(--brand-primary)] opacity-60">Agenda Detallada</p>
      <h2 className="text-xl font-black text-[color:var(--brand-primary)] mt-1 mb-5">{formatLongDate(selectedDate)}</h2>
      <div className="grid gap-4">
        <article className="rounded-2xl border-2 border-[var(--brand-line)] bg-[var(--brand-surface)] p-5 relative overflow-hidden">
          <div className="absolute top-0 right-0 w-24 h-24 bg-[color:var(--brand-primary)] opacity-[0.03] rounded-bl-full"></div>
          <span className="text-[0.65rem] font-black uppercase tracking-wider text-[color:var(--brand-primary)] opacity-60">Status de Operación</span>
          <strong className="mt-1 block text-2xl font-black text-[color:var(--brand-primary)]">
            {openResourceCount} / {resources.length}
          </strong>
          <p className="mt-1 text-xs font-bold text-gray-400 uppercase tracking-tighter">Recursos disponibles hoy</p>
        </article>
        
        <div className="bookings-resource-summary-scroll custom-scrollbar pr-1 max-h-[400px] overflow-y-auto">
          {resources.length === 0 ? (
            <p className="py-8 text-center text-xs font-black text-gray-400 uppercase tracking-widest bg-gray-50 rounded-2xl border-2 border-dashed border-gray-100">Sin recursos operativos</p>
          ) : null}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {resources.map((resource) => (
              <article className="group flex items-center justify-between p-4 rounded-xl border-2 border-gray-50 bg-gray-50/50 hover:bg-white hover:border-[color:var(--brand-primary)]/30 hover:shadow-md transition-all duration-300" key={resource.id}>
                <div className="flex items-center gap-3 overflow-hidden">
                  <span className="h-3 w-3 shrink-0 rounded-full shadow-sm" style={{ backgroundColor: resource.color }} />
                  <strong className="truncate text-sm font-black text-gray-700 group-hover:text-[color:var(--brand-primary)] transition-colors">{resource.name}</strong>
                </div>
                <div className="flex items-center gap-3">
                  <span className="shrink-0 rounded-lg bg-white border border-gray-100 px-2 py-1 text-[10px] font-black text-[var(--brand-primary)] shadow-sm">
                    {resource.bookingCount} Reservas
                  </span>
                  {canManageResources && (
                    <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button onClick={() => onEditResource?.(resource.id)} className="p-1.5 rounded-md hover:bg-gray-100 text-gray-400 hover:text-[color:var(--brand-primary)]" title="Editar">✎</button>
                      <button onClick={() => onDeleteResource?.(resource.id)} className="p-1.5 rounded-md hover:bg-red-50 text-gray-400 hover:text-red-500" title="Eliminar">🗑</button>
                    </div>
                  )}
                </div>
              </article>
            ))}
          </div>
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
    <section className={`app-card p-6 ${className}`}>
      <div className="border-b border-[var(--brand-line)] pb-6 mb-6">
        <div className="flex justify-between items-end gap-4 flex-wrap">
          <div>
            <h2 className="text-2xl font-black text-[color:var(--brand-primary)]">Calendario Maestro</h2>
            <p className="mt-1 text-sm font-medium text-[var(--brand-muted)]">Visualización mensual de disponibilidad.</p>
          </div>
          <button className="h-10 px-6 rounded-xl bg-[color:var(--brand-primary)] text-white text-xs font-black uppercase tracking-widest shadow-lg hover:brightness-110 active:scale-95 transition-all" onClick={onGoToToday} type="button">Hoy</button>
        </div>
        
        <div className="mt-6 flex items-center justify-between bg-[var(--brand-surface)] p-3 rounded-2xl border-2 border-[var(--brand-line)] shadow-inner">
          <button aria-label="Mes anterior" className="h-10 w-10 rounded-xl bg-white shadow-sm border border-[var(--brand-line)] font-black text-[color:var(--brand-primary)] hover:bg-gray-50 transition-colors" onClick={() => onMoveMonth(-1)} type="button">←</button>
          <div className="text-center">
            <p className="text-[10px] font-black uppercase tracking-[0.2em] text-[color:var(--brand-primary)] opacity-50">Periodo</p>
            <p className="text-lg font-black capitalize text-[color:var(--brand-primary)]">{formatMonthLabel(calendarDate)}</p>
          </div>
          <button aria-label="Mes siguiente" className="h-10 w-10 rounded-xl bg-white shadow-sm border border-[var(--brand-line)] font-black text-[color:var(--brand-primary)] hover:bg-gray-50 transition-colors" onClick={() => onMoveMonth(1)} type="button">→</button>
        </div>
      </div>

      <div className="flex flex-wrap gap-2 mb-6">
        {resources.map((resource, index) => (
          <button className={`inline-flex items-center gap-2 rounded-xl border-2 px-4 py-2 text-xs font-black transition-all ${resource.id === resourceId ? "border-[var(--brand-primary)] bg-[var(--brand-primary)] text-white shadow-md shadow-[color:var(--brand-primary)]/20" : "border-[var(--brand-line)] bg-white text-[color:var(--brand-muted)] hover:border-[var(--brand-primary)]/30"}`} key={resource.id} onClick={() => onSelectResource(resource.id)} type="button">
            <span className="h-2.5 w-2.5 rounded-full shadow-sm" style={{ backgroundColor: getResourceColor(index) }} />
            {resource.name.toUpperCase()}
          </button>
        ))}
      </div>

      {resources.length === 0 ? (
        <p className="mt-6 rounded-2xl border-2 border-dashed border-[var(--brand-line)] bg-gray-50 px-3 py-12 text-center text-sm font-bold text-[var(--brand-muted)] italic">No hay recursos configurados en esta sede.</p>
      ) : (
        <div className="mt-4 grid grid-cols-7 overflow-hidden rounded-2xl border-2 border-[var(--brand-line)] shadow-sm bg-[var(--brand-line)] gap-[2px]">
          {weekdays.map((day) => (
            <div className="bg-white py-3 text-center text-[10px] font-black uppercase tracking-widest text-[color:var(--brand-primary)] opacity-40" key={day.key}>{day.shortLabel}</div>
          ))}
          {monthDates.map((day) => {
            const dayBookings = activeBookings.filter((booking) => bookingDate(booking.startsAt) === day.date);
            const activeResourceCount = new Set(dayBookings.map((booking) => booking.resourceId)).size;
            const isSelected = selectedDate === day.date;
            const isToday = today === day.date;
            return (
              <button aria-current={isToday ? "date" : undefined} className={`relative min-h-[100px] p-2 text-left transition flex flex-col group ${day.inCurrentMonth ? "bg-white" : "bg-gray-50/50 text-gray-300"} ${isSelected ? "bg-[color:var(--brand-surface)] ring-4 ring-[var(--brand-primary)]/20 ring-inset" : "hover:bg-gray-50"}`} key={day.date} onClick={() => onSelectDate(day.date)} type="button">
                <div className="flex items-center justify-between">
                  <span className={`h-7 min-w-[28px] inline-flex items-center justify-center rounded-lg text-xs font-black ${isSelected ? "bg-[var(--brand-primary)] text-white" : isToday ? "bg-[var(--brand-accent)] text-white" : "text-gray-900"}`}>{day.dayOfMonth}</span>
                  {isToday && <span className="w-1.5 h-1.5 rounded-full bg-[var(--brand-accent)] animate-pulse"></span>}
                </div>
                
                <div className="mt-2 flex-grow">
                   {dayBookings.length > 0 ? (
                     <div className="px-1.5 py-1 bg-[color:var(--brand-primary)]/5 rounded-md border border-[color:var(--brand-primary)]/10">
                        <span className="text-[10px] font-black text-[color:var(--brand-primary)]">{dayBookings.length} EVENTOS</span>
                     </div>
                   ) : day.inCurrentMonth && (
                     <span className="text-[9px] font-bold text-gray-300 opacity-0 group-hover:opacity-100 transition-opacity ml-1 uppercase">Libre</span>
                   )}
                </div>

                {activeResourceCount > 0 && (
                  <div className="mt-auto pt-1 flex gap-1">
                    {resources.slice(0, 5).map((resource, index) => (
                      dayBookings.some((booking) => booking.resourceId === resource.id)
                        ? <span className="h-1.5 w-1.5 rounded-full shadow-sm" key={resource.id} style={{ backgroundColor: getResourceColor(index) }} />
                        : null
                    ))}
                  </div>
                )}
              </button>
            );
          })}
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
    <section className={`app-card p-6 bg-gray-50/30 ${className}`}>
      <div className="flex items-start justify-between gap-4 mb-6">
        <div>
          <h3 className="text-xl font-black text-[color:var(--brand-primary)]">{formatLongDate(selectedDate)}</h3>
          <p className="text-xs font-bold text-[color:var(--brand-muted)] uppercase tracking-widest mt-1 opacity-60">Visión Diaria por Recurso</p>
        </div>
        <div className="bg-white px-4 py-2 rounded-xl border border-[var(--brand-line)] shadow-sm">
           <span className="text-sm font-black text-[color:var(--brand-primary)]">{bookings.length}</span>
           <span className="text-[10px] font-bold text-[color:var(--brand-muted)] uppercase ml-2 tracking-tighter">Reservas</span>
        </div>
      </div>

      <div className="flex snap-x gap-5 overflow-x-auto pb-4 custom-scrollbar">
        {resources.map((resource, index) => {
          const resourceBookings = bookings.filter((booking) => booking.resourceId === resource.id);
          const hours = getResourceHours(resource);
          return (
            <article className="w-[300px] shrink-0 snap-start rounded-2xl border-2 border-[var(--brand-line)] bg-white p-5 shadow-sm hover:shadow-md transition-shadow" key={resource.id}>
              <div className="mb-4 border-b border-gray-50 pb-4">
                <div className="flex items-center gap-3">
                  <span className="h-3.5 w-3.5 rounded-full shadow-inner" style={{ backgroundColor: getResourceColor(index) }} />
                  <strong className="text-base font-black text-gray-900 truncate uppercase tracking-tight">{resource.name}</strong>
                </div>
                <p className="mt-1.5 text-[10px] font-black text-[color:var(--brand-primary)] opacity-40 uppercase tracking-[0.1em]">{hours ? `${hours.opensAt} — ${hours.closesAt}` : "Cerrado"}</p>
              </div>
              <div className="space-y-3 max-h-[400px] overflow-y-auto pr-1 custom-scrollbar">
                {resourceBookings.length === 0 ? (
                  <div className="py-12 flex flex-col items-center justify-center bg-gray-50 rounded-xl border border-dashed border-gray-200">
                     <span className="text-xl opacity-30">📅</span>
                     <p className="text-[10px] font-black text-gray-400 mt-2">SIN RESERVAS</p>
                  </div>
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
    <article className="rounded-xl p-4 text-white shadow-lg relative overflow-hidden group/card" style={{ backgroundColor: color }}>
      <div className="absolute top-0 right-0 w-16 h-16 bg-white/10 rounded-bl-full pointer-events-none"></div>
      
      <div className="relative z-10">
        <span className="inline-block px-2 py-0.5 bg-black/20 rounded-md text-[9px] font-black uppercase tracking-widest mb-2">{bookingStatusLabel(booking.status)}</span>
        <p className="font-black text-sm leading-tight mb-1">{booking.clientName}</p>
        <p className="text-[11px] font-bold opacity-80">{formatTime(booking.startsAt)} - {formatTime(booking.endsAt)}</p>
        
        <div className={`mt-4 grid gap-2 ${canCancel ? "grid-cols-2" : "grid-cols-1"}`}>
          {canCancel && (
            <button 
              onClick={() => onCancel(booking.id)} 
              className="h-8 rounded-lg bg-black/10 hover:bg-black/20 text-[10px] font-black uppercase tracking-wider transition-colors border border-white/10"
              type="button"
            >
              Cancelar
            </button>
          )}
          <button
            className={`h-8 rounded-lg text-[10px] font-black uppercase tracking-wider transition-all border border-white/10 ${hasNote ? "bg-white text-gray-900 shadow-sm" : "bg-black/10 text-white opacity-50"}`}
            disabled={!hasNote}
            onClick={() => onShowNote(booking)}
            type="button"
          >
            {hasNote ? "Ver Nota" : "Sin Nota"}
          </button>
        </div>
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
