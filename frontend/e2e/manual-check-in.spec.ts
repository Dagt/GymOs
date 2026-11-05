
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
  client, membership: null,
  visitPackage: {
    name: "Paquete de cinco", status: "ACTIVE", remainingVisits: 5, validFrom: "2026-11-01",
    validUntil: "2026-11-30", branchCovered: true,
    benefits: [{ id: "included", name: "Regadera", mode: "INCLUDED_WITH_VISIT" }],
  },
  options: [{ id: benefitId, name: "General", source: "VISIT_PACKAGE" }], denialReason: null,
};

async function boot(page: Page, {
  preview = available, supervisor = false, mayOperate = true, loseFirstResponse = false,
}: { preview?: CheckInPreview; supervisor?: boolean; mayOperate?: boolean; loseFirstResponse?: boolean } = {}) {
  const posts: { key: string | undefined; body: unknown }[] = [];
  const reversals: unknown[] = [];
  const entries: CheckInEntry[] = [];
  const permissions = [
    ...(mayOperate ? ["access.check-in"] : []),
    ...(supervisor ? ["access.check-in.reverse", "access.check-in.override"] : []),
  ];
  const user = {
    userId: "11111111-1111-4111-8111-111111111111", accountScope: "TENANT",
    organizationId: "66666666-6666-4666-8666-666666666666", branchId,
    username: "reception@example.test", fullName: "Recepción de prueba", permissions, roles: ["Recepcion"],
    platformAdmin: false, supportMode: false, supportExpiresAt: null, console: "RECEPTION",
  };
  await page.addInitScript((actor) => {
    sessionStorage.setItem("gymos.accessToken", "fixture-access");
    sessionStorage.setItem("gymos.refreshToken", "fixture-refresh");
    sessionStorage.setItem("gymos.currentUser", JSON.stringify(actor));
  }, user);
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace("/api/v1", "");
    if (path === "/auth/me") return route.fulfill({ json: user });
    if (path === "/access/manual-check-ins/clients") {
      return route.fulfill({ json: url.searchParams.get("query") === "Socio" ? [client] : [] });
    }
    if (path === "/access/manual-check-ins/clients/" + clientId) {
      return route.fulfill({ json: preview });
    }
    if (path === "/access/manual-check-ins" && request.method() === "GET") {
      return route.fulfill({ json: { branchId, branchName: "Centro", timezone: "America/Mexico_City", entries } });
    }
    if (path === "/access/manual-check-ins" && request.method() === "POST") {
      posts.push({ key: request.headers()["idempotency-key"], body: request.postDataJSON() });
      if (posts.length === 1) {
        entries.push(preview.denialReason ? {
          ...grant, decision: "DENIED", reason: preview.denialReason, remainingAfter: null, benefitName: null,
        } : grant);
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

test("muestra paquete agotado y registra el rechazo sin seleccionar un derecho", async ({ page }) => {
  const { posts } = await boot(page, { preview: {
    ...available, options: [], denialReason: "El paquete está agotado.",
    visitPackage: { ...available.visitPackage!, status: "CONSUMED", remainingVisits: 0 },
  } });
  await selectClient(page);
  await expect(page.getByText(/Agotado.*0 visitas restantes/)).toBeVisible();
  await page.getByRole("button", { name: "Registrar rechazo" }).click();
  await expect(page.getByRole("status").getByText("Entrada rechazada", { exact: true })).toBeVisible();
  expect(posts[0].body).toEqual({ branchId, clientId, benefitId: null });
});
