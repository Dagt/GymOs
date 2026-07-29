import test from "node:test";
import assert from "node:assert/strict";
import { buildChangePlanPayload, buildRenewalPayload } from "./renewals-model.ts";

test("buildRenewalPayload constructs valid renewal request payload", () => {
  const payload = buildRenewalPayload({
    method: "CASH",
    reference: "REC-1001",
    notes: "Renovación en mostrador",
  });

  assert.equal(payload.method, "CASH");
  assert.equal(payload.reference, "REC-1001");
  assert.equal(payload.validateNow, true);
  assert.equal(payload.notes, "Renovación en mostrador");
});

test("buildChangePlanPayload constructs valid plan change payload", () => {
  const payload = buildChangePlanPayload({
    targetPlanId: "plan-vip-123",
    method: "CARD_MANUAL",
    reference: "CARD-4455",
    notes: "Cambio a VIP",
  });

  assert.equal(payload.targetPlanId, "plan-vip-123");
  assert.equal(payload.method, "CARD_MANUAL");
  assert.equal(payload.reference, "CARD-4455");
  assert.equal(payload.validateNow, true);
});
