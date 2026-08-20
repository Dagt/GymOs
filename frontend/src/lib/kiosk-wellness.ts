import { publicApiFetch } from "./api/client";

export type KioskWellnessProfessional = {
  id: string;
  displayName: string;
  professionalType: string;
  bio: string | null;
  officeLocation: string | null;
  engagementType: "STAFF" | "EXTERNAL";
};

export type KioskWellnessService = {
  id: string;
  professionalId: string;
  name: string;
  serviceType: string;
  durationMinutes: number;
  priceCents: number;
  currency: string;
};

export type KioskWellnessAvailability = {
  startsAt: string;
  endsAt: string;
  available: boolean;
};

export type KioskWellnessAppointment = {
  id: string;
  professionalId: string;
  professionalName: string;
  serviceId: string;
  serviceName: string;
  priceCents: number;
  startsAt: string;
  endsAt: string;
  status: string;
};

export async function getKioskWellnessProfessionals(sessionId: string, sessionToken: string) {
  return publicApiFetch<KioskWellnessProfessional[]>(
    `/self-service/sessions/${sessionId}/wellness/professionals`,
    { headers: headers(sessionToken) },
  );
}

export async function getKioskWellnessServices(
  sessionId: string,
  sessionToken: string,
  professionalId: string,
) {
  return publicApiFetch<KioskWellnessService[]>(
    `/self-service/sessions/${sessionId}/wellness/professionals/${professionalId}/services`,
    { headers: headers(sessionToken) },
  );
}

export async function getKioskWellnessAvailability(
  sessionId: string,
  sessionToken: string,
  professionalId: string,
  serviceId: string,
  date: string,
) {
  const params = new URLSearchParams({ serviceId, date });
  return publicApiFetch<KioskWellnessAvailability[]>(
    `/self-service/sessions/${sessionId}/wellness/professionals/${professionalId}/availability?${params}`,
    { headers: headers(sessionToken) },
  );
}

export async function createKioskWellnessAppointment(
  sessionId: string,
  sessionToken: string,
  payload: { professionalId: string; serviceId: string; startsAt: string; notes?: string },
) {
  return publicApiFetch<KioskWellnessAppointment>(
    `/self-service/sessions/${sessionId}/wellness/appointments`,
    {
      method: "POST",
      headers: headers(sessionToken),
      body: JSON.stringify(payload),
    },
  );
}

function headers(sessionToken: string): HeadersInit {
  return { "X-Self-Service-Session-Token": sessionToken };
}
