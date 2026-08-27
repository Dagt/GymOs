import test from "node:test";
import assert from "node:assert/strict";
import {
  calculateSubtotal,
  findProductByBarcodeOrSku,
  formatMoney,
  getPosDensityConfig,
  isMembershipPlanProduct,
  resolveScannedProduct,
} from "./pos-model.ts";
import type { CartItem } from "./PosSections.tsx";
import type { ProductSummary } from "@/lib/api";

test("calculateSubtotal correctly sums product prices", () => {
  const cart: CartItem[] = [
    { product: product({ id: "1", name: "P1", priceCents: 10000, sku: "S1" }), quantity: 2 },
    { product: product({ id: "2", name: "P2", priceCents: 5000, sku: "S2" }), quantity: 1 },
  ];
  assert.equal(calculateSubtotal(cart), 25000);
});

test("formatMoney formats cents correctly for MXN", () => {
  assert.equal(formatMoney(125050, "MXN").replace(/\u00a0/g, " "), "$1,250.50");
});

test("pos model provides data-dense configuration for power-user cashier operations", () => {
  const config = getPosDensityConfig();
  assert.equal(config.density, "compact");
  assert.equal(config.dataDenseAdmin, true);
  assert.equal(config.compactCartLayout, true);
});

test("findProductByBarcodeOrSku locates product by exact barcode or SKU", () => {
  const products: ProductSummary[] = [
    product({ id: "1", name: "Agua 600ml", priceCents: 2000, sku: "AGUA-600", barcode: "7501234567890", barcodes: ["7501234567890", "7501234567891"] }),
    product({ id: "2", name: "Proteina Scoop", priceCents: 3500, sku: "SCOOP-WHEY" }),
  ];

  // Match by barcode
  const foundBarcode = findProductByBarcodeOrSku(products, "7501234567890");
  assert.equal(foundBarcode?.id, "1");
  assert.equal(findProductByBarcodeOrSku(products, "7501234567891")?.id, "1");

  // Match by SKU (case insensitive)
  const foundSku = findProductByBarcodeOrSku(products, "scoop-whey");
  assert.equal(foundSku?.id, "2");

  // Non-matching code returns null
  const notFound = findProductByBarcodeOrSku(products, "UNKNOWN-999");
  assert.equal(notFound, null);
});

test("resolveScannedProduct falls back to the exact backend barcode lookup", async () => {
  const remote = product({
    id: "remote",
    name: "Producto fuera de la pagina visible",
    priceCents: 4200,
    sku: "REMOTE-1",
    barcode: "7500000000001",
  });
  let requestedCode = "";

  const resolved = await resolveScannedProduct([], " 7500000000001 ", async (code) => {
    requestedCode = code;
    return remote;
  });

  assert.equal(resolved.id, "remote");
  assert.equal(requestedCode, "7500000000001");
});

test("resolveScannedProduct does not call the backend when the visible catalog matches", async () => {
  const local = product({
    id: "local",
    name: "Agua",
    priceCents: 2000,
    sku: "AGUA",
    barcode: "7500000000002",
  });
  let remoteCalls = 0;

  const resolved = await resolveScannedProduct([local], "7500000000002", async () => {
    remoteCalls += 1;
    throw new Error("No debe consultar");
  });

  assert.equal(resolved.id, "local");
  assert.equal(remoteCalls, 0);
});

test("membership plans remain visible in catalog but cannot enter the retail cart", () => {
  const plan = product({
    id: "plan",
    name: "Plan mensual",
    priceCents: 10000,
    sku: "PLAN-30",
    productType: "MEMBERSHIP_PLAN",
    commercialSubtype: "MEMBERSHIP",
  });

  assert.equal(isMembershipPlanProduct(plan), true);
});

function product(overrides: Partial<ProductSummary> & Pick<ProductSummary, "id" | "name" | "sku" | "priceCents">): ProductSummary {
  const { id, name, sku, priceCents, ...optionalOverrides } = overrides;
  return {
    id,
    sku,
    name,
    description: null,
    productType: "PHYSICAL",
    commercialSubtype: "GENERAL_PRODUCT",
    priceCents,
    currency: "MXN",
    taxable: true,
    status: "ACTIVE",
    barcode: null,
    barcodes: [],
    brand: null,
    presentation: null,
    familyId: null,
    familyName: null,
    variantName: null,
    unitOfMeasure: null,
    inventoryTracked: false,
    displayOrder: 0,
    imageObjectKey: null,
    supplierName: null,
    referenceCostCents: null,
    promotionalPriceCents: null,
    promotionStartsAt: null,
    promotionEndsAt: null,
    categoryId: null,
    categoryName: null,
    tags: [],
    archived: false,
    archivedAt: null,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    version: 0,
    ...optionalOverrides,
  };
}
