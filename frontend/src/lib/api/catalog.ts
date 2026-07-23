import { apiFetch, normalizePayload } from "./client";
import type {
  MembershipPlanPayload,
  PlanPayload,
  PlanSummary,
  ProductPayload,
  ProductSummary,
  UpdateMembershipPlanPayload,
} from "./catalog-types";

export async function getProducts(accessToken: string): Promise<ProductSummary[]> {
  return apiFetch<ProductSummary[]>("/catalog/products", accessToken);
}

export async function createProduct(
  accessToken: string,
  payload: ProductPayload,
): Promise<ProductSummary> {
  return apiFetch<ProductSummary>("/catalog/products", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getPlans(accessToken: string): Promise<PlanSummary[]> {
  return apiFetch<PlanSummary[]>("/catalog/plans", accessToken);
}

export async function createPlan(
  accessToken: string,
  payload: PlanPayload,
): Promise<PlanSummary> {
  return apiFetch<PlanSummary>("/catalog/plans", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function createMembershipPlan(
  accessToken: string,
  payload: MembershipPlanPayload,
): Promise<PlanSummary> {
  return apiFetch<PlanSummary>("/catalog/membership-plans", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function updateMembershipPlan(
  accessToken: string,
  planId: string,
  payload: UpdateMembershipPlanPayload,
): Promise<PlanSummary> {
  return apiFetch<PlanSummary>(`/catalog/plans/${planId}`, accessToken, {
    method: "PATCH",
    body: JSON.stringify(normalizePayload(payload)),
  });
}
