export type BenefitSummary = {
  id: string;
  branchId: string | null;
  code: string;
  name: string;
  benefitType: string;
  discountType: string;
  discountValueCents: number | null;
  discountPercent: number | null;
  requiresEvidence: boolean;
  combinable: boolean;
  priority: number;
  validFrom: string | null;
  validUntil: string | null;
  status: "ACTIVE" | "INACTIVE";
  planIds: string[];
  accessScope?: "LOCAL" | "PLAN_COVERAGE";
  applicablePlanIds?: string[];
  accessBranchIds?: string[];
  version?: number;
};

export type BenefitPayload = {
  branchId?: string | null;
  code: string;
  name: string;
  benefitType: "STUDENT" | "SENIOR" | "AGREEMENT" | "PROMOTION" | "ACCESS" | "CUSTOM";
  discountType: "PERCENT" | "FIXED" | "SPECIAL_PRICE" | "NONE";
  discountValueCents?: number | null;
  discountPercent?: number | null;
  requiresEvidence: boolean;
  combinable: boolean;
  priority: number;
  validFrom?: string | null;
  validUntil?: string | null;
  status: "ACTIVE" | "INACTIVE";
  planIds?: string[];
  accessScope?: "LOCAL" | "PLAN_COVERAGE";
  applicablePlanIds?: string[];
};

export type ClientBenefitSummary = {
  id: string;
  branchId: string | null;
  clientId: string;
  benefitId: string;
  benefitName: string;
  status: string;
  validFrom: string | null;
  validUntil: string | null;
  verifiedAt: string | null;
};

export type AssignClientBenefitPayload = {
  benefitId: string;
  branchId?: string | null;
  validFrom?: string | null;
  validUntil?: string | null;
  notes?: string | null;
};
