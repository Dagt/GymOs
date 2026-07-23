import type { MembershipDetail } from "./sales-types";

export type ClientMembershipSummary = MembershipDetail & {
  saleId: string;
  saleNumber: string;
};

export type RenewMembershipPayload = {
  method: "CASH" | "TRANSFER" | "CARD_MANUAL";
  reference?: string | null;
  validateNow: boolean;
  notes?: string | null;
  idempotencyKey?: string | null;
};

export type ChangeMembershipPlanPayload = RenewMembershipPayload & {
  targetPlanId: string;
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
};
