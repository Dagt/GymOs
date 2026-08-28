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
    (product) => barcodeAliases(product).some((barcode) => barcode.trim().toLowerCase() === normalizedCode),
  );
  if (byBarcode) return byBarcode;

  // 2. Second priority: exact SKU match
  const bySku = products.find(
    (product) => product.sku && product.sku.trim().toLowerCase() === normalizedCode,
  );
  if (bySku) return bySku;

  return null;
}

function barcodeAliases(product: ProductSummary) {
  return product.barcodes?.length ? product.barcodes : product.barcode ? [product.barcode] : [];
}

export function isMembershipPlanProduct(product: ProductSummary): boolean {
  return product.productType === "MEMBERSHIP_PLAN"
    || product.commercialSubtype === "MEMBERSHIP";
}

export async function resolveScannedProduct(
  products: ProductSummary[],
  code: string,
  lookupByBarcode: (barcode: string) => Promise<ProductSummary>,
): Promise<ProductSummary> {
  return findProductByBarcodeOrSku(products, code) ?? lookupByBarcode(code.trim());
}
