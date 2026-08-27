import type {
  MembershipPlanPayload,
  PlanSummary,
  ProductPayload,
} from "@/lib/api";
import { normalizeCatalogUnit } from "@/features/catalog/catalog-units";

export const emptyProduct: ProductPayload = {
  sku: "",
  name: "",
  description: "",
  productType: "PHYSICAL",
  commercialSubtype: "GENERAL_PRODUCT",
  priceCents: 0,
  currency: "MXN",
  taxable: true,
  status: "ACTIVE",
  barcode: null,
  barcodes: [],
  brand: null,
  presentation: null,
  familyId: null,
  variantName: null,
  unitOfMeasure: "PIECE",
  inventoryTracked: true,
  displayOrder: 0,
  supplierName: null,
  referenceCostCents: null,
  promotionalPriceCents: null,
  promotionStartsAt: null,
  promotionEndsAt: null,
  categoryId: null,
  tags: [],
  version: null,
};

export function productToDraft(product: import("@/lib/api").ProductSummary): ProductPayload {
  return {
    sku: product.sku,
    name: product.name,
    description: product.description,
    productType: product.productType,
    commercialSubtype: product.commercialSubtype,
    priceCents: product.priceCents,
    currency: product.currency,
    taxable: product.taxable,
    status: product.status,
    barcode: product.barcode,
    barcodes: product.barcodes?.length ? product.barcodes : product.barcode ? [product.barcode] : [],
    brand: product.brand,
    presentation: product.presentation,
    familyId: product.familyId,
    variantName: product.variantName,
    unitOfMeasure: product.productType === "PHYSICAL"
      ? normalizeCatalogUnit(product.unitOfMeasure) ?? "PIECE"
      : null,
    inventoryTracked: product.inventoryTracked,
    displayOrder: product.displayOrder,
    supplierName: product.supplierName,
    referenceCostCents: product.referenceCostCents,
    promotionalPriceCents: product.promotionalPriceCents,
    promotionStartsAt: product.promotionStartsAt,
    promotionEndsAt: product.promotionEndsAt,
    categoryId: product.categoryId,
    tags: product.tags,
    version: product.version,
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
  const barcodes = [...new Set(form.barcodes.map((barcode) => barcode.trim()).filter(Boolean))];
  return {
    ...form,
    commercialSubtype: form.productType === "PHYSICAL" ? "GENERAL_PRODUCT" : "GENERAL_SERVICE",
    currency: form.currency.trim().toUpperCase() || "MXN",
    priceCents: Math.round(Number(price || "0") * 100),
    sku: form.sku.trim(),
    name: form.name.trim(),
    description: form.description?.trim() || null,
    barcode: barcodes[0] ?? null,
    barcodes,
    variantName: form.variantName?.trim() || null,
    unitOfMeasure: form.productType === "PHYSICAL"
      ? normalizeCatalogUnit(form.unitOfMeasure) ?? "PIECE"
      : null,
    supplierName: form.supplierName?.trim() || null,
    promotionStartsAt: normalizeDateTime(form.promotionStartsAt),
    promotionEndsAt: normalizeDateTime(form.promotionEndsAt),
  };
}

function normalizeDateTime(value?: string | null) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
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
  return new Intl.NumberFormat("es-MX", { style: "currency", currency }).format(cents / 100);
}

export function benefitsToText(value: string | null) {
  if (!value) return "";
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
