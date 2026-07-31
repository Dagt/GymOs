import test from "node:test";
import assert from "node:assert/strict";
import {
  isAccessBenefit,
  isDiscountBenefit,
  validateClientBenefitAssignment,
} from "./benefits-helpers.ts";

test("identifies access benefits vs discount benefits correctly", () => {
  assert.equal(isAccessBenefit({ benefitType: "ACCESS" }), true);
  assert.equal(isAccessBenefit({ benefitType: "STUDENT" }), false);

  assert.equal(isDiscountBenefit({ benefitType: "STUDENT" }), true);
  assert.equal(isDiscountBenefit({ benefitType: "AGREEMENT" }), true);
  assert.equal(isDiscountBenefit({ benefitType: "CUSTOM" }), true);
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
