import { apiFetch, normalizePayload } from "./client";
import type {
  AvailabilitySlot,
  BookingSummary,
  BookingTransition,
  CreateBookingPayload,
  CreateResourcePayload,
  LinkBookingPaymentPayload,
  ResourceSummary,
  UpdateResourcePayload,
} from "./booking-types";

export async function getResources(accessToken: string): Promise<ResourceSummary[]> {
  return apiFetch<ResourceSummary[]>("/resources", accessToken);
}

export async function createResource(
  accessToken: string,
  payload: CreateResourcePayload,
): Promise<ResourceSummary> {
  return apiFetch<ResourceSummary>("/resources", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function updateResource(
  accessToken: string,
  resourceId: string,
  payload: UpdateResourcePayload,
): Promise<ResourceSummary> {
  return apiFetch<ResourceSummary>(`/resources/${resourceId}`, accessToken, {
    method: "PUT",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function deleteResource(accessToken: string, resourceId: string): Promise<void> {
  await apiFetch<void>(`/resources/${resourceId}`, accessToken, {
    method: "DELETE",
  });
}

export async function getResourceAvailability(
  accessToken: string,
  resourceId: string,
  date?: string,
): Promise<AvailabilitySlot[]> {
  const suffix = date ? `?date=${encodeURIComponent(date)}` : "";
  return apiFetch<AvailabilitySlot[]>(`/resources/${resourceId}/availability${suffix}`, accessToken);
}

export async function getBookings(accessToken: string, date?: string): Promise<BookingSummary[]> {
  const suffix = date ? `?date=${encodeURIComponent(date)}` : "";
  return apiFetch<BookingSummary[]>(`/bookings${suffix}`, accessToken);
}

export async function createBooking(
  accessToken: string,
  payload: CreateBookingPayload,
): Promise<BookingSummary> {
  return apiFetch<BookingSummary>("/bookings", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function transitionBooking(
  accessToken: string,
  bookingId: string,
  action: BookingTransition,
): Promise<BookingSummary> {
  return apiFetch<BookingSummary>(`/bookings/${bookingId}/${action}`, accessToken, {
    method: "POST",
  });
}

export async function linkBookingPayment(
  accessToken: string,
  bookingId: string,
  payload: LinkBookingPaymentPayload,
): Promise<BookingSummary> {
  return apiFetch<BookingSummary>(`/bookings/${bookingId}/payments`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}
