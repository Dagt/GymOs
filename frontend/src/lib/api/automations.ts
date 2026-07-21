import { apiFetch, normalizePayload } from "./client";
import type {
  AutomationExecution,
  AutomationRule,
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
