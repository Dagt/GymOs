import assert from "node:assert/strict";
import test from "node:test";
import type { PlatformCustomer } from "../../lib/api/platform-customer-types";
import { customerActions, matchesCustomer, portfolioCsv } from "./platform-portfolio.ts";

const customer: PlatformCustomer = {
  organizationId: "organization-1",
  tradeName: "Gimnasio Centro",
  legalName: "Centro Fitness SA de CV",
  taxId: "CFT010101AA1",
  organizationStatus: "ACTIVE",
  contactEmail: "owner@example.test",
  contactPhone: null,
  brandName: "Centro Fitness",
  logoUrl: null,
  branchCount: 2,
  activeBranchCount: 2,
  lifecycleStatus: "AT_RISK",
  accountOwner: "Ana Comercial",
  billingEmail: "billing@example.test",
  billingPhone: null,
  contractStartedOn: "2026-01-01",
  renewalOn: "2027-01-01",
  paymentTermsDays: 10,
  nextFollowUpOn: "2026-08-20",
  notes: null,
  version: 1,
  updatedAt: null,
  activeMembers: 450,
  monthlyRecurringRevenue: 3180,
  overdueBranchCount: 1,
  unconfiguredBranchCount: 1,
  implementationOutstanding: 0,
  nextBillingDate: "2026-08-15",
  healthStatus: "AT_RISK",
};

test("portfolio action center prioritizes overdue billing and account risk", () => {
  const actions = customerActions([customer], "2026-08-24");

  assert.equal(actions[0].destination, "/platform/billing?organization=organization-1");
  assert.match(actions[0].title, /vencido/i);
  assert.ok(actions.some((action) => /riesgo/i.test(action.title)));
  assert.ok(actions.some((action) => /seguimiento/i.test(action.title)));
});

test("customer search covers commercial owner and billing contact", () => {
  assert.equal(matchesCustomer(customer, "ana comercial", "ALL"), true);
  assert.equal(matchesCustomer(customer, "billing@", "AT_RISK"), true);
  assert.equal(matchesCustomer(customer, "centro", "ACTIVE"), false);
});

test("portfolio export produces a safe and complete csv row", () => {
  const csv = portfolioCsv([{ ...customer, tradeName: 'Centro "Premium"' }]);

  assert.match(csv, /Correo de cobranza/);
  assert.match(csv, /Centro ""Premium""/);
  assert.match(csv, /3180/);
});
