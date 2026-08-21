import test from "node:test";
import assert from "node:assert/strict";
import { canAccessRoute, getConsoleProfile } from "./role-model.ts";
import type { CurrentUser } from "./api/types.ts";

test("manager with reporting access starts in dashboard and cannot open removed operations route", () => {
  const user = manager(["report.sales.read", "client.read"]);

  assert.equal(getConsoleProfile(user).homePath, "/dashboard");
  assert.equal(canAccessRoute(user, "/operations"), false);
});

test("manager without reporting access starts in first useful authorized workflow", () => {
  const user = manager(["client.read", "booking.read"]);

  assert.equal(getConsoleProfile(user).homePath, "/clients");
});

function manager(permissions: string[]): CurrentUser {
  return {
    userId: "manager-id",
    organizationId: "org-id",
    branchId: "branch-id",
    username: "manager@test.local",
    fullName: "Gerente Prueba",
    permissions,
    roles: ["Gerente"],
    platformAdmin: false,
    supportMode: false,
    console: "TENANT_MANAGER",
    organizationName: "Gym Prueba",
    brandName: "Gym Prueba",
    logoUrl: null,
    primaryColor: "#000000",
    accentColor: "#FFFF00",
    surfaceColor: "#FFFFFF",
  };
}
