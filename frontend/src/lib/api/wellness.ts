import { apiFetch, normalizePayload } from "./client";
import type {
  CreateProfessionalAvailabilityPayload,
  CreateProfessionalPayload,
  CreateProfessionalServicePayload,
  CreateWellnessAppointmentPayload,
  ProfessionalAvailability,
  ProfessionalServiceSummary,
  ProfessionalSummary,
  UpdateProfessionalPayload,
  UpdateProfessionalServicePayload,
  WellnessAppointment,
  WellnessAvailabilitySlot,
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

export async function updateProfessional(
  accessToken: string,
  professionalId: string,
  payload: UpdateProfessionalPayload,
): Promise<ProfessionalSummary> {
  return apiFetch<ProfessionalSummary>(`/professionals/${professionalId}`, accessToken, {
    method: "PUT",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function deleteProfessional(
  accessToken: string,
  professionalId: string,
): Promise<void> {
  return apiFetch<void>(`/professionals/${professionalId}`, accessToken, {
    method: "DELETE",
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

export async function updateProfessionalService(
  accessToken: string,
  professionalId: string,
  serviceId: string,
  payload: UpdateProfessionalServicePayload,
): Promise<ProfessionalServiceSummary> {
  return apiFetch<ProfessionalServiceSummary>(`/professionals/${professionalId}/services/${serviceId}`, accessToken, {
    method: "PUT",
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

export async function getProfessionalAvailabilitySlots(
  accessToken: string,
  professionalId: string,
  serviceId?: string,
  date?: string,
): Promise<WellnessAvailabilitySlot[]> {
  const params = new URLSearchParams();
  if (serviceId) params.append("serviceId", serviceId);
  if (date) params.append("date", date);
  const query = params.toString();
  return apiFetch<WellnessAvailabilitySlot[]>(
    `/professionals/${professionalId}/availability-slots${query ? `?${query}` : ""}`,
    accessToken,
  );
}

export async function createWellnessAppointment(
  accessToken: string,
  payload: CreateWellnessAppointmentPayload,
): Promise<WellnessAppointment> {
  return apiFetch<WellnessAppointment>("/wellness/appointments", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function getWellnessAppointments(
  accessToken: string,
  date?: string,
  professionalId?: string,
  status?: string,
): Promise<WellnessAppointment[]> {
  const params = new URLSearchParams();
  if (date) params.append("date", date);
  if (professionalId) params.append("professionalId", professionalId);
  if (status) params.append("status", status);
  const query = params.toString();
  return apiFetch<WellnessAppointment[]>(`/wellness/appointments${query ? `?${query}` : ""}`, accessToken);
}

export async function confirmWellnessAppointment(
  accessToken: string,
  appointmentId: string,
  payload?: { startsAt?: string; notes?: string },
): Promise<WellnessAppointment> {
  return apiFetch<WellnessAppointment>(`/wellness/appointments/${appointmentId}/confirm`, accessToken, {
    method: "POST",
    body: payload ? JSON.stringify(normalizePayload(payload)) : undefined,
  });
}

export async function completeWellnessAppointment(
  accessToken: string,
  appointmentId: string,
): Promise<WellnessAppointment> {
  return apiFetch<WellnessAppointment>(`/wellness/appointments/${appointmentId}/complete`, accessToken, {
    method: "POST",
  });
}

export async function cancelWellnessAppointment(
  accessToken: string,
  appointmentId: string,
  reason?: string,
): Promise<WellnessAppointment> {
  return apiFetch<WellnessAppointment>(`/wellness/appointments/${appointmentId}/cancel`, accessToken, {
    method: "POST",
    body: reason ? JSON.stringify({ reason }) : undefined,
  });
}
