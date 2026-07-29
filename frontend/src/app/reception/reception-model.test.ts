import test from "node:test";
import assert from "node:assert/strict";
import { getFocusActions, getQuickActions } from "./reception-model.ts";

test("reception model provides exactly 7 focus actions by default", () => {
  const actions = getFocusActions();
  assert.equal(actions.length, 7);
  assert.equal(actions[0].label, "Abrir caja");
  assert.equal(actions[1].label, "Ir a socios");
});

test("reception model quick actions separates primary action from secondary", () => {
  const quickActions = getQuickActions();
  assert.equal(quickActions.primary.href, "/pos");
  assert.equal(quickActions.secondary.length, 6);
});
