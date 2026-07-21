import type { BranchUpdatePayload, OrganizationUpdatePayload } from "./organization-types";

export type OnboardingChecklist = {
  organizationProfileComplete: boolean;
  branchConfigured: boolean;
  hasMembershipPlans: boolean;
  hasRetailProducts: boolean;
  hasKioskTerminal: boolean;
  hasClients: boolean;
  readyForPilot: boolean;
  activePlanCount: number;
  activeProductCount: number;
  clientCount: number;
  activeKioskCount: number;
};

export type PilotSetupPayload = {
  organization: OrganizationUpdatePayload;
  branch: BranchUpdatePayload;
  plans: Array<{
    sku: string;
    name: string;
    description?: string | null;
    priceCents: number;
    currency: string;
    durationDays: number;
    graceDays: number;
    benefitsJson?: string | null;
  }>;
  products: Array<{
    sku: string;
    name: string;
    description?: string | null;
    productType: "PHYSICAL" | "SERVICE";
    priceCents: number;
    currency: string;
    taxable: boolean;
  }>;
  kioskTerminalCode?: string | null;
  kioskName?: string | null;
};

export type PilotSetupResult = {
  checklist: OnboardingChecklist;
  productsCreated: number;
  plansCreated: number;
  kioskCreated: boolean;
};
