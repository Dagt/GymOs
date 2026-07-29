import test from "node:test";
import assert from "node:assert/strict";
import { getFocusActions, getQuickActions, getReceptionDensityConfig } from "./reception-model.ts";

test("reception model provides exactly 6 focus actions by default", () => {
  const actions = getFocusActions();
  assert.equal(actions.length, 6);
  assert.equal(actions[0].label, "Abrir caja");
  assert.equal(actions[1].label, "Ir a socios");
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
  assert.equal(config.signalsCount, 3);
});
