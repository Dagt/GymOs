import test from "node:test";
import assert from "node:assert/strict";
import { getFocusActions, getQuickActions, getReceptionDensityConfig, getVisibleFocusActions } from "./reception-model.ts";

test("reception model omits the redundant renewal navigation card", () => {
  const actions = getFocusActions();
  assert.equal(actions.length, 6);
  assert.equal(actions[0].label, "Abrir caja");
  assert.equal(actions[1].label, "Ir a socios");
  assert.equal(actions.some((action) => action.href === "/reception/renewals"), false);
});

test("reception model quick actions separates primary action from secondary", () => {
  const quickActions = getQuickActions();
  assert.equal(quickActions.primary.href, "/pos");
  assert.equal(quickActions.secondary.length, 5);
});

test("reception model provides data-dense layout configuration for hybrid contextual density", () => {
  const config = getReceptionDensityConfig();
  assert.equal(config.density, "compact");
  assert.equal(config.dataDenseAdmin, true);
});

test("reception actions hide kiosk launch unless the session is allowed to open it", () => {
  assert.equal(getVisibleFocusActions(false, false).some((action) => action.href === "/kiosk/launch"), false);
  assert.equal(getVisibleFocusActions(true, false).some((action) => action.href === "/kiosk/launch"), true);
});

test("reception exposes check-in as the first action only with its permission", () => {
  assert.equal(getVisibleFocusActions(false, true)[0].href, "/reception/check-in");
  assert.equal(getVisibleFocusActions(true, false).some((action) => action.href === "/reception/check-in"), false);
});

test("reception only links to memberships and packages with commercial permissions", () => {
  const href = "/reception/memberships";
  assert.equal(getVisibleFocusActions(true, true, false).some((action) => action.href === href), false);
  assert.equal(getVisibleFocusActions(true, true, true).find((action) => action.href === href)?.label, "Membresías y paquetes");
});

test("only the authorized memberships shortcut is featured", () => {
  assert.deepEqual(getVisibleFocusActions(true, true, true).filter(({ featured }) => featured).map(({ href }) => href), ["/reception/memberships"]);
  assert.equal(getVisibleFocusActions(true, true, false).some(({ featured }) => featured), false);
});
