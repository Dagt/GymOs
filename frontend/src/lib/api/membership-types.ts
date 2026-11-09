import type { MembershipDetail } from "./sales-types";

export type ClientMembershipSummary = MembershipDetail & {
  saleId: string;
  saleNumber: string;
};

export type MembershipPeriodSummary = {
  id: string;
  membershipId: string;
  planId: string;
  planName: string;
  saleId: string;
  saleNumber: string;
  operationType: "LEGACY_IMPORT" | "ENROLLMENT" | "RENEWAL" | "PLAN_CHANGE";
  startsOn: string;
  endsOn: string;
  createdAt: string;
};

export type MembershipQuote = {
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
  appliedBenefits: {
    benefitId: string;
    code: string;
    name: string;
    discountCents: number;
  }[];
};

export type RenewMembershipPayload = {
  method: "CASH" | "TRANSFER" | "CARD_MANUAL" | "CARD_MERCADO_PAGO";
  reference?: string | null;
  validateNow: boolean;
  notes?: string | null;
  idempotencyKey?: string | null;
};

export type ChangeMembershipPlanPayload = RenewMembershipPayload & {
  targetPlanId: string;
};

export type EnrollMembershipPayload = RenewMembershipPayload & {
  clientId: string;
  planId: string;
};

export type RenewMembershipResponse = {
  saleId: string;
  saleNumber: string;
  saleStatus: "PENDING_PAYMENT" | "PARTIALLY_PAID" | "PAID" | "CANCELLED";
  totalCents: number;
  paidCents: number;
  balanceCents: number;
  currency: string;
  membership: ClientMembershipSummary | null;
  paymentId: string | null;
  providerOperationId?: string | null;
  checkoutUrl?: string | null;
  checkoutStatus?: string | null;
  providerSessionId?: string | null;
};
