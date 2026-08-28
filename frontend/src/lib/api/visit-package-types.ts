export type VisitPackageQuote = {
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

export type VisitPackageEntitlement = {
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

export type VisitPackagePurchasePayload = {
  clientId: string;
  packagePlanId: string;
  method: "CASH" | "TRANSFER" | "CARD_MANUAL" | "CARD_MERCADO_PAGO";
  reference?: string | null;
  validateNow: boolean;
  notes?: string | null;
  idempotencyKey: string;
};

export type VisitPackagePurchaseResult = {
  entitlementId: string;
  saleId: string;
  paymentId: string | null;
  saleNumber: string;
  saleStatus: string;
  entitlementStatus: string;
  totalCents: number;
  paidCents: number;
  balanceCents: number;
  currency: string;
};
