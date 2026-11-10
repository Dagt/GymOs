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
	accountScope: "PLATFORM",
	organizationId: null,
	branchId: null,
    platformAdmin: true,
    console: "PLATFORM_OWNER",
    roles: ["Administrador GymOS"],
  };

  assert.equal(getConsoleProfile(user).homePath, "/platform");
  assert.equal(getPostLoginPath(user), "/platform");
});

test("tenant cannot reach platform routes even if its permissions are tampered", () => {
  const user = manager(["platform.admin", "organization.read"]);

  assert.equal(canAccessRoute(user, "/platform"), false);
  assert.notEqual(getPostLoginPath(user), "/platform");
});

test("temporary platform support cannot return to platform routes with its tenant context", () => {
  const user: CurrentUser = {
    ...manager(["organization.read", "branch.read"]),
    accountScope: "PLATFORM",
    platformAdmin: true,
    supportMode: true,
    supportExpiresAt: "2026-08-24T23:59:00Z",
    console: "TENANT_OWNER",
  };

  assert.equal(canAccessRoute(user, "/platform"), false);
  assert.equal(canAccessRoute(user, "/dashboard"), true);
});

function manager(permissions: string[]): CurrentUser {
  return {
	userId: "manager-id",
	accountScope: "TENANT",
    organizationId: "org-id",
    branchId: "branch-id",
    username: "manager@test.local",
    fullName: "Gerente Prueba",
    permissions,
    roles: ["Gerente"],
    platformAdmin: false,
	supportMode: false,
	supportExpiresAt: null,
    console: "TENANT_MANAGER",
    organizationName: "Gym Prueba",
    brandName: "Gym Prueba",
    logoUrl: null,
    primaryColor: "#000000",
    accentColor: "#FFFF00",
    surfaceColor: "#FFFFFF",
  };
}

test("reception contracting requires both client search and an actionable operation", () => {
  const path = "/reception/memberships";
  const allowed = (permissions: string[]) => canAccessRoute({ ...manager(permissions), console: "RECEPTION" }, path);
  for (const authority of ["client.read", "client.create", "client.update", "membership.read", "membership.create", "membership.update"]) {
    assert.equal(allowed([authority, "sale.create"]), true);
    assert.equal(allowed([authority]), false);
  }
  assert.equal(allowed(["membership.read", "membership.renew"]), true);
  assert.equal(allowed(["membership.read", "membership.change-plan"]), true);
  assert.equal(allowed(["sale.create"]), false);
  assert.equal(allowed(["client.read", "membership.renew"]), false);
});
