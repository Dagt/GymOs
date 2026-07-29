import test from "node:test";
import assert from "node:assert/strict";
import {
  calculatePlanPriceWithBenefits,
  isAccessBenefit,
  isDiscountBenefit,
  validateClientBenefitAssignment,
} from "./benefits-helpers.ts";

test("identifies access benefits vs discount benefits correctly", () => {
  assert.equal(isAccessBenefit({ benefitType: "ACCESS" }), true);
  assert.equal(isAccessBenefit({ benefitType: "STUDENT" }), false);

  assert.equal(isDiscountBenefit({ benefitType: "STUDENT" }), true);
  assert.equal(isDiscountBenefit({ benefitType: "AGREEMENT" }), true);
  assert.equal(isDiscountBenefit({ benefitType: "ACCESS" }), false);
});

test("requires mandatory validUntil date when assigning discount benefits to client", () => {
  const discountBenefit = { benefitType: "AGREEMENT", name: "Convenio Empresa" };

  const invalidAssignment = validateClientBenefitAssignment({
    benefit: discountBenefit,
    validUntil: null,
  });
  assert.equal(invalidAssignment.valid, false);
  assert.match(invalidAssignment.error || "", /expiraci/i);

  const validAssignment = validateClientBenefitAssignment({
    benefit: discountBenefit,
    validUntil: "2026-12-31",
  });
  assert.equal(validAssignment.valid, true);
});

test("applies active unexpired discount benefits to membership plan price", () => {
  const planPriceCents = 100000; // $1,000 MXN

  const benefits = [
    { discountPercent: 15, validUntil: "2026-12-31" }, // 15% off
    { discountPercent: 10, validUntil: "2026-01-01" }, // Expired
  ];

  const finalPrice = calculatePlanPriceWithBenefits(
    planPriceCents,
    benefits,
    "2026-07-29",
  );

  // 15% discount on $1000 = $850 (85000 cents)
  assert.equal(finalPrice, 85000);
});
