import type {
  WorkoutBlockDraft,
  WorkoutExerciseDraft,
  WorkoutSetDraft,
  WorkoutSetSegmentDraft,
  WorkoutSessionDraft,
} from "@/lib/api";

export function emptyExercise(): WorkoutExerciseDraft {
  return {
    exerciseName: "",
    prescription: "",
    optional: false,
  };
}

export function emptySetSegment(
  reps = "",
  load = "",
): WorkoutSetSegmentDraft {
  return { reps, load };
}

export function emptySet(
  reps = "",
  load = "",
): WorkoutSetDraft {
  return { segments: [emptySetSegment(reps, load)] };
}

export function detailedSetPlan(exercise: WorkoutExerciseDraft): WorkoutSetDraft[] {
  if (exercise.setPlan?.length) return exercise.setPlan;
  const total = Math.max(1, exercise.sets ?? 3);
  return Array.from(
    { length: total },
    () => emptySet(exercise.reps ?? "", exercise.load ?? ""),
  );
}

export function emptyBlock(
  name = "Trabajo principal",
  type = "MAIN",
): WorkoutBlockDraft {
  return {
    name,
    type,
    notes: "",
    exercises: [emptyExercise()],
  };
}

export function emptySession(index: number): WorkoutSessionDraft {
  return {
    name: `Día ${index + 1}`,
    focus: "",
    notes: "",
    blocks: [emptyBlock()],
  };
}

export function moveItem<T>(items: T[], index: number, direction: -1 | 1): T[] {
  const target = index + direction;
  if (target < 0 || target >= items.length) return items;
  const copy = [...items];
  [copy[index], copy[target]] = [copy[target], copy[index]];
  return copy;
}

export function updateBlock(
  sessions: WorkoutSessionDraft[],
  sessionIndex: number,
  blockIndex: number,
  updater: (block: WorkoutBlockDraft) => WorkoutBlockDraft,
): WorkoutSessionDraft[] {
  return sessions.map((session, currentSession) => {
    if (currentSession !== sessionIndex) return session;
    return {
      ...session,
      blocks: session.blocks.map((block, currentBlock) =>
        currentBlock === blockIndex ? updater(block) : block),
    };
  });
}

export function updateExercise(
  sessions: WorkoutSessionDraft[],
  sessionIndex: number,
  blockIndex: number,
  exerciseIndex: number,
  patch: Partial<WorkoutExerciseDraft>,
): WorkoutSessionDraft[] {
  return updateBlock(sessions, sessionIndex, blockIndex, (block) => ({
    ...block,
    exercises: block.exercises.map((exercise, currentExercise) =>
      currentExercise === exerciseIndex ? { ...exercise, ...patch } : exercise),
  }));
}

export function validateWorkout(title: string, sessions: WorkoutSessionDraft[]): string | null {
  if (!title.trim()) return "Escribe un título para la rutina.";
  if (!sessions.length) return "Agrega al menos una sesión.";
  if (sessions.some((session) => !session.name.trim())) {
    return "Cada sesión necesita nombre.";
  }
  if (sessions.some((session) => !session.blocks.length)) {
    return "Cada sesión necesita al menos un bloque.";
  }
  if (sessions.some((session) =>
    session.blocks.some((block) =>
      !block.name.trim()
      || !block.exercises.length
      || block.exercises.some((exercise) => !exercise.exerciseName.trim())))) {
    return "Cada bloque y ejercicio necesita nombre.";
  }
  return null;
}
