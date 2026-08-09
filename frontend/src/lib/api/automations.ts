import { apiFetch, normalizePayload } from "./client";
import type {
  AutomationDeadLetter,
  AutomationDeadLetterPage,
  AutomationExecution,
  AutomationExecutionPage,
  AutomationRecoveryResult,
  AutomationRule,
  CreateAutomationPayload,
  CreateNotificationPayload,
  NotificationLog,
  AutomationOperations,
  AutomationSchedulerStatus,
  EmailNotificationConfiguration,
  EmailRecipientPreference,
  UpdateEmailRecipientPreference,
  UpdateEmailNotificationConfiguration,
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

export async function getEmailNotificationConfiguration(
  accessToken: string,
): Promise<EmailNotificationConfiguration> {
  return apiFetch<EmailNotificationConfiguration>("/notifications/email-configuration", accessToken);
}

export async function updateEmailNotificationConfiguration(
  accessToken: string,
  payload: UpdateEmailNotificationConfiguration,
): Promise<EmailNotificationConfiguration> {
  return apiFetch<EmailNotificationConfiguration>("/notifications/email-configuration", accessToken, {
    method: "PUT",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function sendTestEmail(
  accessToken: string,
  recipient: string,
): Promise<NotificationLog> {
  return apiFetch<NotificationLog>("/notifications/email-configuration/test", accessToken, {
    method: "POST",
    body: JSON.stringify({ recipient }),
  });
}

export async function retryNotificationEmail(
  accessToken: string,
  notificationId: string,
): Promise<NotificationLog> {
  return apiFetch<NotificationLog>(`/notifications/${notificationId}/retry`, accessToken, { method: "POST" });
}

export async function getEmailRecipientPreference(
  accessToken: string,
  recipient: string,
): Promise<EmailRecipientPreference> {
  return apiFetch<EmailRecipientPreference>(
    `/notifications/email-preferences?recipient=${encodeURIComponent(recipient)}`,
    accessToken,
  );
}

export async function updateEmailRecipientPreference(
  accessToken: string,
  payload: UpdateEmailRecipientPreference,
): Promise<void> {
  await apiFetch<void>("/notifications/email-preferences", accessToken, {
    method: "PUT",
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
  page = 0,
  size = 25,
): Promise<AutomationExecutionPage> {
  return apiFetch<AutomationExecutionPage>(
    `/automations/${ruleId}/executions?page=${page}&size=${size}`,
    accessToken,
  );
}

export async function getAutomationDeadLetters(
  accessToken: string,
	page = 0,
	size = 25,
): Promise<AutomationDeadLetterPage> {
  return apiFetch<AutomationDeadLetterPage>(`/automations/dead-letters?page=${page}&size=${size}`, accessToken);
}

export async function getAutomationOperations(accessToken: string): Promise<AutomationOperations> {
  return apiFetch<AutomationOperations>("/automations/operations", accessToken);
}

export async function getAutomationScheduler(accessToken: string): Promise<AutomationSchedulerStatus> {
  return apiFetch<AutomationSchedulerStatus>("/automations/scheduler", accessToken);
}

export async function setAutomationScheduler(
  accessToken: string,
  paused: boolean,
  reason?: string,
): Promise<AutomationSchedulerStatus> {
  return apiFetch<AutomationSchedulerStatus>(`/automations/scheduler/${paused ? "pause" : "resume"}`, accessToken, {
    method: "POST",
    body: JSON.stringify({ reason: reason ?? null }),
  });
}

export async function recoverAutomationDeadLetter(
  accessToken: string,
  deadLetterId: string,
  action: "retry" | "discard",
  source: AutomationDeadLetter["source"],
  reason: string,
): Promise<AutomationRecoveryResult> {
  return apiFetch<AutomationRecoveryResult>(
    `/automations/dead-letters/${deadLetterId}/${action}`,
    accessToken,
    {
      method: "POST",
      body: JSON.stringify({ source, reason }),
    },
  );
}
