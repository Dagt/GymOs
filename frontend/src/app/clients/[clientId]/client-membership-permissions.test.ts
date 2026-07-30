import test from "node:test";
import assert from "node:assert/strict";
import { resolveClientMembershipCapabilities } from "./client-membership-permissions.ts";

test("sale operators can enroll, renew and change plans from the client profile", () => {
  assert.deepEqual(
    resolveClientMembershipCapabilities({ permissions: ["sale.create"] }),
    {
      canChange: true,
      canEnroll: true,
      canManage: true,
      canRenew: true,
    },
  );
});

test("granular membership permissions enable only their corresponding actions", () => {
  assert.deepEqual(
    resolveClientMembershipCapabilities({ permissions: ["membership.renew"] }),
    {
      canChange: false,
      canEnroll: false,
      canManage: true,
      canRenew: true,
    },
  );
  assert.deepEqual(
    resolveClientMembershipCapabilities({ permissions: ["membership.change-plan"] }),
    {
      canChange: true,
      canEnroll: false,
      canManage: true,
      canRenew: false,
    },
  );
});

test("client creation alone does not pretend membership actions are available", () => {
  assert.deepEqual(
    resolveClientMembershipCapabilities({ permissions: ["client.create"] }),
    {
      canChange: false,
      canEnroll: false,
      canManage: false,
      canRenew: false,
    },
  );
});
