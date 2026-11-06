import { expect, test, type Page } from "@playwright/test";

const api = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:18028/api/v1";
const email = process.env.GYMOS_DEMO_OWNER_EMAIL;
const password = process.env.GYMOS_DEMO_OWNER_PASSWORD;

async function submitLogin(page: Page, candidate: string) {
  if (!email) throw new Error("The isolated authentication fixture is missing its email.");
  await page.getByLabel("Usuario o correo").fill(email);
  await page.getByLabel("Contrasena").fill(candidate);
  const received = page.waitForResponse((response) => response.url() === api + "/auth/login");
  await page.getByRole("button", { name: "Entrar a GymFort" }).click();
  return received;
}

test("login, refresh tras una respuesta perdida y logout contra Spring real", async ({ page, context }) => {
  if (!password) throw new Error("The isolated authentication fixture is missing its password.");
  await page.goto("/login");
  expect((await submitLogin(page, "WrongFixturePassword!2026")).status()).toBe(401);
  await expect(page.getByText("Usuario o contraseña incorrectos.")).toBeVisible();
  const login = await submitLogin(page, password);
  expect(login.status()).toBe(200);
  const issued = await login.json();
  expect(Object.hasOwn(issued, "refreshToken")).toBe(false);
  await expect(page).toHaveURL(/\/dashboard$/);

  const cookies = (await context.cookies()).filter((cookie) => cookie.name.startsWith("__Host-gymfort-refresh-"));
  expect(cookies.map(({ httpOnly, secure, sameSite, path }) => ({ httpOnly, secure, sameSite, path })))
    .toEqual([{ httpOnly: true, secure: true, sameSite: "Strict", path: "/" }]);
  expect(await page.evaluate(() => ({
    visibleCookie: document.cookie.includes("__Host-gymfort-refresh-"),
    tokensStored: [localStorage, sessionStorage].some((storage) => ["gymos.accessToken", "gymos.refreshToken"]
      .some((key) => storage.getItem(key) !== null)),
  }))).toEqual({ visibleCookie: false, tokensStored: false });

  const kioskTab = await context.newPage();
  await kioskTab.goto("/reception");
  await expect(kioskTab.getByText("Tu sesión terminó o todavía no has iniciado sesión en esta pestaña.")).toBeVisible();
  await kioskTab.close();

  const operations: string[] = [];
  await page.route(api + "/auth/refresh", async (route) => {
    operations.push(route.request().headers()["x-gymfort-refresh-request"]);
    if (operations.length === 1) {
      const upstream = await route.fetch();
      expect(upstream.status()).toBe(200);
      await route.abort("connectionreset");
    } else {
      await route.continue();
    }
  });
  await page.reload();
  await expect(page.getByRole("button", { name: "Salir", exact: true })).toBeVisible();
  expect(operations.length >= 2 && operations[0] === operations[1]).toBe(true);
  await page.unroute(api + "/auth/refresh");

  await page.route(api + "/auth/logout", (route) => route.abort("connectionreset"), { times: 1 });
  await page.getByRole("button", { name: "Salir", exact: true }).click();
  await expect(page.getByText("No se pudo conectar con GymFort. Revisa tu conexión e intenta de nuevo.")).toBeVisible();
  await page.getByRole("button", { name: "Salir", exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect((await context.cookies()).some((cookie) => cookie.name.startsWith("__Host-gymfort-refresh-"))).toBe(false);
  const rejected = await fetch(api + "/auth/me", { headers: { Authorization: `Bearer ${issued.accessToken}` } });
  expect(rejected.status()).toBe(401);
});

test("la navegación mantiene la sesión al caducar el JWT real", async ({ page }) => {
  if (!password) throw new Error("The isolated authentication fixture is missing its password.");
  await page.goto("/login");
  const login = await submitLogin(page, password);
  expect(login.status()).toBe(200);
  const issued = await login.json();
  expect(issued.expiresIn).toBe(12);
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect.poll(async () => {
    const response = await fetch(api + "/auth/me", { headers: { Authorization: `Bearer ${issued.accessToken}` } });
    return response.status;
  }, { timeout: 20_000, intervals: [500, 1000] }).toBe(401);
  const renewed = page.waitForResponse((response) => response.url() === api + "/auth/refresh" && response.status() === 200);
  await page.locator('a[href="/clients"]').first().click();
  await renewed;
  await expect(page).toHaveURL(/\/clients$/);
  await expect(page.getByRole("button", { name: "Salir", exact: true })).toBeVisible();
});
