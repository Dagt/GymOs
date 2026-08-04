import { apiFetch, normalizePayload, publicApiFetch } from "./client";
import type {
  KioskContactUpdatePayload,
  KioskAutopayMandate,
  KioskAutopaySetupResult,
  KioskAutopayTerms,
  KioskMembershipCheckoutPayload,
  KioskMembershipEnrollmentCheckoutPayload,
  KioskPlanOption,
  KioskMembershipQuote,
  KioskPlanChangeCheckoutPayload,
  KioskProfile,
  KioskSessionResponse,
  KioskSummaryResponse,
  RenewMembershipResponse,
} from "./selfservice-types";

export async function createKioskSession(
  terminalCode: string,
  accessToken: string,
): Promise<KioskSessionResponse> {
  return apiFetch<KioskSessionResponse>(`/self-service/terminals/${terminalCode}/sessions`, accessToken, {
    method: "POST",
  });
}

export async function launchKioskSession(accessToken: string): Promise<KioskSessionResponse> {
  return apiFetch<KioskSessionResponse>("/self-service/kiosk-sessions", accessToken, { method: "POST" });
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

export async function getKioskMembershipQuote(
  sessionId: string,
  sessionToken: string,
  planId: string,
): Promise<KioskMembershipQuote> {
  return publicApiFetch<KioskMembershipQuote>(`/self-service/sessions/${sessionId}/membership-quotes/${planId}`, {
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

export async function enrollMembershipFromKiosk(
  sessionId: string,
  sessionToken: string,
  payload: KioskMembershipEnrollmentCheckoutPayload,
): Promise<RenewMembershipResponse> {
  return publicApiFetch<RenewMembershipResponse>(
    `/self-service/sessions/${sessionId}/membership-enrollments`,
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

export async function resetKioskSession(
  sessionId: string,
  sessionToken: string,
): Promise<KioskSessionResponse> {
  return publicApiFetch<KioskSessionResponse>(`/self-service/sessions/${sessionId}/reset`, {
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

export async function getKioskAutopayTerms(
  sessionId: string,
  sessionToken: string,
  membershipId: string,
): Promise<KioskAutopayTerms> {
  return publicApiFetch<KioskAutopayTerms>(
    `/self-service/sessions/${sessionId}/autopay/${membershipId}/terms`,
    { headers: kioskSessionHeaders(sessionToken) },
  );
}

export async function getKioskAutopayMandate(
  sessionId: string,
  sessionToken: string,
  membershipId: string,
): Promise<KioskAutopayMandate | undefined> {
  return publicApiFetch<KioskAutopayMandate | undefined>(
    `/self-service/sessions/${sessionId}/autopay/${membershipId}`,
    { headers: kioskSessionHeaders(sessionToken) },
  );
}

export async function setupKioskAutopay(
  sessionId: string,
  sessionToken: string,
  membershipId: string,
  termsVersion: string,
  idempotencyKey: string,
  replaceExisting = false,
): Promise<KioskAutopaySetupResult> {
  return publicApiFetch<KioskAutopaySetupResult>(
    `/self-service/sessions/${sessionId}/autopay/${membershipId}/setup`,
    {
      method: "POST",
      headers: kioskSessionHeaders(sessionToken),
      body: JSON.stringify({ termsVersion, accepted: true, idempotencyKey, replaceExisting }),
    },
  );
}

export async function revokeKioskAutopay(
  sessionId: string,
  sessionToken: string,
  membershipId: string,
  reason: string,
): Promise<KioskAutopayMandate> {
  return publicApiFetch<KioskAutopayMandate>(
    `/self-service/sessions/${sessionId}/autopay/${membershipId}/revoke`,
    {
      method: "POST",
      headers: kioskSessionHeaders(sessionToken),
      body: JSON.stringify({ reason }),
    },
  );
}

function kioskSessionHeaders(sessionToken: string): HeadersInit {
  return {
    "X-Self-Service-Session-Token": sessionToken,
  };
}
