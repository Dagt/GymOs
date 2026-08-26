const BUSINESS_ZONE = "America/Mexico_City";

export function businessToday(now = new Date()) {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: BUSINESS_ZONE, year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(now);
  const value = (type: string) => parts.find((part) => part.type === type)?.value ?? "";
  return `${value("year")}-${value("month")}-${value("day")}`;
}

export function businessWeekStart(now = new Date()) {
  const date = new Date(`${businessToday(now)}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() - ((date.getUTCDay() + 6) % 7));
  return date.toISOString().slice(0, 10);
}

export function assignmentDateError(startsOn: string, endsOn: string, today = businessToday()) {
  if (!startsOn || !endsOn) return "Indica el inicio y el fin de la rutina.";
  if (startsOn < today) return "La rutina debe comenzar hoy o en una fecha futura.";
  if (endsOn < startsOn) return "La fecha final no puede ser anterior al inicio.";
  return null;
}
