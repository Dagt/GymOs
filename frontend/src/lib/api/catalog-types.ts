export type ProductSummary = {
  id: string;
  sku: string;
  name: string;
  description: string | null;
  productType: "PHYSICAL" | "SERVICE" | "MEMBERSHIP_PLAN";
  priceCents: number;
  currency: string;
  taxable: boolean;
  status: "ACTIVE" | "INACTIVE";
  createdAt: string;
  updatedAt: string;
};

export type PlanSummary = {
  id: string;
  productId: string;
  sku: string;
  name: string;
  priceCents: number;
  currency: string;
  durationDays: number;
  graceDays: number;
  benefitsJson: string | null;
  multiBranchAccess: boolean;
  status: "ACTIVE" | "INACTIVE";
  createdAt: string;
  updatedAt: string;
};

export type ProductPayload = {
  sku: string;
  name: string;
  description?: string | null;
  productType: "PHYSICAL" | "SERVICE" | "MEMBERSHIP_PLAN";
  priceCents: number;
  currency: string;
  taxable: boolean;
  status: "ACTIVE" | "INACTIVE";
};

export type PlanPayload = {
  productId: string;
  durationDays: number;
  graceDays: number;
  benefitsJson?: string | null;
  multiBranchAccess?: boolean;
  status: "ACTIVE" | "INACTIVE";
};

export type MembershipPlanPayload = {
  sku: string;
  name: string;
  description?: string | null;
  priceCents: number;
  currency: string;
  durationDays: number;
  graceDays: number;
  benefits?: string | null;
  multiBranchAccess?: boolean;
  status: "ACTIVE" | "INACTIVE";
};

export type UpdateMembershipPlanPayload = Omit<MembershipPlanPayload, "sku">;
