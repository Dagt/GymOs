import test from "node:test";
import assert from "node:assert/strict";
import { resolveShellBrand, visibleSectionsForConsole } from "./app-shell-model.ts";
import type { CurrentUser, StoredSession } from "../lib/api/types.ts";

test("tenant owner navigation keeps management and client access visible", () => {
  const sections = visibleSectionsForConsole({
    consoleType: "TENANT_OWNER",
    hasBranch: true,
    permissions: new Set([
      "report.owner.read",
      "client.read",
      "user.read",
      "organization.update",
      "branch.update",
      "catalog.read",
      "booking.read",
      "sale.create",
    ]),
    variant: "tenant",
  });

  const labels = sectionLabels(sections);
  assert.ok(labels.includes("Metricas del negocio"));
  assert.ok(labels.includes("Socios"));
  assert.ok(labels.includes("Equipo y accesos"));
  assert.ok(labels.includes("Datos y sucursales"));
});

test("reception navigation is limited to front desk work", () => {
  const sections = visibleSectionsForConsole({
    consoleType: "RECEPTION",
    hasBranch: true,
    permissions: new Set([
      "client.read", "sale.create", "booking.read", "professional.read",
      "report.sales.read", "benefit.read",
    ]),
    variant: "tenant",
  });

  const labels = sectionLabels(sections);
  assert.ok(labels.includes("Inicio recepcion"));
  assert.ok(labels.includes("Caja / POS"));
  assert.ok(labels.includes("Socios"));
  assert.ok(labels.includes("Wellness"));
  assert.equal(labels.includes("Renovaciones"), false);
  assert.equal(labels.includes("Equipo y accesos"), false);
  assert.equal(labels.includes("Datos y sucursales"), false);
});

test("manager navigation exposes the completed Wellness module", () => {
  const sections = visibleSectionsForConsole({
    consoleType: "TENANT_MANAGER",
    hasBranch: true,
    permissions: new Set(["booking.read", "professional.read"]),
    variant: "tenant",
  });

  assert.ok(sectionLabels(sections).includes("Wellness"));
});

test("branchless tenant users only see setup-safe links", () => {
  const sections = visibleSectionsForConsole({
    consoleType: "TENANT_OWNER",
    hasBranch: false,
    permissions: new Set(["client.read", "user.read", "organization.update", "branch.update", "catalog.read"]),
    variant: "tenant",
  });

  assert.deepEqual(sectionLabels(sections), ["Equipo y accesos", "Datos y sucursales"]);
});

test("platform shell uses neutral GymOS brand outside support mode", () => {
  const session: StoredSession = {
    accessToken: "token",
    refreshToken: "refresh",
    user: user({
      brandName: "Tenant Brand",
      logoUrl: "/tenants/tenant/logo.png",
      platformAdmin: true,
      supportMode: false,
    }),
  };

  assert.deepEqual(resolveShellBrand({
    session,
    variant: "platform",
    visibleBranch: "Centro",
  }), {
    logoUrl: null,
    name: "GymOS Plataforma",
    scope: "CRM de gimnasios",
  });
});

test("support mode uses tenant brand even for platform administrator", () => {
  const session: StoredSession = {
    accessToken: "token",
    refreshToken: "refresh",
    user: user({
      brandName: "Tenant Brand",
      logoUrl: "/tenants/tenant/logo.png",
      platformAdmin: true,
      supportMode: true,
    }),
  };

  assert.deepEqual(resolveShellBrand({
    session,
    variant: "tenant",
    visibleBranch: "Sucursal Centro",
  }), {
    logoUrl: "/tenants/tenant/logo.png",
    name: "Tenant Brand",
    scope: "Sucursal Centro",
  });
});

test("manager without active branch keeps only setup-safe navigation", () => {
  const sections = visibleSectionsForConsole({
    consoleType: "TENANT_MANAGER",
    hasBranch: false,
    permissions: new Set(["client.read", "user.read", "catalog.read", "booking.read", "sale.create"]),
    variant: "tenant",
  });

  assert.deepEqual(sectionLabels(sections), ["Equipo"]);
});

function sectionLabels(sections: ReturnType<typeof visibleSectionsForConsole>) {
  return sections.flatMap((section) => section.links.map((link) => link.label));
}

function user(overrides: Partial<CurrentUser> = {}): CurrentUser {
  return {
    userId: "user-id",
    organizationId: "org-id",
    branchId: "branch-id",
    username: "usuario",
    fullName: "Usuario Prueba",
    permissions: [],
    roles: [],
    platformAdmin: false,
    supportMode: false,
    console: "TENANT_OWNER",
    organizationName: "Gym Prueba",
    brandName: "Gym Prueba",
    logoUrl: null,
    primaryColor: null,
    accentColor: null,
    surfaceColor: null,
    ...overrides,
  };
}
