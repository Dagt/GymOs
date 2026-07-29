import test from "node:test";
import assert from "node:assert/strict";
import { buildRenewalProduct, calculateSubtotal, findProductByBarcodeOrSku, formatMoney, getPosDensityConfig } from "./pos-model.ts";
import type { CartItem } from "./PosSections.tsx";
import type { ProductSummary } from "@/lib/api";

test("calculateSubtotal correctly sums product prices", () => {
  const cart: CartItem[] = [
    { product: { id: "1", name: "P1", priceCents: 10000, currency: "MXN", sku: "S1", status: "ACTIVE" } as any, quantity: 2 },
    { product: { id: "2", name: "P2", priceCents: 5000, currency: "MXN", sku: "S2", status: "ACTIVE" } as any, quantity: 1 },
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
    { id: "1", name: "Agua 600ml", priceCents: 2000, currency: "MXN", sku: "AGUA-600", barcode: "7501234567890", status: "ACTIVE" } as unknown as ProductSummary,
    { id: "2", name: "Proteina Scoop", priceCents: 3500, currency: "MXN", sku: "SCOOP-WHEY", barcode: null, status: "ACTIVE" } as unknown as ProductSummary,
  ];

  // Match by barcode
  const foundBarcode = findProductByBarcodeOrSku(products, "7501234567890");
  assert.equal(foundBarcode?.id, "1");

  // Match by SKU (case insensitive)
  const foundSku = findProductByBarcodeOrSku(products, "scoop-whey");
  assert.equal(foundSku?.id, "2");

  // Non-matching code returns null
  const notFound = findProductByBarcodeOrSku(products, "UNKNOWN-999");
  assert.equal(notFound, null);
});

test("buildRenewalProduct maps membership to catalog product or generates synthetic plan", () => {
  const products: ProductSummary[] = [
    { id: "plan-vip-1", name: "Plan VIP Mensual", priceCents: 85000, currency: "MXN", sku: "PLAN-VIP" } as unknown as ProductSummary,
  ];

  const catalogMatch = buildRenewalProduct(
    { id: "m-1", planId: "plan-vip-1", planName: "Plan VIP Mensual", priceCents: 85000, currency: "MXN" },
    products,
  );
  assert.equal(catalogMatch.id, "plan-vip-1");
  assert.equal(catalogMatch.priceCents, 85000);

  const syntheticPlan = buildRenewalProduct(
    { id: "m-2", planId: "plan-custom", planName: "Plan Personalizado", priceCents: 90000, currency: "MXN" },
    products,
  );
  assert.equal(syntheticPlan.id, "plan-custom");
  assert.equal(syntheticPlan.name, "Renovación: Plan Personalizado");
  assert.equal(syntheticPlan.priceCents, 90000);
});
