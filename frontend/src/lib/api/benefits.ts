import { apiFetch, normalizePayload } from "./client";
import type {
  AssignClientBenefitPayload,
  BenefitPayload,
  BenefitSummary,
  ClientBenefitSummary,
} from "./benefit-types";

export async function getBenefits(accessToken: string): Promise<BenefitSummary[]> {
  return apiFetch<BenefitSummary[]>("/benefits", accessToken);
}

export async function getCatalogAccessBenefits(accessToken: string): Promise<BenefitSummary[]> {
  return apiFetch<BenefitSummary[]>("/catalog/access-benefits", accessToken);
}

export async function getClientBenefits(
  accessToken: string,
  clientId: string,
): Promise<ClientBenefitSummary[]> {
  return apiFetch<ClientBenefitSummary[]>(`/clients/${clientId}/benefits`, accessToken);
}

export async function createBenefit(
  accessToken: string,
  payload: BenefitPayload,
): Promise<BenefitSummary> {
  return apiFetch<BenefitSummary>("/benefits", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function updateBenefit(accessToken: string, benefitId: string, payload: BenefitPayload, expectedVersion: number): Promise<BenefitSummary> {
  const { name, discountType, discountValueCents, discountPercent, requiresEvidence, combinable, priority, validFrom, validUntil, status, planIds, accessScope, applicablePlanIds } = payload;
  return apiFetch<BenefitSummary>(`/benefits/${benefitId}`, accessToken, {
    method: "PUT",
    body: JSON.stringify(normalizePayload({ name, discountType, discountValueCents, discountPercent, requiresEvidence, combinable, priority, validFrom, validUntil, status, planIds, accessScope, applicablePlanIds, expectedVersion })),
  });
}

export async function assignClientBenefit(
  accessToken: string,
  clientId: string,
  payload: AssignClientBenefitPayload,
): Promise<ClientBenefitSummary> {
  return apiFetch<ClientBenefitSummary>(`/clients/${clientId}/benefits`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function verifyClientBenefit(
  accessToken: string,
  clientBenefitId: string,
): Promise<ClientBenefitSummary> {
  return apiFetch<ClientBenefitSummary>(`/client-benefits/${clientBenefitId}/verify`, accessToken, {
    method: "POST",
  });
}
