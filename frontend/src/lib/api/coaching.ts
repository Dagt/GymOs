import { apiFetch, normalizePayload } from "./client";
import type {
  AssignWorkoutPayload,
  CoachReport,
  CoachSummary,
  CreateCoachPayload,
  CreateWorkoutPayload,
  CreateExercisePayload,
  Exercise,
  ExercisePage,
  AssignmentReport,
  WorkoutAssignment,
  WorkoutDetail,
  WorkoutSummary,
} from "./coaching-types";

export async function getCoaches(accessToken: string): Promise<CoachSummary[]> {
  return apiFetch<CoachSummary[]>("/coaches", accessToken);
}

export async function createCoach(
  accessToken: string,
  payload: CreateCoachPayload,
): Promise<CoachSummary> {
  return apiFetch<CoachSummary>("/coaches", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getWorkouts(accessToken: string): Promise<WorkoutSummary[]> {
  return apiFetch<WorkoutSummary[]>("/workouts", accessToken);
}

export async function createWorkout(
  accessToken: string,
  payload: CreateWorkoutPayload,
): Promise<WorkoutDetail> {
  return apiFetch<WorkoutDetail>("/workouts", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getWorkout(accessToken: string, workoutId: string): Promise<WorkoutDetail> {
  return apiFetch<WorkoutDetail>(`/workouts/${workoutId}`, accessToken);
}

export async function updateWorkout(
  accessToken: string,
  workoutId: string,
  payload: CreateWorkoutPayload,
): Promise<WorkoutDetail> {
  return apiFetch<WorkoutDetail>(`/workouts/${workoutId}`, accessToken, {
    method: "PUT",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function updateExerciseCatalog(
  accessToken: string,
  exerciseId: string,
  payload: CreateExercisePayload,
): Promise<Exercise> {
  return apiFetch<Exercise>(`/exercises/${exerciseId}`, accessToken, {
    method: "PUT",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function addExerciseAlias(
  accessToken: string,
  exerciseId: string,
  alias: string,
): Promise<string[]> {
  return apiFetch<string[]>(`/exercises/${exerciseId}/aliases`, accessToken, {
    method: "POST",
    body: JSON.stringify({ alias }),
  });
}

export async function duplicateWorkout(accessToken: string, workoutId: string): Promise<WorkoutDetail> {
  return apiFetch<WorkoutDetail>(`/workouts/${workoutId}/duplicate`, accessToken, { method: "POST" });
}

export async function publishWorkout(accessToken: string, workoutId: string): Promise<WorkoutDetail> {
  return apiFetch<WorkoutDetail>(`/workouts/${workoutId}/publish`, accessToken, { method: "POST" });
}

export async function updateWorkoutAssignmentStatus(
  accessToken: string,
  assignmentId: string,
  status: "ACTIVE" | "COMPLETED" | "CANCELLED" | "REPLACED",
): Promise<WorkoutAssignment> {
  return apiFetch<WorkoutAssignment>(`/workout-assignments/${assignmentId}/status`, accessToken, {
    method: "PATCH",
    body: JSON.stringify({ status }),
  });
}

export async function assignWorkout(
  accessToken: string,
  workoutId: string,
  payload: AssignWorkoutPayload,
): Promise<WorkoutAssignment> {
  return apiFetch<WorkoutAssignment>(`/workouts/${workoutId}/assign`, accessToken, {
    method: "POST",
    headers: payload.idempotencyKey ? { "Idempotency-Key": payload.idempotencyKey } : undefined,
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getClientWorkouts(
  accessToken: string,
  clientId: string,
): Promise<WorkoutAssignment[]> {
  return apiFetch<WorkoutAssignment[]>(`/clients/${clientId}/workouts`, accessToken);
}

export async function getCoachReport(accessToken: string): Promise<CoachReport> {
  return apiFetch<CoachReport>("/reports/coaches", accessToken);
}

export async function getExercises(
  accessToken: string,
  params: { query?: string; muscleGroup?: string; equipment?: string; type?: string; page?: number; size?: number } = {},
): Promise<ExercisePage> {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== "") search.set(key, String(value));
  });
  return apiFetch<ExercisePage>(`/exercises?${search.toString()}`, accessToken);
}

export async function createExercise(
  accessToken: string,
  payload: CreateExercisePayload,
): Promise<Exercise> {
  return apiFetch<Exercise>("/exercises", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getAssignmentReport(
  accessToken: string,
  params: { from?: string; to?: string; coachId?: string; clientId?: string } = {},
): Promise<AssignmentReport> {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => { if (value) search.set(key, value); });
  return apiFetch<AssignmentReport>(
    `/reports/coaches/workout-assignments/summary?${search.toString()}`,
    accessToken,
  );
}

export async function downloadWorkoutPdf(accessToken: string, assignmentId: string) {
  return downloadFile(accessToken, `/workout-assignments/${assignmentId}/pdf`, `rutina-${assignmentId}.pdf`);
}

export async function downloadAssignmentCsv(
  accessToken: string,
  params: { from?: string; to?: string; coachId?: string } = {},
) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => { if (value) search.set(key, value); });
  return downloadFile(
    accessToken,
    `/reports/coaches/workout-assignments/export?${search.toString()}`,
    "asignaciones-rutinas.csv",
  );
}

async function downloadFile(accessToken: string, path: string, filename: string) {
  const base = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";
  const response = await fetch(`${base}${path}`, { headers: { Authorization: `Bearer ${accessToken}` } });
  if (!response.ok) throw new Error(`No fue posible descargar el archivo (${response.status}).`);
  const url = URL.createObjectURL(await response.blob());
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}
