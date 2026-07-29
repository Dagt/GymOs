import type { ProductSummary } from "@/lib/api";
import type { CartItem } from "./PosSections";

export type PosDensityConfig = {
  density: "compact";
  dataDenseAdmin: true;
  compactCartLayout: true;
};

export function getPosDensityConfig(): PosDensityConfig {
  return {
    density: "compact",
    dataDenseAdmin: true,
    compactCartLayout: true,
  };
}

export function calculateSubtotal(cart: CartItem[]): number {
  return cart.reduce((sum, item) => sum + item.product.priceCents * item.quantity, 0);
}

export function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency,
  }).format(cents / 100);
}

export function findProductByBarcodeOrSku(
  products: ProductSummary[],
  code: string,
): ProductSummary | null {
  const normalizedCode = code.trim().toLowerCase();
  if (!normalizedCode) return null;

  // 1. First priority: exact barcode match
  const byBarcode = products.find(
    (product) => product.barcode && product.barcode.trim().toLowerCase() === normalizedCode,
  );
  if (byBarcode) return byBarcode;

  // 2. Second priority: exact SKU match
  const bySku = products.find(
    (product) => product.sku && product.sku.trim().toLowerCase() === normalizedCode,
  );
  if (bySku) return bySku;

  return null;
}

export function buildRenewalProduct(
  membership: { id: string; planId: string; planName: string; priceCents?: number; currency?: string },
  catalogProducts: ProductSummary[],
): ProductSummary {
  const catalogMatch = catalogProducts.find(
    (p) => p.id === membership.planId || p.name.toLowerCase() === membership.planName.toLowerCase(),
  );

  if (catalogMatch) return catalogMatch;

  return {
    id: membership.planId,
    name: `Renovación: ${membership.planName}`,
    sku: `REN-${membership.planName.slice(0, 6).toUpperCase()}`,
    description: "Renovación de membresía desde POS",
    productType: "MEMBERSHIP_PLAN",
    commercialSubtype: "MEMBERSHIP",
    priceCents: membership.priceCents ?? 80000,
    currency: membership.currency ?? "MXN",
    taxable: false,
    status: "ACTIVE",
    barcode: null,
    brand: null,
    presentation: null,
    unitOfMeasure: null,
    inventoryTracked: false,
    displayOrder: 0,
    imageObjectKey: null,
    supplierName: null,
  } as unknown as ProductSummary;
}
