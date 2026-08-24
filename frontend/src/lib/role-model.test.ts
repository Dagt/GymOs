import test from "node:test";
import assert from "node:assert/strict";
import { canAccessRoute, getConsoleProfile, getPostLoginPath } from "./role-model.ts";
import type { CurrentUser } from "./api/types.ts";

test("manager with executive reporting access starts in dashboard and cannot open removed operations route", () => {
  const user = manager(["report.owner.read", "report.sales.read", "client.read"]);

  assert.equal(getConsoleProfile(user).homePath, "/dashboard");
  assert.equal(canAccessRoute(user, "/dashboard"), true);
  assert.equal(canAccessRoute(user, "/operations"), false);
});

test("manager with sales reporting but no executive dashboard starts in sales report", () => {
  const user = manager(["report.sales.read", "client.read"]);

  assert.equal(getConsoleProfile(user).homePath, "/reports/sales");
  assert.equal(canAccessRoute(user, "/dashboard"), false);
});

test("manager without reporting access starts in first useful authorized workflow", () => {
  const user = manager(["client.read", "booking.read"]);

  assert.equal(getConsoleProfile(user).homePath, "/clients");
});

test("platform administrator enters through the customer portfolio home", () => {
  const user: CurrentUser = {
    ...manager(["platform.admin"]),
    platformAdmin: true,
    console: "PLATFORM_OWNER",
    roles: ["Administrador GymOS"],
  };

  assert.equal(getConsoleProfile(user).homePath, "/platform");
  assert.equal(getPostLoginPath(user), "/platform");
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
