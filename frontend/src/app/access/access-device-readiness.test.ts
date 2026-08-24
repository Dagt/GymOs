import assert from "node:assert/strict";
import test from "node:test";
import type { AccessDevice } from "../../lib/api/access-types";
import {
  accessDeviceReadiness,
  initialAccessBenefitSelection,
} from "./access-device-readiness.ts";

const productiveDevice: AccessDevice = {
  id: "device-1",
  name: "Torniquete principal",
  deviceCode: "TURN-01",
  deviceType: "TURNSTILE",
  status: "ACTIVE",
  provider: "HIKCENTRAL",
  lastSeenAt: null,
};

test("productive point exposes correct, incomplete and invalid benefit counts", () => {
  assert.deepEqual(
    [[], ["gym"], ["gym", "vip"]].map(
      (benefits) => accessDeviceReadiness(productiveDevice, benefits).state,
    ),
    ["INCOMPLETE", "READY", "INVALID"],
  );
  assert.equal(
    accessDeviceReadiness(productiveDevice, []).countLabel,
    "0/1 beneficios ACCESS",
  );
  assert.equal(
    accessDeviceReadiness(productiveDevice, ["gym"]).countLabel,
    "1/1 beneficio ACCESS",
  );
  assert.match(
    accessDeviceReadiness(productiveDevice, ["gym", "vip"]).detail,
    /no seleccionará uno automáticamente/i,
  );
});

test("simulated point remains explicitly diagnostic", () => {
  const result = accessDeviceReadiness(
    { ...productiveDevice, provider: "SIMULATED" },
    [],
  );

  assert.equal(result.state, "DIAGNOSTIC");
  assert.equal(result.label, "Diagnóstico");
});

test("legacy multiple benefits do not preselect an arbitrary option", () => {
  assert.equal(initialAccessBenefitSelection([]), "");
  assert.equal(initialAccessBenefitSelection(["gym"]), "gym");
  assert.equal(initialAccessBenefitSelection(["gym", "vip"]), "");
});
