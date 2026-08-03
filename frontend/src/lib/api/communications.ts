import { apiFetch, normalizePayload } from "./client";

export type CommunicationTemplate = {
  id: string;
  organizationId: string;
  branchId: string | null;
  code: string;
  channel: "EMAIL" | "WHATSAPP";
  locale: string;
  subject: string | null;
  body: string;
  allowedVariablesJson: string;
  version: number;
  status: "DRAFT" | "ACTIVE" | "ARCHIVED";
  createdAt: string;
  updatedAt: string;
};

export type CreateCommunicationTemplate = {
  code: string;
  channel: "EMAIL" | "WHATSAPP";
  locale?: string;
  subject?: string | null;
  body: string;
  allowedVariablesJson: string;
  status: "DRAFT" | "ACTIVE";
};

export type CommunicationPreference = {
  organizationId: string;
  clientId: string;
  emailOperationalEnabled: boolean;
  whatsappOperationalEnabled: boolean;
  emailMarketingEnabled: boolean;
  whatsappMarketingEnabled: boolean;
  consentVersion: string | null;
  consentedAt: string | null;
  revokedAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type DeliverySummary = {
  channel: string;
  status: string;
  total: number;
};

export async function getCommunicationTemplates(accessToken: string) {
  return apiFetch<CommunicationTemplate[]>("/communications/templates", accessToken);
}

export async function createCommunicationTemplate(
  accessToken: string,
  payload: CreateCommunicationTemplate,
) {
  return apiFetch<CommunicationTemplate>("/communications/templates", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function archiveCommunicationTemplate(accessToken: string, templateId: string) {
  await apiFetch<void>(`/communications/templates/${templateId}`, accessToken, { method: "DELETE" });
}

export async function getCommunicationPreference(accessToken: string, clientId: string) {
  return apiFetch<CommunicationPreference>(`/communications/clients/${clientId}/preferences`, accessToken);
}

export async function saveCommunicationPreference(
  accessToken: string,
  clientId: string,
  payload: Omit<CommunicationPreference, "organizationId" | "clientId" | "consentedAt" | "revokedAt" | "createdAt" | "updatedAt"> & { consented: boolean },
) {
  return apiFetch<CommunicationPreference>(`/communications/clients/${clientId}/preferences`, accessToken, {
    method: "PUT",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getCommunicationDeliverySummary(accessToken: string, days = 30) {
  return apiFetch<DeliverySummary[]>(`/communications/delivery-summary?days=${days}`, accessToken);
}
