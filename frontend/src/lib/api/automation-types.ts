export type NotificationLog = {
  id: string;
  channel: string;
  recipient: string | null;
  subject: string | null;
  body: string;
  bodyHtml: string | null;
  status: string;
  category: "TRANSACTIONAL" | "MARKETING" | string;
  recipientType: "CLIENT" | "USER" | "DIRECT" | null;
  recipientId: string | null;
  triggerType: string | null;
  referenceType: string | null;
  referenceId: string | null;
  scheduledAt: string | null;
  sentAt: string | null;
  errorMessage: string | null;
  providerKey: string | null;
  providerMessageId: string | null;
  attemptCount: number;
  availableAt: string;
  lockedUntil: string | null;
  lastAttemptAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateNotificationPayload = {
  channel: string;
  recipient?: string | null;
  recipientType?: string | null;
  recipientId?: string | null;
  subject?: string | null;
  body: string;
  category?: "TRANSACTIONAL" | "MARKETING";
  triggerType?: string | null;
  referenceType?: string | null;
  referenceId?: string | null;
  scheduledAt?: string | null;
};

export type EmailNotificationConfiguration = {
  organizationId: string;
  emailEnabled: boolean;
  providerKey: string;
  fromEmail: string | null;
  fromName: string | null;
  replyTo: string | null;
  providerAvailable: boolean;
  ready: boolean;
  updatedAt: string | null;
};

export type UpdateEmailNotificationConfiguration = {
  enabled: boolean;
  providerKey: string;
  fromEmail?: string | null;
  fromName?: string | null;
  replyTo?: string | null;
};

export type EmailRecipientPreference = {
  emailEnabled: boolean;
  marketingEnabled: boolean;
  updatedAt: string | null;
};

export type UpdateEmailRecipientPreference = {
  recipient: string;
  emailEnabled: boolean;
  marketingEnabled: boolean;
};

export type AutomationRule = {
  organizationId: string;
  branchId: string | null;
  id: string;
  name: string;
  triggerType: string;
  conditionJson: string;
  actionType: string;
  actionConfigJson: string;
  status: "ENABLED" | "DISABLED" | string;
  retryLimit: number;
  scheduleCron: string | null;
  nextRunAt: string | null;
  lockedUntil: string | null;
  currentTriggerReference: string | null;
  createdAt: string;
  updatedAt: string;
};

export type AutomationExecution = {
  id: string;
  ruleId: string;
  status: string;
  triggerReference: string | null;
  errorMessage: string | null;
  idempotencyKey: string | null;
  attemptNumber: number;
  ruleVersion: number;
  startedAt: string | null;
  finishedAt: string | null;
  occurredAt: string;
};

export type AutomationExecutionPage = {
  items: AutomationExecution[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type AutomationDeadLetter = {
  id: string;
  source: "EXECUTION" | "EVENT";
  ruleId: string | null;
  ruleName: string | null;
  triggerType: string;
  triggerReference: string;
  errorMessage: string | null;
  attemptNumber: number;
  occurredAt: string;
};

export type AutomationDeadLetterPage = {
  items: AutomationDeadLetter[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type AutomationSchedulerStatus = {
  organizationId: string;
  paused: boolean;
  reason: string | null;
  pausedAt: string | null;
  pausedBy: string | null;
  updatedAt: string | null;
};

export type AutomationOperations = {
  dueRules: number;
  pendingEvents: number;
  retryEvents: number;
  expiredLeases: number;
  deadLetterEvents: number;
  deadLetterExecutions: number;
  oldestPendingAt: string | null;
  schedulerPaused: boolean;
  alertLevel: "HEALTHY" | "WARNING" | "CRITICAL";
};

export type AutomationRecoveryResult = {
  id: string;
  source: "EXECUTION" | "EVENT";
  status: "RETRY_SCHEDULED" | "DISCARDED" | string;
};

export type CreateAutomationPayload = {
  name: string;
  triggerType: string;
  conditionJson?: string | null;
  actionType: string;
  actionConfigJson?: string | null;
  retryLimit: number;
};
