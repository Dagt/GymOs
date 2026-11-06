
import { apiFetch } from "./client";

const path = "/access/manual-check-ins";

export type CheckInClient = { id: string; name: string; phone: string | null; status: string };

export type CheckInPreview = {
  client: CheckInClient;
  membership: { planName: string; endsOn: string; graceEndsOn: string } | null;
  membershipExpired: boolean;
  visitPackage: {
    name: string;
    status: string;
    remainingVisits: number;
    validFrom: string | null;
    validUntil: string | null;
    branchCovered: boolean;
    benefits: { id: string; name: string; mode: string }[];
  } | null;
  options: { id: string; name: string; source: "MEMBERSHIP" | "VISIT_PACKAGE" }[];
  denialReason: string | null;
};

export type CheckInEntry = {
  id: string;
  clientId: string;
  clientName: string;
  occurredAt: string;
  decision: "GRANTED" | "DENIED" | "DUPLICATE";
  reason: string;
  benefitName: string | null;
  remainingAfter: number | null;
  duplicateOf: string | null;
  reversal: { reason: string; remainingAfter: number | null } | null;
};

export type CheckInHistory = {
  branchId: string;
  branchName: string;
  timezone: string;
  entries: CheckInEntry[];
};

export type CheckInRequest = {
  key: string;
  branchId: string;
  clientId: string;
  benefitId: string | null;
  overrideOf?: string;
  overrideReason?: string;
};

export function searchCheckInClients(token: string, query: string) {
  return apiFetch<CheckInClient[]>(path + "/clients?" + new URLSearchParams({ query }), token);
}

export function previewCheckIn(token: string, clientId: string) {
  return apiFetch<CheckInPreview>(path + "/clients/" + clientId, token);
}

export function getCheckInHistory(token: string) {
  return apiFetch<CheckInHistory>(path, token);
}

export function registerCheckIn(token: string, { key, ...request }: CheckInRequest) {
  return apiFetch<CheckInEntry>(path, token, {
    method: "POST",
    headers: { "Idempotency-Key": key },
    body: JSON.stringify(request),
    signal: AbortSignal.timeout(15_000),
  });
}

export function reverseCheckIn(token: string, id: string, branchId: string, reason: string) {
  return apiFetch<CheckInEntry>(path + "/" + id + "/reversal", token, {
    method: "POST",
    body: JSON.stringify({ branchId, reason }),
    signal: AbortSignal.timeout(15_000),
  });
}
