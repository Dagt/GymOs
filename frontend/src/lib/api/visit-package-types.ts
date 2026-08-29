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
  benefits: Array<{
    benefitId: string;
    code: string;
    name: string;
    usageMode: "CONSUMES_VISIT" | "INCLUDED_WITH_VISIT";
    branchIds: string[];
  }>;
  consumptions: Array<{
    id: string;
    status: "CONSUMED" | "REVERSED";
    occurredAt: string;
    branchId: string;
    branchName: string;
    accessDeviceId: string | null;
    deviceName: string | null;
    remainingAfter: number;
  }>;
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
