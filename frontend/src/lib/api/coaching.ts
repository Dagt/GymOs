import { apiFetch, normalizePayload } from "./client";
import type {
  AssignWorkoutPayload,
  CoachReport,
  CoachSummary,
  CreateCoachPayload,
  CreateWorkoutPayload,
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

export async function assignWorkout(
  accessToken: string,
  workoutId: string,
  payload: AssignWorkoutPayload,
): Promise<WorkoutAssignment> {
  return apiFetch<WorkoutAssignment>(`/workouts/${workoutId}/assign`, accessToken, {
    method: "POST",
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
