export type ProfessionalSummary = {
  id: string;
  displayName: string;
  professionalType: string;
  email: string | null;
  phone: string | null;
  bio: string | null;
  officeLocation: string | null;
  engagementType: "STAFF" | "EXTERNAL";
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

export type WellnessAvailabilitySlot = {
  startsAt: string;
  endsAt: string;
  available: boolean;
};

export type WellnessAppointment = {
  id: string;
  organizationId: string;
  branchId: string;
  professionalId: string;
  professionalName: string;
  professionalType: string;
  officeLocation: string | null;
  professionalEngagementType: "STAFF" | "EXTERNAL";
  serviceId: string | null;
  serviceName: string | null;
  durationMinutes: number;
  priceCents: number;
  clientId: string;
  clientFullName: string;
  clientEmail: string | null;
  clientPhone: string | null;
  startsAt: string;
  endsAt: string;
  status: "REQUESTED" | "CONFIRMED" | "COMPLETED" | "CANCELLED" | "REJECTED" | string;
  notes: string | null;
  cancellationReason: string | null;
  confirmedByUserId: string | null;
  confirmedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateProfessionalPayload = {
  displayName: string;
  professionalType: string;
  email?: string | null;
  phone?: string | null;
  bio?: string | null;
  officeLocation?: string | null;
  engagementType: "STAFF" | "EXTERNAL";
};

export type UpdateProfessionalPayload = {
  displayName: string;
  professionalType: string;
  email?: string | null;
  phone?: string | null;
  bio?: string | null;
  officeLocation?: string | null;
  engagementType?: "STAFF" | "EXTERNAL";
  status?: string;
};

export type CreateProfessionalServicePayload = {
  name: string;
  serviceType: string;
  durationMinutes: number;
  priceCents: number;
  currency: string;
};

export type UpdateProfessionalServicePayload = {
  name: string;
  serviceType: string;
  durationMinutes: number;
  priceCents: number;
  currency: string;
  status?: string;
};

export type CreateProfessionalAvailabilityPayload = {
  dayOfWeek: number;
  opensAt: string;
  closesAt: string;
};

export type CreateWellnessAppointmentPayload = {
  professionalId: string;
  serviceId?: string;
  clientId: string;
  startsAt: string;
  notes?: string;
};
