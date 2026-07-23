export type ResourceSummary = {
  id: string;
  name: string;
  resourceType: "ROOM" | "CLASS_AREA" | "EQUIPMENT" | string;
  capacity: number;
  slotMinutes: number;
  status: "ACTIVE" | "INACTIVE" | string;
  opensAt: string;
  closesAt: string;
  notes: string | null;
  allowedPlanIds: string[];
  allowedPlanNames: string;
};

export type BookingSummary = {
  id: string;
  resourceId: string;
  resourceName: string;
  clientId: string;
  clientName: string;
  status: "RESERVED" | "CONFIRMED" | "CHECKED_IN" | "COMPLETED" | "CANCELLED" | string;
  startsAt: string;
  endsAt: string;
  notes: string | null;
  bookingPaymentId: string | null;
  saleId: string | null;
  paymentStatus: "PENDING" | "PAID" | "EXPIRED" | "CANCELLED" | string | null;
  saleNumber: string | null;
};

export type AvailabilitySlot = {
  startsAt: string;
  endsAt: string;
  reserved: number;
  capacity: number;
  available: boolean;
};

export type CreateResourcePayload = {
  name: string;
  resourceType: string;
  capacity: number;
  slotMinutes: number;
  opensAt?: string | null;
  closesAt?: string | null;
  notes?: string | null;
  allowedPlanIds?: string[];
};

export type UpdateResourcePayload = CreateResourcePayload;

export type CreateBookingPayload = {
  resourceId: string;
  clientId: string;
  startsAt: string;
  endsAt: string;
  notes?: string | null;
};

export type LinkBookingPaymentPayload = {
  saleId: string;
  status: "PENDING" | "PAID" | "EXPIRED" | "CANCELLED";
  expiresAt?: string | null;
};

export type BookingTransition = "confirm" | "check-in" | "complete" | "cancel";
