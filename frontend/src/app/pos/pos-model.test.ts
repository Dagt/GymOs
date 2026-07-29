import test from "node:test";
import assert from "node:assert/strict";
import { calculateSubtotal, formatMoney, getPosDensityConfig } from "./pos-model.ts";
import type { CartItem } from "./PosSections.tsx";

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

