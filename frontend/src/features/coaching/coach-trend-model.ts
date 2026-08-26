import type { CoachActivityPoint } from "@/lib/api/coaching-types";

// Match the established dashboard palette. More coaches reuse colors with a distinct dash.
const COACH_COLORS = ["#6366f1", "#14b8a6", "#8b5cf6", "#f59e0b", "#ec4899"];
const COACH_DASHES = [undefined, "7 4", "2 4", "10 4 2 4"];
export function coachSeriesStyle(index: number) {
  return { color: COACH_COLORS[index % COACH_COLORS.length], dash: COACH_DASHES[Math.floor(index / COACH_COLORS.length) % COACH_DASHES.length] };
}
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
