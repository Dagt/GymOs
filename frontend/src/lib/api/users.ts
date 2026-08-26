import { apiFetch, normalizePayload } from "./client";
import type { CreateInternalUserPayload, InternalUserSummary, RoleOption } from "./user-types";

export type PasswordRecoveryIssued = {
  resetUrl: string;
  expiresAt: string;
  emailDeliveryStatus: "SENT" | "FAILED" | "NOT_CONFIGURED" | "NO_EMAIL";
};

export function requestPasswordRecovery(accessToken: string, userId: string): Promise<PasswordRecoveryIssued> {
  return apiFetch(`/users/internal/${userId}/password-recovery`, accessToken, { method: "POST", cache: "no-store" });
}

export async function getUserRoles(accessToken: string): Promise<RoleOption[]> {
  return apiFetch<RoleOption[]>("/users/roles", accessToken);
}

export async function getInternalUsers(accessToken: string): Promise<InternalUserSummary[]> {
  return apiFetch<InternalUserSummary[]>("/users/internal", accessToken);
}

export async function createInternalUser(
  accessToken: string,
  payload: CreateInternalUserPayload,
): Promise<InternalUserSummary> {
  return apiFetch<InternalUserSummary>("/users/internal", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function updateInternalUserStatus(
  accessToken: string,
  userId: string,
  status: "ACTIVE" | "SUSPENDED" | "INACTIVE",
): Promise<InternalUserSummary> {
  return apiFetch<InternalUserSummary>(`/users/internal/${userId}/status`, accessToken, {
    method: "PATCH",
    body: JSON.stringify({ status }),
  });
}

export async function updateInternalUserRole(
  accessToken: string,
  userId: string,
  roleId: string,
): Promise<InternalUserSummary> {
  return apiFetch<InternalUserSummary>(`/users/internal/${userId}/role`, accessToken, {
    method: "PATCH",
    body: JSON.stringify({ roleId }),
  });
}
