import { publicApiFetch } from "./api/client";

export type KioskAutopayMandate = {
  id: string;
  membershipId: string | null;
  provider: string | null;
  status: "PENDING_METHOD" | "ACTIVE" | "PAUSED" | "REVOKED" | "FAILED";
  consentVersion: string;
  consentedAt: string;
  activatedAt: string | null;
  revokedAt: string | null;
  revocationReason: string | null;
  nextChargeAt: string | null;
  lastChargeAt: string | null;
  lastChargeStatus: string | null;
};

export type KioskAutopaySetup = {
  mandate: KioskAutopayMandate;
  provider: string;
  providerSetupId: string;
  checkoutUrl: string | null;
  clientSecret: string | null;
  expiresAt: string | null;
};

export async function getKioskAutopayReadiness(sessionId: string, sessionToken: string) {
  return publicApiFetch<{ ready: boolean }>(
    `/self-service/sessions/${sessionId}/autopay/readiness`,
    { headers: headers(sessionToken) },
  );
}

export async function getKioskAutopayMandates(sessionId: string, sessionToken: string) {
  return publicApiFetch<KioskAutopayMandate[]>(
    `/self-service/sessions/${sessionId}/autopay/mandates`,
    { headers: headers(sessionToken) },
  );
}

export async function startKioskAutopaySetup(
  sessionId: string,
  sessionToken: string,
  payload: {
    membershipId: string;
    consentAccepted: boolean;
    idempotencyKey: string;
    successUrl: string;
    cancelUrl: string;
  },
) {
  return publicApiFetch<KioskAutopaySetup>(
    `/self-service/sessions/${sessionId}/autopay/setup`,
    {
      method: "POST",
      headers: headers(sessionToken),
      body: JSON.stringify(payload),
    },
  );
}

export async function revokeKioskAutopayMandate(
  sessionId: string,
  sessionToken: string,
  mandateId: string,
  reason?: string,
) {
  return publicApiFetch<KioskAutopayMandate>(
    `/self-service/sessions/${sessionId}/autopay/mandates/${mandateId}`,
    {
      method: "DELETE",
      headers: headers(sessionToken),
      body: JSON.stringify({ reason: reason ?? null }),
    },
  );
}

function headers(sessionToken: string): HeadersInit {
  return { "X-Self-Service-Session-Token": sessionToken };
}
