export function isAccessBenefit(benefit: { benefitType: string }): boolean {
  const type = benefit.benefitType.toUpperCase();
  return type === "ACCESS" || type === "VIP_ACCESS";
}

export function isDiscountBenefit(benefit: { benefitType: string }): boolean {
  const type = benefit.benefitType.toUpperCase();
  return (
    type === "STUDENT" ||
    type === "AGREEMENT" ||
    type === "DISCOUNT" ||
    type === "PROMOTION" ||
    type === "SENIOR" ||
    type === "DISABILITY"
  );
}

export function validateClientBenefitAssignment(payload: {
  benefit: { benefitType: string };
  validUntil?: string | null;
}): { valid: boolean; error?: string } {
  if (isDiscountBenefit(payload.benefit) && !payload.validUntil) {
    return {
      valid: false,
      error: "Para evitar descuentos indefinidos, debes especificar una fecha de expiración obligatoria.",
    };
  }
  return { valid: true };
}

export function calculatePlanPriceWithBenefits(
  planPriceCents: number,
  activeBenefits: {
    discountType?: string;
    discountPercent?: number | null;
    discountValueCents?: number | null;
    validUntil?: string | null;
  }[],
  currentDateISO: string = new Date().toISOString().slice(0, 10),
): number {
  let finalCents = planPriceCents;

  const validDiscounts = activeBenefits.filter((b) => {
    if (!b.validUntil) return true;
    return b.validUntil >= currentDateISO;
  });

  for (const discount of validDiscounts) {
    if (discount.discountPercent && discount.discountPercent > 0) {
      const discountAmount = Math.round((finalCents * discount.discountPercent) / 100);
      finalCents = Math.max(0, finalCents - discountAmount);
    } else if (discount.discountValueCents && discount.discountValueCents > 0) {
      finalCents = Math.max(0, finalCents - discount.discountValueCents);
    }
  }

  return finalCents;
}
