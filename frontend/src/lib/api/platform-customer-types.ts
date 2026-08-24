export type PlatformCustomerLifecycle =
  | "ONBOARDING"
  | "ACTIVE"
  | "AT_RISK"
  | "OFFBOARDING"
  | "FORMER";

export type PlatformCustomerHealth =
  | "HEALTHY"
  | "SETUP_REQUIRED"
  | "AT_RISK"
  | "SUSPENDED"
  | "OFFBOARDING"
  | "FORMER";

export type PlatformCustomerSummary = {
  customerCount: number;
  activeCustomerCount: number;
  onboardingCustomerCount: number;
  atRiskCustomerCount: number;
  suspendedCustomerCount: number;
  overdueBranchCount: number;
  followUpsDueCount: number;
  monthlyRecurringRevenue: number;
};

export type PlatformCustomer = {
  organizationId: string;
  tradeName: string;
  legalName: string;
  taxId: string | null;
  organizationStatus: string;
  contactEmail: string | null;
  contactPhone: string | null;
  brandName: string | null;
  logoUrl: string | null;
  branchCount: number;
  activeBranchCount: number;
  lifecycleStatus: PlatformCustomerLifecycle;
  accountOwner: string | null;
  billingEmail: string | null;
  billingPhone: string | null;
  contractStartedOn: string | null;
  renewalOn: string | null;
  paymentTermsDays: number;
  nextFollowUpOn: string | null;
  notes: string | null;
  version: number;
  updatedAt: string | null;
  activeMembers: number;
  monthlyRecurringRevenue: number;
  overdueBranchCount: number;
  unconfiguredBranchCount: number;
  implementationOutstanding: number;
  nextBillingDate: string | null;
  healthStatus: PlatformCustomerHealth;
};

export type PlatformCustomerPortfolio = {
  summary: PlatformCustomerSummary;
  customers: PlatformCustomer[];
};

export type PlatformCustomerUpdatePayload = {
  lifecycleStatus: PlatformCustomerLifecycle;
  accountOwner: string | null;
  billingEmail: string | null;
  billingPhone: string | null;
  contractStartedOn: string | null;
  renewalOn: string | null;
  paymentTermsDays: number;
  nextFollowUpOn: string | null;
  notes: string | null;
  expectedVersion: number;
};

export type PlatformCustomerCreatePayload = {
	legalName: string;
	tradeName: string;
	taxId: string;
	timezone: string;
	defaultCurrency: string;
	contactEmail: string;
	contactPhone: string;
	brandName: string;
	logoUrl: string;
	primaryColor: string;
	accentColor: string;
	surfaceColor: string;
	branchName: string;
	branchCode: string;
	branchPhone: string;
	ownerFirstName: string;
	ownerLastName: string;
	ownerEmail: string;
	ownerPhone: string;
};

export type ProvisionedPlatformCustomer = {
	customer: PlatformCustomer;
	ownerEmail: string;
	invitationUrl: string;
	invitationExpiresAt: string;
};
