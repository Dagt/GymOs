import { apiFetch, normalizePayload } from "./client";
import type {
  BranchSummary,
  BranchUpdatePayload,
  CreateBranchPayload,
  CreateOrganizationPayload,
  ManagedOrganizationSummary,
  OrganizationSummary,
  OrganizationUpdatePayload,
} from "./organization-types";

export async function getCurrentOrganization(accessToken: string): Promise<OrganizationSummary> {
  return apiFetch<OrganizationSummary>("/organizations/current", accessToken);
}

export async function getOrganizations(accessToken: string): Promise<ManagedOrganizationSummary[]> {
  return apiFetch<ManagedOrganizationSummary[]>("/organizations", accessToken);
}

export async function updateOrganizationStatus(
  accessToken: string,
  organizationId: string,
  status: "ACTIVE" | "SUSPENDED" | "INACTIVE",
): Promise<ManagedOrganizationSummary> {
  return apiFetch<ManagedOrganizationSummary>(`/organizations/${organizationId}/status`, accessToken, {
    method: "PATCH",
    body: JSON.stringify({ status }),
  });
}

export async function createOrganization(
  accessToken: string,
  payload: CreateOrganizationPayload,
): Promise<ManagedOrganizationSummary> {
  return apiFetch<ManagedOrganizationSummary>("/organizations", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getBranches(accessToken: string): Promise<BranchSummary[]> {
  return apiFetch<BranchSummary[]>("/organizations/current/branches", accessToken);
}

export async function getAllOrganizationBranches(accessToken: string): Promise<BranchSummary[]> {
  return apiFetch<BranchSummary[]>("/organizations/current/branches/all", accessToken);
}

export async function getOrganizationBranches(
  accessToken: string,
  organizationId: string,
): Promise<BranchSummary[]> {
  return apiFetch<BranchSummary[]>(`/organizations/${organizationId}/branches`, accessToken);
}

export async function createBranch(
  accessToken: string,
  payload: CreateBranchPayload,
): Promise<BranchSummary> {
  return apiFetch<BranchSummary>("/organizations/current/branches", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function updateOrganization(
  accessToken: string,
  payload: OrganizationUpdatePayload,
): Promise<OrganizationSummary> {
  return apiFetch<OrganizationSummary>("/organizations/current", accessToken, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export async function updateBranch(
  accessToken: string,
  branchId: string,
  payload: BranchUpdatePayload,
): Promise<BranchSummary> {
  return apiFetch<BranchSummary>(`/organizations/current/branches/${branchId}`, accessToken, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export async function uploadOrganizationLogo(
  accessToken: string,
  file: File,
): Promise<OrganizationSummary> {
  const formData = new FormData();
  formData.append("file", file);
  return apiFetch<OrganizationSummary>("/organizations/current/logo", accessToken, {
    method: "POST",
    body: formData,
  });
}
