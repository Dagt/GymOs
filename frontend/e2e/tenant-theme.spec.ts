import { test, expect } from "@playwright/test";
import { bootTheme, themeUser } from "./theme-fixtures";

test("restauración retrasada aplica el tema al abrir directamente y al recargar", async ({ page }, info) => {
  const state = await bootTheme(page);
  await page.addInitScript(() => {
    const observed = new Set<string>();
    (window as unknown as { observedInventoryThemes: Set<string> }).observedInventoryThemes = observed;
    new MutationObserver(() => {
      if (document.querySelector("h1")?.textContent === "Inventario") observed.add(getComputedStyle(document.documentElement).getPropertyValue("--brand-primary").trim());
    }).observe(document, { childList: true, subtree: true, attributes: true });
    const network = window.fetch.bind(window);
    window.fetch = async (input, init) => {
      const url = new URL(input instanceof Request ? input.url : String(input), location.href);
      if (url.pathname.endsWith("/auth/refresh")) await new Promise(resolve => setTimeout(resolve, 500));
      return network(input, init);
    };
  });
  await page.goto("/inventory");
  await expect(page.getByRole("heading", { name: "Inventario", exact: true })).toBeVisible();
  await expect.poll(() => page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue("--brand-primary").trim())).toBe(themeUser.primaryColor);
  expect(await page.evaluate(() => [...(window as unknown as { observedInventoryThemes: Set<string> }).observedInventoryThemes])).toEqual([themeUser.primaryColor]);
  await page.screenshot({ path: info.outputPath("tema-carga-directa.png") });
  await page.reload();
  await expect(page.getByRole("heading", { name: "Inventario", exact: true })).toBeVisible();
  await expect.poll(() => page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue("--brand-primary").trim())).toBe(themeUser.primaryColor);
  await page.screenshot({ path: info.outputPath("tema-recarga.png") });
  expect(state.refreshes).toBe(2);
  expect(state.profiles).toBe(0);
});

const color = (page: import("@playwright/test").Page) => page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue("--brand-primary").trim()).catch(() => "navigation");

test("login, navegación, renovación de token, sucursal y logout sincronizan la paleta", async ({ page }) => {
  const state = await bootTheme(page, themeUser, false);
  await page.goto("/login");
  await page.getByPlaceholder("tu@empresa.com").fill("theme@example.test");
  await page.getByPlaceholder("Tu contrasena").fill("qa-only-password");
  await page.getByRole("button", { name: "Entrar a GymFort" }).click();
  await expect(page).not.toHaveURL(/\/login/);
  await expect.poll(() => color(page)).toBe(themeUser.primaryColor);
  await page.goto("/inventory");
  await expect.poll(() => color(page)).toBe(themeUser.primaryColor);
  state.user = { ...state.user, primaryColor: "#943B23" };
  state.expireInventory = true;
  await page.getByRole("button", { name: "Actualizar", exact: true }).click();
  await expect.poll(() => color(page)).toBe("#943B23");
  await Promise.all([
    page.waitForEvent("load"),
    page.getByRole("combobox", { name: "Operar en" }).selectOption("44444444-4444-4444-8444-444444444444"),
  ]);
  await expect.poll(() => state.user.branchId).toBe("44444444-4444-4444-8444-444444444444");
  await expect.poll(() => color(page)).toBe("#943B23");
  expect(state.profiles).toBe(0);
  await page.getByRole("button", { name: "Salir", exact: true }).click();
  await expect.poll(() => color(page)).toBe("#093D3A");
});

test("guardar colores actualiza el tema en la misma página y no acepta un perfil anterior", async ({ page }, info) => {
  const state = await bootTheme(page, { ...themeUser, primaryColor: null, accentColor: null, surfaceColor: null });
  let release!: () => void;
  const deferred = new Promise<void>(resolve => { release = resolve; });
  state.profileHandler = async route => { await deferred; await route.fulfill({ json: { ...themeUser, primaryColor: "#AABBCC" } }); };
  await page.goto("/settings/organization");
  await page.getByRole("button", { name: "Editar gimnasio", exact: true }).click();
  await page.getByLabel("RFC", { exact: false }).fill("XAXX010101000");
  await page.getByLabel("Color acento", { exact: false }).fill("#f5ae31");
  await page.getByLabel("Color fondo", { exact: false }).fill("#f8f3fc");
  await page.getByLabel("Telefono", { exact: false }).fill("5512345678");
  await page.getByLabel("Email de contacto", { exact: false }).fill("qa@example.test");
  await page.getByLabel("Color principal", { exact: false }).fill("#45127a");
  await page.getByRole("button", { name: "Guardar gimnasio" }).click();
  await expect.poll(() => color(page)).toBe("#45127a");
  const path = new URL(page.url()).pathname;
  expect(path).toBe("/settings/organization");
  const oldResponse = page.waitForResponse(response => response.url().endsWith("/auth/me"));
  release();
  await oldResponse;
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  await expect.poll(() => state.profiles).toBe(1);
  await expect.poll(() => color(page)).toBe("#45127a");
  await page.screenshot({ path: info.outputPath("tema-actualizado-sin-navegar.png") });
});

test("perfil incompleto se consulta una vez y la navegación no duplica listeners ni consultas", async ({ page }) => {
  const state = await bootTheme(page, { ...themeUser, primaryColor: null, accentColor: null, surfaceColor: null });
  await page.addInitScript(() => {
    const listeners = new Set<EventListenerOrEventListenerObject>();
    const add = window.addEventListener.bind(window), remove = window.removeEventListener.bind(window);
    window.addEventListener = ((type: string, listener: EventListenerOrEventListenerObject, options?: AddEventListenerOptions | boolean) => {
      if (type === "gymos.session-refreshed") listeners.add(listener);
      add(type, listener, options);
    }) as typeof window.addEventListener;
    window.removeEventListener = ((type: string, listener: EventListenerOrEventListenerObject, options?: EventListenerOptions | boolean) => {
      if (type === "gymos.session-refreshed") listeners.delete(listener);
      remove(type, listener, options);
    }) as typeof window.removeEventListener;
    (window as unknown as { themeListenerCount: () => number }).themeListenerCount = () => listeners.size;
  });
  state.profileHandler = route => route.fulfill({ json: themeUser });
  await page.goto("/inventory");
  await expect.poll(() => color(page)).toBe(themeUser.primaryColor);
  expect(state.profiles).toBe(1);
  for (let index = 0; index < 3; index++) {
    const catalogLink = page.locator('nav a[href="/catalog"]');
    if (!await catalogLink.isVisible()) await page.locator("nav summary").filter({ hasText: "Administración" }).click();
    await catalogLink.click();
    await expect(page).toHaveURL(/\/catalog/);
    await expect.poll(() => color(page)).toBe(themeUser.primaryColor);
    const inventoryLink = page.locator('nav a[href="/inventory"]');
    if (!await inventoryLink.isVisible()) await page.locator("nav summary").filter({ hasText: "Gestión operativa" }).click();
    await inventoryLink.click();
    await expect(page).toHaveURL(/\/inventory/);
    await expect.poll(() => color(page)).toBe(themeUser.primaryColor);
  }
  expect(state.profiles).toBe(1);
  expect(state.refreshes).toBe(1);
  expect(await page.evaluate(() => (window as unknown as { themeListenerCount: () => number }).themeListenerCount())).toBe(1);
});

test("plataforma conserva sus colores y soporte usa la paleta del gimnasio hasta salir", async ({ page }) => {
  const state = await bootTheme(page, { ...themeUser, accountScope: "PLATFORM", platformAdmin: true, supportMode: true, supportExpiresAt: "2099-01-01T00:00:00Z", console: "TENANT_OWNER", permissions: [...themeUser.permissions, "platform.admin"] });
  await page.goto("/inventory");
  await expect.poll(() => color(page)).toBe(themeUser.primaryColor);
  await page.getByRole("button", { name: /Salir.*soporte|Terminar.*soporte|Finalizar.*soporte/ }).click();
  await expect.poll(() => color(page)).toBe("#093D3A");
  expect(state.user.supportMode).toBe(false);
  expect(state.profiles).toBe(0);
});


test("un perfil rechazado no provoca un ciclo de refresh ni repite consultas al navegar", async ({ page }) => {
  const state = await bootTheme(page, { ...themeUser, primaryColor: null, accentColor: null, surfaceColor: null });
  state.profileHandler = route => route.fulfill({ status: 401, json: { message: "Perfil rechazado de prueba" } });
  await page.goto("/inventory");
  await expect(page.getByRole("heading", { name: "Inventario", exact: true })).toBeVisible();
  await expect.poll(() => state.profiles).toBe(2);
  await page.locator("nav summary").filter({ hasText: "Administración" }).click();
  await page.locator('nav a[href="/catalog"]').click();
  await expect(page).toHaveURL(/\/catalog/);
  await expect(page.getByRole("region", { name: "Filtros del catálogo" })).toBeVisible();
  expect(state.profiles).toBe(2);
  expect(state.refreshes).toBe(2);
});


test("cambiar de gimnasio descarta el perfil tardío del tenant anterior", async ({ page }) => {
  const state = await bootTheme(page, { ...themeUser, primaryColor: null, accentColor: null, surfaceColor: null });
  let release!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });
  state.profileHandler = async route => { await gate; await route.fulfill({ json: themeUser }); };
  await page.goto("/inventory");
  await expect.poll(() => state.profiles).toBe(1);
  await page.getByRole("button", { name: "Salir", exact: true }).click();
  await expect(page).toHaveURL(/\/login/);
  await expect.poll(() => color(page)).toBe("#093D3A");
  state.user = { ...themeUser, organizationId: "55555555-5555-4555-8555-555555555555", branchId: "44444444-4444-4444-8444-444444444444", brandName: "Otro Gimnasio QA", primaryColor: "#315975", accentColor: "#E89145", surfaceColor: "#EEF4F8" };
  await page.getByPlaceholder("tu@empresa.com").fill("theme@example.test");
  await page.getByPlaceholder("Tu contrasena").fill("qa-only-password");
  await page.getByRole("button", { name: "Entrar a GymFort" }).click();
  await expect.poll(() => color(page)).toBe("#315975");
  const oldResponse = page.waitForResponse(response => response.url().endsWith("/auth/me"));
  release(); await oldResponse;
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  await expect.poll(() => color(page)).toBe("#315975");
});


test("un guardado tardío no reactiva el tema de un contexto invalidado", async ({ page }) => {
  await bootTheme(page);
  let release!: () => void;
  let requestSeen = false;
  const gate = new Promise<void>(resolve => { release = resolve; });
  await page.route("**/api/v1/organizations/current", async route => {
    if (route.request().method() !== "PUT") return route.fallback();
    requestSeen = true;
    const payload = route.request().postDataJSON();
    await gate;
    await route.fulfill({ json: { id: themeUser.organizationId, ...payload } });
  });
  await page.goto("/settings/organization");
  await page.getByRole("button", { name: "Editar gimnasio", exact: true }).click();
  await page.getByLabel("RFC", { exact: false }).fill("XAXX010101000");
  await page.getByLabel("Telefono", { exact: false }).fill("5512345678");
  await page.getByLabel("Email de contacto", { exact: false }).fill("qa@example.test");
  await page.getByLabel("Color principal", { exact: false }).fill("#45127a");
  await page.getByRole("button", { name: "Guardar gimnasio" }).click();
  await expect.poll(() => requestSeen).toBe(true);
  await page.evaluate(() => window.dispatchEvent(new Event("gymfort.session-context-changed")));
  await expect.poll(() => color(page)).toBe("#093D3A");
  const response = page.waitForResponse(response => response.url().endsWith("/organizations/current") && response.request().method() === "PUT");
  release(); await response;
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  await expect.poll(() => color(page)).toBe("#093D3A");
  await expect(page.getByRole("heading", { name: "La sesión cambió en otra pestaña" })).toBeVisible();
});
