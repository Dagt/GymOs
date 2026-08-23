import { apiFetch, normalizePayload } from "./client";
import type {
  PlatformBillingOverview,
  PlatformBillingPayment,
  PlatformBillingPaymentPayload,
  PlatformBillingPaymentRegistration,
  PlatformBillingUpdatePayload,
  PlatformBranchBilling,
} from "./platform-billing-types";

export async function getPlatformBillingOverview(accessToken: string): Promise<PlatformBillingOverview> {
  return apiFetch<PlatformBillingOverview>("/platform/billing/overview", accessToken);
}

export async function getPlatformBillingPayments(accessToken: string, branchId: string): Promise<PlatformBillingPayment[]> {
  return apiFetch<PlatformBillingPayment[]>(`/platform/billing/branches/${branchId}/payments`, accessToken);
}

export async function recordPlatformBillingPayment(
  accessToken: string,
  branchId: string,
  payload: PlatformBillingPaymentPayload,
): Promise<PlatformBillingPaymentRegistration> {
  return apiFetch<PlatformBillingPaymentRegistration>(`/platform/billing/branches/${branchId}/payments`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
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
