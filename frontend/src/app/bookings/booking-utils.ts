import type { ResourceSummary } from "@/lib/api";

export type WeekdayKey = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";

export type ResourceDay = {
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

export const weekdays: ResourceDay[] = [
  { key: "MONDAY", label: "Lunes", shortLabel: "L", enabled: true, opensAt: "06:00", closesAt: "22:00" },
  { key: "TUESDAY", label: "Martes", shortLabel: "M", enabled: true, opensAt: "06:00", closesAt: "22:00" },
  { key: "WEDNESDAY", label: "Miercoles", shortLabel: "M", enabled: true, opensAt: "06:00", closesAt: "22:00" },
  { key: "THURSDAY", label: "Jueves", shortLabel: "J", enabled: true, opensAt: "06:00", closesAt: "22:00" },
  { key: "FRIDAY", label: "Viernes", shortLabel: "V", enabled: true, opensAt: "06:00", closesAt: "22:00" },
  { key: "SATURDAY", label: "Sabado", shortLabel: "S", enabled: false, opensAt: "08:00", closesAt: "14:00" },
  { key: "SUNDAY", label: "Domingo", shortLabel: "D", enabled: false, opensAt: "08:00", closesAt: "14:00" },
];

export function buildMonthDates(anchor: string) {
  const month = parseDateInput(anchor);
  const first = new Date(month.getFullYear(), month.getMonth(), 1);
  const gridStart = new Date(first);
  gridStart.setDate(first.getDate() - ((first.getDay() + 6) % 7));
  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(gridStart);
    date.setDate(gridStart.getDate() + index);
    return { date: toDateInput(date), dayOfMonth: date.getDate(), inCurrentMonth: date.getMonth() === month.getMonth() };
  });
}

export function resourceHoursForDay(resource: ResourceSummary, weekday: WeekdayKey) {
  const schedule = parseResourceSchedule(resource);
  const day = schedule?.find((item) => item.day === weekday);
  if (day) return { opensAt: normalizeTimeInput(day.opensAt), closesAt: normalizeTimeInput(day.closesAt) };
  if (schedule) return null;
  return { opensAt: normalizeTimeInput(resource.opensAt || "06:00"), closesAt: normalizeTimeInput(resource.closesAt || "22:00") };
}

export function buildResourceScheduleNotes(days: ResourceDay[]) {
  return JSON.stringify({
    schedule: days.map((day) => ({ day: day.key, opensAt: day.opensAt, closesAt: day.closesAt })),
    description: null,
  } satisfies StoredSchedule);
}

export function friendlyBookingError(raw: string, fallback: string) {
  const value = raw.toLowerCase();
  if (value.includes("booking_client_conflict") || value.includes("otra reserva") || value.includes("socio ya tiene")) return "El socio ya tiene otra reserva que coincide con este horario. Elige una hora diferente.";
  if (value.includes("booking_conflict") || value.includes("no tiene disponibilidad") || value.includes("conflict")) return "El recurso ya esta ocupado en ese horario. Elige otra hora o un recurso diferente.";
  if (value.includes("booking_plan_not_allowed") || value.includes("membresia permitida") || value.includes("membresia permitida")) return "El socio no tiene una membresia valida para reservar este recurso. Selecciona otro recurso o revisa su plan.";
  if (value.includes("booking_outside_resource_hours") || value.includes("fuera del horario")) return "La hora seleccionada esta fuera del horario operativo del recurso.";
  if (value.includes("booking_start_in_past") || value.includes("hora actual") || value.includes("pasado")) return "No es posible crear una reserva en una hora que ya paso.";
  if (value.includes("booking_range_invalid")) return "Revisa la fecha y la hora de la reserva.";
  if (value.includes("client_not_found")) return "El socio no esta disponible o no se encuentra activo.";
  const clean = raw.replace(/\[[A-Z0-9_ -]+\]/g, "").replace(/\b[A-Z][A-Z0-9_]{3,}\b/g, "").replace(/https?:\/\/\S+/g, "").replace(/\s+/g, " ").trim();
  return clean && clean.length <= 180 ? clean : fallback;
}

export function isPastDateTime(date: string, time: string) {
  return Boolean(date && isValidTimeInput(time) && new Date(`${date}T${time}`).getTime() <= Date.now());
}

export function currentTimeInput(date = new Date()) {
  const rounded = new Date(date);
  rounded.setSeconds(0, 0);
  rounded.setMinutes(Math.ceil(rounded.getMinutes() / 5) * 5);
  return `${String(rounded.getHours()).padStart(2, "0")}:${String(rounded.getMinutes()).padStart(2, "0")}`;
}

export function addMinutes(value: string, minutes: number) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  date.setMinutes(date.getMinutes() + minutes);
  return toLocalDateTimeInput(date);
}

export function formatDateTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Hora invalida" : new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", timeStyle: "short" }).format(date);
}

export function formatLongDate(value: string) {
  return new Intl.DateTimeFormat("es-MX", { dateStyle: "full" }).format(parseDateInput(value));
}

export function formatMonthLabel(value: string) {
  return new Intl.DateTimeFormat("es-MX", { month: "long", year: "numeric" }).format(parseDateInput(value));
}

export function bookingDate(value: string) {
  return toDateInput(new Date(value));
}

export function resourceColor(index: number) {
  return resourceColors[index % resourceColors.length];
}

export function parseDateInput(value: string) {
  return new Date(`${value}T00:00:00`);
}

export function isPastDate(value: string) {
  return parseDateInput(value).getTime() < parseDateInput(toDateInput(new Date())).getTime();
}

export function dateToWeekday(value: string): WeekdayKey {
  return weekdays[(parseDateInput(value).getDay() + 6) % 7].key;
}

export function isValidTimeInput(value: string) {
  return /^([01]\d|2[0-3]):[0-5]\d$/.test(normalizeTimeInput(value));
}

export function normalizeTimeInput(value: string) {
  const match = value.match(/^([01]\d|2[0-3]):([0-5]\d)(?::[0-5]\d)?$/);
  return match ? `${match[1]}:${match[2]}` : value;
}

export function timeToMinutes(value: string) {
  const [hours, minutes] = value.split(":").map(Number);
  return hours * 60 + minutes;
}

export function minutesToTime(value: number) {
  return `${String(Math.floor(value / 60)).padStart(2, "0")}:${String(value % 60).padStart(2, "0")}`;
}

export function toDateInput(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function parseResourceSchedule(resource: ResourceSummary) {
  if (!resource.notes) return null;
  try {
    const parsed = JSON.parse(resource.notes) as StoredSchedule;
    return Array.isArray(parsed.schedule) ? parsed.schedule.filter((day) => day.day && day.opensAt && day.closesAt) : null;
  } catch {
    return null;
  }
}

function toLocalDateTimeInput(date: Date) {
  return `${toDateInput(date)}T${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}
