import { apiFetch, normalizePayload } from "./client";
import type {
  CreateProfessionalAvailabilityPayload,
  CreateProfessionalPayload,
  CreateProfessionalServicePayload,
  ProfessionalAvailability,
  ProfessionalServiceSummary,
  ProfessionalSummary,
} from "./wellness-types";

export async function getProfessionals(accessToken: string): Promise<ProfessionalSummary[]> {
  return apiFetch<ProfessionalSummary[]>("/professionals", accessToken);
}

export async function createProfessional(
  accessToken: string,
  payload: CreateProfessionalPayload,
): Promise<ProfessionalSummary> {
  return apiFetch<ProfessionalSummary>("/professionals", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getProfessionalServices(
  accessToken: string,
  professionalId: string,
): Promise<ProfessionalServiceSummary[]> {
  return apiFetch<ProfessionalServiceSummary[]>(`/professionals/${professionalId}/services`, accessToken);
}

export async function createProfessionalService(
  accessToken: string,
  professionalId: string,
  payload: CreateProfessionalServicePayload,
): Promise<ProfessionalServiceSummary> {
  return apiFetch<ProfessionalServiceSummary>(`/professionals/${professionalId}/services`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getProfessionalAvailability(
  accessToken: string,
  professionalId: string,
): Promise<ProfessionalAvailability[]> {
  return apiFetch<ProfessionalAvailability[]>(`/professionals/${professionalId}/availability`, accessToken);
}

export async function createProfessionalAvailability(
  accessToken: string,
  professionalId: string,
  payload: CreateProfessionalAvailabilityPayload,
): Promise<ProfessionalAvailability> {
  return apiFetch<ProfessionalAvailability>(`/professionals/${professionalId}/availability`, accessToken, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}
