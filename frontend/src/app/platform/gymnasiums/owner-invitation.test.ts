import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
  deliveryStatusLabel,
  invitationActionLabel,
  invitationDeliveryTitle,
  invitationSuccessMessage,
} from "./owner-invitation.ts";

test("reports a ZeptoMail accepted invitation without asking for manual delivery", () => {
  assert.equal(invitationDeliveryTitle("SENT"), "Invitación enviada por ZeptoMail");
  assert.match(invitationSuccessMessage("SENT"), /ZeptoMail aceptó/);
});

test("keeps a copy fallback when local ZeptoMail is not configured", () => {
  assert.match(invitationSuccessMessage("NOT_CONFIGURED"), /copiar el enlace/i);
  assert.equal(deliveryStatusLabel("NOT_CONFIGURED"), "ZeptoMail no configurado");
});

test("expired invitations offer generation while valid ones offer resend", () => {
  assert.equal(invitationActionLabel("EXPIRED"), "Generar y enviar invitación");
  assert.equal(invitationActionLabel("MISSING"), "Generar y enviar invitación");
  assert.equal(invitationActionLabel("PENDING"), "Reenviar invitación");
});

test("platform customer controller does not refresh through the tenant organization collection", () => {
  const source = readFileSync(
    new URL("./usePlatformGymnasiumsController.ts", import.meta.url),
    "utf8",
  );

  assert.doesNotMatch(source, /getOrganizations/);
  assert.match(source, /getPlatformCustomers/);
  assert.match(source, /refreshPortfolioAfterCommittedAction/);
  assert.match(source, /El gimnasio se creó correctamente/);
});
