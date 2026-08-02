import type { RenewMembershipResponse } from "./membership-types";

export type KioskCapabilities = {
  membershipRenewalAllowed: boolean;
  membershipPlanChangeAllowed: boolean;
  resourceBookingAllowed: boolean;
  wellnessBookingAllowed: boolean;
  profileUpdateAllowed: boolean;
  autopayAllowed: boolean;
  onlineCardPaymentsReady: boolean;
  recurringCardPaymentsReady: boolean;
};

export type KioskProfile = {
  clientId: string;
  displayName: string;
  email: string | null;
  phone: string | null;
};

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
  capabilities: KioskCapabilities;
};

export type KioskSummaryResponse = {
  sessionId: string;
  expiresAt: string;
  clientName: string;
  profile: KioskProfile | null;
  capabilities: KioskCapabilities;
  memberships: KioskMembership[];
  pendingPayments: KioskPendingPayment[];
};

export type KioskSessionContext = {
  organizationId?: string | null;
  branchId?: string | null;
};

export type KioskMembershipCheckoutPayload = {
  idempotencyKey: string;
};

export type KioskPlanChangeCheckoutPayload = KioskMembershipCheckoutPayload & {
  targetPlanId: string;
};

export type KioskContactUpdatePayload = {
  email?: string | null;
  phone?: string | null;
};

export type KioskQuoteOperation = "ENROLLMENT" | "RENEWAL" | "PLAN_CHANGE";

export type KioskQuote = {
  id: string;
  membershipId: string | null;
  operationType: KioskQuoteOperation;
  planId: string;
  planName: string;
  listPriceCents: number;
  discountCents: number;
  totalCents: number;
  currency: string;
  benefitsJson: string;
  status: "ACTIVE" | "CONSUMED" | "EXPIRED" | "CANCELLED";
  expiresAt: string;
};

export type KioskPaymentIntent = {
  id: string;
  quoteId: string;
  provider: string;
  providerIntentId: string | null;
  amountCents: number;
  currency: string;
  status: "CREATED" | "PENDING" | "SUCCEEDED" | "FAILED" | "CANCELLED" | "EXPIRED";
  checkoutUrl: string | null;
  clientSecret: string | null;
  expiresAt: string | null;
  completedAt: string | null;
  lastError: string | null;
};

export type CreateKioskQuotePayload = {
  operationType: KioskQuoteOperation;
  membershipId?: string | null;
  planId?: string | null;
};

export type CreateKioskPaymentIntentPayload = {
  quoteId: string;
  idempotencyKey: string;
  successUrl: string;
  cancelUrl: string;
};

export type { RenewMembershipResponse };
