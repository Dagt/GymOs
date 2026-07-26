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
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED" | "ACTIVE" | "INACTIVE" | string;
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
  printableNotes?: string | null;
  idempotencyKey?: string | null;
};

export type Exercise = {
  id: string;
  organizationId: string | null;
  name: string;
  description: string | null;
  instructions: string | null;
  primaryMuscleGroup: string | null;
  secondaryMuscleGroups: string | null;
  movementPattern: string | null;
  type: string;
  equipment: string | null;
  suggestedLevel: string | null;
  laterality: string | null;
  mediaUrl: string | null;
  warnings: string | null;
  origin: "SYSTEM" | "ORGANIZATION" | "COACH";
  status: "ACTIVE" | "ARCHIVED";
};

export type ExercisePage = {
  items: Exercise[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type CreateExercisePayload = {
  name: string;
  description?: string | null;
  instructions?: string | null;
  primaryMuscleGroup?: string | null;
  secondaryMuscleGroups?: string | null;
  movementPattern?: string | null;
  type: string;
  equipment?: string | null;
  suggestedLevel?: string | null;
  laterality?: string | null;
  mediaUrl?: string | null;
  warnings?: string | null;
};

export type WorkoutExerciseDraft = {
  exerciseId?: string | null;
  exerciseName: string;
  prescription: string;
  sets?: number | null;
  reps?: string | null;
  load?: string | null;
  restSeconds?: number | null;
  tempo?: string | null;
  duration?: string | null;
  distance?: string | null;
  notes?: string | null;
  groupKey?: string | null;
  optional?: boolean;
};

export type WorkoutBlockDraft = {
  name: string;
  type: string;
  notes?: string | null;
  exercises: WorkoutExerciseDraft[];
};

export type WorkoutSessionDraft = {
  name: string;
  focus?: string | null;
  notes?: string | null;
  blocks: WorkoutBlockDraft[];
};

export type AssignmentDetail = {
  id: string;
  assignedOn: string;
  status: string;
  startsOn: string | null;
  endsOn: string | null;
  workoutId: string;
  workoutTitle: string;
  versionNumber: number;
  clientId: string;
  clientName: string;
  coachId: string | null;
  coachName: string | null;
  printableNotes: string | null;
  privateNotes: string | null;
};

export type CoachMetric = {
  coachId: string;
  coachName: string;
  assignments: number;
  uniqueClients: number;
  firstWorkouts: number;
};

export type AssignmentReport = {
  from: string;
  to: string;
  assignments: number;
  uniqueClients: number;
  activeCoaches: number;
  coachesWithoutAssignments: number;
  coaches: CoachMetric[];
  details: AssignmentDetail[];
};
