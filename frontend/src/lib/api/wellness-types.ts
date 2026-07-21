export type ProfessionalSummary = {
  id: string;
  displayName: string;
  professionalType: string;
  email: string | null;
  phone: string | null;
  bio: string | null;
  status: "ACTIVE" | "INACTIVE" | string;
  createdAt: string;
  updatedAt: string;
};

export type ProfessionalServiceSummary = {
  id: string;
  professionalId: string;
  name: string;
  serviceType: string;
  durationMinutes: number;
  priceCents: number;
  currency: string;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type ProfessionalAvailability = {
  id: string;
  professionalId: string;
  dayOfWeek: number;
  opensAt: string;
  closesAt: string;
  status: string;
};

export type CreateProfessionalPayload = {
  displayName: string;
  professionalType: string;
  email?: string | null;
  phone?: string | null;
  bio?: string | null;
};

export type CreateProfessionalServicePayload = {
  name: string;
  serviceType: string;
  durationMinutes: number;
  priceCents: number;
  currency: string;
};

export type CreateProfessionalAvailabilityPayload = {
  dayOfWeek: number;
  opensAt: string;
  closesAt: string;
};
