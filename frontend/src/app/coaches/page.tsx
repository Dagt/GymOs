"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired as AuthRequiredCard } from "@/components/AuthRequired";
import {
  assignWorkout,
  createWorkout,
  getBranches,
  getCoachReport,
  getCoaches,
  getCurrentOrganization,
  getStoredSession,
  getWorkouts,
  searchClients,
  type BranchSummary,
  type CoachReport,
  type CoachSummary,
  type ClientSummary,
  type OrganizationSummary,
  type StoredSession,
  type WorkoutSummary,
} from "@/lib/api";

const defaultWorkoutJson = JSON.stringify({
  days: [
    {
      name: "Dia A",
      blocks: [
        { exercise: "Sentadilla goblet", sets: 3, reps: "10" },
        { exercise: "Press con mancuernas", sets: 3, reps: "8" },
      ],
    },
  ],
}, null, 2);

export default function CoachesPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [organization, setOrganization] = useState<OrganizationSummary | null>(null);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [coaches, setCoaches] = useState<CoachSummary[]>([]);
  const [workouts, setWorkouts] = useState<WorkoutSummary[]>([]);
  const [clients, setClients] = useState<ClientSummary[]>([]);
  const [report, setReport] = useState<CoachReport | null>(null);
  const [workoutTitle, setWorkoutTitle] = useState("");
  const [workoutGoal, setWorkoutGoal] = useState("");
  const [workoutLevel, setWorkoutLevel] = useState<"BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "GENERAL">("GENERAL");
  const [workoutCoachId, setWorkoutCoachId] = useState("");
  const [workoutSummary, setWorkoutSummary] = useState("");
  const [workoutJson, setWorkoutJson] = useState(defaultWorkoutJson);
  const [clientQuery, setClientQuery] = useState("");
  const [selectedClient, setSelectedClient] = useState<ClientSummary | null>(null);
  const [assignmentWorkoutId, setAssignmentWorkoutId] = useState("");
  const [assignmentStartsOn, setAssignmentStartsOn] = useState("");
  const [assignmentEndsOn, setAssignmentEndsOn] = useState("");
  const [assignmentNotes, setAssignmentNotes] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function load() {
      const stored = getStoredSession();
      if (!active) {
        return;
      }
      if (!stored) {
        setLoading(false);
        return;
      }
      setSession(stored);
      try {
        const [loadedOrganization, loadedBranches, loadedCoaches, loadedWorkouts, loadedReport] = await Promise.all([
          getCurrentOrganization(stored.accessToken).catch(() => null),
          getBranches(stored.accessToken),
          getCoaches(stored.accessToken),
          getWorkouts(stored.accessToken),
          getCoachReport(stored.accessToken),
        ]);
        if (!active) {
          return;
        }
        setOrganization(loadedOrganization);
        setBranches(loadedBranches);
        setCoaches(loadedCoaches);
        setWorkouts(loadedWorkouts);
        setReport(loadedReport);
        setWorkoutCoachId(loadedCoaches[0]?.id ?? "");
        setAssignmentWorkoutId(loadedWorkouts[0]?.id ?? "");
      } catch (exception) {
        if (active) {
          setError(exception instanceof Error ? exception.message : "No fue posible cargar coaches.");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void load();

    return () => {
      active = false;
    };
  }, []);

  const selectedWorkout = useMemo(
    () => workouts.find((workout) => workout.id === assignmentWorkoutId) ?? null,
    [assignmentWorkoutId, workouts],
  );
  const activeBranch = branches.find((branch) => branch.id === session?.user.branchId) ?? null;

  async function refresh(activeSession = session) {
    if (!activeSession) {
      return;
    }
    const [nextCoaches, nextWorkouts, nextReport] = await Promise.all([
      getCoaches(activeSession.accessToken),
      getWorkouts(activeSession.accessToken),
      getCoachReport(activeSession.accessToken),
    ]);
    setCoaches(nextCoaches);
    setWorkouts(nextWorkouts);
    setReport(nextReport);
    setWorkoutCoachId((current) => current || nextCoaches[0]?.id || "");
    setAssignmentWorkoutId((current) => current || nextWorkouts[0]?.id || "");
  }

  async function submitWorkout(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const created = await createWorkout(session.accessToken, {
        coachId: workoutCoachId || null,
        title: workoutTitle,
        goal: workoutGoal,
        level: workoutLevel,
        summary: workoutSummary,
        contentJson: workoutJson,
      });
      setWorkoutTitle("");
      setWorkoutGoal("");
      setWorkoutSummary("");
      setWorkoutJson(defaultWorkoutJson);
      setMessage(`Rutina ${created.workout.title} creada.`);
      await refresh(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible crear rutina.");
    } finally {
      setSaving(false);
    }
  }

  async function runClientSearch() {
    if (!session) {
      return;
    }
    setError(null);
    try {
      setClients(await searchClients(session.accessToken, clientQuery, "ACTIVE"));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible buscar clientes.");
    }
  }

  async function submitAssignment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !selectedClient || !selectedWorkout) {
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const assignment = await assignWorkout(session.accessToken, selectedWorkout.id, {
        clientId: selectedClient.id,
        workoutVersionId: selectedWorkout.latestVersionId,
        startsOn: assignmentStartsOn,
        endsOn: assignmentEndsOn,
        notes: assignmentNotes,
      });
      setAssignmentNotes("");
      setMessage(`${assignment.workoutTitle} asignada a ${assignment.clientName}.`);
      await refresh(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "No fue posible asignar rutina.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <LoadingScreen text="Cargando coaches..." />;
  }

  if (!session) {
    return (
      <AuthRequiredCard
        message="Necesitas iniciar sesion para administrar coaches y rutinas."
        title="Inicia sesion"
      />
    );
  }

  return (
    <AppShell
      branchName={activeBranch?.name}
      eyebrow="Gerencia"
      mode={activeBranch ? "operation" : undefined}
      organizationName={organization?.brandName || organization?.tradeName || session.user.brandName || session.user.organizationName}
      session={session}
      subtitle="Entrenadores, planes versionados y asignaciones a clientes."
      title="Coaches y rutinas"
    >
      <section className="grid gap-6">
        <section className="grid gap-4 md:grid-cols-4">
          <Metric label="Coaches" value={report?.coachCount ?? 0} />
          <Metric label="Rutinas" value={report?.workoutCount ?? 0} />
          <Metric label="Asignaciones activas" value={report?.activeAssignments ?? 0} />
          <Metric label="Ultimos 30 dias" value={report?.assignmentsLast30Days ?? 0} />
        </section>

        {error ? (
          <p className="rounded-md border border-[#e5b7b7] bg-[#fff4f4] px-3 py-2 text-sm text-[#8c2f2f]">
            {error}
          </p>
        ) : null}
        {message ? (
          <p className="rounded-md border border-[#bad9c4] bg-[#eff8f1] px-3 py-2 text-sm text-[#31563c]">
            {message}
          </p>
        ) : null}

        <section className="grid items-start gap-6 lg:grid-cols-[0.8fr_1.2fr]">
          <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
            <h2 className="text-xl font-semibold">Coaches activos</h2>
            <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
              {coaches.length === 0 ? (
                <article className="rounded-md border border-dashed border-[#cdd5ca] p-4 text-sm leading-6 text-[#5d6b63]">
                  No hay perfiles operativos de coach disponibles en esta sucursal.
                </article>
              ) : null}
              {coaches.map((coach) => (
                <article className="min-w-0 rounded-md border border-[#d8ddd2] p-4" key={coach.id}>
                  <div className="flex min-w-0 items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="break-words font-semibold">{coach.displayName}</p>
                      <p className="mt-1 break-all text-sm text-[#5d6b63]">{coach.email ?? coach.phone ?? "Sin contacto"}</p>
                    </div>
                    <span className="shrink-0 rounded-md bg-[#eef7f0] px-2 py-1 text-xs font-semibold text-[#31543d]">
                      {coach.status}
                    </span>
                  </div>
                  <p className="mt-3 break-words text-xs text-[#6f7a73]">{formatSpecialties(coach.specialtiesJson)}</p>
                </article>
              ))}
            </div>
          </section>

          <section className="grid gap-6">
            <form className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" onSubmit={submitWorkout}>
              <h2 className="text-xl font-semibold">Nueva rutina</h2>
              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setWorkoutTitle(event.target.value)} placeholder="Titulo" value={workoutTitle} />
                <select className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm" onChange={(event) => setWorkoutCoachId(event.target.value)} value={workoutCoachId}>
                  <option value="">Sin coach</option>
                  {coaches.map((coach) => <option key={coach.id} value={coach.id}>{coach.displayName}</option>)}
                </select>
                <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setWorkoutGoal(event.target.value)} placeholder="Objetivo" value={workoutGoal} />
                <select className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm" onChange={(event) => setWorkoutLevel(event.target.value as "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "GENERAL")} value={workoutLevel}>
                  <option value="GENERAL">General</option>
                  <option value="BEGINNER">Principiante</option>
                  <option value="INTERMEDIATE">Intermedio</option>
                  <option value="ADVANCED">Avanzado</option>
                </select>
              </div>
              <textarea className="mt-3 min-h-20 w-full rounded-md border border-[#cdd5ca] px-3 py-2 text-sm" onChange={(event) => setWorkoutSummary(event.target.value)} placeholder="Resumen" value={workoutSummary} />
              <textarea className="mt-3 min-h-44 w-full rounded-md border border-[#cdd5ca] px-3 py-2 font-mono text-xs" onChange={(event) => setWorkoutJson(event.target.value)} value={workoutJson} />
              <button className="mt-4 h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60" disabled={saving || !workoutTitle.trim()} type="submit">
                Crear rutina
              </button>
            </form>

            <form className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm" onSubmit={submitAssignment}>
              <h2 className="text-xl font-semibold">Asignar rutina</h2>
              <div className="mt-4 flex gap-2">
                <input className="h-10 min-w-0 flex-1 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setClientQuery(event.target.value)} placeholder="Buscar cliente" value={clientQuery} />
                <button className="h-10 rounded-md border border-[#cdd5ca] bg-white px-4 text-sm font-semibold text-[#42514a]" onClick={runClientSearch} type="button">Buscar</button>
              </div>
              <div className="mt-3 grid gap-2">
                {clients.slice(0, 4).map((client) => (
                  <button className={`rounded-md border px-3 py-2 text-left text-sm ${selectedClient?.id === client.id ? "border-[#51735f] bg-[#eef7f0]" : "border-[#d8ddd2]"}`} key={client.id} onClick={() => setSelectedClient(client)} type="button">
                    {client.firstName} {client.lastName}
                  </button>
                ))}
              </div>
              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                <select className="h-10 rounded-md border border-[#cdd5ca] bg-white px-3 text-sm" onChange={(event) => setAssignmentWorkoutId(event.target.value)} value={assignmentWorkoutId}>
                  {workouts.map((workout) => <option key={workout.id} value={workout.id}>{workout.title}</option>)}
                </select>
                <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setAssignmentStartsOn(event.target.value)} type="date" value={assignmentStartsOn} />
                <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setAssignmentEndsOn(event.target.value)} type="date" value={assignmentEndsOn} />
                <input className="h-10 rounded-md border border-[#cdd5ca] px-3 text-sm" onChange={(event) => setAssignmentNotes(event.target.value)} placeholder="Notas" value={assignmentNotes} />
              </div>
              <button className="mt-4 h-10 rounded-md bg-[#13251c] px-4 text-sm font-semibold text-white transition hover:bg-[#243d30] disabled:opacity-60" disabled={saving || !selectedClient || !selectedWorkout} type="submit">
                Asignar
              </button>
            </form>
          </section>
        </section>

        <section className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
          <h2 className="text-xl font-semibold">Rutinas disponibles</h2>
          <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {workouts.map((workout) => (
              <article className="rounded-md border border-[#d8ddd2] p-4" key={workout.id}>
                <p className="font-semibold">{workout.title}</p>
                <p className="mt-1 text-sm text-[#5d6b63]">{workout.goal ?? "Sin objetivo"} - {workout.level}</p>
                <p className="mt-3 text-xs text-[#6f7a73]">
                  {workout.coachName ?? "Sin coach"} - version {workout.latestVersionNumber ?? "N/A"}
                </p>
              </article>
            ))}
          </div>
        </section>
      </section>
    </AppShell>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <article className="rounded-lg border border-[#d8ddd2] bg-white p-5 shadow-sm">
      <p className="text-sm font-medium text-[#5d6b63]">{label}</p>
      <p className="mt-2 text-3xl font-semibold">{value}</p>
    </article>
  );
}

function formatSpecialties(value: string | null) {
  if (!value) {
    return "Sin especialidades";
  }
  try {
    const parsed = JSON.parse(value) as string[];
    return parsed.length ? parsed.join(", ") : "Sin especialidades";
  } catch {
    return value;
  }
}
