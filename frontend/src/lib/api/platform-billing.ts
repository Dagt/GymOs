import { apiFetch, normalizePayload } from "./client";
import type {
  PlatformBillingOverview,
  PlatformBillingUpdatePayload,
  PlatformBranchBilling,
} from "./platform-billing-types";

export async function getPlatformBillingOverview(accessToken: string): Promise<PlatformBillingOverview> {
  return apiFetch<PlatformBillingOverview>("/platform/billing/overview", accessToken);
}

export async function updatePlatformBranchBilling(
  accessToken: string,
  branchId: string,
  payload: PlatformBillingUpdatePayload,
): Promise<PlatformBranchBilling> {
  return apiFetch<PlatformBranchBilling>(`/platform/billing/branches/${branchId}`, accessToken, {
    method: "PUT",
    body: JSON.stringify(normalizePayload(payload)),
  });
}
