import { apiFetch, normalizePayload } from "./client";
import type {
  AutomationExecution,
  AutomationRule,
  AutomationWorkItem,
  AutomationWorkItemStatus,
  CreateAutomationPayload,
  CreateNotificationPayload,
  NotificationLog,
} from "./automation-types";

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
  return apiFetch<AutomationRule>("/automations", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
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
