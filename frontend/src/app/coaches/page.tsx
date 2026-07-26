"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { AppShell, LoadingScreen } from "@/components/AppShell";
import { AuthRequired } from "@/components/AuthRequired";
import {
  addExerciseAlias,
  assignWorkout,
  createExercise,
  createWorkout,
  deleteExerciseAlias,
  downloadAssignmentCsv,
  downloadWorkoutPdf,
  duplicateWorkout,
  getAssignmentReport,
  getAssignmentTrend,
  getBranches,
  getCoaches,
  getExerciseAliases,
  getExercises,
  getStoredSession,
  getWorkout,
  getWorkouts,
  publishWorkout,
  removeExerciseMedia,
  replaceWorkoutAssignment,
  searchClients,
  updateExerciseCatalog,
  updateExerciseStatus,
  updateWorkout,
  updateWorkoutAssignmentStatus,
  uploadExerciseMedia,
  type AssignmentReport,
  type BranchSummary,
  type ClientSummary,
  type CoachSummary,
  type CreateExercisePayload,
  type Exercise,
  type StoredSession,
  type WeeklyTrendReport,
  type WorkoutBlockDraft,
  type WorkoutExerciseDraft,
  type WorkoutSessionDraft,
  type WorkoutSummary,
} from "@/lib/api";
import {
  emptyBlock,
  emptyExercise,
  emptySession,
  moveItem,
  updateBlock as updateBlockList,
  updateExercise as updateExerciseList,
  validateWorkout,
} from "./workout-builder";

type Tab = "builder" | "catalog" | "assignments" | "metrics";

const EMPTY_EXERCISE_FORM: CreateExercisePayload = {
  name: "",
  description: "",
  instructions: "",
  primaryMuscleGroup: "",
  secondaryMuscleGroups: "",
  movementPattern: "",
  type: "STRENGTH",
  equipment: "",
  suggestedLevel: "",
  laterality: "",
  warnings: "",
};

export default function CoachesPage() {
  const [session, setSession] = useState<StoredSession | null>(null);
  const [branches, setBranches] = useState<BranchSummary[]>([]);
  const [coaches, setCoaches] = useState<CoachSummary[]>([]);
  const [workouts, setWorkouts] = useState<WorkoutSummary[]>([]);
  const [exercisePage, setExercisePage] = useState<Exercise[]>([]);
  const [report, setReport] = useState<AssignmentReport | null>(null);
  const [trend, setTrend] = useState<WeeklyTrendReport | null>(null);
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
  const [exerciseAliases, setExerciseAliases] = useState<Record<string, string[]>>({});
  const [exerciseForm, setExerciseForm] = useState<CreateExercisePayload>({ ...EMPTY_EXERCISE_FORM });
  const [editingExerciseId, setEditingExerciseId] = useState<string | null>(null);

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
  const [reportCoachId, setReportCoachId] = useState("");
  const [reportPage, setReportPage] = useState(0);
  const [trendWeeks, setTrendWeeks] = useState(12);
  const [metricClientQuery, setMetricClientQuery] = useState("");
  const [metricClients, setMetricClients] = useState<ClientSummary[]>([]);
  const [reportClient, setReportClient] = useState<ClientSummary | null>(null);

  useEffect(() => {
    let active = true;
    async function load() {
      const stored = getStoredSession();
      if (!stored) {
        setLoading(false);
        return;
      }
      setSession(stored);
      try {
        const ownerMetricsOnly = isTenantOwner(stored);
        const canReadWorkouts = !ownerMetricsOnly && hasAnyPermission(
          stored, "workout.read.own", "workout.read.all", "workout.manage.own", "workout.manage.all",
        );
        const canReadExercises = !ownerMetricsOnly
          && stored.user.permissions.includes("exercise.read");
        const canReadMetrics = stored.user.permissions.includes("coach.metrics.read");
        const [loadedBranches, loadedCoaches, loadedWorkouts, loadedExercises, loadedReport, loadedTrend] =
          await Promise.all([
            getBranches(stored.accessToken),
            getCoaches(stored.accessToken),
            canReadWorkouts ? getWorkouts(stored.accessToken) : Promise.resolve([]),
            canReadExercises
              ? getExercises(stored.accessToken, { size: 100 })
              : Promise.resolve({ items: [] }),
            canReadMetrics
              ? getAssignmentReport(stored.accessToken, { from: startOfWeek(), to: today() })
              : Promise.resolve(null),
            canReadMetrics
              ? getAssignmentTrend(stored.accessToken, { weeks: 12, to: today() })
              : Promise.resolve(null),
          ]);
        if (!active) return;
        setBranches(loadedBranches);
        setCoaches(loadedCoaches);
        setWorkouts(loadedWorkouts);
        setExercisePage(loadedExercises.items);
        setReport(loadedReport);
        setTrend(loadedTrend);
        setTab(availableTabs(stored)[0] ?? "metrics");
        const canManageAll = stored.user.permissions.includes("workout.manage.all");
        setCoachId(
          (canManageAll
            ? loadedCoaches[0]
            : loadedCoaches.find((coach) => coach.userAccountId === stored.user.userId)
          )?.id ?? "",
        );
        setSelectedWorkoutId(
          loadedWorkouts.find((workout) => isPublished(workout))?.id ?? "",
        );
      } catch (exception) {
        if (active) setError(errorMessage(exception));
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => {
      active = false;
    };
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
    () => workouts.filter(isPublished),
    [workouts],
  );
  const canManageAllWorkouts = session?.user.permissions.includes("workout.manage.all") ?? false;
  const selectableCoaches = canManageAllWorkouts
    ? coaches
    : coaches.filter((coach) => coach.userAccountId === session?.user.userId);
  const canManageExerciseCatalog = session?.user.permissions.includes("exercise.manage") ?? false;
  const canManageAssignments = session
    ? !isTenantOwner(session) && session.user.permissions.includes("workout.assign")
    : false;
  const canDownloadAssignmentPdf = session
    ? !isTenantOwner(session) && session.user.permissions.includes("workout.pdf.download")
    : false;

  async function refresh() {
    if (!session) return;
    const reportParams = {
      from: reportFrom,
      to: reportTo,
      coachId: reportCoachId || undefined,
      clientId: reportClient?.id,
      page: reportPage,
      size: 25,
    };
    const ownerMetricsOnly = isTenantOwner(session);
    const canReadWorkouts = !ownerMetricsOnly && hasAnyPermission(
      session, "workout.read.own", "workout.read.all", "workout.manage.own", "workout.manage.all",
    );
    const canReadExercises = !ownerMetricsOnly
      && session.user.permissions.includes("exercise.read");
    const canReadMetrics = session.user.permissions.includes("coach.metrics.read");
    const [nextWorkouts, nextExercises, nextReport, nextTrend] = await Promise.all([
      canReadWorkouts ? getWorkouts(session.accessToken) : Promise.resolve(workouts),
      canReadExercises
        ? getExercises(session.accessToken, { query: exerciseQuery, size: 100 })
        : Promise.resolve({ items: exercisePage }),
      canReadMetrics ? getAssignmentReport(session.accessToken, reportParams) : Promise.resolve(report),
      canReadMetrics ? getAssignmentTrend(session.accessToken, {
          weeks: trendWeeks,
          to: reportTo,
          coachId: reportCoachId || undefined,
          clientId: reportClient?.id,
        }) : Promise.resolve(trend),
    ]);
    setWorkouts(nextWorkouts);
    setExercisePage(nextExercises.items);
    setReport(nextReport);
    setTrend(nextTrend);
    setSelectedWorkoutId((current) =>
      current || nextWorkouts.find(isPublished)?.id || "");
  }

  async function saveWorkout(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) return;
    const validation = validateWorkout(title, sessions);
    if (validation) {
      setError(validation);
      return;
    }
    setSaving(true);
    clearFeedback();
    try {
      const payload = {
        coachId: coachId || null,
        title,
        goal,
        level: level as "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "GENERAL",
        summary,
        contentJson: JSON.stringify({ sessions }),
      };
      const saved = editingWorkoutId
        ? await updateWorkout(session.accessToken, editingWorkoutId, payload)
        : await createWorkout(session.accessToken, payload);
      const submitter = (event.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null;
      const shouldPublish = submitter?.value !== "draft";
      if (shouldPublish) await publishWorkout(session.accessToken, saved.workout.id);
      setMessage(shouldPublish
        ? `Rutina “${saved.workout.title}” publicada correctamente.`
        : `Borrador “${saved.workout.title}” guardado.`);
      resetWorkoutForm();
      await refresh();
    } catch (exception) {
      setError(errorMessage(exception));
    } finally {
      setSaving(false);
    }
  }

  async function saveExercise(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) return;
    setSaving(true);
    clearFeedback();
    try {
      const saved = editingExerciseId
        ? await updateExerciseCatalog(session.accessToken, editingExerciseId, exerciseForm)
        : await createExercise(session.accessToken, exerciseForm);
      setExercisePage((current) =>
        [...current.filter((item) => item.id !== saved.id), saved]
          .sort((a, b) => a.name.localeCompare(b.name)));
      resetExerciseForm();
      setMessage(`Ejercicio “${saved.name}” guardado correctamente.`);
    } catch (exception) {
      setError(errorMessage(exception));
    } finally {
      setSaving(false);
    }
  }

  async function editExercise(exercise: Exercise) {
    if (!session) return;
    clearFeedback();
    try {
      const aliases = await getExerciseAliases(session.accessToken, exercise.id);
      setExerciseAliases((current) => ({ ...current, [exercise.id]: aliases }));
      setEditingExerciseId(exercise.id);
      setExerciseForm({
        name: exercise.name,
        description: exercise.description ?? "",
        instructions: exercise.instructions ?? "",
        primaryMuscleGroup: exercise.primaryMuscleGroup ?? "",
        secondaryMuscleGroups: exercise.secondaryMuscleGroups ?? "",
        movementPattern: exercise.movementPattern ?? "",
        type: exercise.type,
        equipment: exercise.equipment ?? "",
        suggestedLevel: exercise.suggestedLevel ?? "",
        laterality: exercise.laterality ?? "",
        mediaUrl: exercise.mediaUrl,
        warnings: exercise.warnings ?? "",
      });
      setMessage(`Editando “${exercise.name}”.`);
    } catch (exception) {
      setError(errorMessage(exception));
    }
  }

  async function saveAlias(exercise: Exercise) {
    if (!session) return;
    const alias = aliasDrafts[exercise.id]?.trim();
    if (!alias) return;
    setSaving(true);
    clearFeedback();
    try {
      const aliases = await addExerciseAlias(session.accessToken, exercise.id, alias);
      setExerciseAliases((current) => ({ ...current, [exercise.id]: aliases }));
      setAliasDrafts((current) => ({ ...current, [exercise.id]: "" }));
      setMessage(`Alias “${alias}” agregado a ${exercise.name}.`);
    } catch (exception) {
      setError(errorMessage(exception));
    } finally {
      setSaving(false);
    }
  }

  async function removeAlias(exercise: Exercise, alias: string) {
    if (!session) return;
    setSaving(true);
    clearFeedback();
    try {
      const aliases = await deleteExerciseAlias(session.accessToken, exercise.id, alias);
      setExerciseAliases((current) => ({ ...current, [exercise.id]: aliases }));
      setMessage(`Alias “${alias}” eliminado.`);
    } catch (exception) {
      setError(errorMessage(exception));
    } finally {
      setSaving(false);
    }
  }

  async function changeExerciseStatus(exercise: Exercise, status: "ACTIVE" | "ARCHIVED") {
    if (!session) return;
    setSaving(true);
    clearFeedback();
    try {
      await updateExerciseStatus(session.accessToken, exercise.id, status);
      setMessage(`Ejercicio “${exercise.name}” ${status === "ARCHIVED" ? "archivado" : "restaurado"}.`);
      resetExerciseForm();
      await refresh();
    } catch (exception) {
      setError(errorMessage(exception));
    } finally {
      setSaving(false);
    }
  }

  async function changeExerciseMedia(file?: File) {
    if (!session || !editingExerciseId) return;
    setSaving(true);
    clearFeedback();
    try {
      const updated = file
        ? await uploadExerciseMedia(session.accessToken, editingExerciseId, file)
        : await removeExerciseMedia(session.accessToken, editingExerciseId);
      setExercisePage((current) =>
        current.map((exercise) => exercise.id === updated.id ? updated : exercise));
      setExerciseForm((current) => ({ ...current, mediaUrl: updated.mediaUrl }));
      setMessage(file ? "Medio del ejercicio actualizado." : "Medio del ejercicio eliminado.");
    } catch (exception) {
      setError(errorMessage(exception));
    } finally {
      setSaving(false);
    }
  }

  async function findClients() {
    if (!session) return;
    clearFeedback();
    try {
      setClients(await searchClients(session.accessToken, clientQuery, "ACTIVE"));
    } catch (exception) {
      setError(errorMessage(exception));
    }
  }

  async function findMetricClients() {
    if (!session) return;
    clearFeedback();
    try {
      setMetricClients(await searchClients(session.accessToken, metricClientQuery, "ACTIVE"));
    } catch (exception) {
      setError(errorMessage(exception));
    }
  }

  async function saveAssignment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !selectedClient || !selectedWorkoutId) return;
    setSaving(true);
    clearFeedback();
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
      setPrintableNotes("");
      setPrivateNotes("");
      await refresh();
      setTab("assignments");
    } catch (exception) {
      setError(errorMessage(exception));
    } finally {
      setSaving(false);
    }
  }

  async function loadReport() {
    if (!session) return;
    setSaving(true);
    clearFeedback();
    try {
      setReportPage(0);
      const filters = {
        coachId: reportCoachId || undefined,
        clientId: reportClient?.id,
      };
      const [nextReport, nextTrend] = await Promise.all([
        getAssignmentReport(session.accessToken, {
          from: reportFrom,
          to: reportTo,
          ...filters,
          page: 0,
          size: 25,
        }),
        getAssignmentTrend(session.accessToken, {
          weeks: trendWeeks,
          to: reportTo,
          ...filters,
        }),
      ]);
      setReport(nextReport);
      setTrend(nextTrend);
    } catch (exception) {
      setError(errorMessage(exception));
    } finally {
      setSaving(false);
    }
  }

  async function loadReportPage(page: number) {
    if (!session) return;
    setSaving(true);
    clearFeedback();
    try {
      setReport(await getAssignmentReport(session.accessToken, {
        from: reportFrom,
        to: reportTo,
        coachId: reportCoachId || undefined,
        clientId: reportClient?.id,
        page,
        size: 25,
      }));
      setReportPage(page);
    } catch (exception) {
      setError(errorMessage(exception));
    } finally {
      setSaving(false);
    }
  }

  function updateSession(index: number, patch: Partial<WorkoutSessionDraft>) {
    setSessions((current) =>
      current.map((item, position) => position === index ? { ...item, ...patch } : item));
  }

  function updateBlock(
    sessionIndex: number,
    blockIndex: number,
    patch: Partial<WorkoutBlockDraft>,
  ) {
    setSessions((current) =>
      updateBlockList(current, sessionIndex, blockIndex, (block) => ({ ...block, ...patch })));
  }

  function updateExercise(
    sessionIndex: number,
    blockIndex: number,
    exerciseIndex: number,
    patch: Partial<WorkoutExerciseDraft>,
  ) {
    setSessions((current) =>
      updateExerciseList(current, sessionIndex, blockIndex, exerciseIndex, patch));
  }

  function mutateBlock(
    sessionIndex: number,
    blockIndex: number,
    updater: (block: WorkoutBlockDraft) => WorkoutBlockDraft,
  ) {
    setSessions((current) =>
      updateBlockList(current, sessionIndex, blockIndex, updater));
  }

  function addCatalogExercise(sessionIndex: number, blockIndex: number, exercise: Exercise) {
    mutateBlock(sessionIndex, blockIndex, (block) => ({
      ...block,
      exercises: [...block.exercises, {
        exerciseId: exercise.id,
        exerciseName: exercise.name,
        prescription: "",
        optional: false,
      }],
    }));
  }

  async function copyWorkout(workoutId: string) {
    if (!session) return;
    setSaving(true);
    clearFeedback();
    try {
      const copy = await duplicateWorkout(session.accessToken, workoutId);
      setMessage(`Se creó “${copy.workout.title}”.`);
      await refresh();
    } catch (exception) {
      setError(errorMessage(exception));
    } finally {
      setSaving(false);
    }
  }

  async function editWorkout(workoutId: string) {
    if (!session) return;
    setSaving(true);
    clearFeedback();
    try {
      const detail = await getWorkout(session.accessToken, workoutId);
      const version = detail.versions[0];
      const content = version
        ? JSON.parse(version.contentJson) as {
            sessions?: WorkoutSessionDraft[];
            days?: WorkoutSessionDraft[];
          }
        : {};
      setEditingWorkoutId(workoutId);
      setTitle(detail.workout.title);
      setGoal(detail.workout.goal ?? "");
      setLevel(detail.workout.level);
      setCoachId(detail.workout.coachId ?? "");
      setSummary(version?.summary ?? "");
      setSessions(content.sessions ?? content.days ?? [emptySession(0)]);
      setTab("builder");
      setMessage(`Editando “${detail.workout.title}”.`);
      window.scrollTo({ top: 0, behavior: "smooth" });
    } catch (exception) {
      setError(errorMessage(exception));
    } finally {
      setSaving(false);
    }
  }

  function resetWorkoutForm() {
    setEditingWorkoutId(null);
    setTitle("");
    setGoal("");
    setSummary("");
    setSessions([emptySession(0)]);
  }

  function resetExerciseForm() {
    setEditingExerciseId(null);
    setExerciseForm({ ...EMPTY_EXERCISE_FORM });
  }

  async function publishExistingWorkout(workoutId: string) {
    if (!session) return;
    setSaving(true);
    clearFeedback();
    try {
      const published = await publishWorkout(session.accessToken, workoutId);
      setMessage(`“${published.workout.title}” quedó publicada.`);
      await refresh();
    } catch (exception) {
      setError(errorMessage(exception));
    } finally {
      setSaving(false);
    }
  }

  const navigationTabs = availableTabs(session).map((value): [Tab, string] => [
    value,
    {
      builder: "Constructor",
      catalog: "Ejercicios",
      assignments: "Asignaciones",
      metrics: "Métricas",
    }[value],
  ]);

  if (loading) return <LoadingScreen text="Cargando Coaches y rutinas..." />;
  if (!session) {
    return (
      <AuthRequired
        message="Necesitas iniciar sesión para gestionar rutinas."
        title="Inicia sesión"
      />
    );
  }

  return (
    <AppShell
      branchName={activeBranch?.name}
      eyebrow="Entrenamiento"
      mode={activeBranch ? "operation" : undefined}
      organizationName={session.user.brandName || session.user.organizationName}
      session={session}
      subtitle={isTenantOwner(session)
        ? "Consulta el desempeño y acompañamiento del equipo de coaches."
        : "Crea planes completos, asígnalos a socios y mide el acompañamiento del equipo."}
      title="Coaches y rutinas"
    >
      <div className="grid gap-5">
        <nav aria-label="Secciones de Coaches" className="flex flex-wrap gap-2">
          {navigationTabs.map(([value, label]) => (
            <button
              className={`rounded-full px-4 py-2 text-sm font-semibold ${
                tab === value
                  ? "bg-[#173c2a] text-white"
                  : "border border-[#cfd9d2] bg-white text-[#365143]"
              }`}
              key={value}
              onClick={() => setTab(value)}
              type="button"
            >
              {label}
            </button>
          ))}
        </nav>

        {error ? (
          <p className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800" role="alert">
            {error}
          </p>
        ) : null}
        {message ? (
          <p className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">
            {message}
          </p>
        ) : null}

        {tab === "builder" ? (
          <form className="grid gap-5" onSubmit={saveWorkout}>
            <section className="rounded-xl border border-[#d9e2dc] bg-white p-5 shadow-sm">
              <div className="mb-4">
                <p className="text-xs font-semibold uppercase tracking-wider text-[#6b7d72]">Plantilla</p>
                <h2 className="text-xl font-semibold">
                  {editingWorkoutId ? "Editar rutina" : "Información de la rutina"}
                </h2>
              </div>
              <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                <Field label="Título">
                  <input className="app-input" onChange={(event) => setTitle(event.target.value)} value={title} />
                </Field>
                <Field label="Objetivo">
                  <input className="app-input" onChange={(event) => setGoal(event.target.value)} value={goal} />
                </Field>
                <Field label="Nivel">
                  <select className="app-input" onChange={(event) => setLevel(event.target.value)} value={level}>
                    <option value="GENERAL">General</option>
                    <option value="BEGINNER">Principiante</option>
                    <option value="INTERMEDIATE">Intermedio</option>
                    <option value="ADVANCED">Avanzado</option>
                  </select>
                </Field>
                <Field label="Coach">
                  <select
                    className="app-input"
                    disabled={!canManageAllWorkouts}
                    onChange={(event) => setCoachId(event.target.value)}
                    value={coachId}
                  >
                    {canManageAllWorkouts ? <option value="">Sin coach</option> : null}
                    {selectableCoaches.map((coach) => (
                      <option key={coach.id} value={coach.id}>{coach.displayName}</option>
                    ))}
                  </select>
                </Field>
              </div>
              <Field label="Indicaciones generales">
                <textarea
                  className="app-input mt-3 min-h-20"
                  onChange={(event) => setSummary(event.target.value)}
                  value={summary}
                />
              </Field>
            </section>

            {sessions.map((day, sessionIndex) => (
              <section className="rounded-xl border border-[#d9e2dc] bg-white p-5 shadow-sm" key={sessionIndex}>
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="grid flex-1 gap-3 md:grid-cols-2">
                    <Field label={`Sesión ${sessionIndex + 1}`}>
                      <input
                        className="app-input"
                        onChange={(event) => updateSession(sessionIndex, { name: event.target.value })}
                        value={day.name}
                      />
                    </Field>
                    <Field label="Enfoque">
                      <input
                        className="app-input"
                        onChange={(event) => updateSession(sessionIndex, { focus: event.target.value })}
                        placeholder="Pecho y hombro"
                        value={day.focus ?? ""}
                      />
                    </Field>
                  </div>
                  <OrderButtons
                    canDelete={sessions.length > 1}
                    index={sessionIndex}
                    length={sessions.length}
                    onDelete={() => setSessions((items) => items.filter((_, index) => index !== sessionIndex))}
                    onMove={(direction) => setSessions((items) => moveItem(items, sessionIndex, direction))}
                  />
                </div>
                <textarea
                  className="app-input mt-3 min-h-16"
                  onChange={(event) => updateSession(sessionIndex, { notes: event.target.value })}
                  placeholder="Movilidad, calentamiento o notas del día"
                  value={day.notes ?? ""}
                />

                <div className="mt-4 grid gap-4">
                  {day.blocks.map((block, blockIndex) => (
                    <article className="rounded-xl border border-[#cfdad2] bg-[#f8faf8] p-4" key={blockIndex}>
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div className="grid flex-1 gap-3 md:grid-cols-2">
                          <Field label={`Bloque ${blockIndex + 1}`}>
                            <input
                              className="app-input"
                              onChange={(event) => updateBlock(sessionIndex, blockIndex, { name: event.target.value })}
                              value={block.name}
                            />
                          </Field>
                          <Field label="Tipo">
                            <select
                              className="app-input"
                              onChange={(event) => updateBlock(sessionIndex, blockIndex, { type: event.target.value })}
                              value={block.type}
                            >
                              <option value="WARMUP">Calentamiento</option>
                              <option value="MAIN">Trabajo principal</option>
                              <option value="SUPERSET">Superserie</option>
                              <option value="CIRCUIT">Circuito</option>
                              <option value="FINISHER">Finalizador</option>
                              <option value="COOLDOWN">Vuelta a la calma</option>
                            </select>
                          </Field>
                        </div>
                        <OrderButtons
                          canDelete={day.blocks.length > 1}
                          index={blockIndex}
                          length={day.blocks.length}
                          onDelete={() => updateSession(sessionIndex, {
                            blocks: day.blocks.filter((_, index) => index !== blockIndex),
                          })}
                          onMove={(direction) => updateSession(sessionIndex, {
                            blocks: moveItem(day.blocks, blockIndex, direction),
                          })}
                        />
                      </div>
                      <textarea
                        className="app-input mt-3 min-h-14"
                        onChange={(event) => updateBlock(sessionIndex, blockIndex, { notes: event.target.value })}
                        placeholder="Notas del bloque"
                        value={block.notes ?? ""}
                      />

                      <div className="mt-3 grid gap-3">
                        {block.exercises.map((exercise, exerciseIndex) => (
                          <ExerciseEditor
                            blockIndex={blockIndex}
                            exercise={exercise}
                            exerciseIndex={exerciseIndex}
                            key={exerciseIndex}
                            listId={`exercise-options-${sessionIndex}-${blockIndex}`}
                            onChange={(patch) =>
                              updateExercise(sessionIndex, blockIndex, exerciseIndex, patch)}
                            onDelete={() => mutateBlock(sessionIndex, blockIndex, (current) => ({
                              ...current,
                              exercises: current.exercises.filter((_, index) => index !== exerciseIndex),
                            }))}
                            onMove={(direction) => mutateBlock(sessionIndex, blockIndex, (current) => ({
                              ...current,
                              exercises: moveItem(current.exercises, exerciseIndex, direction),
                            }))}
                            total={block.exercises.length}
                          />
                        ))}
                        <datalist id={`exercise-options-${sessionIndex}-${blockIndex}`}>
                          {exercisePage.map((exercise) => (
                            <option key={exercise.id} value={exercise.name} />
                          ))}
                        </datalist>
                        <div className="flex flex-wrap gap-2">
                          <button
                            className="rounded-md border border-[#b9c9bf] bg-white px-3 py-2 text-sm font-semibold"
                            onClick={() => mutateBlock(sessionIndex, blockIndex, (current) => ({
                              ...current,
                              exercises: [...current.exercises, emptyExercise()],
                            }))}
                            type="button"
                          >
                            + Ejercicio libre
                          </button>
                          <select
                            aria-label={`Agregar ejercicio de catálogo al bloque ${blockIndex + 1}`}
                            className="rounded-md border border-[#b9c9bf] bg-white px-3 py-2 text-sm"
                            defaultValue=""
                            onChange={(event) => {
                              const found = exercisePage.find((item) => item.id === event.target.value);
                              if (found) addCatalogExercise(sessionIndex, blockIndex, found);
                              event.target.value = "";
                            }}
                          >
                            <option value="">Agregar desde catálogo…</option>
                            {exercisePage.map((exercise) => (
                              <option key={exercise.id} value={exercise.id}>{exercise.name}</option>
                            ))}
                          </select>
                        </div>
                      </div>
                    </article>
                  ))}
                  <button
                    className="w-fit rounded-md border border-[#9eb2a5] bg-white px-3 py-2 text-sm font-semibold"
                    onClick={() => updateSession(sessionIndex, {
                      blocks: [...day.blocks, emptyBlock(`Bloque ${day.blocks.length + 1}`)],
                    })}
                    type="button"
                  >
                    + Agregar bloque
                  </button>
                </div>
              </section>
            ))}

            <div className="flex flex-wrap justify-between gap-3">
              <button
                className="rounded-lg border border-[#9eb2a5] bg-white px-4 py-2 font-semibold"
                onClick={() => setSessions((items) => [...items, emptySession(items.length)])}
                type="button"
              >
                + Agregar día
              </button>
              <div className="flex flex-wrap gap-2">
                {editingWorkoutId ? (
                  <button
                    className="rounded-lg border border-[#9eb2a5] bg-white px-5 py-2 font-semibold"
                    onClick={resetWorkoutForm}
                    type="button"
                  >
                    Cancelar edición
                  </button>
                ) : null}
                <button
                  className="rounded-lg border border-[#9eb2a5] bg-white px-5 py-2 font-semibold disabled:opacity-60"
                  disabled={saving}
                  type="submit"
                  value="draft"
                >
                  Guardar borrador
                </button>
                <button
                  className="rounded-lg bg-[#173c2a] px-5 py-2 font-semibold text-white disabled:opacity-60"
                  disabled={saving}
                  type="submit"
                  value="publish"
                >
                  {saving ? "Guardando…" : "Publicar rutina"}
                </button>
              </div>
            </div>

            {workouts.length ? (
              <section className="rounded-xl border border-[#d9e2dc] bg-white p-5">
                <h2 className="text-xl font-semibold">Rutinas disponibles</h2>
                <div className="mt-3 grid gap-2 md:grid-cols-2">
                  {workouts.map((workout) => (
                    <article
                      className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-[#e0e7e2] p-3"
                      key={workout.id}
                    >
                      <div>
                        <strong>{workout.title}</strong>
                        <p className="text-xs text-[#637268]">
                          {workout.coachName || "Sin coach"} · versión {workout.latestVersionNumber} · {workout.status}
                        </p>
                      </div>
                      <div className="flex flex-wrap gap-2">
                        <button className="rounded-md border px-3 py-2 text-sm font-semibold" onClick={() => editWorkout(workout.id)} type="button">
                          Editar
                        </button>
                        {workout.status === "DRAFT" ? (
                          <button className="rounded-md bg-[#173c2a] px-3 py-2 text-sm font-semibold text-white" onClick={() => publishExistingWorkout(workout.id)} type="button">
                            Publicar
                          </button>
                        ) : null}
                        <button className="rounded-md border px-3 py-2 text-sm font-semibold" onClick={() => copyWorkout(workout.id)} type="button">
                          Duplicar
                        </button>
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
                <div>
                  <h2 className="text-xl font-semibold">Catálogo de ejercicios</h2>
                  <p className="text-sm text-[#637268]">{exercisePage.length} opciones disponibles en esta vista.</p>
                </div>
                <input
                  className="app-input max-w-xs"
                  onChange={(event) => setExerciseQuery(event.target.value)}
                  placeholder="Buscar nombre, músculo o equipo"
                  value={exerciseQuery}
                />
              </div>
              <div className="mt-4 grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
                {filteredExercises.map((exercise) => {
                  const manageable = canManageExerciseCatalog
                    || exercise.createdBy === session.user.userId;
                  const aliases = exerciseAliases[exercise.id];
                  return (
                    <article className="rounded-lg border border-[#dde5df] p-3" key={exercise.id}>
                      <div className="flex justify-between gap-2">
                        <strong>{exercise.name}</strong>
                        <span className="text-[10px] font-semibold text-[#66786d]">{exercise.origin}</span>
                      </div>
                      <p className="mt-1 text-xs text-[#637268]">
                        {exercise.primaryMuscleGroup || "General"} · {exercise.equipment || "Sin equipo"}
                      </p>
                      {exercise.instructions ? <p className="mt-2 line-clamp-2 text-xs">{exercise.instructions}</p> : null}
                      {exercise.mediaUrl ? <p className="mt-2 text-xs font-semibold text-emerald-800">Medio adjunto</p> : null}
                      {manageable && exercise.origin !== "SYSTEM" ? (
                        <>
                          <div className="mt-3 flex flex-wrap gap-2">
                            <button className="rounded-md border px-2 py-1 text-xs font-semibold" onClick={() => editExercise(exercise)} type="button">
                              Editar y ver aliases
                            </button>
                            {canManageExerciseCatalog ? (
                              <button className="rounded-md border border-red-200 px-2 py-1 text-xs font-semibold text-red-700" onClick={() => changeExerciseStatus(exercise, "ARCHIVED")} type="button">
                                Archivar
                              </button>
                            ) : null}
                          </div>
                          <div className="mt-3 flex gap-2">
                            <input
                              aria-label={`Nuevo alias para ${exercise.name}`}
                              className="app-input min-w-0 text-xs"
                              onChange={(event) => setAliasDrafts((current) => ({
                                ...current,
                                [exercise.id]: event.target.value,
                              }))}
                              placeholder="Agregar alias"
                              value={aliasDrafts[exercise.id] ?? ""}
                            />
                            <button
                              className="rounded-md border px-2 text-xs font-semibold"
                              disabled={saving || !aliasDrafts[exercise.id]?.trim()}
                              onClick={() => saveAlias(exercise)}
                              type="button"
                            >
                              Guardar
                            </button>
                          </div>
                          {aliases?.length ? (
                            <div className="mt-2 flex flex-wrap gap-1">
                              {aliases.map((alias) => (
                                <button
                                  className="rounded-full bg-[#edf3ef] px-2 py-1 text-[11px]"
                                  key={alias}
                                  onClick={() => removeAlias(exercise, alias)}
                                  title="Eliminar alias"
                                  type="button"
                                >
                                  {alias} ×
                                </button>
                              ))}
                            </div>
                          ) : null}
                        </>
                      ) : null}
                    </article>
                  );
                })}
              </div>
            </article>

            <form className="rounded-xl border border-[#d9e2dc] bg-white p-5" onSubmit={saveExercise}>
              <div className="flex items-center justify-between gap-3">
                <h2 className="text-xl font-semibold">{editingExerciseId ? "Editar ejercicio" : "Agregar ejercicio"}</h2>
                {editingExerciseId ? (
                  <button className="text-sm font-semibold text-[#365143]" onClick={resetExerciseForm} type="button">
                    Cancelar
                  </button>
                ) : null}
              </div>
              <div className="mt-4 grid gap-3">
                <Field label="Nombre"><ExerciseInput field="name" form={exerciseForm} setForm={setExerciseForm} /></Field>
                <Field label="Descripción"><ExerciseInput field="description" form={exerciseForm} setForm={setExerciseForm} /></Field>
                <Field label="Grupo muscular principal"><ExerciseInput field="primaryMuscleGroup" form={exerciseForm} setForm={setExerciseForm} /></Field>
                <Field label="Grupos secundarios"><ExerciseInput field="secondaryMuscleGroups" form={exerciseForm} setForm={setExerciseForm} /></Field>
                <Field label="Patrón de movimiento"><ExerciseInput field="movementPattern" form={exerciseForm} setForm={setExerciseForm} /></Field>
                <Field label="Equipo"><ExerciseInput field="equipment" form={exerciseForm} setForm={setExerciseForm} /></Field>
                <Field label="Tipo">
                  <select className="app-input" onChange={(event) => setExerciseForm({ ...exerciseForm, type: event.target.value })} value={exerciseForm.type}>
                    <option value="STRENGTH">Fuerza</option>
                    <option value="MOBILITY">Movilidad</option>
                    <option value="CARDIO">Cardio</option>
                    <option value="CORE">Core</option>
                    <option value="WARMUP">Calentamiento</option>
                    <option value="STRETCHING">Estiramiento</option>
                  </select>
                </Field>
                <Field label="Nivel sugerido"><ExerciseInput field="suggestedLevel" form={exerciseForm} setForm={setExerciseForm} /></Field>
                <Field label="Lateralidad"><ExerciseInput field="laterality" form={exerciseForm} setForm={setExerciseForm} /></Field>
                <Field label="Instrucciones">
                  <textarea className="app-input min-h-24" onChange={(event) => setExerciseForm({ ...exerciseForm, instructions: event.target.value })} value={exerciseForm.instructions ?? ""} />
                </Field>
                <Field label="Advertencias">
                  <textarea className="app-input min-h-20" onChange={(event) => setExerciseForm({ ...exerciseForm, warnings: event.target.value })} value={exerciseForm.warnings ?? ""} />
                </Field>
                {editingExerciseId ? (
                  <div className="rounded-lg border border-dashed p-3">
                    <p className="text-sm font-semibold">Imagen o video demostrativo</p>
                    <p className="mb-2 text-xs text-[#637268]">JPG, PNG, WebP, GIF o MP4; máximo 8 MB.</p>
                    <input
                      accept="image/jpeg,image/png,image/webp,image/gif,video/mp4"
                      disabled={saving}
                      onChange={(event) => {
                        const file = event.target.files?.[0];
                        if (file) void changeExerciseMedia(file);
                        event.target.value = "";
                      }}
                      type="file"
                    />
                    {exerciseForm.mediaUrl ? (
                      <button className="mt-2 block text-xs font-semibold text-red-700" onClick={() => changeExerciseMedia()} type="button">
                        Eliminar medio actual
                      </button>
                    ) : null}
                  </div>
                ) : (
                  <p className="text-xs text-[#637268]">Guarda el ejercicio para poder adjuntar una imagen o video.</p>
                )}
                <button
                  className="rounded-lg bg-[#173c2a] px-4 py-2 font-semibold text-white disabled:opacity-60"
                  disabled={saving || !exerciseForm.name.trim()}
                  type="submit"
                >
                  {editingExerciseId ? "Actualizar ejercicio" : "Guardar ejercicio"}
                </button>
              </div>
            </form>
          </section>
        ) : null}

        {tab === "assignments" ? (
          <section className="grid gap-5 lg:grid-cols-[0.8fr_1.2fr]">
            <form className="rounded-xl border border-[#d9e2dc] bg-white p-5" onSubmit={saveAssignment}>
              <h2 className="text-xl font-semibold">Asignar a un socio</h2>
              <div className="mt-4 flex gap-2">
                <input className="app-input" onChange={(event) => setClientQuery(event.target.value)} placeholder="Nombre, correo o teléfono" value={clientQuery} />
                <button className="rounded-md border px-3 text-sm font-semibold" onClick={findClients} type="button">Buscar</button>
              </div>
              <div className="mt-2 grid gap-2">
                {clients.slice(0, 6).map((client) => (
                  <button
                    className={`rounded-md border p-2 text-left text-sm ${
                      selectedClient?.id === client.id ? "border-emerald-700 bg-emerald-50" : ""
                    }`}
                    key={client.id}
                    onClick={() => setSelectedClient(client)}
                    type="button"
                  >
                    {client.firstName} {client.lastName}
                  </button>
                ))}
              </div>
              <div className="mt-4 grid gap-3">
                <Field label="Rutina">
                  <select className="app-input" onChange={(event) => setSelectedWorkoutId(event.target.value)} value={selectedWorkoutId}>
                    <option value="">Selecciona una rutina publicada</option>
                    {publishedWorkouts.map((workout) => (
                      <option key={workout.id} value={workout.id}>{workout.title} · v{workout.latestVersionNumber}</option>
                    ))}
                  </select>
                </Field>
                <div className="grid grid-cols-2 gap-3">
                  <Field label="Inicio"><input className="app-input" onChange={(event) => setStartsOn(event.target.value)} type="date" value={startsOn} /></Field>
                  <Field label="Fin"><input className="app-input" onChange={(event) => setEndsOn(event.target.value)} type="date" value={endsOn} /></Field>
                </div>
                <Field label="Indicaciones imprimibles"><textarea className="app-input min-h-20" onChange={(event) => setPrintableNotes(event.target.value)} value={printableNotes} /></Field>
                <Field label="Notas privadas"><textarea className="app-input min-h-16" onChange={(event) => setPrivateNotes(event.target.value)} value={privateNotes} /></Field>
                <button className="rounded-lg bg-[#173c2a] px-4 py-2 font-semibold text-white disabled:opacity-60" disabled={saving || !selectedClient || !selectedWorkoutId} type="submit">
                  Asignar rutina
                </button>
              </div>
            </form>
            <AssignmentTable
              canDownloadPdf={canDownloadAssignmentPdf}
              canManage={canManageAssignments}
              onChanged={refresh}
              onError={(value) => setError(value)}
              onMessage={(value) => setMessage(value)}
              onPageChange={loadReportPage}
              report={report}
              session={session}
              workouts={publishedWorkouts}
            />
          </section>
        ) : null}

        {tab === "metrics" ? (
          <section className="grid gap-5">
            <div className="grid gap-4 rounded-xl border border-[#d9e2dc] bg-white p-4">
              <div className="flex flex-wrap items-end gap-3">
                <Field label="Desde"><input className="app-input" onChange={(event) => setReportFrom(event.target.value)} type="date" value={reportFrom} /></Field>
                <Field label="Hasta"><input className="app-input" onChange={(event) => setReportTo(event.target.value)} type="date" value={reportTo} /></Field>
                <Field label="Coach">
                  <select className="app-input min-w-48" onChange={(event) => setReportCoachId(event.target.value)} value={reportCoachId}>
                    <option value="">Todos</option>
                    {coaches.map((coach) => <option key={coach.id} value={coach.id}>{coach.displayName}</option>)}
                  </select>
                </Field>
                <Field label="Histórico">
                  <select className="app-input min-w-36" onChange={(event) => setTrendWeeks(Number(event.target.value))} value={trendWeeks}>
                    <option value={8}>8 semanas</option>
                    <option value={12}>12 semanas</option>
                    <option value={26}>26 semanas</option>
                    <option value={52}>52 semanas</option>
                  </select>
                </Field>
                <button className="rounded-md bg-[#173c2a] px-4 py-2 text-sm font-semibold text-white" disabled={saving} onClick={loadReport} type="button">
                  Actualizar
                </button>
                <button
                  className="rounded-md border px-4 py-2 text-sm font-semibold"
                  onClick={() => downloadAssignmentCsv(session.accessToken, {
                    from: reportFrom,
                    to: reportTo,
                    coachId: reportCoachId || undefined,
                    clientId: reportClient?.id,
                  })}
                  type="button"
                >
                  Exportar CSV
                </button>
              </div>
              <div>
                <p className="mb-1 text-sm font-medium text-[#435249]">Socio</p>
                <div className="flex max-w-2xl flex-wrap gap-2">
                  <input
                    className="app-input min-w-64 flex-1"
                    onChange={(event) => setMetricClientQuery(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter") {
                        event.preventDefault();
                        void findMetricClients();
                      }
                    }}
                    placeholder="Nombre, correo o teléfono"
                    value={metricClientQuery}
                  />
                  <button className="rounded-md border px-3 py-2 text-sm font-semibold" onClick={findMetricClients} type="button">
                    Buscar socio
                  </button>
                </div>
                {metricClients.length ? (
                  <div className="mt-2 flex flex-wrap gap-2">
                    {metricClients.slice(0, 8).map((client) => (
                      <button
                        className="rounded-full border border-[#b8c9bd] px-3 py-1 text-xs font-semibold"
                        key={client.id}
                        onClick={() => {
                          setReportClient(client);
                          setMetricClientQuery(`${client.firstName} ${client.lastName}`);
                          setMetricClients([]);
                        }}
                        type="button"
                      >
                        {client.firstName} {client.lastName}
                      </button>
                    ))}
                  </div>
                ) : null}
                {reportClient ? (
                  <div className="mt-2 flex items-center gap-2 text-sm">
                    <span className="rounded-full bg-emerald-50 px-3 py-1 font-semibold text-emerald-800">
                      Filtrando: {reportClient.firstName} {reportClient.lastName}
                    </span>
                    <button
                      className="font-semibold text-red-700"
                      onClick={() => {
                        setReportClient(null);
                        setMetricClientQuery("");
                      }}
                      type="button"
                    >
                      Quitar filtro
                    </button>
                  </div>
                ) : null}
              </div>
            </div>
            {report ? (
              <p className="text-sm text-[#637268]">
                Comparación contra el periodo anterior: {report.previousFrom} a {report.previousTo}.
              </p>
            ) : null}
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <Metric change={report?.assignmentChange} label="Rutinas asignadas" value={report?.assignments ?? 0} />
              <Metric change={report?.uniqueClientChange} label="Socios atendidos" value={report?.uniqueClients ?? 0} />
              <Metric label="Coaches activos" value={report?.activeCoaches ?? 0} />
              <Metric label="Sin asignaciones" value={report?.coachesWithoutAssignments ?? 0} />
            </div>
            <section className="rounded-xl border border-[#d9e2dc] bg-white p-5">
              <div>
                <p className="text-xs font-semibold uppercase tracking-wider text-[#6b7d72]">Tendencia histórica</p>
                <h2 className="text-xl font-semibold">Actividad semanal</h2>
                {trend ? (
                  <p className="mt-1 text-sm text-[#637268]">
                    {trend.weeks} semanas, del {trend.from} al {trend.to}
                    {reportClient ? ` · ${reportClient.firstName} ${reportClient.lastName}` : ""}
                  </p>
                ) : null}
              </div>
              <div className="mt-5 grid gap-4 xl:grid-cols-3">
                <WeeklyTrendChart
                  color="#1b6b45"
                  label="Rutinas asignadas"
                  points={trend?.points ?? []}
                  value={(point) => point.assignments}
                />
                <WeeklyTrendChart
                  color="#276f91"
                  label="Socios únicos"
                  points={trend?.points ?? []}
                  value={(point) => point.uniqueClients}
                />
                <WeeklyTrendChart
                  color="#b26a1b"
                  label="Primeras rutinas"
                  points={trend?.points ?? []}
                  value={(point) => point.firstWorkouts}
                />
              </div>
            </section>
            <section className="rounded-xl border border-[#d9e2dc] bg-white p-5">
              <h2 className="text-xl font-semibold">Desempeño por coach</h2>
              <div className="mt-4 grid gap-2">
                {report?.coaches.map((coach) => (
                  <article className="grid items-center gap-2 rounded-lg border border-[#e0e7e2] p-3 sm:grid-cols-[1fr_repeat(3,120px)]" key={coach.coachId}>
                    <strong>{coach.coachName}</strong>
                    <span>{coach.assignments} asignadas</span>
                    <span>{coach.uniqueClients} socios</span>
                    <span>{coach.firstWorkouts} primeras</span>
                  </article>
                ))}
              </div>
            </section>
            <AssignmentTable
              canDownloadPdf={canDownloadAssignmentPdf}
              canManage={canManageAssignments}
              onChanged={refresh}
              onError={(value) => setError(value)}
              onMessage={(value) => setMessage(value)}
              onPageChange={loadReportPage}
              report={report}
              session={session}
              workouts={publishedWorkouts}
            />
          </section>
        ) : null}
      </div>
    </AppShell>
  );

  function clearFeedback() {
    setError(null);
    setMessage(null);
  }
}

function ExerciseEditor({
  blockIndex,
  exercise,
  exerciseIndex,
  listId,
  onChange,
  onDelete,
  onMove,
  total,
}: {
  blockIndex: number;
  exercise: WorkoutExerciseDraft;
  exerciseIndex: number;
  listId: string;
  onChange: (patch: Partial<WorkoutExerciseDraft>) => void;
  onDelete: () => void;
  onMove: (direction: -1 | 1) => void;
  total: number;
}) {
  return (
    <article className="rounded-lg border border-[#e0e7e2] bg-white p-3">
      <div className="flex items-start justify-between gap-3">
        <p className="text-xs font-semibold uppercase tracking-wide text-[#637268]">
          Ejercicio {exerciseIndex + 1} · bloque {blockIndex + 1}
        </p>
        <OrderButtons
          canDelete={total > 1}
          compact
          index={exerciseIndex}
          length={total}
          onDelete={onDelete}
          onMove={onMove}
        />
      </div>
      <div className="mt-2 grid gap-2 md:grid-cols-2 xl:grid-cols-4">
        <Field label="Ejercicio">
          <input
            className="app-input"
            list={listId}
            onChange={(event) => onChange({ exerciseName: event.target.value, exerciseId: null })}
            value={exercise.exerciseName}
          />
        </Field>
        <Field label="Prescripción libre">
          <input className="app-input" onChange={(event) => onChange({ prescription: event.target.value })} placeholder="3x12, 10/8/6" value={exercise.prescription} />
        </Field>
        <Field label="Series">
          <input className="app-input" min="1" onChange={(event) => onChange({ sets: numberOrNull(event.target.value) })} type="number" value={exercise.sets ?? ""} />
        </Field>
        <Field label="Repeticiones">
          <input className="app-input" onChange={(event) => onChange({ reps: event.target.value })} placeholder="8-12" value={exercise.reps ?? ""} />
        </Field>
        <Field label="Carga">
          <input className="app-input" onChange={(event) => onChange({ load: event.target.value })} placeholder="20 kg / RPE 8" value={exercise.load ?? ""} />
        </Field>
        <Field label="Descanso (segundos)">
          <input className="app-input" min="0" onChange={(event) => onChange({ restSeconds: numberOrNull(event.target.value) })} type="number" value={exercise.restSeconds ?? ""} />
        </Field>
        <Field label="Tempo">
          <input className="app-input" onChange={(event) => onChange({ tempo: event.target.value })} placeholder="3-1-1" value={exercise.tempo ?? ""} />
        </Field>
        <Field label="Duración">
          <input className="app-input" onChange={(event) => onChange({ duration: event.target.value })} placeholder="45 segundos" value={exercise.duration ?? ""} />
        </Field>
        <Field label="Distancia">
          <input className="app-input" onChange={(event) => onChange({ distance: event.target.value })} placeholder="500 m" value={exercise.distance ?? ""} />
        </Field>
        <Field label="Agrupación">
          <input className="app-input" onChange={(event) => onChange({ groupKey: event.target.value })} placeholder="A1 / circuito 1" value={exercise.groupKey ?? ""} />
        </Field>
        <Field label="Notas técnicas">
          <input className="app-input" onChange={(event) => onChange({ notes: event.target.value })} value={exercise.notes ?? ""} />
        </Field>
        <label className="flex items-center gap-2 self-end pb-2 text-sm font-medium">
          <input checked={exercise.optional ?? false} onChange={(event) => onChange({ optional: event.target.checked })} type="checkbox" />
          Ejercicio opcional
        </label>
      </div>
    </article>
  );
}

function AssignmentTable({
  canDownloadPdf,
  canManage,
  onChanged,
  onError,
  onMessage,
  onPageChange,
  report,
  session,
  workouts,
}: {
  canDownloadPdf: boolean;
  canManage: boolean;
  onChanged: () => Promise<void>;
  onError: (message: string) => void;
  onMessage: (message: string) => void;
  onPageChange: (page: number) => Promise<void>;
  report: AssignmentReport | null;
  session: StoredSession;
  workouts: WorkoutSummary[];
}) {
  const [changing, setChanging] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState<string | null>(null);
  const [cancelReason, setCancelReason] = useState("");
  const [replacing, setReplacing] = useState<string | null>(null);
  const [replacementWorkoutId, setReplacementWorkoutId] = useState("");

  async function changeStatus(id: string, status: "COMPLETED" | "CANCELLED") {
    if (status === "CANCELLED" && !cancelReason.trim()) {
      onError("Indica el motivo de cancelación.");
      return;
    }
    setChanging(id);
    onError("");
    try {
      await updateWorkoutAssignmentStatus(
        session.accessToken,
        id,
        status,
        status === "CANCELLED" ? cancelReason : null,
      );
      setCancelling(null);
      setCancelReason("");
      onMessage(status === "COMPLETED" ? "Asignación completada." : "Asignación cancelada.");
      await onChanged();
    } catch (exception) {
      onError(errorMessage(exception));
    } finally {
      setChanging(null);
    }
  }

  async function replaceAssignment(assignment: AssignmentReport["details"][number]) {
    const workout = workouts.find((item) => item.id === replacementWorkoutId);
    if (!workout) {
      onError("Selecciona la rutina de reemplazo.");
      return;
    }
    setChanging(assignment.id);
    onError("");
    try {
      await replaceWorkoutAssignment(session.accessToken, assignment.id, {
        clientId: assignment.clientId,
        workoutId: workout.id,
        workoutVersionId: workout.latestVersionId,
        startsOn: assignment.startsOn,
        endsOn: assignment.endsOn,
        printableNotes: assignment.printableNotes,
        notes: assignment.privateNotes,
        idempotencyKey: `replace:${assignment.id}:${workout.id}:${crypto.randomUUID()}`,
      });
      setReplacing(null);
      setReplacementWorkoutId("");
      onMessage("La asignación anterior fue reemplazada y se conservó el historial.");
      await onChanged();
    } catch (exception) {
      onError(errorMessage(exception));
    } finally {
      setChanging(null);
    }
  }

  return (
    <section className="rounded-xl border border-[#d9e2dc] bg-white p-5">
      <h2 className="text-xl font-semibold">Historial de asignaciones</h2>
      <div className="mt-4 grid gap-2">
        {report?.details.length ? report.details.map((assignment) => (
          <article className="grid gap-2 rounded-lg border border-[#e0e7e2] p-3" key={assignment.id}>
            <div className="grid gap-2 md:grid-cols-[1fr_1fr_auto]">
              <div>
                <strong>{assignment.clientName}</strong>
                <p className="text-xs text-[#637268]">{assignment.assignedOn} · {assignment.coachName || "Sin coach"}</p>
              </div>
              <div>
                <p>{assignment.workoutTitle}</p>
                <p className="text-xs text-[#637268]">Versión {assignment.versionNumber} · {assignment.status}</p>
                {assignment.statusReason ? (
                  <p className="text-xs text-[#637268]">Motivo: {assignment.statusReason}</p>
                ) : null}
                {assignment.replacedAssignmentId ? (
                  <p className="text-xs text-[#637268]">
                    Reemplazada por la asignación {assignment.replacedAssignmentId}
                  </p>
                ) : null}
              </div>
              <div className="flex flex-wrap gap-2">
                {canDownloadPdf ? (
                  <button className="rounded-md border border-[#9eb2a5] px-3 py-2 text-sm font-semibold" onClick={() => downloadWorkoutPdf(session.accessToken, assignment.id)} type="button">
                    PDF
                  </button>
                ) : null}
                {canManage && assignment.status === "ACTIVE" ? (
                  <>
                    <button className="rounded-md border px-3 py-2 text-sm font-semibold" disabled={changing === assignment.id} onClick={() => changeStatus(assignment.id, "COMPLETED")} type="button">
                      Completar
                    </button>
                    <button className="rounded-md border px-3 py-2 text-sm font-semibold" disabled={changing === assignment.id} onClick={() => {
                      setCancelling(assignment.id);
                      setReplacing(null);
                    }} type="button">
                      Cancelar
                    </button>
                    <button className="rounded-md border px-3 py-2 text-sm font-semibold" disabled={changing === assignment.id} onClick={() => {
                      setReplacing(assignment.id);
                      setCancelling(null);
                    }} type="button">
                      Reemplazar
                    </button>
                  </>
                ) : null}
              </div>
            </div>
            {canManage && cancelling === assignment.id ? (
              <div className="flex flex-wrap gap-2 rounded-md bg-red-50 p-2">
                <input className="app-input min-w-64 flex-1" onChange={(event) => setCancelReason(event.target.value)} placeholder="Motivo obligatorio de cancelación" value={cancelReason} />
                <button className="rounded-md bg-red-700 px-3 py-2 text-sm font-semibold text-white" disabled={changing === assignment.id || !cancelReason.trim()} onClick={() => changeStatus(assignment.id, "CANCELLED")} type="button">
                  Confirmar cancelación
                </button>
              </div>
            ) : null}
            {canManage && replacing === assignment.id ? (
              <div className="flex flex-wrap gap-2 rounded-md bg-[#f1f6f2] p-2">
                <select
                  aria-label="Selecciona la nueva rutina"
                  className="app-input min-w-64 flex-1"
                  onChange={(event) => setReplacementWorkoutId(event.target.value)}
                  value={replacementWorkoutId}
                >
                  <option value="">Selecciona la nueva rutina</option>
                  {workouts.filter((workout) => workout.id !== assignment.workoutId).map((workout) => (
                    <option key={workout.id} value={workout.id}>{workout.title} · v{workout.latestVersionNumber}</option>
                  ))}
                </select>
                <button className="rounded-md bg-[#173c2a] px-3 py-2 text-sm font-semibold text-white" disabled={changing === assignment.id || !replacementWorkoutId} onClick={() => replaceAssignment(assignment)} type="button">
                  Confirmar reemplazo
                </button>
              </div>
            ) : null}
          </article>
        )) : (
          <p className="rounded-lg border border-dashed p-5 text-sm text-[#637268]">
            No hay asignaciones en el periodo.
          </p>
        )}
      </div>
      {report && report.totalPages > 1 ? (
        <div className="mt-4 flex items-center justify-between gap-3">
          <button
            className="rounded-md border px-3 py-2 text-sm font-semibold disabled:opacity-50"
            disabled={report.page === 0}
            onClick={() => onPageChange(report.page - 1)}
            type="button"
          >
            Anterior
          </button>
          <span className="text-sm text-[#637268]">
            Página {report.page + 1} de {report.totalPages} · {report.totalDetails} asignaciones
          </span>
          <button
            className="rounded-md border px-3 py-2 text-sm font-semibold disabled:opacity-50"
            disabled={report.page + 1 >= report.totalPages}
            onClick={() => onPageChange(report.page + 1)}
            type="button"
          >
            Siguiente
          </button>
        </div>
      ) : null}
    </section>
  );
}

function ExerciseInput({
  field,
  form,
  setForm,
}: {
  field: keyof CreateExercisePayload;
  form: CreateExercisePayload;
  setForm: React.Dispatch<React.SetStateAction<CreateExercisePayload>>;
}) {
  return (
    <input
      className="app-input"
      onChange={(event) => setForm((current) => ({ ...current, [field]: event.target.value }))}
      value={String(form[field] ?? "")}
    />
  );
}

function OrderButtons({
  canDelete,
  compact = false,
  index,
  length,
  onDelete,
  onMove,
}: {
  canDelete: boolean;
  compact?: boolean;
  index: number;
  length: number;
  onDelete: () => void;
  onMove: (direction: -1 | 1) => void;
}) {
  const style = compact ? "px-2 py-1 text-xs" : "px-2 py-2 text-sm";
  return (
    <div className="flex gap-1">
      <button aria-label="Mover arriba" className={`rounded-md border ${style}`} disabled={index === 0} onClick={() => onMove(-1)} type="button">↑</button>
      <button aria-label="Mover abajo" className={`rounded-md border ${style}`} disabled={index === length - 1} onClick={() => onMove(1)} type="button">↓</button>
      {canDelete ? <button className={`rounded-md border border-red-200 font-semibold text-red-700 ${style}`} onClick={onDelete} type="button">Eliminar</button> : null}
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="grid min-w-0 gap-1 text-sm font-medium text-[#435249]">
      <span>{label}</span>
      {children}
    </label>
  );
}

function WeeklyTrendChart({
  color,
  label,
  points,
  value,
}: {
  color: string;
  label: string;
  points: WeeklyTrendReport["points"];
  value: (point: WeeklyTrendReport["points"][number]) => number;
}) {
  const width = 640;
  const height = 220;
  const paddingX = 28;
  const paddingTop = 20;
  const paddingBottom = 34;
  const values = points.map(value);
  const maximum = Math.max(1, ...values);
  const chartHeight = height - paddingTop - paddingBottom;
  const chartWidth = width - (paddingX * 2);
  const coordinates = points.map((point, index) => {
    const x = paddingX + (points.length === 1 ? chartWidth / 2 : (index / (points.length - 1)) * chartWidth);
    const y = paddingTop + chartHeight - ((value(point) / maximum) * chartHeight);
    return { point, x, y };
  });
  const total = values.reduce((sum, current) => sum + current, 0);

  return (
    <article className="rounded-lg border border-[#e0e7e2] p-4">
      <div className="flex items-end justify-between gap-3">
        <h3 className="text-sm font-semibold">{label}</h3>
        <p className="text-2xl font-semibold">{total}</p>
      </div>
      {points.length ? (
        <>
          <svg
            aria-label={`${label} por semana`}
            className="mt-3 h-auto w-full"
            role="img"
            viewBox={`0 0 ${width} ${height}`}
          >
            {[0, 0.5, 1].map((ratio) => {
              const y = paddingTop + (chartHeight * ratio);
              return (
                <line
                  key={ratio}
                  stroke="#dce5df"
                  strokeDasharray={ratio === 1 ? undefined : "4 5"}
                  strokeWidth="1"
                  x1={paddingX}
                  x2={width - paddingX}
                  y1={y}
                  y2={y}
                />
              );
            })}
            <polyline
              fill="none"
              points={coordinates.map(({ x, y }) => `${x},${y}`).join(" ")}
              stroke={color}
              strokeLinejoin="round"
              strokeWidth="4"
            />
            {coordinates.map(({ point, x, y }) => (
              <circle cx={x} cy={y} fill="white" key={point.weekFrom} r="5" stroke={color} strokeWidth="3">
                <title>{formatShortDate(point.weekFrom)}: {value(point)}</title>
              </circle>
            ))}
            <text fill="#637268" fontSize="18" x={paddingX} y={height - 7}>
              {formatShortDate(points[0].weekFrom)}
            </text>
            <text fill="#637268" fontSize="18" textAnchor="end" x={width - paddingX} y={height - 7}>
              {formatShortDate(points[points.length - 1].weekFrom)}
            </text>
          </svg>
          <p className="mt-1 text-xs text-[#637268]">Máximo semanal: {maximum}</p>
        </>
      ) : (
        <p className="mt-3 rounded-md border border-dashed p-4 text-sm text-[#637268]">Sin datos para graficar.</p>
      )}
    </article>
  );
}

function Metric({ change, label, value }: { change?: number; label: string; value: number }) {
  const direction = change === undefined ? null : change > 0 ? "increase" : change < 0 ? "decrease" : "same";
  return (
    <article className="rounded-xl border border-[#d9e2dc] bg-white p-5">
      <p className="text-sm text-[#637268]">{label}</p>
      <div className="mt-1 flex items-end justify-between gap-2">
        <p className="text-3xl font-semibold">{value}</p>
        {direction ? (
          <span className={`rounded-full px-2 py-1 text-xs font-semibold ${
            direction === "increase"
              ? "bg-emerald-50 text-emerald-800"
              : direction === "decrease"
                ? "bg-red-50 text-red-700"
                : "bg-slate-100 text-slate-700"
          }`}>
            {change && change > 0 ? "+" : ""}{change} vs. anterior
          </span>
        ) : null}
      </div>
    </article>
  );
}

function isPublished(workout: WorkoutSummary) {
  return workout.status === "PUBLISHED" || workout.status === "ACTIVE";
}

function numberOrNull(value: string) {
  return value === "" ? null : Number(value);
}

function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : "No fue posible completar la operación.";
}

function today() {
  return new Date().toISOString().slice(0, 10);
}

function startOfWeek() {
  const date = new Date();
  const day = date.getDay() || 7;
  date.setDate(date.getDate() - day + 1);
  return date.toISOString().slice(0, 10);
}

function formatShortDate(value: string) {
  return new Intl.DateTimeFormat("es-MX", { day: "2-digit", month: "short", timeZone: "UTC" })
    .format(new Date(`${value}T00:00:00Z`));
}

function availableTabs(session: StoredSession | null): Tab[] {
  if (!session) return [];
  if (isTenantOwner(session)) {
    return session.user.permissions.includes("coach.metrics.read") ? ["metrics"] : [];
  }
  const tabs: Tab[] = [];
  if (hasAnyPermission(session, "workout.manage.own", "workout.manage.all")) tabs.push("builder");
  if (session.user.permissions.includes("exercise.read")) tabs.push("catalog");
  if (session.user.permissions.includes("workout.assign")) tabs.push("assignments");
  if (session.user.permissions.includes("coach.metrics.read")) tabs.push("metrics");
  return tabs;
}

function hasAnyPermission(session: StoredSession, ...permissions: string[]) {
  return permissions.some((permission) => session.user.permissions.includes(permission));
}

function isTenantOwner(session: StoredSession) {
  return session.user.roles.some((role) => {
    const normalized = role
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .toLowerCase();
    return normalized === "dueno del gimnasio" || normalized === "administrador";
  });
}
