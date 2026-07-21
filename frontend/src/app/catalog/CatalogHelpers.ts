import type {
  MembershipPlanPayload,
  PlanSummary,
  ProductPayload,
} from "@/lib/api";

export const emptyProduct: ProductPayload = {
  sku: "",
  name: "",
  description: "",
  productType: "PHYSICAL",
  priceCents: 0,
  currency: "MXN",
  taxable: true,
  status: "ACTIVE",
};

export type PlanDraft = {
  sku: string;
  name: string;
  description: string;
  price: string;
  currency: string;
  durationDays: string;
  graceDays: string;
  benefitsJson: string;
  multiBranchAccess: boolean;
  status: MembershipPlanPayload["status"];
};

export const emptyPlan: PlanDraft = {
  sku: "",
  name: "",
  description: "",
  price: "",
  currency: "MXN",
  durationDays: "30",
  graceDays: "0",
  benefitsJson: "",
  multiBranchAccess: false,
  status: "ACTIVE",
};

export function planToDraft(plan: PlanSummary): PlanDraft {
  return {
    sku: plan.sku,
    name: plan.name,
    description: "",
    price: String(plan.priceCents / 100),
    currency: plan.currency,
    durationDays: String(plan.durationDays),
    graceDays: String(plan.graceDays),
    benefitsJson: benefitsToText(plan.benefitsJson),
    multiBranchAccess: plan.multiBranchAccess,
    status: plan.status,
  };
}

export function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency,
  }).format(cents / 100);
}

export function benefitsToText(value: string | null) {
  if (!value) {
    return "";
  }
  try {
    const parsed = JSON.parse(value) as { included?: unknown };
    if (Array.isArray(parsed.included)) {
      return parsed.included.filter((item): item is string => typeof item === "string").join(", ");
    }
  } catch {
    return value;
  }
  return value;
}
