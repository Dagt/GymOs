import test from "node:test";
import assert from "node:assert/strict";
import {
  canChangeMembershipPlan,
  canRenewMembership,
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
