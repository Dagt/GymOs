"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import {
  addExerciseAlias,
  assignWorkout,
  createExercise,
  createWorkout,
  downloadAssignmentCsv,
  downloadWorkoutPdf,
  duplicateWorkout,
  getAssignmentReport,
  getBranches,
  getCoaches,
  getExercises,
  getStoredSession,
  getWorkout,
  getWorkouts,
  publishWorkout,
  searchClients,
  updateWorkout,
  updateWorkoutAssignmentStatus,
  type AssignmentReport,
  type BranchSummary,
  type ClientSummary,
  type CoachSummary,
  type Exercise,
  type StoredSession,
  type WorkoutExerciseDraft,
  type WorkoutSessionDraft,
  type WorkoutSummary,
} from "@/lib/api";

type Tab = "builder" | "catalog" | "assignments" | "metrics";

const emptyExercise = (): WorkoutExerciseDraft => ({
  exerciseName: "",
  prescription: "",
  optional: false,
});

const emptySession = (index: number): WorkoutSessionDraft => ({
  name: `Día ${index + 1}`,
  focus: "",
  notes: "",
  blocks: [{ name: "Trabajo principal", type: "MAIN", notes: "", exercises: [emptyExercise()] }],
});

export default function CoachesPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [coaches, setCoaches] = useState<CoachSummary[]>([]);
  const [workouts, setWorkouts] = useState<WorkoutSummary[]>([]);
  const [exercisePage, setExercisePage] = useState<Exercise[]>([]);
  const [report, setReport] = useState<AssignmentReport | null>(null);
  const [tab, setTab] = useState<Tab>("builder");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const [title, setTitle] = useState("");
  const [goal, setGoal] = useState("");
  const [level, setLevel] = useState("GENERAL");
  const [coachId, setCoachId] = useState("");
  const [summary, setSummary] = useState("");
  const [editingWorkoutId, setEditingWorkoutId] = useState<string | null>(null);
  const [sessions, setSessions] = useState<WorkoutSessionDraft[]>([emptySession(0)]);

  const [exerciseQuery, setExerciseQuery] = useState("");
  const [aliasDrafts, setAliasDrafts] = useState<Record<string, string>>({});
  const [newExercise, setNewExercise] = useState({
    name: "", primaryMuscleGroup: "", equipment: "", type: "STRENGTH", instructions: "",
  });

  const [clientQuery, setClientQuery] = useState("");
  const [clients, setClients] = useState<ClientSummary[]>([]);
  const [selectedClient, setSelectedClient] = useState<ClientSummary | null>(null);
  const [selectedWorkoutId, setSelectedWorkoutId] = useState("");
  const [startsOn, setStartsOn] = useState("");
  const [endsOn, setEndsOn] = useState("");
  const [printableNotes, setPrintableNotes] = useState("");
  const [privateNotes, setPrivateNotes] = useState("");
  const [reportFrom, setReportFrom] = useState(startOfWeek());
  const [reportTo, setReportTo] = useState(today());

  useEffect(() => {
    let active = true;
    async function load() {
      const stored = getStoredSession();
      if (!stored) { setLoading(false); return; }
      setSession(stored);
      try {
        const [loadedBranches, loadedCoaches, loadedWorkouts, loadedExercises, loadedReport] = await Promise.all([
          getBranches(stored.accessToken),
          getCoaches(stored.accessToken),
          getWorkouts(stored.accessToken),
          getExercises(stored.accessToken, { size: 60 }),
          getAssignmentReport(stored.accessToken, { from: startOfWeek(), to: today() }),
        ]);
        if (!active) return;
        setBranches(loadedBranches);
        setCoaches(loadedCoaches);
        setWorkouts(loadedWorkouts);
        setExercisePage(loadedExercises.items);
        setReport(loadedReport);
        setCoachId(loadedCoaches[0]?.id ?? "");
        setSelectedWorkoutId(loadedWorkouts[0]?.id ?? "");
      } catch (exception) {
        if (active) setError(errorMessage(exception));
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => { active = false; };
  }, []);

  const activeBranch = branches.find((branch) => branch.id === session?.user.branchId);
  const filteredExercises = useMemo(() => {
    const query = exerciseQuery.trim().toLowerCase();
    if (!query) return exercisePage;
    return exercisePage.filter((exercise) =>
      [exercise.name, exercise.primaryMuscleGroup, exercise.equipment]
        .some((value) => value?.toLowerCase().includes(query)),
    );
  }, [exercisePage, exerciseQuery]);
  const publishedWorkouts = useMemo(
    () => workouts.filter((workout) => workout.status === "PUBLISHED" || workout.status === "ACTIVE"),
    [workouts],
  );

  async function refresh() {
    if (!session) return;
    const [nextWorkouts, nextExercises, nextReport] = await Promise.all([
      getWorkouts(session.accessToken),
      getExercises(session.accessToken, { query: exerciseQuery, size: 100 }),
      getAssignmentReport(session.accessToken, { from: reportFrom, to: reportTo }),
    ]);
    setWorkouts(nextWorkouts);
    setExercisePage(nextExercises.items);
    setReport(nextReport);
    setSelectedWorkoutId((current) => current || nextWorkouts[0]?.id || "");
  }

  async function saveWorkout(event: FormEvent) {
    event.preventDefault();
    if (!session) return;
    const validation = validateWorkout(title, sessions);
    if (validation) { setError(validation); return; }
    setSaving(true); clearFeedback();
    try {
      const payload = {
        coachId: coachId || null,
        title, goal, level: level as "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "GENERAL",
        summary,
        contentJson: JSON.stringify({ sessions }),
      };
      const created = editingWorkoutId
        ? await updateWorkout(session.accessToken, editingWorkoutId, payload)
        : await createWorkout(session.accessToken, payload);
      const submitter = (event.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null;
      const shouldPublish = submitter?.value !== "draft";
      if (shouldPublish) await publishWorkout(session.accessToken, created.workout.id);
      setMessage(shouldPublish
        ? `Rutina “${created.workout.title}” publicada correctamente.`
        : `Borrador “${created.workout.title}” guardado.`);
      resetWorkoutForm();
      await refresh();
    } catch (exception) { setError(errorMessage(exception)); }
    finally { setSaving(false); }
  }

  async function saveExercise(event: FormEvent) {
    event.preventDefault();
    if (!session) return;
    setSaving(true); clearFeedback();
    try {
      const created = await createExercise(session.accessToken, newExercise);
      setExercisePage((current) => [...current, created].sort((a, b) => a.name.localeCompare(b.name)));
      setNewExercise({ name: "", primaryMuscleGroup: "", equipment: "", type: "STRENGTH", instructions: "" });
      setMessage(`Ejercicio “${created.name}” agregado al catálogo.`);
    } catch (exception) { setError(errorMessage(exception)); }
    finally { setSaving(false); }
  }

  async function saveAlias(exercise: Exercise) {
    if (!session) return;
    const alias = aliasDrafts[exercise.id]?.trim();
    if (!alias) return;
    setSaving(true); clearFeedback();
    try {
      await addExerciseAlias(session.accessToken, exercise.id, alias);
      setAliasDrafts((current) => ({ ...current, [exercise.id]: "" }));
      setMessage(`Alias "${alias}" agregado a ${exercise.name}.`);
    } catch (exception) { setError(errorMessage(exception)); }
    finally { setSaving(false); }
  }

  async function findClients() {
    if (!session) return;
    clearFeedback();
    try { setClients(await searchClients(session.accessToken, clientQuery, "ACTIVE")); }
    catch (exception) { setError(errorMessage(exception)); }
  }

  async function saveAssignment(event: FormEvent) {
    event.preventDefault();
    if (!session || !selectedClient || !selectedWorkoutId) return;
    setSaving(true); clearFeedback();
    try {
      const workout = workouts.find((item) => item.id === selectedWorkoutId);
      const assignment = await assignWorkout(session.accessToken, selectedWorkoutId, {
        clientId: selectedClient.id,
        workoutVersionId: workout?.latestVersionId ?? null,
        startsOn: startsOn || null,
        endsOn: endsOn || null,
        notes: privateNotes || null,
        printableNotes: printableNotes || null,
        idempotencyKey: `workout:${selectedWorkoutId}:${selectedClient.id}:${crypto.randomUUID()}`,
      });
      setMessage(`Rutina asignada a ${assignment.clientName}. El PDF ya está disponible.`);
      setPrintableNotes(""); setPrivateNotes("");
      await refresh();
      setTab("assignments");
    } catch (exception) { setError(errorMessage(exception)); }
    finally { setSaving(false); }
  }

  async function loadReport() {
    if (!session) return;
    setSaving(true); clearFeedback();
    try { setReport(await getAssignmentReport(session.accessToken, { from: reportFrom, to: reportTo })); }
    catch (exception) { setError(errorMessage(exception)); }
    finally { setSaving(false); }
  }

  function updateSession(index: number, patch: Partial<WorkoutSessionDraft>) {
    setSessions((current) => current.map((item, position) => position === index ? { ...item, ...patch } : item));
  }

  function updateExercise(sessionIndex: number, exerciseIndex: number, patch: Partial<WorkoutExerciseDraft>) {
    setSessions((current) => current.map((day, dayIndex) => {
      if (dayIndex !== sessionIndex) return day;
      const block = day.blocks[0];
      return {
        ...day,
        blocks: [{
          ...block,
          exercises: block.exercises.map((exercise, position) =>
            position === exerciseIndex ? { ...exercise, ...patch } : exercise),
        }],
      };
    }));
  }

  function addCatalogExercise(sessionIndex: number, exercise: Exercise) {
    setSessions((current) => current.map((day, dayIndex) => {
      if (dayIndex !== sessionIndex) return day;
      const block = day.blocks[0];
      return {
        ...day,
        blocks: [{
          ...block,
          exercises: [...block.exercises, {
            exerciseId: exercise.id, exerciseName: exercise.name, prescription: "", optional: false,
          }],
        }],
      };
    }));
  }

  async function copyWorkout(workoutId: string) {
    if (!session) return;
    setSaving(true); clearFeedback();
    try {
      const copy = await duplicateWorkout(session.accessToken, workoutId);
      setMessage(`Se creó “${copy.workout.title}”.`);
      await refresh();
    } catch (exception) { setError(errorMessage(exception)); }
    finally { setSaving(false); }
  }

  async function editWorkout(workoutId: string) {
    if (!session) return;
    setSaving(true); clearFeedback();
    try {
      const detail = await getWorkout(session.accessToken, workoutId);
      const version = detail.versions[0];
      const content = version
        ? JSON.parse(version.contentJson) as { sessions?: WorkoutSessionDraft[]; days?: WorkoutSessionDraft[] }
        : {};
      setEditingWorkoutId(workoutId);
      setTitle(detail.workout.title);
      setGoal(detail.workout.goal ?? "");
      setLevel(detail.workout.level);
      setCoachId(detail.workout.coachId ?? "");
      setSummary(version?.summary ?? "");
      setSessions(content.sessions ?? content.days ?? [emptySession(0)]);
      setMessage(`Editando "${detail.workout.title}".`);
      window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (exception) { setError(errorMessage(exception)); }
    finally { setSaving(false); }
  }

  function resetWorkoutForm() {
    setEditingWorkoutId(null);
    setTitle(""); setGoal(""); setSummary(""); setSessions([emptySession(0)]);
  }

  async function publishExistingWorkout(workoutId: string) {
    if (!session) return;
    setSaving(true); clearFeedback();
    try {
      const published = await publishWorkout(session.accessToken, workoutId);
      setMessage(`“${published.workout.title}” quedó publicada.`);
      await refresh();
    } catch (exception) { setError(errorMessage(exception)); }
    finally { setSaving(false); }
  }

  if (loading) return <LoadingScreen text="Cargando Coaches y rutinas..." />;
  if (!session) return <AuthRequired message="Necesitas iniciar sesión para gestionar rutinas." title="Inicia sesión" />;

  return (
    <AppShell
      branchName={activeBranch?.name}
      eyebrow="Entrenamiento"
      mode={activeBranch ? "operation" : undefined}
      organizationName={session.user.brandName || session.user.organizationName}
      session={session}
      subtitle="Crea planes completos, asígnalos a socios y mide el acompañamiento del equipo."
      title="Coaches y rutinas"
    >
      <div className="grid gap-5">
        <nav aria-label="Secciones de Coaches" className="flex flex-wrap gap-2">
          {([
            ["builder", "Constructor"],
            ["catalog", "Ejercicios"],
            ["assignments", "Asignaciones"],
            ["metrics", "Métricas"],
          ] as [Tab, string][]).map(([value, label]) => (
            <button
              className={`rounded-full px-4 py-2 text-sm font-semibold ${tab === value ? "bg-[#173c2a] text-white" : "border border-[#cfd9d2] bg-white text-[#365143]"}`}
              key={value}
              onClick={() => setTab(value)}
              type="button"
            >
              {label}
            </button>
          ))}
        </nav>

        {error ? <p className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800" role="alert">{error}</p> : null}
        {message ? <p className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">{message}</p> : null}

        {tab === "builder" ? (
          <form className="grid gap-5" onSubmit={saveWorkout}>
            <section className="rounded-xl border border-[#d9e2dc] bg-white p-5 shadow-sm">
              <div className="mb-4">
                <p className="text-xs font-semibold uppercase tracking-wider text-[#6b7d72]">Plantilla</p>
                <h2 className="text-xl font-semibold">{editingWorkoutId ? "Editar rutina" : "Información de la rutina"}</h2>
              </div>
              <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                <Field label="Título"><input className="app-input" onChange={(e) => setTitle(e.target.value)} value={title} /></Field>
                <Field label="Objetivo"><input className="app-input" onChange={(e) => setGoal(e.target.value)} value={goal} /></Field>
                <Field label="Nivel">
                  <select className="app-input" onChange={(e) => setLevel(e.target.value)} value={level}>
                    <option value="GENERAL">General</option><option value="BEGINNER">Principiante</option>
                    <option value="INTERMEDIATE">Intermedio</option><option value="ADVANCED">Avanzado</option>
                  </select>
                </Field>
                <Field label="Coach">
                  <select className="app-input" onChange={(e) => setCoachId(e.target.value)} value={coachId}>
                    <option value="">Sin coach</option>
                    {coaches.map((coach) => <option key={coach.id} value={coach.id}>{coach.displayName}</option>)}
                  </select>
                </Field>
              </div>
              <Field label="Indicaciones generales">
                <textarea className="app-input mt-3 min-h-20" onChange={(e) => setSummary(e.target.value)} value={summary} />
              </Field>
            </section>

            {sessions.map((day, sessionIndex) => (
              <section className="rounded-xl border border-[#d9e2dc] bg-white p-5 shadow-sm" key={sessionIndex}>
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="grid flex-1 gap-3 md:grid-cols-2">
                    <Field label={`Sesión ${sessionIndex + 1}`}>
                      <input className="app-input" onChange={(e) => updateSession(sessionIndex, { name: e.target.value })} value={day.name} />
                    </Field>
                    <Field label="Enfoque">
                      <input className="app-input" onChange={(e) => updateSession(sessionIndex, { focus: e.target.value })} placeholder="Pecho y hombro" value={day.focus ?? ""} />
                    </Field>
                  </div>
                  {sessions.length > 1 ? <button className="text-sm font-semibold text-red-700" onClick={() => setSessions((items) => items.filter((_, i) => i !== sessionIndex))} type="button">Eliminar día</button> : null}
                </div>
                <textarea className="app-input mt-3 min-h-16" onChange={(e) => updateSession(sessionIndex, { notes: e.target.value })} placeholder="Movilidad, calentamiento o notas del día" value={day.notes ?? ""} />

                <div className="mt-4 grid gap-3">
                  {day.blocks[0].exercises.map((exercise, exerciseIndex) => (
                    <article className="grid gap-2 rounded-lg border border-[#e0e7e2] bg-[#f8faf8] p-3 lg:grid-cols-[1.2fr_1fr_1fr_auto]" key={exerciseIndex}>
                      <input className="app-input" list={`exercise-options-${sessionIndex}`} onChange={(e) => updateExercise(sessionIndex, exerciseIndex, { exerciseName: e.target.value, exerciseId: null })} placeholder="Ejercicio" value={exercise.exerciseName} />
                      <input className="app-input" onChange={(e) => updateExercise(sessionIndex, exerciseIndex, { prescription: e.target.value })} placeholder="3x12, 10, 8 subiendo" value={exercise.prescription} />
                      <input className="app-input" onChange={(e) => updateExercise(sessionIndex, exerciseIndex, { notes: e.target.value })} placeholder="Carga, técnica o alternativa VS" value={exercise.notes ?? ""} />
                      <button className="px-2 text-sm font-semibold text-red-700" disabled={day.blocks[0].exercises.length === 1} onClick={() => updateSession(sessionIndex, { blocks: [{ ...day.blocks[0], exercises: day.blocks[0].exercises.filter((_, i) => i !== exerciseIndex) }] })} type="button">Quitar</button>
                    </article>
                  ))}
                  <datalist id={`exercise-options-${sessionIndex}`}>{exercisePage.map((exercise) => <option key={exercise.id} value={exercise.name} />)}</datalist>
                  <div className="flex flex-wrap gap-2">
                    <button className="rounded-md border border-[#b9c9bf] bg-white px-3 py-2 text-sm font-semibold" onClick={() => updateSession(sessionIndex, { blocks: [{ ...day.blocks[0], exercises: [...day.blocks[0].exercises, emptyExercise()] }] })} type="button">+ Ejercicio libre</button>
                    <select className="rounded-md border border-[#b9c9bf] bg-white px-3 py-2 text-sm" defaultValue="" onChange={(e) => { const found = exercisePage.find((item) => item.id === e.target.value); if (found) addCatalogExercise(sessionIndex, found); e.target.value = ""; }}>
                      <option value="">Agregar desde catálogo…</option>
                      {exercisePage.map((exercise) => <option key={exercise.id} value={exercise.id}>{exercise.name}</option>)}
                    </select>
                  </div>
                </div>
              </section>
            ))}

            <div className="flex flex-wrap justify-between gap-3">
              <button className="rounded-lg border border-[#9eb2a5] bg-white px-4 py-2 font-semibold" onClick={() => setSessions((items) => [...items, emptySession(items.length)])} type="button">+ Agregar día</button>
              <div className="flex gap-2">
                {editingWorkoutId ? <button className="rounded-lg border border-[#9eb2a5] bg-white px-5 py-2 font-semibold" onClick={resetWorkoutForm} type="button">Cancelar edición</button> : null}
                <button className="rounded-lg border border-[#9eb2a5] bg-white px-5 py-2 font-semibold disabled:opacity-60" disabled={saving} type="submit" value="draft">Guardar borrador</button>
                <button className="rounded-lg bg-[#173c2a] px-5 py-2 font-semibold text-white disabled:opacity-60" disabled={saving} type="submit" value="publish">{saving ? "Guardando…" : "Publicar rutina"}</button>
              </div>
            </div>
            {workouts.length ? (
              <section className="rounded-xl border border-[#d9e2dc] bg-white p-5">
                <h2 className="text-xl font-semibold">Rutinas publicadas</h2>
                <div className="mt-3 grid gap-2 md:grid-cols-2">
                  {workouts.map((workout) => (
                    <article className="flex items-center justify-between gap-3 rounded-lg border border-[#e0e7e2] p-3" key={workout.id}>
                      <div><strong>{workout.title}</strong><p className="text-xs text-[#637268]">{workout.coachName || "Sin coach"} · versión {workout.latestVersionNumber} · {workout.status}</p></div>
                      <div className="flex gap-2">
                        <button className="rounded-md border px-3 py-2 text-sm font-semibold" onClick={() => editWorkout(workout.id)} type="button">Editar</button>
                        {workout.status === "DRAFT" ? <button className="rounded-md bg-[#173c2a] px-3 py-2 text-sm font-semibold text-white" onClick={() => publishExistingWorkout(workout.id)} type="button">Publicar</button> : null}
                        <button className="rounded-md border px-3 py-2 text-sm font-semibold" onClick={() => copyWorkout(workout.id)} type="button">Duplicar</button>
                      </div>
                    </article>
                  ))}
                </div>
              </section>
            ) : null}
          </form>
        ) : null}

        {tab === "catalog" ? (
          <section className="grid gap-5 lg:grid-cols-[1.4fr_0.8fr]">
            <article className="rounded-xl border border-[#d9e2dc] bg-white p-5">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div><h2 className="text-xl font-semibold">Catálogo de ejercicios</h2><p className="text-sm text-[#637268]">{exercisePage.length} opciones disponibles en esta vista.</p></div>
                <input className="app-input max-w-xs" onChange={(e) => setExerciseQuery(e.target.value)} placeholder="Buscar nombre, músculo o equipo" value={exerciseQuery} />
              </div>
              <div className="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
                {filteredExercises.map((exercise) => (
                  <article className="rounded-lg border border-[#dde5df] p-3" key={exercise.id}>
                    <div className="flex justify-between gap-2"><strong>{exercise.name}</strong><span className="text-[10px] font-semibold text-[#66786d]">{exercise.origin}</span></div>
                    <p className="mt-1 text-xs text-[#637268]">{exercise.primaryMuscleGroup || "General"} · {exercise.equipment || "Sin equipo"}</p>
                    {exercise.instructions ? <p className="mt-2 line-clamp-2 text-xs">{exercise.instructions}</p> : null}
                    {exercise.origin !== "SYSTEM" ? (
                      <div className="mt-3 flex gap-2">
                        <input
                          aria-label={`Nuevo alias para ${exercise.name}`}
                          className="app-input min-w-0 text-xs"
                          onChange={(event) => setAliasDrafts((current) => ({ ...current, [exercise.id]: event.target.value }))}
                          placeholder="Agregar alias"
                          value={aliasDrafts[exercise.id] ?? ""}
                        />
                        <button className="rounded-md border px-2 text-xs font-semibold" disabled={saving || !aliasDrafts[exercise.id]?.trim()} onClick={() => saveAlias(exercise)} type="button">Guardar</button>
                      </div>
                    ) : null}
                  </article>
                ))}
              </div>
            </article>
            <form className="rounded-xl border border-[#d9e2dc] bg-white p-5" onSubmit={saveExercise}>
              <h2 className="text-xl font-semibold">Agregar ejercicio</h2>
              <div className="mt-4 grid gap-3">
                <Field label="Nombre"><input className="app-input" onChange={(e) => setNewExercise({ ...newExercise, name: e.target.value })} value={newExercise.name} /></Field>
                <Field label="Grupo muscular"><input className="app-input" onChange={(e) => setNewExercise({ ...newExercise, primaryMuscleGroup: e.target.value })} value={newExercise.primaryMuscleGroup} /></Field>
                <Field label="Equipo"><input className="app-input" onChange={(e) => setNewExercise({ ...newExercise, equipment: e.target.value })} value={newExercise.equipment} /></Field>
                <Field label="Tipo"><select className="app-input" onChange={(e) => setNewExercise({ ...newExercise, type: e.target.value })} value={newExercise.type}><option value="STRENGTH">Fuerza</option><option value="MOBILITY">Movilidad</option><option value="CARDIO">Cardio</option><option value="CORE">Core</option><option value="WARMUP">Calentamiento</option><option value="STRETCHING">Estiramiento</option></select></Field>
                <Field label="Instrucciones"><textarea className="app-input min-h-20" onChange={(e) => setNewExercise({ ...newExercise, instructions: e.target.value })} value={newExercise.instructions} /></Field>
                <button className="rounded-lg bg-[#173c2a] px-4 py-2 font-semibold text-white disabled:opacity-60" disabled={saving || !newExercise.name.trim()} type="submit">Guardar ejercicio</button>
              </div>
            </form>
          </section>
        ) : null}

        {tab === "assignments" ? (
          <section className="grid gap-5 lg:grid-cols-[0.8fr_1.2fr]">
            <form className="rounded-xl border border-[#d9e2dc] bg-white p-5" onSubmit={saveAssignment}>
              <h2 className="text-xl font-semibold">Asignar a un socio</h2>
              <div className="mt-4 flex gap-2"><input className="app-input" onChange={(e) => setClientQuery(e.target.value)} placeholder="Nombre, correo o teléfono" value={clientQuery} /><button className="rounded-md border px-3 text-sm font-semibold" onClick={findClients} type="button">Buscar</button></div>
              <div className="mt-2 grid gap-2">{clients.slice(0, 6).map((client) => <button className={`rounded-md border p-2 text-left text-sm ${selectedClient?.id === client.id ? "border-emerald-700 bg-emerald-50" : ""}`} key={client.id} onClick={() => setSelectedClient(client)} type="button">{client.firstName} {client.lastName}</button>)}</div>
              <div className="mt-4 grid gap-3">
                <Field label="Rutina"><select className="app-input" onChange={(e) => setSelectedWorkoutId(e.target.value)} value={selectedWorkoutId}><option value="">Selecciona una rutina publicada</option>{publishedWorkouts.map((workout) => <option key={workout.id} value={workout.id}>{workout.title} · v{workout.latestVersionNumber}</option>)}</select></Field>
                <div className="grid grid-cols-2 gap-3"><Field label="Inicio"><input className="app-input" onChange={(e) => setStartsOn(e.target.value)} type="date" value={startsOn} /></Field><Field label="Fin"><input className="app-input" onChange={(e) => setEndsOn(e.target.value)} type="date" value={endsOn} /></Field></div>
                <Field label="Indicaciones imprimibles"><textarea className="app-input min-h-20" onChange={(e) => setPrintableNotes(e.target.value)} value={printableNotes} /></Field>
                <Field label="Notas privadas"><textarea className="app-input min-h-16" onChange={(e) => setPrivateNotes(e.target.value)} value={privateNotes} /></Field>
                <button className="rounded-lg bg-[#173c2a] px-4 py-2 font-semibold text-white disabled:opacity-60" disabled={saving || !selectedClient || !selectedWorkoutId} type="submit">Asignar rutina</button>
              </div>
            </form>
            <AssignmentTable report={report} session={session} />
          </section>
        ) : null}

        {tab === "metrics" ? (
          <section className="grid gap-5">
            <div className="flex flex-wrap items-end gap-3 rounded-xl border border-[#d9e2dc] bg-white p-4">
              <Field label="Desde"><input className="app-input" onChange={(e) => setReportFrom(e.target.value)} type="date" value={reportFrom} /></Field>
              <Field label="Hasta"><input className="app-input" onChange={(e) => setReportTo(e.target.value)} type="date" value={reportTo} /></Field>
              <button className="rounded-md bg-[#173c2a] px-4 py-2 text-sm font-semibold text-white" onClick={loadReport} type="button">Actualizar</button>
              <button className="rounded-md border px-4 py-2 text-sm font-semibold" onClick={() => downloadAssignmentCsv(session.accessToken, { from: reportFrom, to: reportTo })} type="button">Exportar CSV</button>
            </div>
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <Metric label="Rutinas asignadas" value={report?.assignments ?? 0} />
              <Metric label="Socios atendidos" value={report?.uniqueClients ?? 0} />
              <Metric label="Coaches activos" value={report?.activeCoaches ?? 0} />
              <Metric label="Sin asignaciones" value={report?.coachesWithoutAssignments ?? 0} />
            </div>
            <section className="rounded-xl border border-[#d9e2dc] bg-white p-5">
              <h2 className="text-xl font-semibold">Desempeño por coach</h2>
              <div className="mt-4 grid gap-2">
                {report?.coaches.map((coach) => (
                  <article className="grid items-center gap-2 rounded-lg border border-[#e0e7e2] p-3 sm:grid-cols-[1fr_repeat(3,120px)]" key={coach.coachId}>
                    <strong>{coach.coachName}</strong><span>{coach.assignments} asignadas</span><span>{coach.uniqueClients} socios</span><span>{coach.firstWorkouts} primeras</span>
                  </article>
                ))}
              </div>
            </section>
            <AssignmentTable report={report} session={session} />
          </section>
        ) : null}
      </div>
    </AppShell>
  );

  function clearFeedback() { setError(null); setMessage(null); }
}

function AssignmentTable({ report, session }: { report: AssignmentReport | null; session: StoredSession }) {
  const [changing, setChanging] = useState<string | null>(null);
  async function changeStatus(id: string, status: "COMPLETED" | "CANCELLED") {
    setChanging(id);
    try {
      await updateWorkoutAssignmentStatus(session.accessToken, id, status);
      window.location.reload();
    } finally {
      setChanging(null);
    }
  }
  return (
    <section className="rounded-xl border border-[#d9e2dc] bg-white p-5">
      <h2 className="text-xl font-semibold">Historial de asignaciones</h2>
      <div className="mt-4 grid gap-2">
        {report?.details.length ? report.details.map((assignment) => (
          <article className="grid gap-2 rounded-lg border border-[#e0e7e2] p-3 md:grid-cols-[1fr_1fr_auto]" key={assignment.id}>
            <div><strong>{assignment.clientName}</strong><p className="text-xs text-[#637268]">{assignment.assignedOn} · {assignment.coachName || "Sin coach"}</p></div>
            <div><p>{assignment.workoutTitle}</p><p className="text-xs text-[#637268]">Versión {assignment.versionNumber} · {assignment.status}</p></div>
            <div className="flex flex-wrap gap-2">
              <button className="rounded-md border border-[#9eb2a5] px-3 py-2 text-sm font-semibold" onClick={() => downloadWorkoutPdf(session.accessToken, assignment.id)} type="button">PDF</button>
              {assignment.status === "ACTIVE" ? <button className="rounded-md border px-3 py-2 text-sm font-semibold" disabled={changing === assignment.id} onClick={() => changeStatus(assignment.id, "COMPLETED")} type="button">Completar</button> : null}
            </div>
          </article>
        )) : <p className="rounded-lg border border-dashed p-5 text-sm text-[#637268]">No hay asignaciones en el periodo.</p>}
      </div>
    </section>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="grid min-w-0 gap-1 text-sm font-medium text-[#435249]"><span>{label}</span>{children}</label>;
}

function Metric({ label, value }: { label: string; value: number }) {
  return <article className="rounded-xl border border-[#d9e2dc] bg-white p-5"><p className="text-sm text-[#637268]">{label}</p><p className="mt-1 text-3xl font-semibold">{value}</p></article>;
}

function validateWorkout(title: string, sessions: WorkoutSessionDraft[]) {
  if (!title.trim()) return "Escribe un título para la rutina.";
  if (!sessions.length) return "Agrega al menos una sesión.";
  if (sessions.some((session) => !session.name.trim())) return "Cada sesión necesita nombre.";
  if (sessions.some((session) => session.blocks.some((block) => block.exercises.some((exercise) => !exercise.exerciseName.trim())))) return "Todos los ejercicios necesitan nombre.";
  return null;
}

function errorMessage(error: unknown) { return error instanceof Error ? error.message : "No fue posible completar la operación."; }
function today() { return new Date().toISOString().slice(0, 10); }
function startOfWeek() {
  const date = new Date();
  const day = date.getDay() || 7;
  date.setDate(date.getDate() - day + 1);
  return date.toISOString().slice(0, 10);
}
