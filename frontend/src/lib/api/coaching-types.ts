export type CoachSummary = {
  id: string;
  displayName: string;
  email: string | null;
  phone: string | null;
  specialtiesJson: string | null;
  bio: string | null;
  status: "ACTIVE" | "INACTIVE" | string;
  createdAt: string;
  updatedAt: string;
};

export type WorkoutSummary = {
  id: string;
  coachId: string | null;
  coachName: string | null;
  title: string;
  goal: string | null;
  level: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "GENERAL" | string;
  status: "ACTIVE" | "INACTIVE" | string;
  latestVersionId: string | null;
  latestVersionNumber: number | null;
  createdAt: string;
  updatedAt: string;
};

export type WorkoutVersion = {
  id: string;
  workoutId: string;
  versionNumber: number;
  summary: string | null;
  contentJson: string;
  status: "PUBLISHED" | "DRAFT" | string;
  createdAt: string;
};

export type WorkoutDetail = {
  workout: Omit<WorkoutSummary, "latestVersionId" | "latestVersionNumber">;
  versions: WorkoutVersion[];
};

export type WorkoutAssignment = {
  id: string;
  workoutId: string;
  workoutVersionId: string;
  clientId: string;
  clientName: string;
  coachId: string | null;
  coachName: string | null;
  workoutTitle: string;
  level: string;
  versionNumber: number;
  status: "ACTIVE" | "COMPLETED" | "CANCELLED" | string;
  assignedOn: string;
  startsOn: string | null;
  endsOn: string | null;
  notes: string | null;
};

export type CoachReport = {
  coachCount: number;
  workoutCount: number;
  activeAssignments: number;
  assignmentsLast30Days: number;
};

export type CreateCoachPayload = {
  displayName: string;
  email?: string | null;
  phone?: string | null;
  specialties?: string[];
  bio?: string | null;
};

export type CreateWorkoutPayload = {
  coachId?: string | null;
  title: string;
  goal?: string | null;
  level: "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "GENERAL";
  summary?: string | null;
  contentJson: string;
};

export type AssignWorkoutPayload = {
  clientId: string;
  workoutVersionId?: string | null;
  startsOn?: string | null;
  endsOn?: string | null;
  notes?: string | null;
};
