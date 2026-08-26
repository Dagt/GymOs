import type { CoachActivityPoint } from "@/lib/api/coaching-types";

export const COACH_COLORS = ["#2563eb", "#b7791f", "#c2410c", "#687d31", "#be185d"];
export const coachKey = (id: string | null) => `coach:${id ?? "unassigned"}`;

export function businessDateLabel(value: string) {
  return new Intl.DateTimeFormat("es-MX", { day: "2-digit", month: "short", timeZone: "UTC" })
    .format(new Date(`${value}T12:00:00Z`));
}

/** Complete daily buckets, not interpolation. Cumulative counts start at the selected from date. */
export function coachTimeline(from: string, to: string, activity: CoachActivityPoint[], cumulative: boolean, seriesKeys: string[] = []) {
  const first = Date.parse(`${from}T00:00:00Z`);
  const last = Date.parse(`${to}T00:00:00Z`);
  if (!Number.isFinite(first) || !Number.isFinite(last) || last < first || last - first > 366 * 86_400_000) return [];
  const keys = [...new Set([...seriesKeys, ...activity.map((point) => coachKey(point.coachId))])];
  const daily = new Map<string, Record<string, number>>();
  for (const point of activity) {
    if (point.date < from || point.date > to) continue;
    const row = daily.get(point.date) ?? {};
    const key = coachKey(point.coachId);
    row[key] = (row[key] ?? 0) + point.assignments;
    daily.set(point.date, row);
  }
  const totals: Record<string, number> = {};
  const rows: Array<{ date: string } & Record<string, string | number>> = [];
  for (let time = first; time <= last; time += 86_400_000) {
    const date = new Date(time).toISOString().slice(0, 10);
    const row: { date: string } & Record<string, string | number> = { date };
    for (const key of keys) {
      const count = daily.get(date)?.[key] ?? 0;
      totals[key] = (totals[key] ?? 0) + count;
      row[key] = cumulative ? totals[key] : count;
    }
    rows.push(row);
  }
  return rows;
}
