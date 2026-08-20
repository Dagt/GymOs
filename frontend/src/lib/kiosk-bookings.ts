const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

export type KioskBookingResource = {
  id: string;
  name: string;
  slotMinutes: number;
  opensAt?: string | null;
  closesAt?: string | null;
  allowedPlanNames?: string | null;
};

export type KioskAvailabilitySlot = {
  startsAt: string;
  endsAt: string;
  reserved: number;
  capacity: number;
  available: boolean;
};

function headers(sessionToken: string): HeadersInit {
  return {
    "Content-Type": "application/json",
    "X-Self-Service-Session-Token": sessionToken,
  };
}

async function request<T>(path: string, sessionToken: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: { ...headers(sessionToken), ...(init.headers ?? {}) },
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => null) as { message?: string } | null;
    throw new Error(payload?.message || "No fue posible completar la operación.");
  }
  return response.json() as Promise<T>;
}

export function getKioskBookingResources(sessionId: string, sessionToken: string, date: string) {
  const params = new URLSearchParams({ date });
  return request<KioskBookingResource[]>(`/self-service/sessions/${sessionId}/booking-resources?${params.toString()}`, sessionToken);
}

export function getKioskBookingAvailability(sessionId: string, sessionToken: string, resourceId: string, date: string) {
  const params = new URLSearchParams({ resourceId, date });
  return request<KioskAvailabilitySlot[]>(`/self-service/sessions/${sessionId}/booking-availability?${params.toString()}`, sessionToken);
}

export function createKioskBooking(sessionId: string, sessionToken: string, payload: { resourceId: string; startsAt: string; endsAt: string; notes?: string }) {
  return request(`/self-service/sessions/${sessionId}/bookings`, sessionToken, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}
