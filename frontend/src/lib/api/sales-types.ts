export type SaleStatus = "PENDING_PAYMENT" | "PARTIALLY_PAID" | "PAID" | "CANCELLED";
export type PaymentMethod = "CASH" | "TRANSFER" | "CARD_MANUAL" | "CARD_MERCADO_PAGO" | "CARD_ONLINE";
export type ReceptionPaymentMethod = Exclude<PaymentMethod, "CARD_ONLINE">;
export type PaymentStatus = "PENDING_VALIDATION" | "COMPLETED" | "REJECTED" | "CANCELLED";

export type SaleSummary = {
  id: string;
  saleNumber: string;
  clientId: string | null;
  clientName: string;
  status: SaleStatus;
  businessContext: "RETAIL_SALE" | "MEMBERSHIP_ENROLLMENT" | "MEMBERSHIP_RENEWAL" | "MEMBERSHIP_PLAN_CHANGE" | string;
  totalCents: number;
  paidCents: number;
  balanceCents: number;
  currency: string;
  soldAt: string;
};

export type SaleHeader = SaleSummary & {
  branchId: string;
  subtotalCents: number;
  discountCents: number;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
};

export type SaleItemDetail = {
  id: string;
  productId: string | null;
  itemType: string;
  name: string;
  quantity: number;
  unitPriceCents: number;
  discountCents: number;
  totalCents: number;
  refundedQuantity: number;
  createdAt: string;
};

export type AppliedBenefitDetail = {
  benefitId: string;
  code: string;
  name: string;
  discountCents: number;
  applicationOrder: number;
};

export type PaymentDetail = {
  id: string;
  method: PaymentMethod;
  status: PaymentStatus;
  amountCents: number;
  currency: string;
  reference: string | null;
  receiptObjectKey: string | null;
  receivedAt: string;
  validatedAt: string | null;
  notes: string | null;
};

export type RefundDetail = {
  id: string;
  saleId: string;
  paymentId: string | null;
  status: "REQUESTED" | "PROCESSED" | "REJECTED" | "CANCELLED" | string;
  amountCents: number;
  currency: string;
  reason: string;
  idempotencyKey: string | null;
  requestedBy: string | null;
  requestedAt: string;
  processedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type MembershipDetail = {
  id: string;
  clientId: string;
  planId: string;
  planName: string;
  status: "ACTIVE" | "EXPIRED" | "CANCELLED";
  activatedAt: string;
  startsOn: string;
  endsOn: string;
  graceEndsOn: string;
};

export type SaleDetailResponse = {
  sale: SaleHeader;
  operationPaymentId?: string | null;
  items: SaleItemDetail[];
  appliedBenefits: AppliedBenefitDetail[];
  payments: PaymentDetail[];
  refunds: RefundDetail[];
  memberships: MembershipDetail[];
};

export type SalesReportTotals = {
  currency: string;
  saleCount: number;
  subtotalCents: number;
  discountCents: number;
  totalCents: number;
  paidCents: number;
  balanceCents: number;
  paidSales: number;
  partiallyPaidSales: number;
  pendingSales: number;
};

export type PaymentReportTotals = {
  paymentCount: number;
  completedPayments: number;
  pendingPayments: number;
  rejectedPayments: number;
  completedCents: number;
  pendingCents: number;
  rejectedCents: number;
};

export type SaleStatusBreakdown = {
  status: SaleStatus;
  saleCount: number;
  totalCents: number;
  paidCents: number;
  balanceCents: number;
};

export type PaymentMethodBreakdown = {
  method: PaymentMethod;
  paymentCount: number;
  totalCents: number;
  completedCents: number;
  pendingCents: number;
  rejectedCents: number;
};

export type SaleBusinessContextBreakdown = {
  businessContext: SaleSummary["businessContext"];
  saleCount: number;
  subtotalCents: number;
  discountCents: number;
  totalCents: number;
  paidCents: number;
  balanceCents: number;
};

export type SalesReportResponse = {
  fromDate: string;
  toDate: string;
  currency: string;
  sales: SalesReportTotals;
  payments: PaymentReportTotals;
  salesByStatus: SaleStatusBreakdown[];
  salesByBusinessContext: SaleBusinessContextBreakdown[];
  paymentsByMethod: PaymentMethodBreakdown[];
  recentSales: SaleSummary[];
};

export type SaleItemPayload = {
  productId: string;
  quantity: number;
  discountCents: number;
};

export type ManualPaymentPayload = {
  method: ReceptionPaymentMethod;
  amountCents: number;
  reference?: string | null;
  receiptObjectKey?: string | null;
  validateNow: boolean;
  notes?: string | null;
  idempotencyKey?: string | null;
};

export type SaleRefundItemPayload = {
  saleItemId: string;
  quantity: number;
  restock: boolean;
};

export type SaleRefundPayload = {
  paymentId?: string | null;
  amountCents: number;
  reason: string;
  idempotencyKey?: string | null;
  items?: SaleRefundItemPayload[];
};

export type CreateSalePayload = {
  clientId?: string | null;
  items: SaleItemPayload[];
  discountCents: number;
  notes?: string | null;
  payment?: ManualPaymentPayload | null;
  idempotencyKey?: string | null;
};

export type MercadoPagoPointOrder = {
  operationId: string;
  providerOrderId: string;
  status: string;
};
