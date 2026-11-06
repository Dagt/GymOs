export type AccessDevice = {
  id: string;
  name: string;
  deviceCode: string;
  deviceType: "TURNSTILE" | "DOOR" | string;
  status: "ACTIVE" | "INACTIVE" | string;
  provider: string;
  lastSeenAt: string | null;
};

export type AccessDevicePayload = {
  name: string;
  deviceCode: string;
  deviceType: "TURNSTILE" | "DOOR" | "GATE" | "ZONE" | "BIOMETRIC";
  provider: string;
  requiredBenefitId?: string | null;
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
  decision: "GRANTED" | "DENIED" | "DUPLICATE";
  reversedAt?: string | null;
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

export type EffectiveAccessBenefit = {
  benefitId: string;
  code: string;
  name: string;
  membershipId: string;
  planId: string;
  planName: string;
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
  idempotencyKey?: string | null;
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

export type PhysicalAccessBenefit = {
  id: string;
  code: string;
  name: string;
};

export type PhysicalBenefitMapping = {
  benefitId: string;
  benefitCode: string | null;
  benefitName: string | null;
  externalAccessLevelRef: string | null;
  externalAccessLevelName: string | null;
};

export type PhysicalIntegrationConnection = {
  id: string | null;
  providerKey: "HIKCENTRAL" | string;
  displayName: string;
  connectorKey: string | null;
  enabled: boolean;
  syncStatus:
    | "DISABLED"
    | "AWAITING_CONNECTOR"
    | "OFFLINE"
    | "HEALTHY"
    | "DEGRADED"
    | string;
  credentialActive: boolean;
  lastHeartbeatAt: string | null;
  lastSynchronizedAt: string | null;
  lastError: string | null;
  pendingJobs: number;
  failedJobs: number;
  connectorVersion: string | null;
  connectorActivatedAt: string | null;
};

export type PhysicalProviderDescriptor = {
  providerKey: string;
  displayName: string;
  availability: "AVAILABLE" | "PLANNED" | string;
  requiresLocalConnector: boolean;
  supportsPersonSynchronization: boolean;
  supportsAccessLevels: boolean;
  supportsResourceDiscovery: boolean;
  supportsRemoteOpen: boolean;
  supportsEvents: boolean;
  implementationNote: string;
};

export type PhysicalDeviceLink = {
  id: string;
  accessDeviceId: string;
  deviceName: string;
  deviceCode: string;
  externalDeviceRef: string;
  externalDeviceName: string | null;
  updatedAt: string;
};

export type PhysicalIntegration = {
  providers: PhysicalProviderDescriptor[];
  connection: PhysicalIntegrationConnection;
  availableBenefits: PhysicalAccessBenefit[];
  mappings: PhysicalBenefitMapping[];
  deviceLinks: PhysicalDeviceLink[];
};

export type PhysicalIntegrationPayload = {
  enabled: boolean;
  providerKey: string;
  displayName: string;
  connectorKey?: string | null;
  benefitMappings: Array<{
    benefitId: string;
    externalAccessLevelRef: string;
    externalAccessLevelName?: string | null;
  }>;
};

export type PhysicalAccessSyncState = {
  desiredAccessLevelRefs: string[];
  action: "UPSERT" | "REVOKE" | "RECONCILE" | string;
  status: "PENDING" | "BLOCKED" | "SYNCED" | "FAILED" | string;
  requestedAt: string;
  lastAttemptAt: string | null;
  lastSynchronizedAt: string | null;
  lastError: string | null;
  desiredVersion: number;
  blockedReasonCode: string | null;
};

export type PhysicalClientState = {
  clientId: string;
  clientName: string;
  externalPersonRef: string | null;
  effectiveBenefits: PhysicalAccessBenefit[];
  effectiveMappings: PhysicalBenefitMapping[];
  syncState: PhysicalAccessSyncState | null;
};

export type PhysicalConnectorActivationCode = {
  connectorKey: string;
  activationCode: string;
  purpose: "ACTIVATION" | "ROTATION" | string;
  expiresAt: string;
  warning: string;
};

export type PhysicalPersonCandidate = {
  id: string;
  externalPersonRef: string;
  externalDisplayName: string | null;
  email: string | null;
  phone: string | null;
  externalEmployeeNumber: string | null;
  matchStatus:
    | "MATCHED"
    | "SUGGESTED"
    | "UNMATCHED"
    | "CONFLICT"
    | "REJECTED"
    | string;
  suggestedClientId: string | null;
  matchedClientId: string | null;
  matchReason: string | null;
  updatedAt: string;
};
