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

async function json(route: Route, value: unknown, status = 200) { await route.fulfill({ status, contentType: "application/json", body: JSON.stringify(value) }); return true; }

async function boot(page: Page, override?: (route: Route, path: string, url: URL) => Promise<boolean>, currentUser = owner) {
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url()), path = url.pathname.replace(/^\/api\/v1/, "");
    if (override && await override(route, path, url)) return;
    if (path === "/auth/me") return void await json(route, currentUser);
    if (path === "/system/info") return void await json(route, { capabilities: ["operational-pagination-v1"] });
    if (path === "/organizations/current") return void await json(route, { id: orgId, tradeName: "Gimnasio de prueba" });
    if (path.endsWith("/branches")) return void await json(route, [{ id: branchId, name: "Centro", code: "CEN" }]);
    if (path === "/benefits") return void await json(route, [benefit]);
    if (path === "/catalog/plans") return void await json(route, [{ id: vip, name: "VIP", durationDays: 30, status: "ACTIVE" }, { id: daily, name: "VIP diario", durationDays: 1, status: "ACTIVE" }]);
    if (path === "/users/internal") return void await json(route, staff);
    if (path === "/users/roles") return void await json(route, [{ id: "role-coach", name: "Coach", permissionCount: 7 }]);
    if (path === "/reports/owner-dashboard") return void await json(route, { fromDate: url.searchParams.get("fromDate") ?? from, toDate: url.searchParams.get("toDate") ?? to, paidRevenueCents: 0, saleCount: 0, newClients: 0, activeMemberships: 0, bookingCount: 0, lowStockItems: 0, activeWorkoutAssignments: 2, activeProfessionals: 0, queuedNotifications: 0, enabledAutomations: 0 });
    if (path === "/reports/coaches") return void await json(route, { coachCount: 2, workoutCount: 3, activeAssignments: 2, assignmentsLast30Days: 7 });
    if (path === "/reports/coaches/workout-assignments/summary") return void await json(route, { from, to, assignments: 7, uniqueClients: 4, activeCoaches: 2, coachesWithoutAssignments: 0, previousAssignments: 1, previousUniqueClients: 1, assignmentChange: 6, uniqueClientChange: 3, page: 0, size: 1, totalDetails: 7, totalPages: 7, coaches: [], details: [] });
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
  await page.getByRole("button", { name: "Editar Sauna" }).click();
  const drawer = page.getByRole("dialog");
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
    expect((await lines.first().getAttribute("d"))?.match(/L/g)?.length).toBeGreaterThan(5);
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
    await expect(bars).toHaveCount(8);
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
