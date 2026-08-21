import assert from "node:assert/strict";
import test from "node:test";

import type { KioskAutopayMandate } from "./api/selfservice-types.ts";
import {
  classifyKioskAutopayResult,
  matchesStripeCheckoutSession,
  readKioskAutopayReturn,
  readKioskPaymentReturn,
  writeKioskAutopayReturn,
  writeKioskPaymentReturn,
  type KioskAutopayReturnContext,
} from "./kiosk-autopay-return.ts";

const context: KioskAutopayReturnContext = {
  sessionId: "session-1",
  membershipId: "membership-1",
  providerSessionId: "cs_test_1",
  returnPath: "/kiosk/reception",
  startedAt: "2026-08-20T15:00:00.000Z",
};

test("autopay return context round-trips only while it is recent", () => {
  const storage = memoryStorage();
  writeKioskAutopayReturn(storage, context);

  assert.deepEqual(
    readKioskAutopayReturn(storage, new Date("2026-08-20T15:30:00.000Z").getTime()),
    context,
  );
  assert.equal(
    readKioskAutopayReturn(storage, new Date("2026-08-20T16:00:01.000Z").getTime()),
    null,
  );
});

test("autopay return rejects unsafe kiosk paths", () => {
  const storage = memoryStorage();
  writeKioskAutopayReturn(storage, { ...context, returnPath: "https://example.com" });

  assert.equal(readKioskAutopayReturn(storage, new Date(context.startedAt).getTime()), null);
});

test("payment return preserves the payment used to reconcile Stripe", () => {
  const storage = memoryStorage();
  const paymentContext = {
    sessionId: context.sessionId,
    paymentId: "payment-1",
    saleId: "sale-1",
    returnPath: context.returnPath,
    startedAt: context.startedAt,
  };
  writeKioskPaymentReturn(storage, paymentContext);

  assert.deepEqual(
    readKioskPaymentReturn(storage, new Date("2026-08-20T15:30:00.000Z").getTime()),
    paymentContext,
  );
});

test("Stripe session match detects a stale or unrelated return", () => {
  assert.equal(matchesStripeCheckoutSession(context, "cs_test_1"), true);
  assert.equal(matchesStripeCheckoutSession(context, "cs_test_other"), false);
  assert.equal(matchesStripeCheckoutSession(context, null), false);
  assert.equal(matchesStripeCheckoutSession({ ...context, providerSessionId: null }, "cs_test_other"), true);
});

test("autopay result only reports success for an active mandate", () => {
  assert.equal(classifyKioskAutopayResult(null), "PENDING");
  assert.equal(classifyKioskAutopayResult(mandate("PENDING_SETUP")), "PENDING");
  assert.equal(classifyKioskAutopayResult(mandate("ACTIVE")), "SUCCESS");
  assert.equal(classifyKioskAutopayResult(mandate("REVOKED")), "FAILED");
});

function mandate(status: KioskAutopayMandate["status"]): KioskAutopayMandate {
  return {
    id: "mandate-1",
    membershipId: context.membershipId,
    planId: "plan-1",
    status,
    provider: "STRIPE",
    brand: status === "ACTIVE" ? "visa" : null,
    lastFour: status === "ACTIVE" ? "4242" : null,
    expiresMonth: 12,
    expiresYear: 2030,
    termsVersion: "2026-08-20",
    amountRule: "CURRENT_MEMBERSHIP_RENEWAL_TOTAL",
    consentedAt: context.startedAt,
    nextChargeOn: "2026-09-08",
    consecutiveFailures: 0,
    lastError: null,
    revokedAt: null,
    canReplacePaymentMethod: true,
    recoveryMessage: null,
  };
}

function memoryStorage() {
  const values = new Map<string, string>();
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
  };
}
