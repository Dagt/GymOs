import assert from "node:assert/strict";
import test from "node:test";
import { consumeKioskLaunch, encodeKioskLaunch } from "./kiosk-launch.ts";
import type { KioskSessionResponse } from "./api/selfservice-types.ts";

const session: KioskSessionResponse = {
  sessionId: "session-123",
  sessionToken: "token-seguro",
  terminalName: "Autoservicio - Centro",
  organizationName: "Gimnasio Prueba",
  brandName: "Marca Prueba",
  logoUrl: null,
  primaryColor: "#123456",
  accentColor: "#abcdef",
  surfaceColor: "#ffffff",
  expiresAt: "2026-07-31T20:00:00Z",
  verified: false,
  capabilities: {
    membershipRenewalAllowed: true,
    membershipPlanChangeAllowed: true,
    resourceBookingAllowed: true,
    wellnessBookingAllowed: false,
    profileUpdateAllowed: true,
    autopayAllowed: false,
    onlineCardPaymentsReady: false,
    recurringCardPaymentsReady: false,
  },
};

test("serializes and consumes a kiosk launch without losing tenant branding", () => {
  const encoded = encodeKioskLaunch(session);
  const decoded = consumeKioskLaunch(`#launch=${encoded}`);

  assert.deepEqual(decoded, session);
  assert.equal(encoded.includes(session.sessionToken), false);
});

test("rejects malformed or incomplete handoff values", () => {
  assert.equal(consumeKioskLaunch("#invalid=value"), null);
  assert.equal(consumeKioskLaunch("#launch=not-base64"), null);

  const incomplete = encodeBase64Url(JSON.stringify({ sessionId: "only-id" }));
  assert.equal(consumeKioskLaunch(`#launch=${incomplete}`), null);
});

function encodeBase64Url(value: string) {
  return Buffer.from(value, "utf8")
    .toString("base64")
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replaceAll("=", "");
}
