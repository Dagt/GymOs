import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { MANUAL_MOVEMENT_TYPES, calculateValuation } from "./inventory-utils.ts";

describe("Inventory Helpers", () => {
  it("contains all expected manual movement types including internal consumption and transfers", () => {
    const values = MANUAL_MOVEMENT_TYPES.map((m) => m.value);
    assert.ok(values.includes("PURCHASE"));
    assert.ok(values.includes("ADJUSTMENT_IN"));
    assert.ok(values.includes("ADJUSTMENT_OUT"));
    assert.ok(values.includes("WASTE"));
    assert.ok(values.includes("INTERNAL_CONSUMPTION"));
    assert.ok(values.includes("RETURN"));
    assert.ok(values.includes("INITIAL"));
  });

  it("does not include auto-managed SALE movement type in manual list", () => {
    const values = MANUAL_MOVEMENT_TYPES.map((m) => m.value);
    assert.strictEqual(values.includes("SALE" as any), false);
  });

  it("calculates valuation only for positive stock and valid cost", () => {
    assert.strictEqual(calculateValuation(10, 500), 5000);
    assert.strictEqual(calculateValuation(0, 500), 0);
    assert.strictEqual(calculateValuation(-5, 500), 0);
    assert.strictEqual(calculateValuation(10, null), 0);
  });
});
