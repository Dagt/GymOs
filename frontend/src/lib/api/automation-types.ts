export type NotificationLog = {
  id: string;
  channel: string;
  recipient: string | null;
  subject: string | null;
  body: string;
  status: string;
  triggerType: string | null;
  referenceType: string | null;
  referenceId: string | null;
  scheduledAt: string | null;
  sentAt: string | null;
  errorMessage: string | null;
  createdAt: string;
};

export type CreateNotificationPayload = {
  channel: string;
  recipient?: string | null;
  subject?: string | null;
  body: string;
  triggerType?: string | null;
  referenceType?: string | null;
  referenceId?: string | null;
  scheduledAt?: string | null;
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
  createdAt: string;
  updatedAt: string;
};

export type AutomationExecution = {
  id: string;
  ruleId: string;
  status: string;
  triggerReference: string | null;
  errorMessage: string | null;
  occurredAt: string;
};

export type CreateAutomationPayload = {
  name: string;
  triggerType: string;
  conditionJson?: string | null;
  actionType: string;
  actionConfigJson?: string | null;
  retryLimit: number;
};
