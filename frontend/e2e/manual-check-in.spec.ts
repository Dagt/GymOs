import { mockStaffSession } from "./session-fixtures";

import { expect, test, type Page } from "@playwright/test";
import type { CheckInEntry, CheckInPreview } from "../src/lib/api/manual-check-in";

const branchId = "22222222-2222-4222-8222-222222222222";
const clientId = "33333333-3333-4333-8333-333333333333";
const benefitId = "44444444-4444-4444-8444-444444444444";
const entryId = "55555555-5555-4555-8555-555555555555";
const client = { id: clientId, name: "Socio de prueba", phone: "5550100200", status: "ACTIVE" };
const grant: CheckInEntry = {
  id: entryId, clientId, clientName: client.name, occurredAt: "2026-11-05T20:00:00Z",
  decision: "GRANTED", reason: "Entrada autorizada con una visita del paquete.", benefitName: "General",
  remainingAfter: 4, duplicateOf: null, reversal: null,
};
const available: CheckInPreview = {
  client, membership: null, membershipExpired: false,
  visitPackage: {
    name: "Paquete de cinco", status: "ACTIVE", remainingVisits: 5, validFrom: "2026-11-01",
    validUntil: "2026-11-30", branchCovered: true,
    benefits: [{ id: "included", name: "Regadera", mode: "INCLUDED_WITH_VISIT" }],
  },
  options: [{ id: benefitId, name: "General", source: "VISIT_PACKAGE" }], denialReason: null,
};

async function boot(page: Page, {
  preview = available, previewAfterSubmit, firstEntry, supervisor = false, loseFirstResponse = false,
}: {
  preview?: CheckInPreview;
  previewAfterSubmit?: CheckInPreview | "error";
  firstEntry?: CheckInEntry;
  supervisor?: boolean;
  loseFirstResponse?: boolean;
} = {}) {
  const posts: { key: string | undefined; body: unknown }[] = [];
  const reversals: unknown[] = [];
  const entries: CheckInEntry[] = [];
  const permissions = [
    "access.check-in",
    ...(supervisor ? ["access.check-in.reverse", "access.check-in.override"] : []),
  ];
  const user = {
    userId: "11111111-1111-4111-8111-111111111111", accountScope: "TENANT",
    organizationId: "66666666-6666-4666-8666-666666666666", branchId,
    username: "reception@example.test", fullName: "Recepción de prueba", permissions, roles: ["Recepcion"],
    platformAdmin: false, supportMode: false, supportExpiresAt: null, console: "RECEPTION",
  };
  await mockStaffSession(page, "fixture-access", user);
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace("/api/v1", "");
    if (path === "/auth/me") return route.fulfill({ json: user });
    if (path === "/access/manual-check-ins/clients") {
      return route.fulfill({ json: url.searchParams.get("query") === "Socio" ? [client] : [] });
    }
    if (path === "/access/manual-check-ins/clients/" + clientId) {
      const current = posts.length > 0 ? previewAfterSubmit ?? preview : preview;
      return current === "error" ? route.fulfill({ status: 503, json: { message: "Consulta no disponible" } })
        : route.fulfill({ json: current });
    }
    if (path === "/access/manual-check-ins" && request.method() === "GET") {
      return route.fulfill({ json: { branchId, branchName: "Centro", timezone: "America/Mexico_City", entries } });
    }
    if (path === "/access/manual-check-ins" && request.method() === "POST") {
      posts.push({ key: request.headers()["idempotency-key"], body: request.postDataJSON() });
      if (posts.length === 1) {
        entries.push(firstEntry ?? (preview.denialReason ? {
          ...grant, decision: "DENIED", reason: preview.denialReason, remainingAfter: null, benefitName: null,
        } : grant));
        if (loseFirstResponse) return route.abort("connectionreset");
      }
      return route.fulfill({ json: entries[0] });
    }
    if (path === "/access/manual-check-ins/" + entryId + "/reversal") {
      const body = request.postDataJSON();
      reversals.push(body);
      entries[0] = { ...grant, reversal: { reason: body.reason, remainingAfter: 5 } };
      return route.fulfill({ json: entries[0] });
    }
    return route.fulfill({ json: [] });
  });
  await page.goto("/reception/check-in");
  return { posts, reversals };
}

async function selectClient(page: Page) {
  await page.getByLabel("Nombre, teléfono o identificador").fill("Socio");
  await page.getByRole("button", { name: "Buscar", exact: true }).click();
  await page.getByRole("button", { name: /Socio de prueba/ }).click();
}

test("recupera la respuesta perdida después de recargar sin crear otra operación", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const { posts } = await boot(page, { loseFirstResponse: true });
  await selectClient(page);
  await expect(page.getByText("Regadera · incluido con la visita del paquete")).toBeVisible();
  await expect(page.getByText(/Paquete de cinco.*5 visitas restantes/)).toBeVisible();
  await page.getByRole("button", { name: "Confirmar entrada" }).click();
  await expect(page.getByRole("button", { name: "Reintentar entrada" })).toBeEnabled();
  await page.reload();
  await page.getByRole("button", { name: "Reintentar entrada" }).click();
  await expect(page.getByText("4 visitas restantes tras la operación")).toBeVisible();
  expect(posts).toHaveLength(2);
  expect(posts[0].key).toMatch(/^[0-9a-f-]{36}$/);
  expect(posts[1]).toEqual(posts[0]);
  expect(posts[0].body).toEqual({ branchId, clientId, benefitId });
  await expect(page.getByRole("button", { name: "Revertir entrada" })).toHaveCount(0);
});

test("permite revertir una entrada autorizada a un supervisor con motivo", async ({ page }) => {
  const { reversals } = await boot(page, { supervisor: true });
  await selectClient(page);
  await page.getByRole("button", { name: "Confirmar entrada" }).click();
  await page.getByRole("button", { name: "Revertir entrada" }).click();
  await expect(page.getByRole("button", { name: "Confirmar reversión" })).toBeDisabled();
  await page.getByLabel("Motivo de reversión").fill("Registro equivocado");
  await page.getByRole("button", { name: "Confirmar reversión" }).click();
  await expect(page.getByText("5 visitas restantes tras la operación")).toBeVisible();
  expect(reversals).toEqual([{ branchId, reason: "Registro equivocado" }]);
  await expect(page.getByRole("button", { name: "Revertir entrada" })).toHaveCount(0);
});

const exhausted = {
  ...available, membershipExpired: true, options: [], denialReason: "El paquete está agotado.",
  visitPackage: { ...available.visitPackage!, status: "CONSUMED", remainingVisits: 0 },
};

test("muestra paquete agotado y registra el rechazo sin seleccionar un derecho", async ({ page }) => {
  const { posts } = await boot(page, { preview: exhausted });
  await selectClient(page);
  await expect(page.getByText(/Agotado.*0 visitas restantes/)).toBeVisible();
  await expect(page.getByText("Membresía vencida", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Confirmar entrada" })).toHaveCount(0);
  await expect(page.getByText(/Regadera/)).toHaveCount(0);
  await page.getByRole("button", { name: "Registrar rechazo" }).click();
  await expect(page.getByRole("status").getByText("Entrada rechazada", { exact: true })).toBeVisible();
  expect(posts[0].body).toEqual({ branchId, clientId, benefitId: null });
});

test("un paquete vencido con saldo no ofrece confirmar entrada ni sus servicios incluidos", async ({ page }) => {
  const { posts } = await boot(page, { preview: {
    ...exhausted, denialReason: "El paquete está vencido.",
    visitPackage: { ...exhausted.visitPackage, status: "EXPIRED", remainingVisits: 2, validUntil: "2026-11-04" },
  } });
  await selectClient(page);
  await expect(page.getByText(/Vencido.*2 visitas restantes/)).toBeVisible();
  await expect(page.getByRole("button", { name: "Confirmar entrada" })).toHaveCount(0);
  await expect(page.getByLabel("Derecho de acceso")).toHaveCount(0);
  await expect(page.getByText(/Regadera/)).toHaveCount(0);
  expect(posts).toHaveLength(0);
});

test("la membresía vigente permite confirmar con el paquete agotado", async ({ page }) => {
  const { posts } = await boot(page, { preview: {
    ...exhausted, membershipExpired: false, denialReason: null,
    membership: { planName: "Mensual", endsOn: "2026-11-30", graceEndsOn: "2026-11-30" },
    options: [{ id: benefitId, name: "General", source: "MEMBERSHIP" }],
  }, firstEntry: { ...grant, reason: "Entrada autorizada por membresía.", remainingAfter: null } });
  await selectClient(page);
  await expect(page.getByText(/Agotado.*0 visitas restantes/)).toBeVisible();
  await expect(page.getByLabel("Derecho de acceso")).toHaveValue(benefitId);
  await expect(page.getByRole("option", { name: /General.*membresía/ })).toHaveCount(1);
  await expect(page.getByText(/Regadera/)).toHaveCount(0);
  await page.getByRole("button", { name: "Confirmar entrada" }).click();
  await expect(page.getByText(/Entrada autorizada por membresía/).first()).toBeVisible();
  expect(posts[0].body).toEqual({ branchId, clientId, benefitId });
});

test("refresca el saldo si otra recepción consumió la última visita después de la consulta", async ({ page }) => {
  const { posts } = await boot(page, { previewAfterSubmit: exhausted,
    firstEntry: { ...grant, decision: "DENIED", reason: "El paquete se agotó.", remainingAfter: null } });
  await selectClient(page);
  await page.getByRole("button", { name: "Confirmar entrada" }).click();
  await expect(page.getByText(/Agotado.*0 visitas restantes/)).toBeVisible();
  await expect(page.getByRole("button", { name: "Confirmar entrada" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Registrar rechazo" })).toBeEnabled();
  expect(posts).toHaveLength(1);
});

test("un fallo de consulta después de un duplicado oculta la excepción hasta consultar de nuevo", async ({ page }) => {
  const { posts } = await boot(page, { supervisor: true, previewAfterSubmit: "error",
    firstEntry: { ...grant, decision: "DUPLICATE", duplicateOf: entryId, remainingAfter: null } });
  await selectClient(page);
  await page.getByRole("button", { name: "Confirmar entrada" }).click();
  await expect(page.getByText(/Consulta no disponible/)).toBeVisible();
  await expect(page.getByRole("button", { name: "Es una entrada distinta" })).toHaveCount(0);
  await expect(page.getByLabel("Derecho de acceso")).toHaveCount(0);
  expect(posts).toHaveLength(1);
});
