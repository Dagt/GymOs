import type {
  ChangeMembershipPlanPayload,
  RenewMembershipPayload,
  RenewMembershipResponse,
} from "./membership-types";

export type KioskMembership = {
  id: string;
  planId: string;
  planName: string;
  status: "ACTIVE" | "EXPIRED" | "CANCELLED";
  startsOn: string;
  endsOn: string;
  graceEndsOn: string;
};

export type KioskPendingPayment = {
  id: string;
  saleId: string;
  saleNumber: string;
  method: "CASH" | "TRANSFER" | "CARD_MANUAL";
  status: "PENDING_VALIDATION";
  amountCents: number;
  currency: string;
  receivedAt: string;
};

export type KioskPlanOption = {
  id: string;
  productId: string;
  name: string;
  priceCents: number;
  currency: string;
  durationDays: number;
  graceDays: number;
};

export type KioskSessionResponse = {
  sessionId: string;
  sessionToken: string;
  terminalName: string;
  organizationName: string | null;
  brandName: string | null;
  logoUrl: string | null;
  primaryColor: string | null;
  accentColor: string | null;
  surfaceColor: string | null;
  expiresAt: string;
  verified: boolean;
};

export type KioskSummaryResponse = {
  sessionId: string;
  expiresAt: string;
  clientName: string;
  memberships: KioskMembership[];
  pendingPayments: KioskPendingPayment[];
};

export type KioskSessionContext = {
  organizationId?: string | null;
  branchId?: string | null;
};

export type {
  ChangeMembershipPlanPayload,
  RenewMembershipPayload,
  RenewMembershipResponse,
};
