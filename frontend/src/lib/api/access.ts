import { apiFetch, normalizePayload } from "./client";
import type {
  AccessDecision,
  AccessDevice,
  AccessDevicePayload,
  AccessLog,
  AccessRequestPayload,
  ConsumeVisitPassPayload,
  CreateVisitPassPayload,
  EffectiveAccessBenefit,
  ManualOpenPayload,
  PhysicalClientState,
  PhysicalConnectorActivationCode,
  PhysicalDeviceLink,
  PhysicalIntegration,
  PhysicalIntegrationPayload,
  PhysicalPersonCandidate,
  TemporaryPass,
  TemporaryPassPayload,
  VisitPass,
} from "./access-types";

export async function getAccessDevices(
  accessToken: string,
): Promise<AccessDevice[]> {
  return apiFetch<AccessDevice[]>("/access/devices", accessToken);
}

export async function createAccessDevice(
  accessToken: string,
  payload: AccessDevicePayload,
): Promise<AccessDevice> {
  return apiFetch<AccessDevice>("/access/devices", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getDeviceRequiredBenefits(
  accessToken: string,
  deviceId: string,
): Promise<string[]> {
  return apiFetch<string[]>(
    `/access/devices/${deviceId}/benefits`,
    accessToken,
  );
}

export async function replaceDeviceRequiredBenefits(
  accessToken: string,
  deviceId: string,
  benefitIds: string[],
): Promise<string[]> {
  return apiFetch<string[]>(
    `/access/devices/${deviceId}/benefits`,
    accessToken,
    {
      method: "PUT",
      body: JSON.stringify(normalizePayload({ benefitIds })),
    },
  );
}

export async function getAccessLogs(
  accessToken: string,
  limit = 30,
): Promise<AccessLog[]> {
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

export async function getPhysicalIntegration(
  accessToken: string,
): Promise<PhysicalIntegration> {
  return apiFetch<PhysicalIntegration>(
    "/access/physical-integration",
    accessToken,
  );
}

export async function updatePhysicalIntegration(
  accessToken: string,
  payload: PhysicalIntegrationPayload,
): Promise<PhysicalIntegration> {
  return apiFetch<PhysicalIntegration>(
    "/access/physical-integration",
    accessToken,
    {
      method: "PUT",
      body: JSON.stringify(normalizePayload(payload)),
    },
  );
}

export async function getPhysicalClientState(
  accessToken: string,
  clientId: string,
): Promise<PhysicalClientState> {
  return apiFetch<PhysicalClientState>(
    `/access/physical-integration/clients/${clientId}`,
    accessToken,
  );
}

export async function linkPhysicalClient(
  accessToken: string,
  clientId: string,
  externalPersonRef: string,
): Promise<PhysicalClientState> {
  return apiFetch<PhysicalClientState>(
    `/access/physical-integration/clients/${clientId}/link`,
    accessToken,
    {
      method: "PUT",
      body: JSON.stringify(normalizePayload({ externalPersonRef })),
    },
  );
}

export async function queuePhysicalClientSynchronization(
  accessToken: string,
  clientId: string,
): Promise<PhysicalClientState> {
  return apiFetch<PhysicalClientState>(
    `/access/physical-integration/clients/${clientId}/synchronize`,
    accessToken,
    {
      method: "POST",
    },
  );
}

export async function reconcilePhysicalIntegration(
  accessToken: string,
): Promise<{ queuedClients: number }> {
  return apiFetch<{ queuedClients: number }>(
    "/access/physical-integration/reconcile",
    accessToken,
    { method: "POST" },
  );
}

export async function linkPhysicalDevice(
  accessToken: string,
  deviceId: string,
  externalDeviceRef: string,
  externalDeviceName?: string | null,
): Promise<PhysicalDeviceLink> {
  return apiFetch<PhysicalDeviceLink>(
    `/access/physical-integration/devices/${deviceId}/link`,
    accessToken,
    {
      method: "PUT",
      body: JSON.stringify(
        normalizePayload({ externalDeviceRef, externalDeviceName }),
      ),
    },
  );
}

export async function unlinkPhysicalDevice(
  accessToken: string,
  deviceId: string,
): Promise<void> {
  return apiFetch<void>(
    `/access/physical-integration/devices/${deviceId}/link`,
    accessToken,
    { method: "DELETE" },
  );
}

export async function issuePhysicalConnectorActivation(
  accessToken: string,
  purpose: "ACTIVATION" | "ROTATION",
): Promise<PhysicalConnectorActivationCode> {
  return apiFetch<PhysicalConnectorActivationCode>(
    "/access/physical-integration/connector/activation",
    accessToken,
    {
      method: "POST",
      body: JSON.stringify({ purpose }),
    },
  );
}

export async function revokePhysicalConnector(
  accessToken: string,
): Promise<void> {
  return apiFetch<void>(
    "/access/physical-integration/connector/revoke",
    accessToken,
    { method: "POST" },
  );
}

export async function getPhysicalPersonCandidates(
  accessToken: string,
): Promise<PhysicalPersonCandidate[]> {
  return apiFetch<PhysicalPersonCandidate[]>(
    "/access/physical-integration/candidates?limit=100",
    accessToken,
  );
}

export async function matchPhysicalPersonCandidate(
  accessToken: string,
  candidateId: string,
  clientId: string,
): Promise<PhysicalPersonCandidate> {
  return apiFetch<PhysicalPersonCandidate>(
    `/access/physical-integration/candidates/${candidateId}/match`,
    accessToken,
    {
      method: "PUT",
      body: JSON.stringify({ clientId }),
    },
  );
}

export async function rejectPhysicalPersonCandidate(
  accessToken: string,
  candidateId: string,
): Promise<PhysicalPersonCandidate> {
  return apiFetch<PhysicalPersonCandidate>(
    `/access/physical-integration/candidates/${candidateId}/reject`,
    accessToken,
    { method: "POST" },
  );
}

export async function getVisitPasses(
  accessToken: string,
  clientId?: string,
): Promise<VisitPass[]> {
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
  return apiFetch<VisitPass>(
    `/access/visit-passes/${visitPassId}/consume`,
    accessToken,
    {
      method: "POST",
      body: JSON.stringify(normalizePayload(payload)),
    },
  );
}
