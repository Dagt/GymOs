export function canRenewMembership(status: string): boolean {
  const normalized = status.trim().toUpperCase();
  return normalized === "ACTIVE" || normalized === "EXPIRED";
}

export function canChangeMembershipPlan(status: string): boolean {
  return status.trim().toUpperCase() === "ACTIVE";
}
