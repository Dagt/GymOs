import test from "node:test";
import assert from "node:assert/strict";
import {
  canChangeMembershipPlan,
  canRenewMembership,
  currentMembership,
  membershipPeriodsForTimeline,
} from "./client-membership-helpers.ts";

test("active and expired memberships can renew", () => {
  assert.equal(canRenewMembership("ACTIVE"), true);
  assert.equal(canRenewMembership("EXPIRED"), true);
  assert.equal(canRenewMembership("CANCELLED"), false);
});

test("only an active membership can change plans", () => {
  assert.equal(canChangeMembershipPlan("ACTIVE"), true);
  assert.equal(canChangeMembershipPlan("EXPIRED"), false);
  assert.equal(canChangeMembershipPlan("CANCELLED"), false);
});

test("current membership hides the cancelled record left by a plan change", () => {
  const base = {
    clientId: "client",
    activatedAt: "2026-07-30T12:00:00Z",
    graceEndsOn: "2026-10-01",
    saleId: "sale",
    saleNumber: "S-1",
  };
  const cancelled = {
    ...base,
    id: "old",
    planId: "regular",
    planName: "Regular",
    status: "CANCELLED" as const,
    startsOn: "2026-07-01",
    endsOn: "2026-07-29",
  };
  const active = {
    ...base,
    id: "new",
    planId: "full",
    planName: "Full",
    status: "ACTIVE" as const,
    startsOn: "2026-07-30",
    endsOn: "2026-09-27",
  };

  assert.equal(currentMembership([cancelled, active])?.id, "new");
  assert.equal(currentMembership([cancelled]), null);
});

test("membership periods are flattened, deduplicated and sorted for the activity timeline", () => {
  const first = {
    id: "period-1",
    membershipId: "membership-1",
    planId: "regular",
    planName: "Regular",
    saleId: "sale-1",
    saleNumber: "S-1",
    operationType: "ENROLLMENT" as const,
    startsOn: "2026-07-01",
    endsOn: "2026-07-30",
    createdAt: "2026-07-01T10:00:00Z",
  };
  const second = {
    ...first,
    id: "period-2",
    operationType: "PLAN_CHANGE" as const,
    planName: "Full",
    createdAt: "2026-07-30T10:00:00Z",
  };

  assert.deepEqual(
    membershipPeriodsForTimeline({ old: [first], new: [second, second] }).map((period) => period.id),
    ["period-2", "period-1"],
  );
});
