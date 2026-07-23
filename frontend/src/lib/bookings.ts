import { normalizeTimeInput, parseDateInput, toDateInput } from "./date-time.ts";

export type WeekdayKey = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";

export type ResourceSchedule = Array<{
  day: WeekdayKey;
  opensAt: string;
  closesAt: string;
}>;

type ResourceWithHours = {
  notes?: string | null;
  opensAt?: string | null;
  closesAt?: string | null;
};

const weekdayKeys: WeekdayKey[] = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
];

export function buildMonthDates(anchor: string) {
  const month = parseDateInput(anchor);
  const first = new Date(month.getFullYear(), month.getMonth(), 1);
  const gridStart = new Date(first);
  gridStart.setDate(first.getDate() - ((first.getDay() + 6) % 7));

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(gridStart);
    date.setDate(gridStart.getDate() + index);
    return {
      date: toDateInput(date),
      dayOfMonth: date.getDate(),
      inCurrentMonth: date.getMonth() === month.getMonth(),
    };
  });
}

export function dateToWeekday(value: string): WeekdayKey {
  return weekdayKeys[(parseDateInput(value).getDay() + 6) % 7];
}

export function parseResourceSchedule(notes?: string | null): ResourceSchedule | null {
  if (!notes) return null;

  try {
    const parsed = JSON.parse(notes) as { schedule?: unknown };
    if (!Array.isArray(parsed.schedule)) return null;

    const schedule = parsed.schedule.filter(isScheduleEntry);
    return schedule.length > 0 ? schedule : null;
  } catch {
    return null;
  }
}

export function resourceHoursForDay(resource: ResourceWithHours, weekday: WeekdayKey) {
  const schedule = parseResourceSchedule(resource.notes);
  const day = schedule?.find((item) => item.day === weekday);

  if (day) {
    return {
      opensAt: normalizeTimeInput(day.opensAt),
      closesAt: normalizeTimeInput(day.closesAt),
    };
  }
  if (schedule) return null;

  return {
    opensAt: normalizeTimeInput(resource.opensAt || "06:00"),
    closesAt: normalizeTimeInput(resource.closesAt || "22:00"),
  };
}

export function timeToMinutes(value: string): number {
  const [hours, minutes] = value.split(":").map(Number);
  return hours * 60 + minutes;
}

export function minutesToTime(value: number): string {
  return `${String(Math.floor(value / 60)).padStart(2, "0")}:${String(value % 60).padStart(2, "0")}`;
}

export function friendlyBookingError(raw: string, fallback: string): string {
  const value = raw.toLowerCase();
  if (value.includes("booking_client_conflict") || value.includes("otra reserva") || value.includes("socio ya tiene")) {
    return "El socio ya tiene otra reserva que coincide con este horario. Elige una hora diferente.";
  }
  if (value.includes("booking_conflict") || value.includes("no tiene disponibilidad") || value.includes("conflict")) {
    return "El recurso ya está ocupado en ese horario. Elige otra hora o un recurso diferente.";
  }
  if (value.includes("booking_plan_not_allowed") || value.includes("membresia permitida") || value.includes("membresía permitida")) {
    return "El socio no tiene una membresía válida para reservar este recurso. Selecciona otro recurso o revisa su plan.";
  }
  if (value.includes("booking_outside_resource_hours") || value.includes("fuera del horario")) {
    return "La hora seleccionada está fuera del horario operativo del recurso.";
  }
  if (value.includes("booking_start_in_past") || value.includes("hora actual") || value.includes("pasado")) {
    return "No es posible crear una reserva en una hora que ya pasó.";
  }
  if (value.includes("booking_range_invalid")) return "Revisa la fecha y la hora de la reserva.";
  if (value.includes("client_not_found")) return "El socio no está disponible o no se encuentra activo.";

  const clean = raw
    .replace(/\[[A-Z0-9_ -]+\]/g, "")
    .replace(/\b[A-Z][A-Z0-9_]{3,}\b/g, "")
    .replace(/https?:\/\/\S+/g, "")
    .replace(/\s+/g, " ")
    .trim();
  return clean && clean.length <= 180 ? clean : fallback;
}

function isScheduleEntry(value: unknown): value is ResourceSchedule[number] {
  if (!value || typeof value !== "object") return false;
  const entry = value as Record<string, unknown>;
  return weekdayKeys.includes(entry.day as WeekdayKey)
    && typeof entry.opensAt === "string"
    && typeof entry.closesAt === "string"
    && Boolean(entry.opensAt)
    && Boolean(entry.closesAt);
}
