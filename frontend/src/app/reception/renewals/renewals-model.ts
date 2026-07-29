import type { ChangeMembershipPlanPayload, RenewMembershipPayload } from "@/lib/api";

export function buildRenewalPayload({
  method,
  reference,
  notes,
}: {
  method: "CASH" | "TRANSFER" | "CARD_MANUAL";
  reference?: string;
  notes?: string;
}): RenewMembershipPayload {
  return {
    method,
    reference: reference || null,
    validateNow: true,
    notes: notes || "Renovación en mostrador",
  };
}

export function buildChangePlanPayload({
  targetPlanId,
  method,
  reference,
  notes,
}: {
  targetPlanId: string;
  method: "CASH" | "TRANSFER" | "CARD_MANUAL";
  reference?: string;
  notes?: string;
}): ChangeMembershipPlanPayload {
  return {
    targetPlanId,
    method,
    reference: reference || null,
    validateNow: true,
    notes: notes || "Cambio de plan en mostrador",
  };
}
