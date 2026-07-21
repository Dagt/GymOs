export type AccessDevice = {
  id: string;
  name: string;
  deviceCode: string;
  deviceType: "TURNSTILE" | "DOOR" | string;
  status: "ACTIVE" | "INACTIVE" | string;
  provider: string;
  lastSeenAt: string | null;
};

export type AccessDecision = {
  logId: string;
  decision: "GRANTED" | "DENIED";
  accessType: "MEMBERSHIP_CHECK" | "TEMPORARY_PASS" | "MANUAL_OPEN" | string;
  reason: string | null;
  commandStatus: "SIMULATED_OPEN" | "NOT_SENT" | string | null;
  occurredAt: string;
  deviceName: string | null;
  deviceCode: string | null;
  clientId: string | null;
  clientName: string | null;
  membershipId: string | null;
  planName: string | null;
  temporaryPassId: string | null;
};

export type AccessLog = {
  id: string;
  decision: "GRANTED" | "DENIED";
  accessType: string;
  source: string;
  reason: string | null;
  commandStatus: string | null;
  occurredAt: string;
  deviceName: string | null;
  deviceCode: string | null;
  clientId: string | null;
  clientName: string | null;
};

export type TemporaryPass = {
  id: string;
  clientId: string;
  clientName: string;
  status: "ACTIVE" | "CONSUMED" | "CANCELLED" | string;
  startsAt: string;
  expiresAt: string;
  maxUses: number;
  usesCount: number;
  reason: string;
};

export type VisitPass = {
  id: string;
  clientId: string;
  clientName: string;
  saleId: string | null;
  passType: "DROP_IN" | "VISIT_PACK" | "COURTESY" | "PROMOTION" | string;
  status: "ACTIVE" | "CONSUMED" | "CANCELLED" | string;
  totalVisits: number;
  remainingVisits: number;
  validFrom: string;
  validUntil: string | null;
  createdAt: string;
  updatedAt: string;
};

export type AccessRequestPayload = {
  clientId?: string | null;
  identifier?: string | null;
  deviceCode?: string | null;
};

export type ManualOpenPayload = {
  clientId?: string | null;
  deviceCode?: string | null;
  reason: string;
};

export type TemporaryPassPayload = {
  clientId: string;
  startsAt?: string | null;
  expiresAt: string;
  maxUses: number;
  reason: string;
};

export type CreateVisitPassPayload = {
  clientId: string;
  saleId?: string | null;
  passType: "DROP_IN" | "VISIT_PACK" | "COURTESY" | "PROMOTION";
  totalVisits: number;
  validFrom?: string | null;
  validUntil?: string | null;
};

export type ConsumeVisitPassPayload = {
  reason?: string | null;
};
