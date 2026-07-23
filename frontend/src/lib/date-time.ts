export function toDateInput(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

export function parseDateInput(value: string): Date {
  return new Date(`${value}T00:00:00`);
}

export function normalizeTimeInput(value: string): string {
  const match = value.match(/^([01]\d|2[0-3]):([0-5]\d)(?::[0-5]\d)?$/);
  return match ? `${match[1]}:${match[2]}` : value;
}

export function isValidTimeInput(value: string): boolean {
  return /^([01]\d|2[0-3]):[0-5]\d$/.test(normalizeTimeInput(value));
}

export function toLocalDateTimeInput(date: Date): string {
  return `${toDateInput(date)}T${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

export function addMinutes(localDateTime: string, minutes: number): string {
  const date = new Date(localDateTime);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  date.setMinutes(date.getMinutes() + minutes);
  return toLocalDateTimeInput(date);
}

export function isPastDate(value: string, now = new Date()): boolean {
  return parseDateInput(value).getTime() < parseDateInput(toDateInput(now)).getTime();
}

export function isPastDateTime(date: string, time: string, now = new Date()): boolean {
  return Boolean(date && isValidTimeInput(time) && new Date(`${date}T${time}`).getTime() <= now.getTime());
}

export function currentTimeInput(date = new Date()): string {
  const rounded = new Date(date);
  rounded.setSeconds(0, 0);
  rounded.setMinutes(Math.ceil(rounded.getMinutes() / 5) * 5);
  return `${String(rounded.getHours()).padStart(2, "0")}:${String(rounded.getMinutes()).padStart(2, "0")}`;
}

export function parseMexicanDate(value: string, currentYear = new Date().getFullYear()): string | null {
  const match = value.trim().match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
  if (!match) {
    return null;
  }

  const day = Number(match[1]);
  const month = Number(match[2]);
  const year = Number(match[3]);
  const date = new Date(year, month - 1, day);
  const valid = year >= 1900
    && year <= currentYear
    && date.getFullYear() === year
    && date.getMonth() === month - 1
    && date.getDate() === day;

  return valid ? `${String(year).padStart(4, "0")}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}` : null;
}

export function formatMexicanDateInput(value: string): string {
  const digits = value.replace(/\D/g, "").slice(0, 8);
  return [digits.slice(0, 2), digits.slice(2, 4), digits.slice(4, 8)].filter(Boolean).join("/");
}
