import assert from "node:assert/strict";
import test from "node:test";
import type { ClientSummary } from "@/lib/api";
import { clientMembershipStatus } from "./client-list-model.ts";

test("shows active memberships with a muted pistachio treatment", () => {
  const presentation = clientMembershipStatus(client({ membershipStatus: "ACTIVE" }));

  assert.equal(presentation.label, "ACTIVA");
  assert.match(presentation.className, /#E4EDCF/);
});

test("shows expired memberships with a muted red treatment", () => {
  const presentation = clientMembershipStatus(client({ membershipStatus: "EXPIRED" }));

  assert.equal(presentation.label, "VENCIDA");
  assert.match(presentation.className, /#F3D9D5/);
});

test("distinguishes clients without a membership from expired memberships", () => {
  const presentation = clientMembershipStatus(client({ membershipStatus: null }));

  assert.equal(presentation.label, "SIN MEMBRESÍA");
  assert.match(presentation.className, /stone/);
});

function client(overrides: Partial<ClientSummary>): ClientSummary {
  return {
    id: "11111111-1111-4111-8111-111111111111",
    firstName: "Andrea",
    lastName: "Landeros",
    maternalLastName: "Ruiz",
    phone: "+5233319876543",
    email: "andy@example.com",
    status: "ACTIVE",
    currentPlanName: "Full",
    membershipStatus: "ACTIVE",
    joinedAt: "2026-07-30T10:00:00Z",
    updatedAt: "2026-07-30T10:00:00Z",
    ...overrides,
  };
}
