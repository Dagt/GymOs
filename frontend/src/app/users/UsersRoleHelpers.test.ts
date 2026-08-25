import test from "node:test";
import assert from "node:assert/strict";
import {
  canManageUserForSession,
  canSeeUserForSession,
  canShowRoleForSession,
  displayRoleName,
  withSynchronizedLoginEmail,
} from "./UsersRoleHelpers.ts";
import type { CreateInternalUserPayload } from "../../lib/api/user-types.ts";
import { canAccessRoute, canLaunchKiosk } from "../../lib/role-model.ts";
import type { CurrentUser } from "../../lib/api/auth-types.ts";

test("normalizes user-facing role names", () => {
  assert.equal(displayRoleName("Administrador GymOS"), "Admin GymFort");
  assert.equal(displayRoleName("Administrador"), "Dueno del gimnasio");
  assert.equal(displayRoleName("Gerente"), "Gerente");
  assert.equal(displayRoleName("Recepcion y caja"), "Recepcion");
  assert.equal(displayRoleName("Coach"), "Coach");
});

test("tenant users cannot create platform administrators", () => {
  assert.equal(canShowRoleForSession(["Administrador"], "Administrador GymOS", false), false);
  assert.equal(canShowRoleForSession(["Gerente"], "Administrador GymOS", false), false);
});

test("tenant owner can create operational roles but not another owner", () => {
  assert.equal(canShowRoleForSession(["Administrador"], "Gerente", false), true);
  assert.equal(canShowRoleForSession(["Administrador"], "Recepcion", false), true);
  assert.equal(canShowRoleForSession(["Administrador"], "Coach", false), true);
  assert.equal(canShowRoleForSession(["Administrador"], "Administrador", false), false);
});

test("manager cannot see or manage tenant owners and other managers", () => {
  assert.equal(canSeeUserForSession(["Gerente"], ["Administrador"], false), false);
  assert.equal(canSeeUserForSession(["Gerente"], ["Gerente"], false), false);
  assert.equal(canSeeUserForSession(["Gerente"], ["Recepcion"], false), true);
  assert.equal(canSeeUserForSession(["Gerente"], ["Coach"], false), true);
  assert.equal(canManageUserForSession(["Gerente"], ["Administrador"], false), false);
  assert.equal(canManageUserForSession(["Gerente"], ["Gerente"], false), false);
  assert.equal(canManageUserForSession(["Gerente"], ["Recepcion"], false), true);
  assert.equal(canManageUserForSession(["Gerente"], ["Coach"], false), true);
});

test("platform administrator keeps full support visibility", () => {
  assert.equal(canShowRoleForSession(["Administrador GymOS"], "Administrador", true), true);
  assert.equal(canSeeUserForSession(["Administrador GymOS"], ["Administrador"], true), true);
  assert.equal(canManageUserForSession(["Administrador GymOS"], ["Administrador"], true), true);
});

test("email typing keeps the login identifier synchronized character by character", () => {
  let form = emptyUserForm();
  const email = "ana.gerencia@example.com";

  for (let index = 1; index <= email.length; index += 1) {
    form = withSynchronizedLoginEmail(form, email.slice(0, index));
  }

  assert.equal(form.email, email);
  assert.equal(form.username, email);
});

test("a deliberately customized login identifier is preserved when email changes", () => {
  const form = {
    ...emptyUserForm(),
    email: "ana@example.com",
    username: "ana.gerencia",
  };

  const updated = withSynchronizedLoginEmail(form, "ana.nueva@example.com");

  assert.equal(updated.email, "ana.nueva@example.com");
  assert.equal(updated.username, "ana.gerencia");
});

test("receptionist role cannot access /benefits route", () => {
  const receptionUser: CurrentUser = {
	userId: "user-1",
	accountScope: "TENANT",
    organizationId: "organization-1",
    branchId: "branch-1",
    username: "reception@gymos.local",
    fullName: "Recepcionista Test",
    roles: ["Recepcion y caja"],
    permissions: ["sale.create", "client.create"],
    console: "RECEPTION",
    platformAdmin: false,
	supportMode: false,
	supportExpiresAt: null,
    organizationName: "GymOS Test",
  };

  assert.equal(canAccessRoute(receptionUser, "/benefits"), false);
  assert.equal(canAccessRoute(receptionUser, "/pos"), true);
});

test("public policy and support routes stay available for authenticated users", () => {
  const receptionUser: CurrentUser = {
	userId: "user-1",
	accountScope: "TENANT",
    organizationId: "organization-1",
    branchId: "branch-1",
    username: "reception@gymfort.mx",
    fullName: "Recepcionista Test",
    roles: ["Recepcion y caja"],
    permissions: ["sale.create", "client.create"],
    console: "RECEPTION",
    platformAdmin: false,
	supportMode: false,
	supportExpiresAt: null,
    organizationName: "GymFort Test",
  };

  for (const route of ["/privacy-policy", "/terms-of-service", "/support"]) {
    assert.equal(canAccessRoute(receptionUser, route), true, `${route} should be public`);
  }
});

test("only reception can open the kiosk even when another role inherits the permission", () => {
  const receptionUser = kioskUser({ console: "RECEPTION", roles: ["Recepcion"] });
  const ownerUser = kioskUser({ console: "TENANT_OWNER", roles: ["Dueno del gimnasio"] });
  const managerUser = kioskUser({ console: "TENANT_MANAGER", roles: ["Gerente"] });
  const platformUser = kioskUser({ console: "PLATFORM_OWNER", roles: ["Administrador GymOS"], platformAdmin: true });

  assert.equal(canLaunchKiosk(receptionUser), true);
  assert.equal(canAccessRoute(receptionUser, "/kiosk/launch"), true);
  for (const user of [ownerUser, managerUser, platformUser]) {
    assert.equal(canLaunchKiosk(user), false);
    assert.equal(canAccessRoute(user, "/kiosk/launch"), false);
  }
});

function kioskUser(overrides: Partial<CurrentUser>): CurrentUser {
  return {
	userId: "user-kiosk",
	organizationId: "organization-1",
    branchId: "branch-1",
    username: "user@gymfort.mx",
    fullName: "Usuario Kiosco",
    roles: [],
    permissions: ["selfservice.session.launch"],
    console: "RECEPTION",
    platformAdmin: false,
	supportMode: false,
	supportExpiresAt: null,
    organizationName: "GymFort Test",
	...overrides,
	accountScope: overrides.accountScope ?? "TENANT",
  };
}

function emptyUserForm(): CreateInternalUserPayload {
  return {
    firstName: "Ana",
    lastName: "Gerencia",
    email: "",
    phone: "+525512345678",
    username: "",
    temporaryPassword: "Temporal123!",
    roleId: "role-id",
    branchIds: ["branch-id"],
  };
}
