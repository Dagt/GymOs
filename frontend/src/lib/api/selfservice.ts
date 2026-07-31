import { normalizePayload, publicApiFetch } from "./client";
import type {
  KioskContactUpdatePayload,
  KioskMembershipCheckoutPayload,
  KioskPlanOption,
  KioskPlanChangeCheckoutPayload,
  KioskProfile,
  KioskSessionContext,
  KioskSessionResponse,
  KioskSummaryResponse,
  RenewMembershipResponse,
} from "./selfservice-types";

export async function createKioskSession(
  terminalCode: string,
  context: KioskSessionContext = {},
): Promise<KioskSessionResponse> {
  const headers: HeadersInit = {};
  if (context.organizationId && context.branchId) {
    headers["X-GymOS-Organization-Id"] = context.organizationId;
    headers["X-GymOS-Branch-Id"] = context.branchId;
  }
  return publicApiFetch<KioskSessionResponse>(`/self-service/terminals/${terminalCode}/sessions`, {
    method: "POST",
    headers,
  });
}

export async function verifyKioskSession(
  sessionId: string,
  sessionToken: string,
  identifier: string,
  birthDate: string,
): Promise<KioskSummaryResponse> {
  return publicApiFetch<KioskSummaryResponse>(`/self-service/sessions/${sessionId}/verify`, {
    method: "POST",
    headers: kioskSessionHeaders(sessionToken),
    body: JSON.stringify({ identifier, birthDate }),
  });
}

export async function getKioskSummary(
  sessionId: string,
  sessionToken: string,
): Promise<KioskSummaryResponse> {
  return publicApiFetch<KioskSummaryResponse>(`/self-service/sessions/${sessionId}/summary`, {
    headers: kioskSessionHeaders(sessionToken),
  });
}

export async function getKioskMembershipOptions(
  sessionId: string,
  sessionToken: string,
): Promise<KioskPlanOption[]> {
  return publicApiFetch<KioskPlanOption[]>(`/self-service/sessions/${sessionId}/membership-options`, {
    headers: kioskSessionHeaders(sessionToken),
  });
}

export async function renewMembershipFromKiosk(
  sessionId: string,
  sessionToken: string,
  membershipId: string,
  payload: KioskMembershipCheckoutPayload,
): Promise<RenewMembershipResponse> {
  return publicApiFetch<RenewMembershipResponse>(
    `/self-service/sessions/${sessionId}/membership-renewals/${membershipId}`,
    {
      method: "POST",
      headers: kioskSessionHeaders(sessionToken),
      body: JSON.stringify(normalizePayload(payload)),
    },
  );
}

export async function changeMembershipPlanFromKiosk(
  sessionId: string,
  sessionToken: string,
  membershipId: string,
  payload: KioskPlanChangeCheckoutPayload,
): Promise<RenewMembershipResponse> {
  return publicApiFetch<RenewMembershipResponse>(
    `/self-service/sessions/${sessionId}/membership-change-requests/${membershipId}`,
    {
      method: "POST",
      headers: kioskSessionHeaders(sessionToken),
      body: JSON.stringify(normalizePayload(payload)),
    },
  );
}

export async function endKioskSession(sessionId: string, sessionToken: string): Promise<void> {
  await publicApiFetch<void>(`/self-service/sessions/${sessionId}/end`, {
    method: "POST",
    headers: kioskSessionHeaders(sessionToken),
  });
}

export async function updateKioskContactData(
  sessionId: string,
  sessionToken: string,
  payload: KioskContactUpdatePayload,
): Promise<KioskProfile> {
  return publicApiFetch<KioskProfile>(`/self-service/sessions/${sessionId}/contact-data`, {
    method: "PATCH",
    headers: kioskSessionHeaders(sessionToken),
    body: JSON.stringify(normalizePayload(payload)),
  });
}

function kioskSessionHeaders(sessionToken: string): HeadersInit {
  return {
    "X-Self-Service-Session-Token": sessionToken,
  };
}
