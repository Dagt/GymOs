import { apiFetch, normalizePayload } from "./client";
import type {
  VisitPackageEntitlement,
  VisitPackagePurchasePayload,
  VisitPackagePurchaseResult,
  VisitPackageQuote,
} from "./visit-package-types";

export async function quoteVisitPackage(
  accessToken: string,
  clientId: string,
  packagePlanId: string,
): Promise<VisitPackageQuote> {
  return apiFetch<VisitPackageQuote>("/visit-packages/quotes", accessToken, {
    method: "POST",
    body: JSON.stringify({ clientId, packagePlanId }),
  });
}

export async function purchaseVisitPackage(
  accessToken: string,
  payload: VisitPackagePurchasePayload,
): Promise<VisitPackagePurchaseResult> {
  return apiFetch<VisitPackagePurchaseResult>("/visit-packages/purchases", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getCurrentVisitPackage(
  accessToken: string,
  clientId: string,
): Promise<VisitPackageEntitlement | null> {
  return apiFetch<VisitPackageEntitlement | null>(`/clients/${clientId}/visit-package`, accessToken);
}
