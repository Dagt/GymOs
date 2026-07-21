import { apiFetch, normalizePayload, withIdempotency } from "./client";
import type {
  ChangeMembershipPlanPayload,
  ClientMembershipSummary,
  RenewMembershipPayload,
  RenewMembershipResponse,
} from "./membership-types";

export async function getClientMemberships(
  accessToken: string,
  clientId: string,
): Promise<ClientMembershipSummary[]> {
  return apiFetch<ClientMembershipSummary[]>(`/clients/${clientId}/memberships`, accessToken);
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
