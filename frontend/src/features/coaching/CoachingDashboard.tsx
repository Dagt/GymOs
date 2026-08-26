"use client";

import { useEffect, useMemo, useState } from "react";
import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { getAssignmentReport, getAssignmentTrend, getCoachReport, type AssignmentReport, type CoachReport, type WeeklyTrendReport } from "@/lib/api";
import { businessDateLabel, COACH_COLORS, coachKey, coachTimeline } from "./coach-trend-model";

// Chart contract: weekly counts/composition use grouped bars; coach progress uses
// a date axis with daily buckets, optionally accumulated within the selected period.
// Both use the same branch/range; cancelled assignments are excluded by the API.
// Up to five selected coaches, explicit palette + dash patterns + named legend.
// Missing requests show an error, genuine empty results show an empty state.
export function CoachingDashboard({ accessToken, from, to }: { accessToken: string; from: string; to: string }) {
  const [data, setData] = useState<{ summary: CoachReport; report: AssignmentReport; trend: WeeklyTrendReport } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const [cumulative, setCumulative] = useState(true);
  const [selected, setSelected] = useState<string[] | null>(null);

  useEffect(() => {
    let active = true;
    async function load() {
      setLoading(true); setError(null);
      try {
        const [summary, report, trend] = await Promise.all([
          getCoachReport(accessToken), getAssignmentReport(accessToken, { from, to, size: 1 }),
          getAssignmentTrend(accessToken, { from, to }),
        ]);
        if (active) { setData({ summary, report, trend }); setSelected(null); }
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
  const visible = selected ?? coaches.slice(0, 5).map((coach) => coach.key);
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
        <figcaption><h3 className="font-bold">Tendencia semanal de asignaciones</h3><p className="mt-1 text-xs text-[var(--brand-muted)]">{period}. Semanas de lunes a domingo; sólo días del periodo.</p></figcaption>
        <div className="mt-3 h-64">{hasActivity ? <ResponsiveContainer width="100%" height="100%"><BarChart data={weekly} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e5e7eb" /><XAxis dataKey="weekFrom" tickFormatter={businessDateLabel} fontSize={11} minTickGap={20} /><YAxis allowDecimals={false} fontSize={11} />
          <Tooltip labelFormatter={(value) => `Semana del ${businessDateLabel(String(value))}`} /><Legend />
          <Bar dataKey="assignments" name="Asignaciones" fill="#2563eb" maxBarSize={34} isAnimationActive={false} /><Bar dataKey="uniqueClients" name="Socios únicos" fill="#b7791f" maxBarSize={34} isAnimationActive={false} />
        </BarChart></ResponsiveContainer> : empty}</div>
      </figure>
      <figure className="app-card min-w-0 p-5">
        <figcaption><h3 className="font-bold">Primerizas vs. renovaciones</h3><p className="mt-1 text-xs text-[var(--brand-muted)]">{period}. Primera asignación del socio en el gimnasio vs. asignaciones posteriores.</p></figcaption>
        <div className="mt-3 h-64">{hasActivity ? <ResponsiveContainer width="100%" height="100%"><BarChart data={weekly} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e5e7eb" /><XAxis dataKey="weekFrom" tickFormatter={businessDateLabel} fontSize={11} minTickGap={20} /><YAxis allowDecimals={false} fontSize={11} />
          <Tooltip labelFormatter={(value) => `Semana del ${businessDateLabel(String(value))}`} /><Legend />
          <Bar dataKey="firstWorkouts" name="Primerizas" fill="#2563eb" maxBarSize={34} isAnimationActive={false} /><Bar dataKey="renewals" name="Renovaciones" fill="#b7791f" maxBarSize={34} isAnimationActive={false} />
        </BarChart></ResponsiveContainer> : empty}</div>
      </figure>
    </section>

    <figure className="app-card min-w-0 p-5">
      <figcaption className="flex flex-wrap items-start justify-between gap-3">
        <div><h3 className="font-bold">Rutinas asignadas por coach a lo largo del tiempo</h3><p className="mt-1 text-xs text-[var(--brand-muted)]">{period}. {cumulative ? "Asignaciones acumuladas desde el inicio del periodo." : "Número de asignaciones creadas cada día."}</p></div>
        <label className="text-sm">Vista<select aria-label="Vista de evolución por coach" className="app-input mt-1" value={cumulative ? "cumulative" : "daily"} onChange={(event) => setCumulative(event.target.value === "cumulative")}><option value="cumulative">Acumuladas en el periodo</option><option value="daily">Por día</option></select></label>
      </figcaption>
      {coaches.length ? <fieldset className="app-scroll-list app-scroll-list--short mt-3 flex flex-wrap gap-3 text-sm"><legend className="mb-2 text-xs text-[var(--brand-muted)]">Coaches visibles (máximo 5). Las líneas tienen colores y trazos distintos.</legend>{coaches.map((coach) => <label key={coach.key} className="inline-flex items-center gap-2"><input type="checkbox" checked={visible.includes(coach.key)} disabled={!visible.includes(coach.key) && visible.length >= 5} onChange={(event) => setSelected(event.target.checked ? [...visible, coach.key] : visible.filter((key) => key !== coach.key))} />{coach.name}</label>)}</fieldset> : null}
      <div className="mt-4 h-72" data-testid="coach-timeline">{trend.coachActivity === undefined ? <p role="alert" className="text-sm text-amber-800">Actualiza el backend para consultar la evolución diaria por coach.</p> : coaches.length && visible.length ? <ResponsiveContainer width="100%" height="100%"><LineChart data={timeline} margin={{ top: 10, right: 20, left: -20, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e5e7eb" /><XAxis dataKey="date" tickFormatter={businessDateLabel} fontSize={11} minTickGap={35} /><YAxis allowDecimals={false} fontSize={11} /><Tooltip labelFormatter={(value) => businessDateLabel(String(value))} /><Legend />
        {coaches.filter((coach) => visible.includes(coach.key)).map((coach, index) => <Line key={coach.key} dataKey={coach.key} name={coach.name} type="linear" stroke={COACH_COLORS[index]} strokeWidth={2} strokeDasharray={[undefined, "6 3", "2 3", "8 3 2 3", "12 4"][index]} dot={timeline.length <= 12 ? { r: 3 } : false} activeDot={{ r: 4 }} isAnimationActive={false} />)}
      </LineChart></ResponsiveContainer> : coaches.length ? <p className="text-sm text-[var(--brand-muted)]">Selecciona al menos un coach.</p> : empty}</div>
      <details className="mt-4"><summary className="cursor-pointer text-sm font-semibold">Ver totales y datos semanales</summary><div className="app-scroll-list mt-3" tabIndex={0} role="region" aria-label="Datos semanales de coaches"><table className="w-full text-left text-sm"><caption className="sr-only">Totales semanales del periodo seleccionado</caption><thead><tr><th className="p-2">Semana</th><th className="p-2">Asignaciones</th><th className="p-2">Socios únicos</th><th className="p-2">Primerizas</th><th className="p-2">Renovaciones</th></tr></thead><tbody>{weekly.map((row) => <tr className="border-t" key={row.weekFrom}><th className="p-2 font-normal">{businessDateLabel(row.weekFrom)}</th><td className="p-2">{row.assignments}</td><td className="p-2">{row.uniqueClients}</td><td className="p-2">{row.firstWorkouts}</td><td className="p-2">{row.renewals}</td></tr>)}</tbody></table></div><p className="mt-3 text-xs text-[var(--brand-muted)]">{report.assignments} asignaciones y {report.uniqueClients} socios únicos en el periodo. Los socios únicos semanales no se suman: un socio puede aparecer en varias semanas. Se excluyen cancelaciones; un reemplazo conserva la asignación anterior en el historial.</p></details>
    </figure>
  </div>;
}
