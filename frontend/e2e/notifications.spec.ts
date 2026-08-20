import { expect, test, type Page, type Route } from "@playwright/test";

test("una automatizacion de membresia puede enviar correo al socio del evento", async ({ page }) => {
  let createdPayload: Record<string, unknown> | null = null;
  const createdRule = {
    organizationId: "11111111-1111-4111-8111-111111111111",
    branchId: "22222222-2222-4222-8222-222222222222",
    id: "33333333-3333-4333-8333-333333333333",
    name: "Aviso por correo",
    triggerType: "MEMBERSHIP_EXPIRING",
    conditionJson: "{\"daysBefore\":3}",
    actionType: "CREATE_NOTIFICATION",
    actionConfigJson: "{}",
    status: "ENABLED",
    retryLimit: 3,
    scheduleCron: null,
    nextRunAt: null,
    lockedUntil: null,
    currentTriggerReference: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };
  await boot(page, async (route, path) => {
    if (path === "/automations" && route.request().method() === "GET") {
      return fulfill(route, createdPayload ? [createdRule] : []);
    }
    if (path === "/automations" && route.request().method() === "POST") {
      createdPayload = route.request().postDataJSON() as Record<string, unknown>;
      return fulfill(route, { ...createdRule, ...createdPayload });
    }
    if (path.startsWith("/automations/dead-letters")) {
      return fulfill(route, { items: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
    }
    if (path === "/automations/operations") {
      return fulfill(route, {
        dueRules: 0,
        pendingEvents: 0,
        retryEvents: 0,
        expiredLeases: 0,
        deadLetterEvents: 0,
        deadLetterExecutions: 0,
        oldestPendingAt: null,
        schedulerPaused: false,
        alertLevel: "HEALTHY",
      });
    }
    if (path === "/automations/scheduler") {
      return fulfill(route, {
        organizationId: createdRule.organizationId,
        paused: false,
        reason: null,
        pausedAt: null,
        pausedBy: null,
        updatedAt: null,
      });
    }
    if (path.startsWith("/notifications")) return fulfill(route, []);
    return false;
  });

  await page.goto("/automations");
  await page.getByRole("button", { name: "Usar receta: Avisar que la membresía vencerá" }).click();
  const drawer = page.getByRole("dialog");
  await expect(drawer.getByLabel("Nombre para reconocerla")).toHaveValue("Avisar que la membresía vencerá");
  await expect(drawer.getByLabel("2. ¿A quién debe avisar?")).toHaveValue("EMAIL");
  await drawer.getByLabel("Nombre para reconocerla").fill("Aviso por correo");
  await drawer.locator("select").nth(0).selectOption("MEMBERSHIP_EXPIRING");
  await drawer.locator("select").nth(1).selectOption("EMAIL");
  await drawer.getByPlaceholder("Asunto de la notificación").fill("Tu membresia vence pronto");
  await drawer.locator("textarea").fill("Hola {{recipientName}}, tu membresia vence el {{endsOn}}.");
  await drawer.getByRole("button", { name: "Guardar y activar" }).click();

  await expect(page.getByText("Automatización “Aviso por correo” creada y activa.")).toBeVisible();
  expect(createdPayload).not.toBeNull();
  const action = JSON.parse(String(createdPayload?.actionConfigJson)) as Record<string, unknown>;
  expect(action).toMatchObject({
    channel: "EMAIL",
    category: "TRANSACTIONAL",
    subject: "Tu membresia vence pronto",
    body: "Hola {{recipientName}}, tu membresia vence el {{endsOn}}.",
  });
});

async function boot(
  page: Page,
  handler: (route: Route, path: string) => Promise<boolean>,
) {
  await page.addInitScript(() => {
    window.localStorage.setItem("gymos.accessToken", "e2e-access-token");
    window.localStorage.setItem("gymos.refreshToken", "e2e-refresh-token");
    window.localStorage.setItem("gymos.currentUser", JSON.stringify({
      userId: "44444444-4444-4444-8444-444444444444",
      organizationId: "11111111-1111-4111-8111-111111111111",
      branchId: "22222222-2222-4222-8222-222222222222",
      username: "owner@gymos.test",
      fullName: "Owner",
      permissions: ["automation.read", "automation.manage", "automation.execute", "notification.read", "notification.manage"],
      roles: ["Dueno del gimnasio"],
      console: "TENANT_OWNER",
    }));
  });
  await page.route("**/api/v1/**", async (route) => {
    const path = new URL(route.request().url()).pathname.replace(/^\/api\/v1/, "");
    if (await handler(route, path)) return;
    await fulfill(route, {});
  });
}

async function fulfill(route: Route, body: unknown) {
  await route.fulfill({ contentType: "application/json", body: JSON.stringify(body) });
  return true;
}
