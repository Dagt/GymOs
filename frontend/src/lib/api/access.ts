import { apiFetch, normalizePayload } from "./client";
import type {
  AccessDecision,
  AccessDevice,
  AccessLog,
  AccessRequestPayload,
  ConsumeVisitPassPayload,
  CreateVisitPassPayload,
  EffectiveAccessBenefit,
  ManualOpenPayload,
  PhysicalClientState,
  PhysicalIntegration,
  PhysicalIntegrationPayload,
  TemporaryPass,
  TemporaryPassPayload,
  VisitPass,
} from "./access-types";

export async function getAccessDevices(accessToken: string): Promise<AccessDevice[]> {
  return apiFetch<AccessDevice[]>("/access/devices", accessToken);
}

export async function getAccessLogs(accessToken: string, limit = 30): Promise<AccessLog[]> {
  return apiFetch<AccessLog[]>(`/access/logs?limit=${limit}`, accessToken);
}

export async function getEffectiveAccessBenefits(
  accessToken: string,
  clientId: string,
): Promise<EffectiveAccessBenefit[]> {
  return apiFetch<EffectiveAccessBenefit[]>(
    `/access/clients/${clientId}/effective-benefits`,
    accessToken,
  );
}

export async function requestAccess(
  accessToken: string,
  payload: AccessRequestPayload,
): Promise<AccessDecision> {
  return apiFetch<AccessDecision>("/access/requests", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function manualOpenAccess(
  accessToken: string,
  payload: ManualOpenPayload,
): Promise<AccessDecision> {
  return apiFetch<AccessDecision>("/access/commands/open", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function createTemporaryPass(
  accessToken: string,
  payload: TemporaryPassPayload,
): Promise<TemporaryPass> {
  return apiFetch<TemporaryPass>("/access/commands/temporary", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getPhysicalIntegration(accessToken: string): Promise<PhysicalIntegration> {
  return apiFetch<PhysicalIntegration>("/access/physical-integration", accessToken);
}

export async function updatePhysicalIntegration(
  accessToken: string,
  payload: PhysicalIntegrationPayload,
): Promise<PhysicalIntegration> {
  return apiFetch<PhysicalIntegration>("/access/physical-integration", accessToken, {
    method: "PUT",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getPhysicalClientState(
  accessToken: string,
  clientId: string,
): Promise<PhysicalClientState> {
  return apiFetch<PhysicalClientState>(`/access/physical-integration/clients/${clientId}`, accessToken);
}

export async function linkPhysicalClient(
  accessToken: string,
  clientId: string,
  externalPersonRef: string,
): Promise<PhysicalClientState> {
  return apiFetch<PhysicalClientState>(`/access/physical-integration/clients/${clientId}/link`, accessToken, {
    method: "PUT",
    body: JSON.stringify(normalizePayload({ externalPersonRef })),
  });
}

export async function queuePhysicalClientSynchronization(
  accessToken: string,
  clientId: string,
): Promise<PhysicalClientState> {
  return apiFetch<PhysicalClientState>(`/access/physical-integration/clients/${clientId}/synchronize`, accessToken, {
    method: "POST",
  });
}

export async function getVisitPasses(accessToken: string, clientId?: string): Promise<VisitPass[]> {
  const params = new URLSearchParams();
  if (clientId) params.set("clientId", clientId);
  const suffix = params.toString() ? `?${params.toString()}` : "";
  return apiFetch<VisitPass[]>(`/access/visit-passes${suffix}`, accessToken);
}

export async function createVisitPass(
  accessToken: string,
  payload: CreateVisitPassPayload,
): Promise<VisitPass> {
  return apiFetch<VisitPass>("/access/visit-passes", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function consumeVisitPass(
  accessToken: string,
  visitPassId: string,
  payload: ConsumeVisitPassPayload = {},
): Promise<VisitPass> {
  return apiFetch<VisitPass>(`/access/visit-passes/${visitPassId}/consume`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}
