import { apiFetch, normalizePayload } from "./client";

export type CommunicationTemplate = {
  id: string; organizationId: string; branchId: string | null; code: string;
  channel: "EMAIL" | "WHATSAPP"; locale: string; subject: string | null; body: string;
  allowedVariablesJson: string; version: number; status: "DRAFT" | "ACTIVE" | "ARCHIVED";
  createdAt: string; updatedAt: string;
};

export type CreateCommunicationTemplate = {
  code: string; channel: "EMAIL" | "WHATSAPP"; locale?: string; subject?: string | null;
  body: string; allowedVariablesJson: string; status: "DRAFT" | "ACTIVE";
};

export type CommunicationPreference = {
  organizationId: string; clientId: string; emailOperationalEnabled: boolean;
  whatsappOperationalEnabled: boolean; emailMarketingEnabled: boolean;
  whatsappMarketingEnabled: boolean; consentVersion: string | null;
  consentedAt: string | null; revokedAt: string | null; createdAt: string | null; updatedAt: string | null;
};

export type DeliverySummary = { channel: string; status: string; total: number };

export type OutboundDelivery = {
  id: string; batchId: string; ruleId: string | null; workItemId: string | null; clientId: string;
  channel: "EMAIL" | "WHATSAPP"; recipient: string | null; subject: string | null; body: string;
  templateCode: string | null; templateVersion: number; communicationType: string; status: string;
  provider: string | null; providerMessageId: string | null; attemptCount: number;
  nextAttemptAt: string | null; acceptedAt: string | null; deliveredAt: string | null;
  failedAt: string | null; lastError: string | null; createdAt: string; updatedAt: string;
};

export async function getCommunicationTemplates(accessToken: string) {
  return apiFetch<CommunicationTemplate[]>("/communications/templates", accessToken);
}
export async function createCommunicationTemplate(accessToken: string, payload: CreateCommunicationTemplate) {
  return apiFetch<CommunicationTemplate>("/communications/templates", accessToken, { method: "POST", body: JSON.stringify(normalizePayload(payload)) });
}
export async function archiveCommunicationTemplate(accessToken: string, templateId: string) {
  await apiFetch<void>(`/communications/templates/${templateId}`, accessToken, { method: "DELETE" });
}
export async function getCommunicationPreference(accessToken: string, clientId: string) {
  return apiFetch<CommunicationPreference>(`/communications/clients/${clientId}/preferences`, accessToken);
}
export async function saveCommunicationPreference(accessToken: string, clientId: string, payload: Omit<CommunicationPreference, "organizationId" | "clientId" | "consentedAt" | "revokedAt" | "createdAt" | "updatedAt"> & { consented: boolean }) {
  return apiFetch<CommunicationPreference>(`/communications/clients/${clientId}/preferences`, accessToken, { method: "PUT", body: JSON.stringify(normalizePayload(payload)) });
}
export async function getCommunicationDeliverySummary(accessToken: string, days = 30) {
  return apiFetch<DeliverySummary[]>(`/communications/delivery-summary?days=${days}`, accessToken);
}
export async function getOutboundDeliveries(accessToken: string, filters: { channel?: string; status?: string; clientId?: string; ruleId?: string; limit?: number } = {}) {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => { if (value !== undefined && value !== "") params.set(key, String(value)); });
  return apiFetch<OutboundDelivery[]>(`/communications/deliveries?${params.toString()}`, accessToken);
}
export async function retryOutboundDelivery(accessToken: string, messageId: string) {
  await apiFetch<void>(`/communications/deliveries/${messageId}/retry`, accessToken, { method: "POST" });
}
export async function cancelOutboundDelivery(accessToken: string, messageId: string) {
  await apiFetch<void>(`/communications/deliveries/${messageId}/cancel`, accessToken, { method: "POST" });
}
