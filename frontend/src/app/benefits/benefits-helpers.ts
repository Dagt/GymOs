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
