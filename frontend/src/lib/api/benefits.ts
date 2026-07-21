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

export async function createBenefit(
  accessToken: string,
  payload: BenefitPayload,
): Promise<BenefitSummary> {
  return apiFetch<BenefitSummary>("/benefits", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
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
