import { expect, test, type Page, type Route } from "@playwright/test";

const orgId = "11111111-1111-4111-8111-111111111111";
const branchId = "22222222-2222-4222-8222-222222222222";
const ownerId = "33333333-3333-4333-8333-333333333333";
const coachId = "44444444-4444-4444-8444-444444444444";
const benefitId = "55555555-5555-4555-8555-555555555555";
const vip = "66666666-6666-4666-8666-666666666666";
const daily = "77777777-7777-4777-8777-777777777777";
const from = "2026-08-18", to = "2026-08-25";
const owner = {
  userId: ownerId, accountScope: "TENANT", organizationId: orgId, branchId, username: "owner@example.test", fullName: "Dueño Prueba",
  permissions: ["branch.read", "organization.read", "user.read", "user.manage", "coach.profile.manage", "benefit.read", "benefit.manage", "catalog.read", "report.owner.read", "report.sales.read", "report.coaches.read", "coach.metrics.read.all", "coach.read", "inventory.read"],
  roles: ["Dueno del gimnasio"], platformAdmin: false, supportMode: false, supportExpiresAt: null, console: "TENANT_OWNER",
  organizationName: "Gimnasio de prueba", brandName: "GymFort QA", logoUrl: "", primaryColor: "#173c2a", accentColor: "#EAB308", surfaceColor: "#F4F7FB",
};
const benefit = { id: benefitId, branchId, code: "SAUNA", name: "Sauna", benefitType: "ACCESS", discountType: "NONE", discountPercent: null, discountValueCents: null, requiresEvidence: false, combinable: false, priority: 10, validFrom: null, validUntil: null, status: "ACTIVE", planIds: [vip], version: 4 };
const staff = [
  { id: ownerId, fullName: "Dueño Prueba", username: "owner@example.test", email: "owner@example.test", roles: ["Dueno del gimnasio"] },
  { id: coachId, fullName: "Coach Ana", username: "ana@example.test", email: "ana@example.test", roles: ["Coach"] },
].map((user) => ({ ...user, phone: null, status: "ACTIVE", branches: ["Centro"], branchIds: [branchId] }));
const trend = {
  from, to, weeks: 2,
  points: [
    { weekFrom: "2026-08-17", weekTo: "2026-08-23", assignments: 3, uniqueClients: 3, firstWorkouts: 2 },
    { weekFrom: "2026-08-24", weekTo: "2026-08-25", assignments: 4, uniqueClients: 3, firstWorkouts: 1 },
  ],
  coachActivity: [
    { date: "2026-08-18", coachId: "ana", coachName: "Ana", assignments: 2, uniqueClients: 2, firstWorkouts: 2 },
    { date: "2026-08-20", coachId: "luis", coachName: "Luis", assignments: 1, uniqueClients: 1, firstWorkouts: 0 },
    { date: "2026-08-24", coachId: "ana", coachName: "Ana", assignments: 3, uniqueClients: 2, firstWorkouts: 1 },
    { date: "2026-08-25", coachId: "luis", coachName: "Luis", assignments: 1, uniqueClients: 1, firstWorkouts: 0 },
  ],
};
const coachTotals = [
  { coachId: "ana", coachName: "Ana", assignments: 5, uniqueClients: 3, firstWorkouts: 3 },
  { coachId: "luis", coachName: "Luis", assignments: 2, uniqueClients: 1, firstWorkouts: 0 },
];

async function json(route: Route, value: unknown, status = 200) { await route.fulfill({ status, contentType: "application/json", body: JSON.stringify(value) }); return true; }

async function boot(page: Page, override?: (route: Route, path: string, url: URL) => Promise<boolean>, currentUser = owner) {
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url()), path = url.pathname.replace(/^\/api\/v1/, "");
    if (override && await override(route, path, url)) return;
    if (path === "/auth/me") return void await json(route, currentUser);
    if (path === "/system/info") return void await json(route, { capabilities: ["operational-pagination-v1"] });
    if (path === "/organizations/current") return void await json(route, { id: orgId, tradeName: "Gimnasio de prueba" });
    if (path.endsWith("/branches")) return void await json(route, [{ id: branchId, name: "Centro", code: "CEN", status: "ACTIVE" }]);
    if (path === "/benefits") return void await json(route, [benefit]);
    if (path === "/catalog/plans") return void await json(route, [{ id: vip, name: "VIP", durationDays: 30, status: "ACTIVE" }, { id: daily, name: "VIP diario", durationDays: 1, status: "ACTIVE" }]);
    if (path === "/users/internal") return void await json(route, staff);
    if (path === "/users/roles") return void await json(route, [{ id: "role-coach", name: "Coach", permissionCount: 7 }]);
    if (path === "/reports/owner-dashboard") return void await json(route, { fromDate: url.searchParams.get("fromDate") ?? from, toDate: url.searchParams.get("toDate") ?? to, paidRevenueCents: 0, saleCount: 0, newClients: 0, activeMemberships: 0, bookingCount: 0, lowStockItems: 0, activeWorkoutAssignments: 2, activeProfessionals: 0, queuedNotifications: 0, enabledAutomations: 0 });
    if (path === "/reports/coaches") return void await json(route, { coachCount: 2, workoutCount: 3, activeAssignments: 2, assignmentsLast30Days: 7 });
    if (path === "/reports/coaches/workout-assignments/summary") return void await json(route, { from, to, assignments: 7, uniqueClients: 4, activeCoaches: 2, coachesWithoutAssignments: 0, previousAssignments: 1, previousUniqueClients: 1, assignmentChange: 6, uniqueClientChange: 3, page: 0, size: 1, totalDetails: 7, totalPages: 7, coaches: coachTotals, details: [] });
    if (path === "/reports/coaches/workout-assignments/trend") return void await json(route, trend);
    if (path === "/inventory/summary") return void await json(route, { totalValuationCents: 0, totalTrackedProducts: 0, lowStockCount: 0, outOfStockCount: 0, allowNegativeStock: false });
    if (path.endsWith("/reports/summary")) return void await json(route, { fromDate: from, toDate: to, currency: "MXN", sales: { saleCount: 0, totalCents: 0, paidCents: 0, balanceCents: 0, paidSales: 0, partiallyPaidSales: 0, pendingSales: 0 }, payments: { paymentCount: 0, completedPayments: 0, pendingPayments: 0, rejectedPayments: 0, completedCents: 0, pendingCents: 0, rejectedCents: 0 }, salesByStatus: [], salesByBusinessContext: [], paymentsByMethod: [], recentSales: [] });
    await json(route, []);
  });
  await page.addInitScript((user) => {
    sessionStorage.setItem("gymos.accessToken", "operational-e2e-only"); sessionStorage.setItem("gymos.refreshToken", "operational-e2e-refresh"); sessionStorage.setItem("gymos.currentUser", JSON.stringify(user));
  }, currentUser);
}

test("edita Sauna, conserva VIP y añade el plan VIP diario sin duplicar el beneficio", async ({ page }, testInfo) => {
  let payload: Record<string, unknown> | null = null;
  await boot(page, async (route, path) => {
    if (path === `/benefits/${benefitId}` && route.request().method() === "PUT") {
      payload = route.request().postDataJSON(); return json(route, { ...benefit, ...payload, version: 5 });
    }
    return false;
  });
  await page.goto("/benefits");
  await expect(page.getByText("Sucursal actual", { exact: true })).toHaveCount(0);
  await expect(page.getByText("ACTIVE", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Editar Sauna" })).toHaveText("");
  await page.screenshot({ path: testInfo.outputPath("beneficios-listado-limpio.png") });
  await page.getByRole("button", { name: "Editar Sauna" }).click();
  const drawer = page.getByRole("dialog");
  await expect(drawer.getByLabel("Vigente desde")).toHaveCount(0);
  await expect(drawer.getByLabel("Vigente hasta")).toHaveCount(0);
  await expect(drawer.getByLabel("Requiere evidencia")).toHaveCount(0);
  await expect(drawer.getByLabel("Código", { exact: true })).toBeDisabled();
  await expect(drawer.getByRole("checkbox", { name: "VIP", exact: true })).toBeChecked();
  await drawer.getByRole("checkbox", { name: "VIP diario", exact: true }).check();
  await page.screenshot({ path: testInfo.outputPath("editar-beneficio.png") });
  await drawer.getByRole("button", { name: "Guardar cambios" }).click();
  await expect.poll(() => payload).toMatchObject({ planIds: [vip, daily], expectedVersion: 4 });
  expect(payload).not.toHaveProperty("benefitType"); expect(payload).not.toHaveProperty("code");
  await expect(page.getByText("Planes: VIP, VIP diario", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Editar Sauna" })).toHaveCount(1);
});

test("una sede puede consultar un beneficio compartido sin editar la definición de otra sucursal", async ({ page }) => {
  await boot(page, async (route, path) => path === "/benefits" ? json(route, [
    benefit, { ...benefit, id: "other-benefit", branchId: "other-branch", name: "Acceso compartido", accessScope: "PLAN_COVERAGE" },
  ]) : false);
  await page.goto("/benefits");
  await expect(page.getByText("Acceso compartido", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Editar Acceso compartido" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Editar Sauna" })).toBeVisible();
});

test("editar un acceso preserva sus vigencias y evidencia históricas sin exponer controles", async ({ page }) => {
  const legacy = { ...benefit, validFrom: "2026-01-01", validUntil: "2027-01-01", requiresEvidence: true };
  let payload: Record<string, unknown> | null = null;
  await boot(page, async (route, path) => {
    if (path === "/benefits") return json(route, [legacy]);
    if (path === `/benefits/${benefitId}`) { payload = route.request().postDataJSON(); return json(route, { ...legacy, ...payload, version: 5 }); }
    return false;
  });
  await page.goto("/benefits"); await page.getByRole("button", { name: "Editar Sauna" }).click();
  const drawer = page.getByRole("dialog");
  await expect(drawer.locator('input[type="date"]')).toHaveCount(0);
  await expect(drawer.getByLabel("Requiere evidencia")).toHaveCount(0);
  await drawer.getByRole("checkbox", { name: "VIP diario", exact: true }).check();
  await drawer.getByRole("button", { name: "Guardar cambios" }).click();
  await expect.poll(() => payload).toMatchObject({ validFrom: legacy.validFrom, validUntil: legacy.validUntil, requiresEvidence: true, planIds: [vip, daily], expectedVersion: 4 });
});

test("un nuevo acceso no hereda evidencia del formulario de descuentos", async ({ page }) => {
  let payload: Record<string, unknown> | null = null;
  await boot(page, async (route, path) => {
    if (path === "/benefits" && route.request().method() === "POST") { payload = route.request().postDataJSON(); return json(route, { ...benefit, ...payload, id: "88888888-8888-4888-8888-888888888888" }); }
    return false;
  });
  await page.goto("/benefits"); await page.getByRole("button", { name: "Crear beneficio", exact: true }).click();
  const drawer = page.getByRole("dialog");
  await drawer.getByLabel("Requiere evidencia").check();
  await drawer.getByRole("combobox", { name: "Tipo", exact: true }).selectOption("ACCESS");
  await expect(drawer.getByLabel("Requiere evidencia")).toHaveCount(0);
  await expect(drawer.locator('input[type="date"]')).toHaveCount(0);
  await drawer.getByRole("checkbox", { name: "VIP", exact: true }).check();
  await drawer.getByRole("button", { name: "Crear beneficio", exact: true }).click();
  await expect.poll(() => payload).toMatchObject({ benefitType: "ACCESS", requiresEvidence: false, planIds: [vip] });
});

test("un conflicto de edición se explica sin cerrar el formulario ni perder los planes elegidos", async ({ page }) => {
  await boot(page, async (route, path) => path === `/benefits/${benefitId}` ? json(route, { code: "BENEFIT_VERSION_CONFLICT", message: "Otro usuario modificó este beneficio. Actualiza la lista y vuelve a intentarlo." }, 400) : false);
  await page.goto("/benefits"); await page.getByRole("button", { name: "Editar Sauna" }).click();
  const drawer = page.getByRole("dialog"); await drawer.getByRole("checkbox", { name: "VIP diario", exact: true }).check();
  await drawer.getByRole("button", { name: "Guardar cambios" }).click();
  await expect(drawer.getByRole("alert")).toContainText("Otro usuario modificó");
  await expect(drawer.getByRole("checkbox", { name: "VIP diario", exact: true })).toBeChecked();
});

test("el dueño recupera acceso del coach y distingue un correo fallido de un enlace generado", async ({ page }, testInfo) => {
  await boot(page, async (route, path) => path === `/users/internal/${coachId}/password-recovery`
    ? json(route, { resetUrl: "https://example.invalid/reset-password#token=test-token-only", expiresAt: "2099-01-01T15:00:00Z", emailDeliveryStatus: "FAILED" }) : false);
  await page.goto("/users");
  const users = page.getByRole("region", { name: "Usuarios del gimnasio" });
  await expect(users.getByRole("article").filter({ hasText: "Dueño Prueba" }).getByRole("button", { name: "Recuperar contraseña" })).toHaveCount(0);
  await users.getByRole("article").filter({ hasText: "Coach Ana" }).getByRole("button", { name: "Recuperar contraseña" }).click();
  const drawer = page.getByRole("dialog"); await drawer.getByRole("button", { name: "Generar y enviar enlace" }).click();
  await expect(drawer.getByRole("status")).toContainText("No se pudo enviar el correo");
  await expect(drawer.getByLabel("Enlace privado de recuperación")).toHaveValue(/#token=test-token-only$/);
  await page.screenshot({ path: testInfo.outputPath("recuperacion-equipo.png") });
  await drawer.getByRole("button", { name: "Cerrar", exact: true }).click();
  expect(await page.evaluate(() => JSON.stringify({ session: { ...sessionStorage }, local: { ...localStorage } }))).not.toContain("test-token-only");
});

test("recuperación pública usa token en fragmento y valida confirmación antes de cambiar contraseña", async ({ page }) => {
  let accepted = 0;
  await boot(page, async (route, path) => {
    if (path === "/auth/password-recovery/accept") {
      accepted++; expect(route.request().headers().authorization).toBeUndefined();
      expect(route.request().postDataJSON()).toEqual({ token: "e2e-single-use", password: "NewPassword123!" });
      return json(route, { status: "PASSWORD_CHANGED" });
    }
    return false;
  });
  await page.goto("/reset-password#token=e2e-single-use");
  await expect(page.getByRole("heading", { name: "Elige tu nueva contraseña" })).toBeVisible();
  await expect(page).toHaveURL(/\/reset-password$/);
  await page.getByLabel("Nueva contraseña", { exact: true }).fill("NewPassword123!");
  await page.getByLabel("Confirmar contraseña").fill("DifferentPassword123!");
  await page.getByRole("button", { name: "Guardar nueva contraseña" }).click();
  await expect(page.locator(".login-error[role=alert]")).toContainText("no coinciden"); expect(accepted).toBe(0);
  await page.getByLabel("Confirmar contraseña").fill("NewPassword123!");
  await page.getByRole("button", { name: "Guardar nueva contraseña" }).click();
  await expect(page.getByRole("heading", { name: "Contraseña actualizada" })).toBeVisible(); expect(accepted).toBe(1);
  expect(await page.evaluate(() => sessionStorage.getItem("gymos.accessToken"))).toBeNull();
});

test("el equipo organiza nombres largos, sucursales y acciones sin desbordarse", async ({ page }, testInfo) => {
  const longName = "María Fernanda Hernández del Castillo";
  await boot(page, async (route, path) => path === "/users/roles" ? json(route, [{ id: "role-coach", name: "Coach", permissionCount: 7 }, { id: "role-reception", name: "Recepcion", permissionCount: 5 }]) : path === "/users/internal" ? json(route, [
    staff[0], { ...staff[1], fullName: longName, username: "maria.fernanda.hernandez.del.castillo@example.test", branches: ["Centro", "Sucursal Tecnológica Norte"] },
    { ...staff[1], id: "reception", fullName: "Luis Recepción", roles: ["Recepcion"], username: "luis@example.test" },
    { ...staff[1], id: "suspended", fullName: "Ana Suspendida", status: "SUSPENDED" },
  ]) : false);
  await page.goto("/users");
  const users = page.getByRole("region", { name: "Usuarios del gimnasio" });
  await expect(users.getByRole("article")).toHaveCount(4);
  await expect(page.getByText("4 usuarios · 3 activos", { exact: true })).toBeVisible();
  await expect(users.getByRole("article").filter({ hasText: "Dueño Prueba" }).getByRole("button")).toHaveCount(0);
  const suspended = users.getByRole("article").filter({ hasText: "Ana Suspendida" });
  await expect(suspended.getByRole("button", { name: "Recuperar contraseña" })).toHaveCount(0);
  await expect(suspended.getByRole("button", { name: "Reactivar" })).toBeVisible();
  for (const width of [1440, 1024, 768, 390]) {
    await page.setViewportSize({ width, height: 900 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
    expect(await users.evaluate((node) => node.scrollWidth <= node.clientWidth + 1)).toBe(true);
    const coach = users.getByRole("article").filter({ hasText: longName });
    await coach.scrollIntoViewIfNeeded();
    const row = (await coach.boundingBox())!;
    for (const button of await coach.getByRole("button").all()) {
      const box = (await button.boundingBox())!;
      expect(box.width).toBeGreaterThanOrEqual(39);
      expect(box.x).toBeGreaterThanOrEqual(row.x);
      expect(box.x + box.width).toBeLessThanOrEqual(row.x + row.width);
    }
    if (width === 1440 || width === 390) await page.screenshot({ path: testInfo.outputPath(`equipo-${width}.png`) });
  }
});

test("recuperación enviada muestra un resultado breve y permite regenerar sin conservar el enlace anterior", async ({ page }, testInfo) => {
  let issued = 0;
  await boot(page, async (route, path) => path === `/users/internal/${coachId}/password-recovery`
    ? json(route, { resetUrl: `https://example.invalid/reset-password#token=private-link-${++issued}`, expiresAt: "2099-01-01T15:00:00Z", emailDeliveryStatus: "SENT" }) : false);
  await page.goto("/users");
  await page.getByRole("button", { name: "Recuperar contraseña" }).click();
  const drawer = page.getByRole("dialog");
  await expect(drawer.getByRole("heading", { name: "Restablecer contraseña" })).toBeVisible();
  await expect(drawer.locator(".app-drawer-card")).toHaveCSS("background-color", "rgb(255, 255, 255)");
  await expect(drawer.getByText("ana@example.test", { exact: true })).toHaveCount(1);
  await expect(drawer.locator("details")).not.toHaveAttribute("open", "");
  await page.screenshot({ path: testInfo.outputPath("recuperacion-inicial.png") });
  await drawer.getByRole("button", { name: "Generar y enviar enlace" }).click();
  await expect(drawer.getByRole("status")).toContainText("Enlace enviado");
  await expect(drawer.getByLabel("Enlace privado de recuperación")).toHaveCount(0);
  await drawer.getByRole("button", { name: "Ver enlace", exact: true }).click();
  await expect(drawer.getByLabel("Enlace privado de recuperación")).toHaveValue(/private-link-1$/);
  await expect(drawer.getByText("Invalida el anterior.", { exact: true })).toBeVisible();
  await drawer.getByRole("button", { name: "Generar otro enlace", exact: true }).click();
  await expect.poll(() => issued).toBe(2);
  await expect(drawer.getByLabel("Enlace privado de recuperación")).toHaveCount(0);
  await drawer.getByRole("button", { name: "Ver enlace", exact: true }).click();
  await expect(drawer.getByLabel("Enlace privado de recuperación")).toHaveValue(/private-link-2$/);
  await drawer.getByRole("button", { name: "Ocultar enlace", exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath("recuperacion-enviada-mobile.png") });
  await drawer.getByRole("button", { name: "Cerrar", exact: true }).click();
  await page.getByRole("button", { name: "Recuperar contraseña" }).click();
  await expect(drawer.getByRole("button", { name: "Generar y enviar enlace" })).toBeVisible();
  await expect(drawer.getByLabel("Enlace privado de recuperación")).toHaveCount(0);
  expect(await page.evaluate(() => JSON.stringify({ session: { ...sessionStorage }, local: { ...localStorage } }))).not.toContain("private-link-");
});

test("cancelar un cambio de rol conserva el rol vigente en la fila", async ({ page }) => {
  let mutations = 0;
  await boot(page, async (route, path) => {
    if (route.request().method() !== "GET") mutations++;
    if (path === "/users/roles") return json(route, [{ id: "role-coach", name: "Coach" }, { id: "role-reception", name: "Recepcion" }]);
    return false;
  });
  await page.goto("/users");
  const role = page.getByRole("combobox", { name: "Rol de Coach Ana" });
  await expect(role).toHaveValue("role-coach");
  page.once("dialog", (dialog) => dialog.dismiss());
  await role.selectOption("role-reception");
  await expect(role).toHaveValue("role-coach");
  expect(mutations).toBe(0);
});

test("gerencia ve su equipo y limita los cambios de cuentas que cubren otras sucursales", async ({ page }) => {
  await boot(page, async (route, path) => path === "/users/internal" ? json(route, [
    staff[0], staff[1],
    { ...staff[1], id: "other-branch", fullName: "Coach Norte", branchIds: ["other-branch"], branches: ["Norte"] },
    { ...staff[1], id: "shared-coach", fullName: "Coach Compartido", branchIds: [branchId, "other-branch"], branches: ["Centro", "Norte"] },
  ]) : false, { ...owner, userId: "manager", roles: ["Gerente"], console: "TENANT_MANAGER" });
  await page.goto("/users");
  const rows = page.getByRole("region", { name: "Usuarios del gimnasio" }).getByRole("article");
  await expect(rows.filter({ hasText: "Coach Ana" }).getByRole("button", { name: "Recuperar contraseña" })).toBeVisible();
  await expect(rows.filter({ hasText: "Coach Norte" })).toHaveCount(0);
  await expect(rows.filter({ hasText: "Coach Compartido" }).getByText("Solo lectura")).toBeVisible();
  await expect(rows.filter({ hasText: "Dueño Prueba" })).toHaveCount(0);
});

test("un gerente con user.read puede consultar al equipo sin acciones de escritura", async ({ page }) => {
  await boot(page, undefined, { ...owner, userId: "manager", roles: ["Gerente"], console: "TENANT_MANAGER",
    permissions: owner.permissions.filter((permission) => permission !== "user.manage") });
  await page.goto("/users");
  await expect(page.getByText("Coach Ana", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Crear usuario" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Recuperar contraseña" })).toHaveCount(0);
  await expect(page.getByRole("combobox", { name: "Rol de Coach Ana" })).toHaveCount(0);
});

test("los tres historiales de pagos solicitan 100 y mantienen scroll interno", async ({ page }) => {
  const queries: URL[] = [];
  const entries = Array.from({ length: 127 }, (_, index) => ({ id: `history-${index}`, dueOn: "2026-08-26", status: "ACTIVE", amountCents: 10000, currency: "MXN", attemptNumber: 1, lastError: null, consentedAt: "2026-08-26T12:00:00Z", nextChargeOn: "2026-09-26", consecutiveFailures: 0, termsVersion: "v1", receivedAt: "2026-08-26T12:00:00Z", provider: "STRIPE", externalEventId: `test-event-${index}`, eventType: "test", errorMessage: null }));
  await boot(page, async (route, path, url) => {
    if (path === "/payment-settings") return json(route, { branchId, receptionManualTerminalEnabled: false, receptionMercadoPagoEnabled: false, stripeSelfCheckoutEnabled: false, stripeRecurringEnabled: false, stripeConnected: false, mercadoPagoConnected: false });
    if (path === "/payment-settings/operations") return json(route, { pastDueMandates: 0, retryCharges: 0, failedCharges: 0, failedWebhooks: 0, staleOperations: 0, alertLevel: "HEALTHY" });
    if (path.startsWith("/payment-settings/history/")) {
      queries.push(url);
      const page = Number(url.searchParams.get("page")), size = Number(url.searchParams.get("size"));
      return json(route, { items: entries.slice(page * size, (page + 1) * size), page, size, totalElements: entries.length, totalPages: Math.ceil(entries.length / size) });
    }
    return false;
  }, { ...owner, permissions: [...owner.permissions, "payment.settings.read"] });
  await page.goto("/settings/organization#payment-operations");
  const history = page.locator("#payment-operations");
  for (const label of ["Cargos", "Consentimientos", "Webhooks"]) {
    await history.getByRole("button", { name: label, exact: true }).click();
    const list = history.getByRole("region", { name: "Registros del historial de pagos" });
    await expect(list.getByRole("row")).toHaveCount(101);
    await expect(list).toHaveCSS("scrollbar-width", "none");
    await expect(history).toContainText("127 registros · Página 1 de 2");
    await history.getByRole("button", { name: "Siguiente", exact: true }).click();
    await expect(list.getByRole("row")).toHaveCount(28);
    await expect(history.getByRole("button", { name: "Siguiente" })).toBeDisabled();
  }
  expect(queries.length).toBeGreaterThanOrEqual(6);
  expect(queries.every((url) => url.searchParams.get("size") === "100")).toBe(true);
});

for (const delivery of ["NO_EMAIL", "NOT_CONFIGURED"]) {
  test(`recuperación ${delivery} ofrece entrega privada sin afirmar que se envió correo`, async ({ page }) => {
    await boot(page, async (route, path) => {
      if (path === "/users/internal" && delivery === "NO_EMAIL") return json(route, [staff[0], { ...staff[1], email: null }]);
      if (path === `/users/internal/${coachId}/password-recovery`) return json(route, { resetUrl: "https://example.invalid/reset-password#token=private-manual", expiresAt: "2099-01-01T15:00:00Z", emailDeliveryStatus: delivery });
      return false;
    });
    await page.goto("/users"); await page.getByRole("button", { name: "Recuperar contraseña" }).click();
    const drawer = page.getByRole("dialog");
    await drawer.getByRole("button", { name: delivery === "NO_EMAIL" ? "Generar enlace privado" : "Generar y enviar enlace" }).click();
    await expect(drawer.getByRole("status")).toContainText(delivery === "NO_EMAIL" ? "no tiene correo" : "no está configurado");
    await expect(drawer.getByLabel("Enlace privado de recuperación")).toHaveValue(/private-manual$/);
    await expect(drawer.getByText("Verifica su identidad antes de compartir el enlace.")).toBeVisible();
    await drawer.getByText("Cómo funciona la recuperación", { exact: true }).click();
    await expect(drawer.getByText(/entonces se cierran sus sesiones anteriores/)).toBeVisible();
  });
}

test("fallo al copiar revela el enlace y un fallo al generar no simula un envío", async ({ page }) => {
  let fail = true;
  await boot(page, async (route, path) => path === `/users/internal/${coachId}/password-recovery`
    ? fail ? json(route, { message: "Espera antes de generar otro enlace." }, 429)
      : json(route, { resetUrl: "https://example.invalid/reset-password#token=copy-fallback", expiresAt: "2099-01-01T15:00:00Z", emailDeliveryStatus: "SENT" }) : false);
  await page.addInitScript(() => Object.defineProperty(navigator, "clipboard", { value: { writeText: () => Promise.reject(new Error("Clipboard unavailable")) } }));
  await page.goto("/users"); await page.getByRole("button", { name: "Recuperar contraseña" }).click();
  const drawer = page.getByRole("dialog");
  await drawer.getByRole("button", { name: "Generar y enviar enlace" }).click();
  await expect(drawer.getByRole("alert")).toContainText("Espera antes de generar");
  await expect(drawer.getByRole("status")).toHaveCount(0);
  fail = false;
  await drawer.getByRole("button", { name: "Generar y enviar enlace" }).click();
  await expect(drawer.getByRole("status")).toContainText("Enlace enviado");
  await drawer.getByRole("button", { name: "Copiar enlace privado" }).click();
  await expect(drawer.getByRole("alert")).toContainText("No se pudo copiar");
  await expect(drawer.getByLabel("Enlace privado de recuperación")).toHaveValue(/copy-fallback$/);
});

test("el enlace vencido explica cómo generar otro", async ({ page }) => {
  await boot(page, async (route, path) => path === "/auth/password-recovery/accept" ? json(route, { code: "PASSWORD_RESET_INVALID", message: "El enlace no es válido, ya se utilizó o venció. Solicita uno nuevo al administrador del gimnasio." }, 400) : false);
  await page.goto("/reset-password#token=expired-test-token");
  await page.getByLabel("Nueva contraseña", { exact: true }).fill("NewPassword123!"); await page.getByLabel("Confirmar contraseña").fill("NewPassword123!");
  await page.getByRole("button", { name: "Guardar nueva contraseña" }).click();
  await expect(page.locator(".login-error[role=alert]")).toContainText("Solicita uno nuevo");
});

for (const role of ["Dueno del gimnasio", "Gerente", "Administrador"]) {
  test(`indicadores de ${role} muestran semanas con datos y líneas temporales por coach`, async ({ page }, testInfo) => {
    const queries: URL[] = [];
    await boot(page, async (_route, path, url) => { if (path.endsWith("workout-assignments/trend")) queries.push(url); return false; }, { ...owner, roles: [role], console: role === "Gerente" ? "TENANT_MANAGER" : "TENANT_OWNER" });
    await page.goto("/dashboard"); await page.getByRole("button", { name: "Coaches y Rutinas" }).click();
    await expect(page.getByRole("heading", { name: "Tendencia semanal de asignaciones" })).toBeVisible();
    await expect(page.getByText("Renovaciones", { exact: true }).first()).toBeVisible();
    const lines = page.getByTestId("coach-timeline").locator(".recharts-line-curve");
    await expect(lines).toHaveCount(2);
    expect((await lines.first().getAttribute("d"))?.match(/[LC]/g)?.length).toBeGreaterThan(5);
    await expect(page.getByTestId("coaching-dashboard").getByRole("checkbox")).toHaveCount(0);
    const summary = page.getByRole("region", { name: "Resumen por coach", exact: true });
    await expect(summary.getByRole("row").filter({ hasText: "Ana" }).getByRole("cell")).toHaveText(["5", "3", "3"]);
    await expect(summary.getByRole("row").filter({ hasText: "Luis" }).getByRole("cell")).toHaveText(["2", "1", "0"]);
    const plotBox = (await page.getByTestId("coach-timeline").boundingBox())!;
    expect((await summary.boundingBox())!.x).toBeGreaterThanOrEqual(plotBox.x + plotBox.width);
    await expect(page.getByTestId("first-renewal-trend").locator(".recharts-area-curve")).toHaveCount(2);
    expect(queries[0].searchParams.get("from")).toBe(from); expect(queries[0].searchParams.get("to")).toBe(to);
    await page.getByLabel("Vista de evolución por coach").selectOption("daily");
    await expect(page.getByText(/Número de asignaciones creadas cada día/)).toBeVisible();
    await page.getByText("Ver totales y datos semanales").click();
    await expect(page.getByRole("region", { name: "Datos semanales de coaches" })).toContainText("Renovaciones");
    await page.getByTestId("coach-timeline").scrollIntoViewIfNeeded();
    if (role === "Dueno del gimnasio") await page.screenshot({ path: testInfo.outputPath("coaches-indicadores-desktop.png"), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
    const bars = page.getByTestId("coaching-dashboard").locator(".recharts-bar-rectangle .recharts-rectangle");
    await expect(bars).toHaveCount(4);
    await expect.poll(() => bars.evaluateAll((elements) => elements.every((bar) => {
      const bounds = bar.getBoundingClientRect();
      const chart = bar.closest("svg")!.getBoundingClientRect();
      return bounds.left >= chart.left && bounds.right <= chart.right + 1;
    }))).toBe(true);
    if (role === "Dueno del gimnasio") await page.screenshot({ path: testInfo.outputPath("coaches-indicadores-mobile.png"), fullPage: true });
  });
}

test("un error de métricas no se disfraza como cero asignaciones", async ({ page }) => {
  await boot(page, async (route, path) => path.endsWith("workout-assignments/trend") ? json(route, { message: "No se pudieron consultar las asignaciones." }, 500) : false);
  await page.goto("/dashboard"); await page.getByRole("button", { name: "Coaches y Rutinas" }).click();
  await expect(page.getByRole("alert").filter({ hasText: "No se pudieron consultar" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Reintentar métricas" })).toBeVisible();
  await expect(page.getByTestId("coach-timeline")).toHaveCount(0);
});

test("muestra todos los coaches superpuestos, incluso más de cinco, con resumen por fila", async ({ page }, testInfo) => {
  const coaches = Array.from({ length: 8 }, (_, index) => ({ coachId: `coach-${index}`, coachName: index === 7 ? "Valentina Fernández del Castillo" : `Coach ${index + 1}`, assignments: index + 1, uniqueClients: 1, firstWorkouts: 1 }));
  await boot(page, async (route, path) => {
    if (path.endsWith("workout-assignments/summary")) return json(route, { from, to, assignments: 36, uniqueClients: 8, coaches, details: [] });
    if (path === "/reports/coaches") return json(route, { coachCount: 8, workoutCount: 3, activeAssignments: 8, assignmentsLast30Days: 36 });
    if (path.endsWith("workout-assignments/trend")) return json(route, { ...trend, points: [
      { weekFrom: "2026-08-17", weekTo: "2026-08-23", assignments: 21, uniqueClients: 6, firstWorkouts: 6 },
      { weekFrom: "2026-08-24", weekTo: "2026-08-25", assignments: 15, uniqueClients: 2, firstWorkouts: 2 },
    ], coachActivity: coaches.map((coach, index) => ({ ...coach, date: `2026-08-${18 + index}` })) });
    return false;
  });
  await page.goto("/dashboard"); await page.getByRole("button", { name: "Coaches y Rutinas" }).click();
  await expect(page.getByTestId("coach-timeline").locator(".recharts-line-curve")).toHaveCount(8);
  await expect(page.getByRole("list", { name: "Coaches en la gráfica" }).getByRole("listitem")).toHaveCount(8);
  await expect(page.getByRole("region", { name: "Resumen por coach", exact: true }).getByRole("row")).toHaveCount(9);
  await expect(page.getByTestId("coaching-dashboard").getByRole("checkbox")).toHaveCount(0);
  await page.getByTestId("coach-timeline").scrollIntoViewIfNeeded();
  await page.screenshot({ path: testInfo.outputPath("comparacion-ocho-coaches.png"), fullPage: true });
});
