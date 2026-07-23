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
  barcode: null,
  brand: null,
  presentation: null,
  unitOfMeasure: null,
  inventoryTracked: true,
  displayOrder: 0,
  imageObjectKey: null,
  categoryId: null,
  tags: [],
};

export function productToDraft(product: import("@/lib/api").ProductSummary): ProductPayload {
  return {
    sku: product.sku,
    name: product.name,
    description: product.description,
    productType: product.productType,
    priceCents: product.priceCents,
    currency: product.currency,
    taxable: product.taxable,
    status: product.status,
    barcode: product.barcode,
    brand: product.brand,
    presentation: product.presentation,
    unitOfMeasure: product.unitOfMeasure,
    inventoryTracked: product.inventoryTracked,
    displayOrder: product.displayOrder,
    imageObjectKey: product.imageObjectKey,
    categoryId: product.categoryId,
    tags: product.tags,
  };
}

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

export function productDraftToPayload(form: ProductPayload, price: string): ProductPayload {
  return {
    ...form,
    currency: form.currency.trim().toUpperCase() || "MXN",
    priceCents: Math.round(Number(price || "0") * 100),
    sku: form.sku.trim(),
    name: form.name.trim(),
    description: form.description?.trim() || null,
  };
}

export function planDraftToPayload(plan: PlanDraft): MembershipPlanPayload {
  return {
    sku: plan.sku.trim(),
    name: plan.name.trim(),
    description: plan.description.trim() || null,
    priceCents: Math.round(Number(plan.price || "0") * 100),
    currency: plan.currency.trim().toUpperCase() || "MXN",
    durationDays: Number(plan.durationDays || "30"),
    graceDays: Number(plan.graceDays || "0"),
    benefits: plan.benefitsJson.trim() || null,
    multiBranchAccess: plan.multiBranchAccess,
    status: plan.status,
  };
}

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
