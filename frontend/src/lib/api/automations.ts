import { apiFetch, normalizePayload } from "./client";
import type {
  ArchivedAutomationRule,
  AutomationExecution,
  AutomationPreview,
  AutomationRule,
  AutomationRuleRevision,
  AutomationWorkItem,
  AutomationWorkItemStatus,
  CreateAutomationPayload,
  CreateNotificationPayload,
  NotificationLog,
  UpdateAutomationPayload,
} from "./automation-types";

const DOMAIN_TRIGGERS = new Set([
  "MEMBERSHIP_EXPIRING",
  "MEMBERSHIP_EXPIRED",
  "MEMBERSHIP_CREATED",
  "MEMBERSHIP_RENEWED",
  "MEMBERSHIP_PLAN_CHANGED",
  "MEMBERSHIP_CANCELLED",
  "PAYMENT_COMPLETED",
  "PAYMENT_FAILED",
  "BOOKING_CREATED",
  "BOOKING_CANCELLED",
  "WELLNESS_APPOINTMENT_CREATED",
  "WELLNESS_APPOINTMENT_CANCELLED",
]);

export async function getNotifications(accessToken: string, limit = 80): Promise<NotificationLog[]> {
  return apiFetch<NotificationLog[]>(`/notifications?limit=${limit}`, accessToken);
}

export async function createNotification(
  accessToken: string,
  payload: CreateNotificationPayload,
): Promise<NotificationLog> {
  return apiFetch<NotificationLog>("/notifications", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getAutomations(accessToken: string): Promise<AutomationRule[]> {
  return apiFetch<AutomationRule[]>("/automations", accessToken);
}

export async function createAutomation(
  accessToken: string,
  payload: CreateAutomationPayload,
): Promise<AutomationRule> {
  const domainRule = DOMAIN_TRIGGERS.has(payload.triggerType);
  const endpoint = domainRule ? "/automations/domain-rules" : "/automations";
  const body = domainRule
    ? {
        name: payload.name,
        triggerType: payload.triggerType,
        conditionJson: payload.conditionJson,
        actionConfigJson: normalizeCustomerMessageConfig(payload.actionConfigJson, payload.name),
        retryLimit: payload.retryLimit,
      }
    : payload;
  return apiFetch<AutomationRule>(endpoint, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(body)),
  });
}

export async function updateAutomation(
  accessToken: string,
  ruleId: string,
  payload: UpdateAutomationPayload,
): Promise<AutomationRule> {
  return apiFetch<AutomationRule>(`/automations/${ruleId}`, accessToken, {
    method: "PUT",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function duplicateAutomation(accessToken: string, ruleId: string): Promise<AutomationRule> {
  return apiFetch<AutomationRule>(`/automations/${ruleId}/duplicate`, accessToken, { method: "POST" });
}

export async function archiveAutomation(accessToken: string, ruleId: string): Promise<void> {
  await apiFetch<void>(`/automations/${ruleId}`, accessToken, { method: "DELETE" });
}

export async function restoreAutomation(
  accessToken: string,
  ruleId: string,
): Promise<ArchivedAutomationRule> {
  return apiFetch<ArchivedAutomationRule>(`/automations/${ruleId}/restore`, accessToken, { method: "POST" });
}

export async function getAutomationVersions(
  accessToken: string,
  ruleId: string,
): Promise<AutomationRuleRevision[]> {
  return apiFetch<AutomationRuleRevision[]>(`/automations/${ruleId}/versions`, accessToken);
}

export async function previewAutomation(
  accessToken: string,
  ruleId: string,
  limit = 25,
): Promise<AutomationPreview> {
  return apiFetch<AutomationPreview>(`/automations/${ruleId}/preview?limit=${limit}`, accessToken);
}

export async function setAutomationStatus(
  accessToken: string,
  ruleId: string,
  action: "enable" | "disable",
): Promise<AutomationRule> {
  return apiFetch<AutomationRule>(`/automations/${ruleId}/${action}`, accessToken, {
    method: "POST",
  });
}

export async function executeAutomation(
  accessToken: string,
  ruleId: string,
  triggerReference?: string,
): Promise<AutomationExecution> {
  return apiFetch<AutomationExecution>(`/automations/${ruleId}/execute`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload({ triggerReference: triggerReference ?? null })),
  });
}

export async function getAutomationExecutions(
  accessToken: string,
  ruleId: string,
): Promise<AutomationExecution[]> {
  return apiFetch<AutomationExecution[]>(`/automations/${ruleId}/executions`, accessToken);
}

export async function getAutomationWorkItems(
  accessToken: string,
  filters: { ruleId?: string; status?: AutomationWorkItemStatus; limit?: number } = {},
): Promise<AutomationWorkItem[]> {
  const params = new URLSearchParams();
  if (filters.ruleId) params.set("ruleId", filters.ruleId);
  if (filters.status) params.set("status", filters.status);
  params.set("limit", String(filters.limit ?? 100));
  return apiFetch<AutomationWorkItem[]>(`/automations/work-items?${params.toString()}`, accessToken);
}

export async function getAutomationWorkItem(
  accessToken: string,
  workItemId: string,
): Promise<AutomationWorkItem> {
  return apiFetch<AutomationWorkItem>(`/automations/work-items/${workItemId}`, accessToken);
}

export async function retryAutomationWorkItem(
  accessToken: string,
  workItemId: string,
): Promise<AutomationWorkItem> {
  return apiFetch<AutomationWorkItem>(`/automations/work-items/${workItemId}/retry`, accessToken, {
    method: "POST",
  });
}

export async function cancelAutomationWorkItem(
  accessToken: string,
  workItemId: string,
): Promise<AutomationWorkItem> {
  return apiFetch<AutomationWorkItem>(`/automations/work-items/${workItemId}/cancel`, accessToken, {
    method: "POST",
  });
}

function normalizeCustomerMessageConfig(value: string | null | undefined, fallbackSubject: string) {
  let parsed: Record<string, unknown> = {};
  try {
    parsed = value ? JSON.parse(value) as Record<string, unknown> : {};
  } catch {
    parsed = {};
  }
  const channels = Array.isArray(parsed.channels)
    ? parsed.channels
    : ["EMAIL", "WHATSAPP"];
  return JSON.stringify({
    ...parsed,
    channel: undefined,
    channels,
    subject: typeof parsed.subject === "string" ? parsed.subject : fallbackSubject,
    templateCode: typeof parsed.templateCode === "string" ? parsed.templateCode : null,
    templateVersion: typeof parsed.templateVersion === "number" ? parsed.templateVersion : 1,
    communicationType: typeof parsed.communicationType === "string" ? parsed.communicationType : "OPERATIONAL",
  });
}
