import { apiFetch, normalizePayload, withIdempotency } from "./client";
import type {
  ChangeMembershipPlanPayload,
  ClientMembershipSummary,
  EnrollMembershipPayload,
  MembershipPeriodSummary,
  MembershipQuote,
  RenewMembershipPayload,
  RenewMembershipResponse,
} from "./membership-types";

export async function enrollMembership(
  accessToken: string,
  payload: EnrollMembershipPayload,
): Promise<RenewMembershipResponse> {
  return apiFetch<RenewMembershipResponse>("/membership-enrollments", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(withIdempotency(payload, "membership-enrollment", payload.clientId))),
  });
}

export async function getClientMemberships(
  accessToken: string,
  clientId: string,
): Promise<ClientMembershipSummary[]> {
  return apiFetch<ClientMembershipSummary[]>(`/clients/${clientId}/memberships`, accessToken);
}

export async function getMembershipPeriods(
  accessToken: string,
  membershipId: string,
): Promise<MembershipPeriodSummary[]> {
  return apiFetch<MembershipPeriodSummary[]>(`/memberships/${membershipId}/periods`, accessToken);
}

export async function getMembershipQuote(
  accessToken: string,
  clientId: string,
  planId: string,
): Promise<MembershipQuote> {
  return apiFetch<MembershipQuote>("/membership-quotes", accessToken, {
    method: "POST",
    body: JSON.stringify({ clientId, planId }),
  });
}

export async function renewMembership(
  accessToken: string,
  membershipId: string,
  payload: RenewMembershipPayload,
): Promise<RenewMembershipResponse> {
  return apiFetch<RenewMembershipResponse>(`/memberships/${membershipId}/renew`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(withIdempotency(payload, "membership-renewal", membershipId))),
  });
}

export async function changeMembershipPlan(
  accessToken: string,
  membershipId: string,
  payload: ChangeMembershipPlanPayload,
): Promise<RenewMembershipResponse> {
  return apiFetch<RenewMembershipResponse>(`/memberships/${membershipId}/change-plan`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(withIdempotency(payload, "membership-plan-change", membershipId))),
  });
}
