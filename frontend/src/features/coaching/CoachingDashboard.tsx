"use client";

import { useEffect, useMemo, useState } from "react";
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { getAssignmentReport, getAssignmentTrend, getCoachReport, type AssignmentReport, type CoachReport, type WeeklyTrendReport } from "@/lib/api";
import { businessDateLabel, coachSeriesStyle, coachKey, coachTimeline } from "./coach-trend-model";

// Chart contract: weekly counts use rounded grouped bars; first/renewal counts
// use unstacked areas, restoring the existing dashboard's blue/teal/violet style.
// Coach progress uses a date axis with complete daily buckets (normally 30),
// optionally accumulated within the selected period, and ALL coaches overlaid.
// Both use the same branch/range; cancelled assignments are excluded by the API.
// Exact per-coach totals come from the report, never sums of daily unique clients.
// Palette + dash patterns + named legend/table; zero baseline, sparse points shown.
// Native Recharts in the existing app; desktop side-by-side, stacked on mobile.
// Missing requests show an error, genuine empty results show an empty state.
const chartAxis = { tickLine: false, axisLine: false, tick: { fill: "#6b7280", fontSize: 11 } };
const chartTooltip = { borderRadius: 12, border: "1px solid #e5e7eb", boxShadow: "0 8px 24px rgb(15 23 42 / .08)", fontSize: 12 };
const chartMargin = { top: 12, right: 12, left: -24, bottom: 0 };

export function CoachingDashboard({ accessToken, from, to }: { accessToken: string; from: string; to: string }) {
  const [data, setData] = useState<{ summary: CoachReport; report: AssignmentReport; trend: WeeklyTrendReport } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const [cumulative, setCumulative] = useState(true);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true); setError(null);
      try {
        const [summary, report, trend] = await Promise.all([
          getCoachReport(accessToken), getAssignmentReport(accessToken, { from, to, size: 1 }),
          getAssignmentTrend(accessToken, { from, to }),
        ]);
        if (active) setData({ summary, report, trend });
      } catch (exception) {
        if (active) setError(exception instanceof Error ? exception.message : "No fue posible consultar las métricas de coaches.");
      } finally { if (active) setLoading(false); }
    }
    void load();
    return () => { active = false; };
  }, [accessToken, from, to, revision]);

  const coaches = useMemo(() => {
    const options = new Map<string, string>();
    for (const coach of data?.report.coaches ?? []) options.set(coachKey(coach.coachId), coach.coachName);
    for (const point of data?.trend.coachActivity ?? []) options.set(coachKey(point.coachId), point.coachName);
    return [...options].map(([key, name]) => ({ key, name })).sort((left, right) => left.name.localeCompare(right.name, "es"));
  }, [data]);
  const timeline = useMemo(() => coachTimeline(from, to, data?.trend.coachActivity ?? [], cumulative, coaches.map((coach) => coach.key)), [from, to, data, cumulative, coaches]);

  if (loading) return <p role="status" className="app-card p-6">Cargando métricas de coaches…</p>;
  if (error) return <section className="app-card p-5"><p role="alert" className="text-sm text-red-700">{error}</p><button className="app-button-secondary mt-3" type="button" onClick={() => setRevision((value) => value + 1)}>Reintentar métricas</button></section>;
  if (!data) return null;
  const { summary, report, trend } = data;
  const weekly = trend.points.map((point) => ({ ...point, renewals: Math.max(0, point.assignments - point.firstWorkouts) }));
  const hasActivity = weekly.some((point) => point.assignments > 0);
  const period = `${businessDateLabel(from)} ${from.slice(0, 4)} – ${businessDateLabel(to)} ${to.slice(0, 4)} · Ciudad de México`;
  const empty = <p className="flex h-full items-center justify-center text-sm text-[var(--brand-muted)]">No hay asignaciones en el periodo seleccionado.</p>;

  return <div className="grid min-w-0 gap-5" data-testid="coaching-dashboard">
    <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {[
        ["Coaches activos", summary.coachCount, "En esta sucursal hoy"],
        ["Asignaciones vigentes", summary.activeAssignments, "Hoy; excluye programadas y finalizadas"],
        ["Asignaciones del periodo", report.assignments, period],
        ["Rutinas disponibles", summary.workoutCount, "Catálogo de esta sucursal"],
      ].map(([title, value, subtitle]) => <article className="app-card p-5" key={title}><p className="app-eyebrow">{title}</p><strong className="mt-2 block text-3xl">{value}</strong><p className="mt-2 text-xs text-[var(--brand-muted)]">{subtitle}</p></article>)}
    </section>

    <section className="grid min-w-0 gap-5 xl:grid-cols-2">
      <figure className="app-card min-w-0 p-5">
        <figcaption><h3 className="text-lg font-semibold">Tendencia semanal de asignaciones</h3><p className="mt-1 text-xs text-[var(--brand-muted)]">{period}. Totales por semana.</p></figcaption>
        <div className="mt-4 h-64" data-testid="weekly-assignments">{hasActivity ? <ResponsiveContainer width="100%" height="100%"><BarChart data={weekly} margin={chartMargin} barGap={5}>
          <CartesianGrid strokeDasharray="3 6" vertical={false} stroke="#e5e7eb" /><XAxis {...chartAxis} dataKey="weekFrom" tickFormatter={businessDateLabel} minTickGap={24} dy={6} /><YAxis {...chartAxis} allowDecimals={false} />
          <Tooltip contentStyle={chartTooltip} cursor={{ fill: "#f1f5f9", radius: 6 }} labelFormatter={(value) => `Semana del ${businessDateLabel(String(value))}`} /><Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12, paddingTop: 12 }} />
          <Bar dataKey="assignments" name="Asignaciones" fill="#3b82f6" radius={[5, 5, 0, 0]} maxBarSize={28} isAnimationActive={false} /><Bar dataKey="uniqueClients" name="Socios únicos" fill="#10b981" radius={[5, 5, 0, 0]} maxBarSize={28} isAnimationActive={false} />
        </BarChart></ResponsiveContainer> : empty}</div>
      </figure>
      <figure className="app-card min-w-0 p-5">
        <figcaption><h3 className="text-lg font-semibold">Primerizas vs. renovaciones</h3><p className="mt-1 text-xs text-[var(--brand-muted)]">{period}. Primera rutina del socio vs. posteriores.</p></figcaption>
        <div className="mt-4 h-64" data-testid="first-renewal-trend">{hasActivity ? <ResponsiveContainer width="100%" height="100%"><AreaChart data={weekly} margin={chartMargin}>
          <CartesianGrid strokeDasharray="3 6" vertical={false} stroke="#e5e7eb" /><XAxis {...chartAxis} dataKey="weekFrom" tickFormatter={businessDateLabel} minTickGap={24} dy={6} /><YAxis {...chartAxis} allowDecimals={false} />
          <Tooltip contentStyle={chartTooltip} labelFormatter={(value) => `Semana del ${businessDateLabel(String(value))}`} /><Legend iconType="circle" iconSize={8} wrapperStyle={{ fontSize: 12, paddingTop: 12 }} />
          <Area type="monotone" dataKey="firstWorkouts" name="Primerizas" stroke="#8b5cf6" fill="#8b5cf6" fillOpacity={.1} strokeWidth={2.5} dot={{ r: 3, fill: "white", strokeWidth: 2 }} isAnimationActive={false} />
          <Area type="monotone" dataKey="renewals" name="Renovaciones" stroke="#14b8a6" fill="#14b8a6" fillOpacity={.06} strokeWidth={2.5} strokeDasharray="6 3" dot={{ r: 3, fill: "white", strokeWidth: 2 }} isAnimationActive={false} />
        </AreaChart></ResponsiveContainer> : empty}</div>
      </figure>
    </section>

    <figure className="app-card min-w-0 p-5">
      <figcaption className="flex flex-wrap items-start justify-between gap-3">
        <div><h3 className="text-lg font-semibold">Rutinas asignadas por coach a lo largo del tiempo</h3><p className="mt-1 text-xs text-[var(--brand-muted)]">{period}. {cumulative ? "Asignaciones acumuladas desde el inicio del periodo." : "Número de asignaciones creadas cada día."}</p></div>
        <label className="flex items-center gap-2 text-xs text-[var(--brand-muted)]">Vista<select aria-label="Vista de evolución por coach" className="rounded-lg border border-[var(--brand-line)] bg-transparent px-2 py-1.5 text-xs" value={cumulative ? "cumulative" : "daily"} onChange={(event) => setCumulative(event.target.value === "cumulative")}><option value="cumulative">Acumuladas</option><option value="daily">Por día</option></select></label>
      </figcaption>
      <div className="mt-5 grid min-w-0 gap-6 xl:grid-cols-[minmax(0,1.5fr)_minmax(20rem,1fr)]">
        <div className="min-w-0">
          <div className="h-72" data-testid="coach-timeline">{trend.coachActivity === undefined ? <p role="alert" className="text-sm text-amber-800">Actualiza el backend para consultar la evolución diaria por coach.</p> : coaches.length ? <ResponsiveContainer width="100%" height="100%"><LineChart data={timeline} margin={chartMargin}>
            <CartesianGrid strokeDasharray="3 6" vertical={false} stroke="#e5e7eb" /><XAxis {...chartAxis} dataKey="date" tickFormatter={businessDateLabel} minTickGap={40} dy={6} /><YAxis {...chartAxis} allowDecimals={false} />
            <Tooltip contentStyle={{ ...chartTooltip, maxHeight: 240, overflowY: "auto" }} labelFormatter={(value) => businessDateLabel(String(value))} />
            {coaches.map((coach, index) => {
              const style = coachSeriesStyle(index);
              return <Line key={coach.key} dataKey={coach.key} name={coach.name} type="monotone" stroke={style.color} strokeWidth={2.5} strokeDasharray={style.dash} dot={timeline.length <= 12 ? { r: 3, fill: "white", strokeWidth: 2 } : false} activeDot={{ r: 5, stroke: "white", strokeWidth: 2 }} isAnimationActive={false} />;
            })}
          </LineChart></ResponsiveContainer> : empty}</div>
          <ul aria-label="Coaches en la gráfica" className="app-scroll-list app-scroll-list--short mt-3 flex flex-wrap justify-center gap-x-4 gap-y-2 text-xs text-[var(--brand-muted)]">
            {coaches.map((coach, index) => <li key={coach.key} className="inline-flex items-center gap-1.5"><svg width="18" height="10" aria-hidden="true"><line x1="0" y1="5" x2="18" y2="5" stroke={coachSeriesStyle(index).color} strokeWidth="2.5" strokeDasharray={coachSeriesStyle(index).dash} /></svg>{coach.name}</li>)}
          </ul>
        </div>
        <div className="min-w-0">
          <h4 className="mb-3 text-sm font-semibold">Resumen por coach</h4>
          <div className="app-scroll-list max-h-80" role="region" aria-label="Resumen por coach" tabIndex={0}>
            <table className="w-full text-left text-xs">
              <caption className="sr-only">Totales del periodo seleccionado por coach</caption>
              <thead className="sticky top-0 text-[var(--brand-muted)]"><tr><th className="rounded-l-lg px-2 py-3 font-medium">Coach</th><th className="px-2 py-3 text-right font-medium">Rutinas</th><th className="px-2 py-3 text-right font-medium">Socios</th><th className="rounded-r-lg px-2 py-3 text-right font-medium">1ra vez</th></tr></thead>
              <tbody>{report.coaches.map((coach) => <tr key={coachKey(coach.coachId)} className="border-b border-[var(--brand-line)] last:border-0">
                <th scope="row" className="px-2 py-3 font-medium"><span className="mr-2 inline-block h-2 w-2 rounded-full" style={{ background: coachSeriesStyle(coaches.findIndex((item) => item.key === coachKey(coach.coachId))).color }} />{coach.coachName}</th>
                <td className="px-2 py-3 text-right font-semibold tabular-nums text-indigo-600">{coach.assignments}</td><td className="px-2 py-3 text-right font-semibold tabular-nums text-teal-600">{coach.uniqueClients}</td><td className="px-2 py-3 text-right tabular-nums">{coach.firstWorkouts}</td>
              </tr>)}</tbody>
            </table>
            {!report.coaches.length ? <p className="py-4 text-xs text-[var(--brand-muted)]">Sin totales por coach en este periodo.</p> : null}
          </div>
        </div>
      </div>
      <details className="mt-4"><summary className="cursor-pointer text-sm font-semibold">Ver totales y datos semanales</summary><div className="app-scroll-list mt-3" tabIndex={0} role="region" aria-label="Datos semanales de coaches"><table className="w-full text-left text-sm"><caption className="sr-only">Totales semanales del periodo seleccionado</caption><thead><tr><th className="p-2">Semana</th><th className="p-2">Asignaciones</th><th className="p-2">Socios únicos</th><th className="p-2">Primerizas</th><th className="p-2">Renovaciones</th></tr></thead><tbody>{weekly.map((row) => <tr className="border-t" key={row.weekFrom}><th className="p-2 font-normal">{businessDateLabel(row.weekFrom)}</th><td className="p-2">{row.assignments}</td><td className="p-2">{row.uniqueClients}</td><td className="p-2">{row.firstWorkouts}</td><td className="p-2">{row.renewals}</td></tr>)}</tbody></table></div><p className="mt-3 text-xs text-[var(--brand-muted)]">{report.assignments} asignaciones y {report.uniqueClients} socios únicos en el periodo. Los socios únicos semanales no se suman: un socio puede aparecer en varias semanas. Se excluyen cancelaciones; un reemplazo conserva la asignación anterior en el historial.</p></details>
    </figure>
  </div>;
}
