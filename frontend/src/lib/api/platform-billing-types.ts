export type PlatformBillingSummary = {
  customerCount: number;
  branchCount: number;
  activeMemberCount: number;
  activeTeamUserCount: number;
  monthlyRecurringRevenue: number;
  overdueBranchCount: number;
  unconfiguredBranchCount: number;
};

export type PlatformBranchBilling = {
  organizationId: string;
  organizationName: string;
  organizationStatus: string;
  branchId: string;
  branchName: string;
  branchCode: string;
  branchStatus: string;
  activeMembers: number;
  activeTeamUserCount: number;
  activeMembershipPlanCount: number;
  suggestedPlanCode: string;
  suggestedPlanName: string;
  suggestedMonthlyPrice: number | null;
  contractedPlanCode: string;
  contractedPlanName: string;
  monthlyAmount: number | null;
  subscriptionStatus: string;
  paymentStatus: string;
  billingAnchorDay: number | null;
  nextBillingDate: string | null;
  lastPaymentDate: string | null;
  lastPaymentAmount: number | null;
  monthlyPriceOverride: number | null;
  implementationFee: number;
  implementationPaid: boolean;
  notes: string | null;
};

export type PlatformBillingOverview = {
  summary: PlatformBillingSummary;
  branches: PlatformBranchBilling[];
};

export type PlatformBillingUpdatePayload = {
  subscriptionStatus: "ACTIVE" | "PENDING" | "PAUSED" | "CANCELLED";
  billingAnchorDay: number;
  nextBillingDate: string;
  lastPaymentDate: string | null;
  lastPaymentAmount: number | null;
  contractedPlanCode: "GYMFORT_150" | "GYMFORT_500" | "GYMFORT_1000" | "GYMFORT_2000" | "ENTERPRISE";
  monthlyPriceOverride: number | null;
  implementationFee: number;
  implementationPaid: boolean;
  notes: string | null;
};

export type PlatformBillingPayment = {
  id: string;
  branchId: string;
  organizationId: string;
  paymentType: "SUBSCRIPTION" | "IMPLEMENTATION" | "OTHER";
  paymentMethod: "TRANSFER" | "CASH" | "CARD" | "STRIPE" | "OTHER";
  amount: number;
  paidOn: string;
  reference: string | null;
  notes: string | null;
  createdAt: string;
};

export type PlatformBillingPaymentPayload = {
  paymentType: PlatformBillingPayment["paymentType"];
  paymentMethod: PlatformBillingPayment["paymentMethod"];
  amount: number;
  paidOn: string;
  reference: string | null;
  notes: string | null;
};

export type PlatformBillingPaymentRegistration = {
  branch: PlatformBranchBilling;
  payment: PlatformBillingPayment;
};
