export type ClientSummary = {
  id: string;
  firstName: string;
  lastName: string;
  maternalLastName?: string | null;
  phone: string | null;
  email: string | null;
  status: "ACTIVE" | "INACTIVE" | "SUSPENDED";
  currentPlanName: string | null;
  membershipStatus: "ACTIVE" | "EXPIRED" | null;
  joinedAt: string;
  updatedAt: string;
};

export type ClientDetail = {
  id: string;
  personId: string;
  firstName: string;
  lastName: string;
  maternalLastName?: string | null;
  birthDate: string | null;
  gender: string | null;
  phone: string | null;
  email: string | null;
  photoReference: string | null;
  status: "ACTIVE" | "INACTIVE" | "SUSPENDED";
  joinedAt: string;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ClientTimelineEntry = {
  action: string;
  origin: string | null;
  reason: string | null;
  occurredAt: string;
};

export type ClientDetailResponse = {
  client: ClientDetail;
  timeline: ClientTimelineEntry[];
};

export type ClientPayload = {
  firstName: string;
  lastName: string;
  maternalLastName?: string | null;
  birthDate?: string | null;
  gender?: string | null;
  phone?: string | null;
  email?: string | null;
  photoReference?: string | null;
  notes?: string | null;
};

export type ClientUpdatePayload = ClientPayload & {
  status: "ACTIVE" | "INACTIVE" | "SUSPENDED";
};

export type ClientImportRow = {
  rowNumber: number;
  status: "VALID" | "CREATED" | "SKIPPED" | "ERROR";
  reason: string;
  clientId: string | null;
  firstName: string | null;
  lastName: string | null;
  email: string | null;
  phone: string | null;
};

export type ClientImportResult = {
  batchId: string;
  dryRun: boolean;
  status: string;
  totalRows: number;
  createdCount: number;
  skippedCount: number;
  errorCount: number;
  createdAt: string;
  rows: ClientImportRow[];
};
