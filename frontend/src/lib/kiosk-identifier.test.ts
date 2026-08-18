import assert from "node:assert/strict";
import test from "node:test";
import { normalizeKioskIdentifier } from "./kiosk-identifier.ts";

test("normaliza correos para identificar al socio", () => {
  assert.equal(normalizeKioskIdentifier("  SOCIO@EXAMPLE.COM "), "socio@example.com");
});

test("normaliza teléfonos mexicanos locales, internacionales y heredados", () => {
  assert.equal(normalizeKioskIdentifier("55 5555 1234"), "+525555551234");
  assert.equal(normalizeKioskIdentifier("+52 (55) 5555-1234"), "+525555551234");
  assert.equal(normalizeKioskIdentifier("+52 1 55 5555 1234"), "+525555551234");
});
