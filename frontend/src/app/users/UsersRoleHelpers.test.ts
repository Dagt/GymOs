import test from "node:test";
import assert from "node:assert/strict";
import {
  canManageUserForSession,
  canSeeUserForSession,
  canShowRoleForSession,
  displayRoleName,
} from "./UsersRoleHelpers.ts";

test("normalizes user-facing role names", () => {
  assert.equal(displayRoleName("Administrador GymOS"), "Admin GymOS");
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
  assert.equal(canManageUserForSession(["Gerente"], ["Administrador"], false), false);
  assert.equal(canManageUserForSession(["Gerente"], ["Gerente"], false), false);
  assert.equal(canManageUserForSession(["Gerente"], ["Recepcion"], false), true);
});

test("platform administrator keeps full support visibility", () => {
  assert.equal(canShowRoleForSession(["Administrador GymOS"], "Administrador", true), true);
  assert.equal(canSeeUserForSession(["Administrador GymOS"], ["Administrador"], true), true);
  assert.equal(canManageUserForSession(["Administrador GymOS"], ["Administrador"], true), true);
});
