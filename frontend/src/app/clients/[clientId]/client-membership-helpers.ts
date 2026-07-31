import type {
  ClientMembershipSummary,
  MembershipPeriodSummary,
} from "@/lib/api";

export function canRenewMembership(status: string): boolean {
  const normalized = status.trim().toUpperCase();
  return normalized === "ACTIVE" || normalized === "EXPIRED";
}

export function canChangeMembershipPlan(status: string): boolean {
  return status.trim().toUpperCase() === "ACTIVE";
}

export function currentMembership(
  memberships: ClientMembershipSummary[],
): ClientMembershipSummary | null {
  return memberships.find((membership) => membership.status === "ACTIVE")
    ?? memberships.find((membership) => membership.status === "EXPIRED")
    ?? null;
}

export function membershipPeriodsForTimeline(
  periodsByMembership: Record<string, MembershipPeriodSummary[]>,
): MembershipPeriodSummary[] {
  return Object.values(periodsByMembership)
    .flat()
    .filter((period, index, periods) =>
      periods.findIndex((candidate) => candidate.id === period.id) === index)
    .sort((left, right) => right.createdAt.localeCompare(left.createdAt));
}
