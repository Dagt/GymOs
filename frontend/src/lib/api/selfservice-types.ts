import type { RenewMembershipResponse } from "./membership-types";

export type KioskCapabilities = {
  clientRegistrationAllowed: boolean;
  membershipEnrollmentAllowed: boolean;
  visitPackagePurchaseAllowed: boolean;
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
  managedAtCurrentBranch: boolean;
  autopayManagedAtCurrentBranch?: boolean;
};

export type KioskPendingPayment = {
  id: string;
  saleId: string;
  saleNumber: string;
  method: "CASH" | "TRANSFER" | "CARD_MANUAL" | "CARD_ONLINE";
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

export type KioskMembershipQuote = {
  clientId: string;
  planId: string;
  planName: string;
  planPriceCents: number;
  registrationFeeCents: number;
  registrationFeeLabel: string | null;
  listPriceCents: number;
  discountCents: number;
  totalCents: number;
  currency: string;
  appliedBenefits: Array<{ benefitId: string; code: string; name: string; discountCents: number }>;
};

export type KioskVisitPackagePlan = {
  id: string;
  productId: string;
  sku: string;
  name: string;
  priceCents: number;
  currency: string;
  totalVisits: number;
  validityDays: number;
  branchScope: "ALL_BRANCHES" | "SELECTED_BRANCHES";
  branchIds: string[];
  benefitIds: string[];
  selfServiceAcquisitionEnabled: boolean;
  registrationFeePolicy: "DEFAULT" | "WAIVED";
};

export type KioskVisitPackageQuote = {
  clientId: string;
  packagePlanId: string;
  packageName: string;
  totalVisits: number;
  validityDays: number;
  packagePriceCents: number;
  registrationFeeCents: number;
  registrationFeeLabel: string | null;
  totalCents: number;
  currency: string;
  branchIds: string[];
  benefitIds: string[];
};

export type KioskVisitPackageEntitlement = {
  id: string;
  clientId: string;
  packagePlanId: string;
  saleId: string;
  packageName: string;
  status: "PENDING_PAYMENT" | "ACTIVE" | "CONSUMED" | "EXPIRED" | "CANCELLED";
  totalVisits: number;
  remainingVisits: number;
  validFrom: string | null;
  validUntil: string | null;
  pendingExpiresAt: string | null;
  branchIds: string[];
  benefitIds: string[];
  createdAt: string;
  updatedAt: string;
};

export type KioskVisitPackageCheckout = {
  entitlementId: string;
  saleId: string;
  paymentId: string;
  saleNumber: string;
  saleStatus: string;
  entitlementStatus: string;
  totalCents: number;
  paidCents: number;
  balanceCents: number;
  currency: string;
  providerOperationId: string | null;
  checkoutUrl: string | null;
  checkoutStatus: string | null;
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
  paymentActivity: KioskPaymentActivity[];
};

export type KioskPaymentActivity = {
  id: string;
  saleId: string;
  saleNumber: string;
  method: string;
  status: string;
  amountCents: number;
  currency: string;
  receivedAt: string;
  provider: string | null;
  receiptUrl: string | null;
  checkoutUrl: string | null;
  providerUpdatedAt: string | null;
};

export type KioskSessionContext = {
  organizationId?: string | null;
  branchId?: string | null;
};

export type KioskMembershipCheckoutPayload = {
  idempotencyKey: string;
};

export type KioskMembershipEnrollmentCheckoutPayload = KioskMembershipCheckoutPayload & {
  planId: string;
};

export type KioskPlanChangeCheckoutPayload = KioskMembershipCheckoutPayload & {
  targetPlanId: string;
};

export type KioskAutopayTerms = {
  termsVersion: string;
  consentText: string;
  amountRule: "CURRENT_MEMBERSHIP_RENEWAL_TOTAL";
  currentPriceCents: number;
  currency: string;
  nextChargeOn: string;
};

export type KioskAutopayMandate = {
  id: string;
  membershipId: string;
  planId: string;
  status: "PENDING_SETUP" | "ACTIVE" | "PAST_DUE" | "PAUSED" | "REVOKED" | "REPLACED";
  provider: "STRIPE";
  brand: string | null;
  lastFour: string | null;
  expiresMonth: number | null;
  expiresYear: number | null;
  termsVersion: string;
  amountRule: string;
  consentedAt: string;
  nextChargeOn: string;
  consecutiveFailures: number;
  lastError: string | null;
  revokedAt: string | null;
  canReplacePaymentMethod: boolean;
  recoveryMessage: string | null;
};

export type KioskAutopaySetupResult = {
  mandateId: string;
  status: string;
  providerOperationId: string | null;
  providerSessionId: string | null;
  checkoutUrl: string | null;
  termsVersion: string;
  nextChargeOn: string;
};

export type KioskContactUpdatePayload = {
  email?: string | null;
  phone?: string | null;
};

export type KioskRegistrationPayload = {
  firstName: string;
  lastName: string;
  maternalLastName?: string | null;
  birthDate: string;
  gender?: "MALE" | "FEMALE" | null;
  phone?: string | null;
  email?: string | null;
};

export type { RenewMembershipResponse };
