import assert from "node:assert/strict";
import test from "node:test";
import { consumeKioskLaunch, encodeKioskLaunch } from "./kiosk-launch.ts";
import type { KioskSessionResponse } from "./api/selfservice-types.ts";

const session: KioskSessionResponse = {
  sessionId: "session-123", sessionToken: "token-seguro", terminalName: "Centro",
  organizationName: "Gimnasio Prueba", brandName: "Marca Prueba", logoUrl: null,
  primaryColor: "#123456", accentColor: "#abcdef", surfaceColor: "#ffffff",
  expiresAt: "2099-07-31T20:00:00Z", verified: false,
  capabilities: {
    clientRegistrationAllowed: false,
    membershipEnrollmentAllowed: true, membershipRenewalAllowed: true,
    membershipPlanChangeAllowed: true, resourceBookingAllowed: true,
    wellnessBookingAllowed: false, profileUpdateAllowed: true, autopayAllowed: true,
    onlineCardPaymentsReady: true, recurringCardPaymentsReady: true,
  },
};

test("serializa y recupera el traspaso temporal del kiosco", () => {
  const encoded = encodeKioskLaunch(session);
  assert.deepEqual(consumeKioskLaunch(encoded), session);
  assert.equal(encoded.includes(session.sessionToken), false);
});

test("rechaza lanzamientos incompletos, inválidos o vencidos", () => {
  assert.equal(consumeKioskLaunch(""), null);
  assert.equal(consumeKioskLaunch("not-base64"), null);
  assert.equal(consumeKioskLaunch(encodeBase64Url(JSON.stringify({ sessionId: "only-id" }))), null);
  assert.equal(consumeKioskLaunch(encodeKioskLaunch({ ...session, expiresAt: "2020-01-01T00:00:00Z" })), null);
});

function encodeBase64Url(value: string) {
  return Buffer.from(value, "utf8").toString("base64").replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}
