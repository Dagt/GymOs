"use client";

import { type FormEvent, type ReactNode, useState } from "react";
import { PhoneField } from "@/components/FormFields";
import { buildMonthDates } from "@/lib/bookings";
import { toDateInput } from "@/lib/date-time";
import type {
  ClientSummary,
  ProfessionalServiceSummary,
  ProfessionalSummary,
  WellnessAppointment,
  WellnessAvailabilitySlot,
} from "@/lib/api";

export type WellnessTabKey = "agenda" | "professionals";

export type ProfessionalScheduleDay = {
  dayOfWeek: number;
  dayName: string;
  enabled: boolean;
  opensAt: string;
  closesAt: string;
};

export const DEFAULT_WEEKLY_SCHEDULE: ProfessionalScheduleDay[] = [
  { dayOfWeek: 1, dayName: "Lunes", enabled: true, opensAt: "09:00", closesAt: "14:00" },
  { dayOfWeek: 2, dayName: "Martes", enabled: true, opensAt: "09:00", closesAt: "14:00" },
  { dayOfWeek: 3, dayName: "Miércoles", enabled: true, opensAt: "09:00", closesAt: "14:00" },
  { dayOfWeek: 4, dayName: "Jueves", enabled: true, opensAt: "09:00", closesAt: "14:00" },
  { dayOfWeek: 5, dayName: "Viernes", enabled: true, opensAt: "09:00", closesAt: "14:00" },
  { dayOfWeek: 6, dayName: "Sábado", enabled: false, opensAt: "09:00", closesAt: "13:00" },
  { dayOfWeek: 7, dayName: "Domingo", enabled: false, opensAt: "09:00", closesAt: "13:00" },
];

export const SPECIALTY_CONSULTATIONS: Record<
  string,
  { serviceName: string; serviceType: string; defaultDuration: number; defaultPrice: string }
> = {
  NUTRITIONIST: {
    serviceName: "Consulta de Nutrición",
    serviceType: "NUTRITION",
    defaultDuration: 45,
    defaultPrice: "500",
  },
  PHYSIOTHERAPIST: {
    serviceName: "Consulta de Fisioterapia",
    serviceType: "PHYSIOTHERAPY",
    defaultDuration: 60,
    defaultPrice: "600",
  },
  CHIROPRACTOR: {
    serviceName: "Consulta de Quiropráctica",
    serviceType: "CHIROPRACTIC",
    defaultDuration: 30,
    defaultPrice: "500",
  },
  WELLNESS: {
    serviceName: "Consulta de Bienestar General",
    serviceType: "WELLNESS",
    defaultDuration: 45,
    defaultPrice: "450",
  },
};

export function WellnessNotice({ tone, text }: { tone: "ok" | "error"; text: string }) {
  const styles =
    tone === "ok"
      ? "border-[var(--brand-line)] bg-[var(--brand-surface)] text-[#31563c]"
      : "border-[#e5b7b7] bg-[#fff4f4] text-[#8c2f2f]";
  return <p className={`rounded-lg border px-3 py-2 text-sm font-semibold ${styles}`}>{text}</p>;
}

export function WellnessTabs({
  activeTab,
  requestedCount,
  onTabChange,
}: {
  activeTab: WellnessTabKey;
  requestedCount: number;
  onTabChange: (tab: WellnessTabKey) => void;
}) {
  return (
    <div className="flex flex-wrap border-b border-[var(--brand-line)] bg-white px-2 pt-2 gap-1 rounded-t-xl">
      <TabButton
        active={activeTab === "agenda"}
        badge={requestedCount > 0 ? requestedCount : undefined}
        label="Agenda & Solicitudes"
        onClick={() => onTabChange("agenda")}
      />
      <TabButton
        active={activeTab === "professionals"}
        label="Equipo Profesional"
        onClick={() => onTabChange("professionals")}
      />
    </div>
  );
}

function TabButton({
  active,
  badge,
  label,
  onClick,
}: {
  active: boolean;
  badge?: number;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      className={`relative flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-bold transition-colors ${
        active
          ? "border-[var(--brand-primary)] text-[var(--brand-primary)]"
          : "border-transparent text-[var(--brand-muted)] hover:text-[var(--brand-ink)]"
      }`}
      onClick={onClick}
      type="button"
    >
      <span>{label}</span>
      {badge !== undefined ? (
        <span className="rounded-full bg-amber-500 px-2 py-0.5 text-xs font-black text-white animate-pulse">
          {badge}
        </span>
      ) : null}
    </button>
  );
}

export function AppointmentsTabSection({
  appointments,
  date,
  loadingAppointments,
  professionals,
  selectedProfessionalId,
  statusFilter,
  onCancel,
  onComplete,
  onConfirm,
  onCoordinate,
  onDateChange,
  onOpenNewModal,
  onProfessionalChange,
  onStatusFilterChange,
}: {
  appointments: WellnessAppointment[];
  date: string;
  loadingAppointments: boolean;
  professionals: ProfessionalSummary[];
  selectedProfessionalId: string;
  statusFilter: string;
  onCancel: (appointmentId: string) => void;
  onComplete: (appointmentId: string) => void;
  onConfirm: (appointmentId: string) => void;
  onCoordinate: (appointment: WellnessAppointment) => void;
  onDateChange: (date: string) => void;
  onOpenNewModal: () => void;
  onProfessionalChange: (professionalId: string) => void;
  onStatusFilterChange: (status: string) => void;
}) {
  const [viewMode, setViewMode] = useState<"day" | "week" | "month">("month");

  const requestedList = appointments.filter((a) => a.status === "REQUESTED");
  const confirmedCount = appointments.filter((a) => a.status === "CONFIRMED").length;
  const completedCount = appointments.filter((a) => a.status === "COMPLETED").length;

  // Calendar calculations using local timezone
  const todayIso = toDateInput(new Date());
  const weekDays = buildWeekDates(date);
  const monthDates = buildMonthDates(date);

  const handleOffsetDate = (days: number) => {
    const cur = new Date(date + "T12:00:00");
    cur.setDate(cur.getDate() + days);
    onDateChange(toDateInput(cur));
  };

  const handleOffsetMonth = (months: number) => {
    const cur = new Date(date + "T12:00:00");
    cur.setMonth(cur.getMonth() + months);
    onDateChange(toDateInput(cur));
  };

  return (
    <div className="grid gap-6">
      {/* Metrics Row */}
      <div className="grid gap-3 sm:grid-cols-3">
        <div className="rounded-xl border border-amber-200 bg-amber-50/50 p-3 flex items-center justify-between shadow-sm">
          <div>
            <p className="text-[10px] font-bold uppercase tracking-wider text-amber-800">Solicitudes Pendientes</p>
            <span className="text-[11px] text-amber-700">Por coordinar</span>
          </div>
          <strong className="text-xl font-black text-amber-900 bg-amber-100 px-2.5 py-1 rounded-lg border border-amber-300">
            {requestedList.length}
          </strong>
        </div>

        <div className="rounded-xl border border-emerald-200 bg-emerald-50/50 p-3 flex items-center justify-between shadow-sm">
          <div>
            <p className="text-[10px] font-bold uppercase tracking-wider text-emerald-800">Citas Confirmadas</p>
            <span className="text-[11px] text-emerald-700">Agendadas en sucursal</span>
          </div>
          <strong className="text-xl font-black text-emerald-900 bg-emerald-100 px-2.5 py-1 rounded-lg border border-emerald-300">
            {confirmedCount}
          </strong>
        </div>

        <div className="rounded-xl border border-sky-200 bg-sky-50/50 p-3 flex items-center justify-between shadow-sm">
          <div>
            <p className="text-[10px] font-bold uppercase tracking-wider text-sky-800">Consultas Completadas</p>
            <span className="text-[11px] text-sky-700">Atendidas con éxito</span>
          </div>
          <strong className="text-xl font-black text-sky-900 bg-sky-100 px-2.5 py-1 rounded-lg border border-sky-300">
            {completedCount}
          </strong>
        </div>
      </div>

      {/* Main Agenda Panel */}
      <div className="admin-panel space-y-4">
        <div className="admin-panel-header flex flex-wrap items-center justify-between gap-4">
          <div>
            <p>Control de Consultas y Especialistas</p>
            <h2>Agenda de Bienestar</h2>
          </div>
          <button
            className="form-primary-button w-auto py-2.5 px-4 font-bold"
            onClick={onOpenNewModal}
            type="button"
          >
            + Agendar / Solicitar Cita
          </button>
        </div>

        {/* Calendar Navigation and View Switcher */}
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-[var(--brand-line)] bg-stone-50 p-3 shadow-inner">
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1">
              <button
                className="h-9 w-9 rounded-lg border border-[var(--brand-line)] bg-white font-bold text-[var(--brand-primary)] hover:bg-stone-100"
                onClick={() => (viewMode === "month" ? handleOffsetMonth(-1) : handleOffsetDate(-7))}
                title="Anterior"
                type="button"
              >
                ‹
              </button>
              <button
                className="h-9 rounded-lg bg-[var(--brand-primary)] px-3 text-xs font-bold text-white shadow-sm"
                onClick={() => onDateChange(todayIso)}
                type="button"
              >
                Hoy
              </button>
              <button
                className="h-9 w-9 rounded-lg border border-[var(--brand-line)] bg-white font-bold text-[var(--brand-primary)] hover:bg-stone-100"
                onClick={() => (viewMode === "month" ? handleOffsetMonth(1) : handleOffsetDate(7))}
                title="Siguiente"
                type="button"
              >
                ›
              </button>
            </div>

            <div className="text-left border-l border-stone-200 pl-3">
              <p className="text-[10px] font-bold uppercase tracking-wider text-[var(--brand-muted)]">Mes visible</p>
              <strong className="text-sm font-black capitalize text-[var(--brand-ink)]">
                {formatMonthLabel(date)}
              </strong>
            </div>

            <input
              className="h-9 rounded-lg border border-[var(--brand-line)] bg-white px-2.5 text-xs font-semibold"
              onChange={(e) => onDateChange(e.target.value)}
              type="date"
              value={date}
            />
          </div>

          {/* View Mode Switcher */}
          <div className="flex items-center rounded-lg border border-[var(--brand-line)] bg-white p-1">
            <button
              className={`rounded-md px-3 py-1 text-xs font-bold transition-all ${
                viewMode === "day"
                  ? "bg-[var(--brand-primary)] text-white shadow-sm"
                  : "text-stone-600 hover:text-stone-900"
              }`}
              onClick={() => setViewMode("day")}
              type="button"
            >
              Día
            </button>
            <button
              className={`rounded-md px-3 py-1 text-xs font-bold transition-all ${
                viewMode === "week"
                  ? "bg-[var(--brand-primary)] text-white shadow-sm"
                  : "text-stone-600 hover:text-stone-900"
              }`}
              onClick={() => setViewMode("week")}
              type="button"
            >
              Semana
            </button>
            <button
              className={`rounded-md px-3 py-1 text-xs font-bold transition-all ${
                viewMode === "month"
                  ? "bg-[var(--brand-primary)] text-white shadow-sm"
                  : "text-stone-600 hover:text-stone-900"
              }`}
              onClick={() => setViewMode("month")}
              type="button"
            >
              Mes
            </button>
          </div>

          {/* Filter Options */}
          <div className="flex items-center gap-2 shrink-0">
            <select
              className="h-9 rounded-lg border border-[var(--brand-line)] bg-white px-3 text-xs font-bold text-[color:var(--brand-ink)] focus:border-[color:var(--brand-primary)] outline-none shadow-sm max-w-[200px]"
              onChange={(e) => onProfessionalChange(e.target.value)}
              value={selectedProfessionalId}
            >
              <option value="">Todos los especialistas</option>
              {professionals.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.displayName} ({p.professionalType})
                </option>
              ))}
            </select>

            <select
              className="h-9 rounded-lg border border-[var(--brand-line)] bg-white px-3 text-xs font-bold text-[color:var(--brand-ink)] focus:border-[color:var(--brand-primary)] outline-none shadow-sm max-w-[170px]"
              onChange={(e) => onStatusFilterChange(e.target.value)}
              value={statusFilter}
            >
              <option value="">Todos los estatus</option>
              <option value="REQUESTED">Solicitada</option>
              <option value="CONFIRMED">Confirmada</option>
              <option value="COMPLETED">Completada</option>
              <option value="CANCELLED">Cancelada</option>
            </select>
          </div>
        </div>

        {/* Content Views */}
        {loadingAppointments ? (
          <p className="py-8 text-center text-sm text-[var(--brand-muted)]">
            Cargando citas...
          </p>
        ) : viewMode === "month" ? (
          /* MONTH CALENDAR GRID */
          <div className="space-y-4">
            <div className="grid grid-cols-7 overflow-hidden rounded-xl border border-[var(--brand-line)] bg-white shadow-sm">
              {["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"].map((d) => (
                <div
                  className="border-b border-r border-[var(--brand-line)] bg-stone-50 py-2 text-center text-xs font-bold uppercase text-[var(--brand-muted)] last:border-r-0"
                  key={d}
                >
                  {d}
                </div>
              ))}
              {monthDates.map((day) => {
                const dayApps = appointments.filter((a) => a.startsAt.startsWith(day.date));
                const isSelected = date === day.date;
                const isToday = todayIso === day.date;

                return (
                  <button
                    className={`relative min-h-[60px] border-r border-t border-[var(--brand-line)] p-1.5 text-left transition-all last:border-r-0 ${
                      day.inCurrentMonth ? "bg-white" : "bg-stone-50/60 text-stone-400"
                    } ${
                      isSelected
                        ? "z-10 bg-emerald-50/80 ring-2 ring-[var(--brand-primary)]"
                        : isToday
                          ? "bg-sky-50/70"
                          : "hover:bg-stone-50"
                    }`}
                    key={day.date}
                    onClick={() => onDateChange(day.date)}
                    type="button"
                  >
                    <div className="flex items-center justify-between">
                      <span
                        className={`inline-flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-bold ${
                          isSelected
                            ? "bg-[var(--brand-primary)] text-white"
                            : isToday
                              ? "bg-sky-600 text-white"
                              : "text-stone-800"
                        }`}
                      >
                        {day.dayOfMonth}
                      </span>
                      {dayApps.length > 0 ? (
                        <span className="flex items-center gap-1 rounded-full bg-amber-500 px-1.5 py-0.2 text-[9px] font-black text-white shadow-sm">
                          <span className="h-1 w-1 rounded-full bg-white animate-pulse" />
                          {dayApps.length}
                        </span>
                      ) : null}
                    </div>

                    {dayApps.length > 0 ? (
                      <div className="mt-1 space-y-0.5 max-h-10 overflow-hidden">
                        {dayApps.slice(0, 2).map((app) => (
                          <div
                            className={`rounded px-1 py-0.2 text-[9px] font-bold truncate leading-tight ${
                              app.status === "REQUESTED"
                                ? "bg-amber-100 text-amber-900 border border-amber-300"
                                : app.status === "CONFIRMED"
                                  ? "bg-emerald-100 text-emerald-900 border border-emerald-300"
                                  : "bg-sky-100 text-sky-900 border border-sky-300"
                            }`}
                            key={app.id}
                          >
                            {formatTime(app.startsAt)} {app.clientFullName.split(" ")[0]}
                          </div>
                        ))}
                        {dayApps.length > 2 ? (
                          <span className="text-[8px] font-bold text-[var(--brand-primary)] pl-0.5">
                            +{dayApps.length - 2} más
                          </span>
                        ) : null}
                      </div>
                    ) : null}
                  </button>
                );
              })}
            </div>

            {/* Selected Day Details below month grid */}
            <div className="rounded-xl border border-[var(--brand-line)] bg-stone-50 p-4">
              <h3 className="text-sm font-black text-[var(--brand-ink)] mb-3">
                Citas para el {date} ({appointments.filter((a) => a.startsAt.startsWith(date)).length} citas)
              </h3>
              {appointments.filter((a) => a.startsAt.startsWith(date)).length === 0 ? (
                <p className="text-xs text-[var(--brand-muted)]">No hay citas registradas para este día.</p>
              ) : (
                <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                  {appointments
                    .filter((a) => a.startsAt.startsWith(date))
                    .map((app) => (
                      <AppointmentCard
                        appointment={app}
                        key={app.id}
                        onCancel={onCancel}
                        onComplete={onComplete}
                        onConfirm={onConfirm}
                        onCoordinate={onCoordinate}
                      />
                    ))}
                </div>
              )}
            </div>
          </div>
        ) : viewMode === "week" ? (
          /* WEEKLY COLUMNS GRID */
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-7 gap-3">
            {weekDays.map((wd) => {
              const dayApps = appointments.filter((a) => a.startsAt.startsWith(wd.date));
              const isToday = todayIso === wd.date;
              const isSelected = date === wd.date;

              return (
                <div
                  className={`rounded-xl border p-2.5 flex flex-col justify-start space-y-2.5 transition-all ${
                    isSelected
                      ? "border-[var(--brand-primary)] bg-emerald-50/30 ring-2 ring-[var(--brand-primary)]"
                      : isToday
                        ? "border-sky-300 bg-sky-50/30"
                        : "border-[var(--brand-line)] bg-white"
                  }`}
                  key={wd.date}
                >
                  <button
                    className="w-full text-left border-b border-[var(--brand-line)] pb-1.5"
                    onClick={() => onDateChange(wd.date)}
                    type="button"
                  >
                    <div className="flex items-center justify-between">
                      <strong className="text-xs font-black uppercase text-stone-700">
                        {wd.dayName}
                      </strong>
                      <div className="flex items-center gap-1">
                        {dayApps.length > 0 ? (
                          <span className="rounded-full bg-amber-500 px-1.5 py-0.2 text-[9px] font-black text-white">
                            {dayApps.length}
                          </span>
                        ) : null}
                        <span
                          className={`inline-flex h-5 min-w-[20px] items-center justify-center rounded-full px-1 text-[10px] font-black ${
                            isToday ? "bg-sky-600 text-white" : "bg-stone-100 text-stone-700"
                          }`}
                        >
                          {wd.dayOfMonth}
                        </span>
                      </div>
                    </div>
                  </button>

                  <div className="space-y-2 min-h-[140px]">
                    {dayApps.length === 0 ? (
                      <p className="text-[10px] italic text-stone-400 text-center py-6">Sin citas</p>
                    ) : (
                      dayApps.map((app) => (
                        <div
                          className="rounded-lg border border-[var(--brand-line)] bg-white p-2 text-xs shadow-sm space-y-1.5"
                          key={app.id}
                        >
                          <div className="flex items-center justify-between gap-1">
                            <span className="font-black text-[var(--brand-primary)] text-[10px]">
                              ⏰ {formatTime(app.startsAt)}
                            </span>
                            <span
                              className={`rounded px-1 py-0.2 text-[9px] font-black uppercase ${
                                app.status === "REQUESTED"
                                  ? "bg-amber-100 text-amber-900 border border-amber-300"
                                  : app.status === "CONFIRMED"
                                    ? "bg-emerald-100 text-emerald-900 border border-emerald-300"
                                    : "bg-sky-100 text-sky-900 border border-sky-300"
                              }`}
                            >
                              {app.status}
                            </span>
                          </div>

                          <strong className="block text-stone-900 font-black leading-tight text-xs">
                            👤 {app.clientFullName}
                          </strong>
                          <p className="text-[10px] text-stone-600 font-semibold truncate">
                            👨‍⚕️ {app.professionalName}
                          </p>

                          <div className="pt-1.5 flex items-center justify-end gap-1 border-t border-stone-100">
                            {app.status === "REQUESTED" ? (
                              <button
                                className="bg-amber-600 hover:bg-amber-700 text-white text-[10px] px-2 py-1 rounded font-bold"
                                onClick={() => onCoordinate(app)}
                                type="button"
                              >
                                Coordinar
                              </button>
                            ) : app.status === "CONFIRMED" ? (
                              <button
                                className="bg-sky-700 hover:bg-sky-800 text-white text-[10px] px-2 py-1 rounded font-bold"
                                onClick={() => onComplete(app.id)}
                                type="button"
                              >
                                Completar
                              </button>
                            ) : null}
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          /* DAILY LIST VIEW */
          <div>
            {appointments.length === 0 ? (
              <div className="rounded-xl border border-dashed border-[var(--brand-line)] p-8 text-center">
                <p className="text-sm font-semibold text-[var(--brand-muted)]">
                  No hay citas agendadas para los filtros seleccionados.
                </p>
                <button
                  className="app-button-secondary mt-3 py-1.5 px-3 text-xs"
                  onClick={onOpenNewModal}
                  type="button"
                >
                  + Registrar la primera cita
                </button>
              </div>
            ) : (
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {appointments.map((app) => (
                  <AppointmentCard
                    appointment={app}
                    key={app.id}
                    onCancel={onCancel}
                    onComplete={onComplete}
                    onConfirm={onConfirm}
                    onCoordinate={onCoordinate}
                  />
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function AppointmentCard({
  appointment,
  onCancel,
  onComplete,
  onConfirm,
  onCoordinate,
}: {
  appointment: WellnessAppointment;
  onCancel: (id: string) => void;
  onComplete: (id: string) => void;
  onConfirm: (id: string) => void;
  onCoordinate: (appointment: WellnessAppointment) => void;
}) {
  const statusStyles: Record<string, string> = {
    REQUESTED: "bg-amber-100 text-amber-900 border-amber-300",
    CONFIRMED: "bg-emerald-100 text-emerald-900 border-emerald-300",
    COMPLETED: "bg-sky-100 text-sky-900 border-sky-300",
    CANCELLED: "bg-stone-100 text-stone-600 border-stone-300 line-through",
    REJECTED: "bg-red-100 text-red-900 border-red-300",
  };

  const isExternal = appointment.professionalEngagementType === "EXTERNAL";

  return (
    <div className="rounded-xl border border-[var(--brand-line)] bg-white p-4 shadow-sm space-y-3 flex flex-col justify-between">
      <div>
        <div className="flex items-start justify-between gap-2">
          <span className="text-xs font-black tracking-wide text-[var(--brand-primary)]">
            ⏰ {formatTime(appointment.startsAt)} - {formatTime(appointment.endsAt)}
          </span>
          <span
            className={`rounded-full border px-2 py-0.5 text-[10px] font-black ${
              statusStyles[appointment.status] || "bg-gray-100 text-gray-700"
            }`}
          >
            {appointment.status}
          </span>
        </div>

        <h3 className="mt-2 text-base font-black text-[var(--brand-ink)]">
          {appointment.clientFullName}
        </h3>
        <p className="text-xs font-semibold text-stone-700">
          👨‍⚕️ {appointment.professionalName}
        </p>
        {appointment.serviceName ? (
          <p className="text-xs font-bold text-[var(--brand-primary)] mt-0.5">
            📋 {appointment.serviceName}{" "}
            {appointment.priceCents
              ? `(${formatMoney(appointment.priceCents, "MXN")})`
              : null}
          </p>
        ) : null}

        <div className="mt-2 text-[11px] text-[var(--brand-muted)] space-y-0.5">
          {appointment.officeLocation ? <p>📍 {appointment.officeLocation}</p> : null}
          {isExternal ? (
            <p className="italic font-semibold text-sky-800">🌐 Atención externa / Por solicitud</p>
          ) : null}
          {appointment.clientPhone ? <span>📞 Tel: {appointment.clientPhone}</span> : null}
          {appointment.notes ? (
            <p className="italic text-stone-600 mt-1">“{appointment.notes}”</p>
          ) : null}
        </div>
      </div>

      <div className="pt-2 border-t border-stone-100 flex items-center justify-end gap-2">
        {appointment.status === "REQUESTED" ? (
          <>
            <button
              className="app-button-secondary py-1 px-2.5 text-xs text-red-700 hover:bg-red-50"
              onClick={() => onCancel(appointment.id)}
              type="button"
            >
              Cancelar
            </button>
            <button
              className="form-primary-button py-1 px-3 text-xs bg-amber-600 hover:bg-amber-700 font-bold"
              onClick={() => {
                if (isExternal) onCoordinate(appointment);
                else onConfirm(appointment.id);
              }}
              type="button"
            >
              {isExternal ? "Coordinar y Confirmar" : "Confirmar Cita"}
            </button>
          </>
        ) : appointment.status === "CONFIRMED" ? (
          <>
            <button
              className="app-button-secondary py-1 px-2 text-xs text-red-700"
              onClick={() => onCancel(appointment.id)}
              type="button"
            >
              Cancelar
            </button>
            <button
              className="form-primary-button py-1 px-3 text-xs bg-sky-700 hover:bg-sky-800 font-bold"
              onClick={() => onComplete(appointment.id)}
              type="button"
            >
              Completar
            </button>
          </>
        ) : null}
      </div>
    </div>
  );
}

export function CoordinateExternalAppointmentModal({
  appointment,
  open,
  saving,
  onClose,
  onSubmit,
}: {
  appointment: WellnessAppointment | null;
  open: boolean;
  saving: boolean;
  onClose: () => void;
  onSubmit: (data: { date: string; time: string; notes: string }) => void;
}) {
  if (!open || !appointment) return null;

  return (
    <CoordinateExternalAppointmentForm
      appointment={appointment}
      key={appointment.id}
      onClose={onClose}
      onSubmit={onSubmit}
      saving={saving}
    />
  );
}

function CoordinateExternalAppointmentForm({
  appointment,
  saving,
  onClose,
  onSubmit,
}: {
  appointment: WellnessAppointment;
  saving: boolean;
  onClose: () => void;
  onSubmit: (data: { date: string; time: string; notes: string }) => void;
}) {
  const defaultDate = appointment.startsAt ? appointment.startsAt.split("T")[0] : toDateInput(new Date());
  const defaultTime = appointment.startsAt ? formatTime(appointment.startsAt) : "10:00";

  const [date, setDate] = useState(defaultDate);
  const [time, setTime] = useState(defaultTime || "10:00");
  const [notes, setNotes] = useState(appointment.notes || "");

  const handleFormSubmit = (e: FormEvent) => {
    e.preventDefault();
    onSubmit({ date, time, notes });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm overflow-y-auto">
      <div className="admin-panel my-8 w-full max-w-lg bg-white p-6 shadow-xl rounded-2xl space-y-4">
        <div className="flex items-center justify-between border-b border-[var(--brand-line)] pb-3">
          <div>
            <p className="text-xs font-bold uppercase tracking-wider text-[var(--brand-muted)]">
              Coordinación de Cita
            </p>
            <h2 className="text-lg font-black text-[var(--brand-ink)]">
              Coordinar y Confirmar Cita
            </h2>
          </div>
          <button className="rounded-lg p-2 text-stone-400 hover:bg-stone-100" onClick={onClose} type="button">
            ✕
          </button>
        </div>

        <div className="rounded-xl border border-sky-200 bg-sky-50 p-3 text-xs text-sky-950 space-y-1">
          <p><strong>Socio:</strong> {appointment.clientFullName}</p>
          <p><strong>Especialista:</strong> {appointment.professionalName}</p>
          {appointment.officeLocation ? <p><strong>Lugar:</strong> {appointment.officeLocation}</p> : null}
        </div>

        <form className="space-y-3" onSubmit={handleFormSubmit}>
          <Field label="Fecha Definitiva Acordada">
            <input
              className="app-input"
              onChange={(e) => setDate(e.target.value)}
              required
              type="date"
              value={date}
            />
          </Field>

          <Field label="Hora de Inicio Acordada">
            <input
              className="app-input"
              onChange={(e) => setTime(e.target.value)}
              required
              type="time"
              value={time}
            />
          </Field>

          <Field label="Notas o Acuerdo de Confirmación">
            <textarea
              className="app-input h-20 text-xs"
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Ej. Acordado por llamada telefónica para las 16:30 hrs en su consultorio."
              value={notes}
            />
          </Field>

          <div className="flex items-center justify-end gap-2 pt-3 border-t border-[var(--brand-line)]">
            <button className="app-button-secondary py-2 px-4" onClick={onClose} type="button">
              Cancelar
            </button>
            <button className="form-primary-button py-2 px-5 font-bold" disabled={saving || !date || !time} type="submit">
              {saving ? "Guardando..." : "Confirmar y Agendar"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export function CreateAppointmentModal({
  appointmentDate,
  clientQuery,
  clientResults,
  externalRequestedTime,
  loadingSlots,
  notes,
  open,
  professionals,
  saving,
  selectedClient,
  selectedProfessionalId,
  selectedServiceId,
  selectedSlot,
  services,
  slots,
  onAppointmentDateChange,
  onClientQueryChange,
  onClose,
  onExternalRequestedTimeChange,
  onNotesChange,
  onSelectClient,
  onSelectProfessional,
  onSelectService,
  onSelectSlot,
  onSubmit,
}: {
  appointmentDate: string;
  clientQuery: string;
  clientResults: ClientSummary[];
  externalRequestedTime: string;
  loadingSlots: boolean;
  notes: string;
  open: boolean;
  professionals: ProfessionalSummary[];
  saving: boolean;
  selectedClient: ClientSummary | null;
  selectedProfessionalId: string;
  selectedServiceId: string;
  selectedSlot: WellnessAvailabilitySlot | null;
  services: ProfessionalServiceSummary[];
  slots: WellnessAvailabilitySlot[];
  onAppointmentDateChange: (date: string) => void;
  onClientQueryChange: (query: string) => void;
  onClose: () => void;
  onExternalRequestedTimeChange: (time: string) => void;
  onNotesChange: (notes: string) => void;
  onSelectClient: (client: ClientSummary) => void;
  onSelectProfessional: (id: string) => void;
  onSelectService: (id: string) => void;
  onSelectSlot: (slot: WellnessAvailabilitySlot) => void;
  onSubmit: (e: FormEvent<HTMLFormElement>) => void;
}) {
  if (!open) return null;

  const selectedProf = professionals.find((p) => p.id === selectedProfessionalId);
  const isExternalProf = selectedProf?.engagementType === "EXTERNAL";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm overflow-y-auto">
      <div className="admin-panel my-8 w-full max-w-2xl bg-white p-6 shadow-xl rounded-2xl">
        <div className="flex items-center justify-between border-b border-[var(--brand-line)] pb-4">
          <div>
            <p className="text-xs font-bold uppercase tracking-wider text-[var(--brand-muted)]">
              Agendamiento de Bienestar
            </p>
            <h2 className="text-xl font-black text-[var(--brand-ink)]">
              Agendar o Solicitar Cita
            </h2>
          </div>
          <button
            className="rounded-lg p-2 text-[var(--brand-muted)] hover:bg-stone-100"
            onClick={onClose}
            type="button"
          >
            ✕
          </button>
        </div>

        <form className="mt-4 grid gap-4" onSubmit={onSubmit}>
          {/* Step 1: Socio */}
          <Field label="1. Buscar Socio">
            <input
              className="app-input"
              onChange={(e) => onClientQueryChange(e.target.value)}
              placeholder="Buscar por nombre, teléfono o email..."
              value={clientQuery}
            />
            {clientResults.length > 0 ? (
              <div className="mt-2 max-h-36 overflow-y-auto rounded-lg border border-[var(--brand-line)] bg-stone-50 p-2">
                {clientResults.map((client) => (
                  <button
                    className={`block w-full rounded-md p-2 text-left text-xs font-semibold ${
                      selectedClient?.id === client.id
                        ? "bg-[var(--brand-primary)] text-white"
                        : "hover:bg-stone-200"
                    }`}
                    key={client.id}
                    onClick={() => onSelectClient(client)}
                    type="button"
                  >
                    {client.firstName} {client.lastName} - {client.phone || client.email || "Socio"}
                  </button>
                ))}
              </div>
            ) : null}
            {selectedClient ? (
              <p className="mt-1 text-xs font-bold text-emerald-700">
                ✓ Socio seleccionado: {selectedClient.firstName} {selectedClient.lastName}
              </p>
            ) : null}
          </Field>

          {/* Step 2: Profesional */}
          <Field label="2. Seleccionar Profesional Especialista">
            <select
              className="app-input"
              onChange={(e) => onSelectProfessional(e.target.value)}
              value={selectedProfessionalId}
            >
              <option value="">-- Selecciona Profesional --</option>
              {professionals.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.displayName} ({p.professionalType})
                </option>
              ))}
            </select>
            {selectedProf?.officeLocation ? (
              <p className="mt-1 text-xs text-[var(--brand-muted)]">
                📍 Lugar de atención: <strong>{selectedProf.officeLocation}</strong>
              </p>
            ) : null}
          </Field>

          {/* Step 3: Servicio (Auto-selected consultation) */}
          {services.length > 0 ? (
            <Field label="3. Consulta Ofrecida">
              <select
                className="app-input font-bold"
                onChange={(e) => onSelectService(e.target.value)}
                value={selectedServiceId || services[0]?.id || ""}
              >
                {services.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name} ({s.durationMinutes} min - {formatMoney(s.priceCents, s.currency)})
                  </option>
                ))}
              </select>
            </Field>
          ) : null}

          {/* Step 4: Fecha y horario interno o solicitado */}
          {isExternalProf ? (
            <div className="grid gap-3">
              <div className="grid gap-3 sm:grid-cols-2">
                <Field label="4. Fecha solicitada">
                  <input
                    className="app-input"
                    onChange={(e) => onAppointmentDateChange(e.target.value)}
                    required
                    type="date"
                    value={appointmentDate}
                  />
                </Field>

                <Field label="Hora solicitada">
                  <input
                    className="app-input"
                    onChange={(e) => onExternalRequestedTimeChange(e.target.value)}
                    required
                    type="time"
                    value={externalRequestedTime}
                  />
                </Field>
              </div>

              <div className="rounded-xl border border-sky-200 bg-sky-50/80 p-4 text-xs space-y-2 text-sky-950">
                <strong className="block text-sm font-black text-sky-900">
                  🌐 Atención Externa / Por Solicitud
                </strong>
                <p className="leading-relaxed">
                  Este profesional no usa los horarios internos de GymFort. La fecha y hora se registrarán como solicitud y podrán ajustarse al coordinar con el especialista.
                </p>
                <p className="text-[11px] font-bold text-sky-800">
                  ✓ Estatus inicial de la cita: SOLICITADA (Pending confirmation).
                </p>
              </div>
            </div>
          ) : (
            <>
              <div className="grid gap-3 sm:grid-cols-2">
                <Field label="4. Fecha de la Cita">
                  <input
                    className="app-input"
                    onChange={(e) => onAppointmentDateChange(e.target.value)}
                    type="date"
                    value={appointmentDate}
                  />
                </Field>

                <Field label="Horario Seleccionado">
                  <div className="rounded-lg border border-[var(--brand-line)] p-2.5 text-center text-sm font-bold bg-stone-50">
                    {selectedSlot
                      ? `${formatTime(selectedSlot.startsAt)} - ${formatTime(selectedSlot.endsAt)}`
                      : "Selecciona un slot abajo"}
                  </div>
                </Field>
              </div>

              {/* Available Slots Grid */}
              <div className="grid gap-2">
                <label className="text-xs font-bold uppercase text-[var(--brand-muted)]">
                  Horarios Disponibles para esta Fecha:
                </label>
                {loadingSlots ? (
                  <p className="text-xs text-[var(--brand-muted)]">Calculando horarios disponibles...</p>
                ) : slots.length === 0 ? (
                  <p className="rounded-lg border border-stone-200 bg-stone-50 p-3 text-center text-xs text-[var(--brand-muted)]">
                    El profesional no tiene horarios disponibles configurados para este día de la semana.
                  </p>
                ) : (
                  <div className="grid grid-cols-3 sm:grid-cols-4 gap-2 max-h-40 overflow-y-auto p-1">
                    {slots.map((slot) => {
                      const isSelected = selectedSlot?.startsAt === slot.startsAt;
                      return (
                        <button
                          className={`rounded-lg border p-2 text-xs font-bold transition-all ${
                            !slot.available
                              ? "bg-gray-100 text-gray-400 border-gray-200 cursor-not-allowed line-through"
                              : isSelected
                                ? "bg-[var(--brand-primary)] text-white border-[var(--brand-primary)] shadow-sm"
                                : "bg-white text-[var(--brand-ink)] border-[var(--brand-line)] hover:border-[var(--brand-primary)]"
                          }`}
                          disabled={!slot.available}
                          key={slot.startsAt}
                          onClick={() => onSelectSlot(slot)}
                          type="button"
                        >
                          {formatTime(slot.startsAt)}
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            </>
          )}

          {/* Step 5: Notas */}
          <Field label="Notas o Motivo de Consulta">
            <textarea
              className="app-input h-20 text-sm"
              onChange={(e) => onNotesChange(e.target.value)}
              placeholder="Ej. Evaluación inicial, molestias en rodilla izquierda..."
              value={notes}
            />
          </Field>

          {/* Buttons */}
          <div className="flex items-center justify-end gap-3 pt-3 border-t border-[var(--brand-line)]">
            <button
              className="app-button-secondary py-2.5 px-4"
              onClick={onClose}
              type="button"
            >
              Cancelar
            </button>
            <button
              className="form-primary-button py-2.5 px-6 font-bold"
              disabled={
                saving
                || !selectedClient
                || !selectedProfessionalId
                || (!selectedSlot && !isExternalProf)
                || (isExternalProf && !externalRequestedTime)
              }
              type="submit"
            >
              {saving ? "Agendando..." : "Confirmar Agendamiento"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export function ProfessionalsDirectorySection({
  canManageProfessionals = true,
  professionals,
  professionalServicesMap,
  onDeleteProfessional,
  onEditProfessional,
  onOpenCreateModal,
}: {
  canManageProfessionals?: boolean;
  professionals: ProfessionalSummary[];
  professionalServicesMap: Record<string, ProfessionalServiceSummary[]>;
  onDeleteProfessional: (id: string) => void;
  onEditProfessional: (p: ProfessionalSummary) => void;
  onOpenCreateModal: () => void;
}) {
  return (
    <div className="admin-panel space-y-4">
      <div className="admin-panel-header flex flex-wrap items-center justify-between gap-4">
        <div>
          <p>Directorio de Especialistas</p>
          <h2>Equipo Profesional</h2>
        </div>
        {canManageProfessionals ? (
          <button
            className="form-primary-button w-auto py-2.5 px-4 font-bold"
            onClick={onOpenCreateModal}
            type="button"
          >
            + Nuevo Especialista
          </button>
        ) : null}
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {professionals.length === 0 ? (
          <div className="col-span-full border-2 border-dashed border-[var(--brand-line)] rounded-2xl p-8 text-center bg-stone-50/80 space-y-3">
            <p className="text-sm font-semibold text-[var(--brand-muted)]">
              No hay especialistas o profesionales registrados actualmente.
            </p>
            {canManageProfessionals ? (
              <button
                className="form-primary-button w-auto py-2.5 px-6 font-bold"
                onClick={onOpenCreateModal}
                type="button"
              >
                + Registrar Primer Especialista
              </button>
            ) : null}
          </div>
        ) : (
          professionals.map((prof) => {
            const isDePlanta = prof.engagementType === "STAFF";
            const profServices = professionalServicesMap[prof.id] || [];
            const mainService = profServices[0];

            return (
              <div
                className="rounded-xl border border-[var(--brand-line)] bg-white p-4 shadow-sm space-y-3 flex flex-col justify-between"
                key={prof.id}
              >
                <div>
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <strong className="block text-base font-black text-[var(--brand-ink)]">
                        {prof.displayName}
                      </strong>
                      <span className="text-xs font-bold text-[var(--brand-primary)]">
                        {prof.professionalType}
                      </span>
                    </div>
                    <div className="flex flex-col items-end gap-1">
                      <span
                        className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
                          isDePlanta
                            ? "bg-emerald-100 text-emerald-800"
                            : "bg-sky-100 text-sky-800"
                        }`}
                      >
                        {isDePlanta ? "🏢 De Planta" : "🌐 Externo"}
                      </span>
                      <span
                        className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${
                          prof.status === "ACTIVE"
                            ? "bg-emerald-50 text-emerald-700"
                            : "bg-stone-100 text-stone-600"
                        }`}
                      >
                        {prof.status}
                      </span>
                    </div>
                  </div>

                  <div className="mt-3 grid gap-1.5 text-xs text-[var(--brand-muted)]">
                    {mainService ? (
                      <p className="font-bold text-[var(--brand-primary)] bg-stone-50 p-2 rounded-lg border border-[var(--brand-line)]">
                        📋 {mainService.name} ({mainService.durationMinutes} min - {formatMoney(mainService.priceCents, mainService.currency)})
                      </p>
                    ) : null}

                    {prof.officeLocation ? (
                      <p className="font-semibold text-stone-800">📍 {prof.officeLocation}</p>
                    ) : null}
                    {!isDePlanta ? (
                      <p className="italic">🌐 Atención externa / Por solicitud</p>
                    ) : null}
                    {prof.email ? <p>✉ {prof.email}</p> : null}
                    {prof.phone ? <p>📞 {prof.phone}</p> : null}
                    {prof.bio ? <p className="mt-1 text-stone-600 line-clamp-2">{prof.bio}</p> : null}
                  </div>
                </div>

                {canManageProfessionals ? (
                  <div className="pt-2 border-t border-stone-100 flex items-center gap-2">
                    <button
                      className="app-button-secondary py-1.5 text-xs font-bold flex-1"
                      onClick={() => onEditProfessional(prof)}
                      type="button"
                    >
                      Editar
                    </button>
                    <button
                      className="app-button-secondary py-1.5 px-3 text-xs font-bold text-red-700 hover:bg-red-50"
                      onClick={() => {
                        if (confirm(`¿Seguro que deseas eliminar al profesional ${prof.displayName}?`)) {
                          onDeleteProfessional(prof.id);
                        }
                      }}
                      type="button"
                    >
                      Eliminar
                    </button>
                  </div>
                ) : null}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}

export function ProfessionalFormModal({
  bio,
  displayName,
  durationMinutes,
  editingProfessionalId,
  email,
  modality,
  officeLocation,
  open,
  phone,
  price,
  professionalType,
  saving,
  scheduleDays,
  status,
  onBioChange,
  onClose,
  onDisplayNameChange,
  onDurationChange,
  onEmailChange,
  onModalityChange,
  onOfficeLocationChange,
  onPhoneChange,
  onPriceChange,
  onProfessionalTypeChange,
  onStatusChange,
  onUpdateScheduleDay,
  onSubmit,
}: {
  bio: string;
  displayName: string;
  durationMinutes: number;
  editingProfessionalId: string | null;
  email: string;
  modality: "STAFF" | "EXTERNAL";
  officeLocation: string;
  open: boolean;
  phone: string;
  price: string;
  professionalType: string;
  saving: boolean;
  scheduleDays: ProfessionalScheduleDay[];
  status: string;
  onBioChange: (v: string) => void;
  onClose: () => void;
  onDisplayNameChange: (v: string) => void;
  onDurationChange: (v: number) => void;
  onEmailChange: (v: string) => void;
  onModalityChange: (m: "STAFF" | "EXTERNAL") => void;
  onOfficeLocationChange: (v: string) => void;
  onPhoneChange: (v: string) => void;
  onPriceChange: (v: string) => void;
  onProfessionalTypeChange: (v: string) => void;
  onStatusChange: (v: string) => void;
  onUpdateScheduleDay: (dayOfWeek: number, patch: Partial<ProfessionalScheduleDay>) => void;
  onSubmit: (e: FormEvent<HTMLFormElement>) => void;
}) {
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm overflow-y-auto">
      <div className="admin-panel my-8 w-full max-w-4xl bg-white p-6 shadow-xl rounded-2xl">
        <div className="flex items-center justify-between border-b border-[var(--brand-line)] pb-4">
          <div>
            <p className="text-xs font-bold uppercase tracking-wider text-[var(--brand-muted)]">
              {editingProfessionalId ? "Modificación de datos" : "Alta de Especialista"}
            </p>
            <h2 className="text-xl font-black text-[var(--brand-ink)]">
              {editingProfessionalId ? "Editar Profesional" : "Nuevo Profesional"}
            </h2>
          </div>
          <button
            className="rounded-lg p-2 text-[var(--brand-muted)] hover:bg-stone-100"
            onClick={onClose}
            type="button"
          >
            ✕
          </button>
        </div>

        <form className="mt-4 grid gap-6 md:grid-cols-2" onSubmit={onSubmit}>
          {/* Column 1: General Info */}
          <div className="space-y-3">
            <Field label="Nombre Visible">
              <input
                className="app-input"
                onChange={(e) => onDisplayNameChange(e.target.value)}
                placeholder="Ej. Dra. Ana López"
                required
                value={displayName}
              />
            </Field>

            <Field label="Especialidad">
              <select
                className="app-input font-semibold"
                onChange={(e) => {
                  const newType = e.target.value;
                  onProfessionalTypeChange(newType);
                  const info = SPECIALTY_CONSULTATIONS[newType];
                  if (info && !editingProfessionalId) {
                    onDurationChange(info.defaultDuration);
                    onPriceChange(info.defaultPrice);
                  }
                }}
                value={professionalType}
              >
                <option value="NUTRITIONIST">Nutriólogo(a) ➔ Consulta de Nutrición</option>
                <option value="PHYSIOTHERAPIST">Fisioterapeuta ➔ Consulta de Fisioterapia</option>
                <option value="CHIROPRACTOR">Quiropráctico(a) ➔ Consulta de Quiropráctica</option>
                <option value="WELLNESS">Bienestar General ➔ Consulta de Bienestar</option>
              </select>
            </Field>

            {/* Duración y Honorarios */}
            <div className="grid grid-cols-2 gap-2 rounded-xl border border-[var(--brand-line)] bg-stone-50 p-3">
              <Field label="Duración Consulta">
                <div className="flex items-center gap-1">
                  <input
                    className="app-input"
                    min={15}
                    onChange={(e) => onDurationChange(Number(e.target.value))}
                    type="number"
                    value={durationMinutes}
                  />
                  <span className="text-xs text-stone-500 font-bold">min</span>
                </div>
              </Field>

              <Field label="Honorarios ($ MXN)">
                <input
                  className="app-input"
                  min={0}
                  onChange={(e) => onPriceChange(e.target.value)}
                  step="0.01"
                  type="number"
                  value={price}
                />
              </Field>
            </div>

            <Field label="Correo Electrónico">
              <input
                className="app-input"
                onChange={(e) => onEmailChange(e.target.value)}
                placeholder="correo@ejemplo.com"
                type="email"
                value={email}
              />
            </Field>

            <PhoneField
              label="Teléfono"
              name="profPhone"
              onChange={onPhoneChange}
              placeholder="+525512345678"
              required={false}
              value={phone}
            />

            {editingProfessionalId ? (
              <Field label="Estatus">
                <select
                  className="app-input"
                  onChange={(e) => onStatusChange(e.target.value)}
                  value={status}
                >
                  <option value="ACTIVE">ACTIVO</option>
                  <option value="INACTIVE">INACTIVO</option>
                </select>
              </Field>
            ) : null}

            <Field label="Biografía / Presentación">
              <textarea
                className="app-input h-16 text-sm"
                onChange={(e) => onBioChange(e.target.value)}
                placeholder="Breve semblanza profesional..."
                value={bio}
              />
            </Field>
          </div>

          {/* Column 2: Modality & Schedule */}
          <div className="space-y-3">
            <Field label="Modalidad de Atención">
              <div className="grid grid-cols-2 gap-2 mt-1">
                <button
                  className={`rounded-lg border p-2.5 text-left transition-all ${
                    modality === "STAFF"
                      ? "border-[var(--brand-primary)] bg-emerald-50 text-[var(--brand-primary)] font-bold shadow-sm"
                      : "border-[var(--brand-line)] bg-white text-stone-600 hover:bg-stone-50"
                  }`}
                  onClick={() => onModalityChange("STAFF")}
                  type="button"
                >
                  <strong className="block text-xs">🏢 De Planta</strong>
                  <span className="text-[10px] opacity-80">Con consultorio y horarios</span>
                </button>

                <button
                  className={`rounded-lg border p-2.5 text-left transition-all ${
                    modality === "EXTERNAL"
                      ? "border-[var(--brand-primary)] bg-sky-50 text-[var(--brand-primary)] font-bold shadow-sm"
                      : "border-[var(--brand-line)] bg-white text-stone-600 hover:bg-stone-50"
                  }`}
                  onClick={() => onModalityChange("EXTERNAL")}
                  type="button"
                >
                  <strong className="block text-xs">🌐 Externo</strong>
                  <span className="text-[10px] opacity-80">Por solicitud / previa cita</span>
                </button>
              </div>
            </Field>

            {modality === "STAFF" ? (
              <Field label="Consultorio / Ubicación en Sucursal">
                <input
                  className="app-input"
                  onChange={(e) => onOfficeLocationChange(e.target.value)}
                  placeholder="Ej. Consultorio 102 - Área Médica Gym"
                  value={officeLocation}
                />
              </Field>
            ) : (
              <Field label="Lugar de Atención / Consultorio Externo">
                <input
                  className="app-input"
                  onChange={(e) => onOfficeLocationChange(e.target.value)}
                  placeholder="Ej. Clínica Médica Norte / Consultorio Privado"
                  value={officeLocation}
                />
              </Field>
            )}

            {/* Integrated Weekly Schedule for Staff Professionals */}
            {modality === "STAFF" ? (
              <div className="rounded-xl border border-[var(--brand-line)] bg-stone-50 p-3 space-y-2">
                <div className="flex items-center justify-between border-b border-stone-200 pb-2">
                  <strong className="text-xs text-[var(--brand-ink)]">🗓 Días y Horarios de Atención</strong>
                  <span className="text-[10px] text-[var(--brand-muted)] font-semibold">Configuración semanal</span>
                </div>

                <div className="grid gap-1.5 max-h-60 overflow-y-auto pr-1">
                  {scheduleDays.map((day) => (
                    <div
                      className={`flex items-center justify-between gap-2 rounded-lg border p-2 text-xs transition-colors ${
                        day.enabled ? "border-[var(--brand-line)] bg-white" : "border-stone-200 bg-stone-100 opacity-60"
                      }`}
                      key={day.dayOfWeek}
                    >
                      <label className="flex items-center gap-2 font-bold cursor-pointer min-w-[85px]">
                        <input
                          checked={day.enabled}
                          className="rounded text-[var(--brand-primary)]"
                          onChange={(e) => onUpdateScheduleDay(day.dayOfWeek, { enabled: e.target.checked })}
                          type="checkbox"
                        />
                        <span>{day.dayName}</span>
                      </label>

                      {day.enabled ? (
                        <div className="flex items-center gap-1">
                          <input
                            className="h-8 rounded border border-[var(--brand-line)] px-1 text-xs"
                            onChange={(e) => onUpdateScheduleDay(day.dayOfWeek, { opensAt: e.target.value })}
                            type="time"
                            value={day.opensAt}
                          />
                          <span className="text-[10px] text-stone-400">a</span>
                          <input
                            className="h-8 rounded border border-[var(--brand-line)] px-1 text-xs"
                            onChange={(e) => onUpdateScheduleDay(day.dayOfWeek, { closesAt: e.target.value })}
                            type="time"
                            value={day.closesAt}
                          />
                        </div>
                      ) : (
                        <span className="text-[10px] italic text-stone-400">Sin atención</span>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            ) : null}
          </div>

          <div className="col-span-full flex items-center justify-end gap-3 pt-3 border-t border-[var(--brand-line)]">
            <button className="app-button-secondary py-2.5 px-4" onClick={onClose} type="button">
              Cancelar
            </button>
            <button
              className="form-primary-button py-2.5 px-6 font-bold"
              disabled={saving || !displayName.trim()}
              type="submit"
            >
              {saving
                ? "Guardando..."
                : editingProfessionalId
                  ? "Guardar Cambios"
                  : "Crear Profesional"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function Field({ children, label }: { children: ReactNode; label: string }) {
  return (
    <label className="grid gap-1.5 text-xs font-bold uppercase tracking-wide text-[var(--brand-muted)]">
      {label}
      {children}
    </label>
  );
}

function buildWeekDates(anchorDateStr: string) {
  const d = new Date(anchorDateStr + "T12:00:00");
  const dayOfWeek = (d.getDay() + 6) % 7;
  const monday = new Date(d);
  monday.setDate(d.getDate() - dayOfWeek);

  const dayNames = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"];
  return Array.from({ length: 7 }, (_, i) => {
    const cur = new Date(monday);
    cur.setDate(monday.getDate() + i);
    const dateIso = toDateInput(cur);
    return {
      date: dateIso,
      dayName: dayNames[i],
      dayOfMonth: cur.getDate(),
    };
  });
}

function formatMonthLabel(dateStr: string) {
  if (!dateStr) return "";
  const d = new Date(dateStr + "T12:00:00");
  const monthName = d.toLocaleDateString("es-MX", { month: "long" });
  return `${monthName.charAt(0).toUpperCase() + monthName.slice(1)} de ${d.getFullYear()}`;
}

function formatMoney(cents: number, currency = "MXN") {
  return new Intl.NumberFormat("es-MX", { style: "currency", currency }).format(cents / 100);
}

function formatTime(isoOrTimeStr: string) {
  if (!isoOrTimeStr) return "";
  if (isoOrTimeStr.includes("T")) {
    const date = new Date(isoOrTimeStr);
    return date.toLocaleTimeString("es-MX", { hour: "2-digit", minute: "2-digit", hour12: false });
  }
  return isoOrTimeStr.slice(0, 5);
}
