import { expect, test, type Page } from "@playwright/test";

type TestUser = {
  userId: string;
  organizationId: string;
  branchId: string | null;
  username: string;
  fullName: string;
  permissions: string[];
  roles: string[];
  platformAdmin: boolean;
  supportMode: boolean;
  console: "PLATFORM_OWNER" | "TENANT_OWNER" | "TENANT_MANAGER" | "RECEPTION" | "COACH";
  organizationName?: string | null;
  brandName?: string | null;
  logoUrl?: string | null;
  primaryColor?: string | null;
  accentColor?: string | null;
  surfaceColor?: string | null;
};

const org = {
  id: "11111111-1111-4111-8111-111111111111",
  legalName: "Appex Hub Deportivo S.A. de C.V.",
  tradeName: "Appex",
  taxId: "APPX260101VA1",
  status: "ACTIVE",
  timezone: "America/Mexico_City",
  defaultCurrency: "MXN",
  contactEmail: "contacto@appex.com",
  contactPhone: "+524921234567",
  brandName: "APPEX",
  logoUrl: "",
  primaryColor: "#164E63",
  accentColor: "#EAB308",
  surfaceColor: "#F4F7FB",
};

const branches = [
  {
    id: "22222222-2222-4222-8222-222222222222",
    name: "Centro",
    code: "APPX-CEN",
    status: "ACTIVE",
    timezone: "America/Mexico_City",
    phone: "+524921234567",
    accessScope: "FULL",
  },
  {
    id: "33333333-3333-4333-8333-333333333333",
    name: "Revolucion 115",
    code: "APPX-REV",
    status: "ACTIVE",
    timezone: "America/Mexico_City",
    phone: "+524921234568",
    accessScope: "FULL",
  },
];

test.describe("consolas por rol", () => {
  test("Admin GymOS entra al CRM de gimnasios cliente", async ({ page }) => {
    await bootAs(page, user({
      console: "PLATFORM_OWNER",
      platformAdmin: true,
      roles: ["Administrador GymOS"],
      permissions: ["platform.admin"],
      branchId: null,
    }));

    await page.goto("/platform/gymnasiums");

    await expect(page.getByRole("heading", { name: /CRM de gimnasios cliente/i })).toBeVisible();
    await expect(page.getByLabel("Perfil activo").getByText("Admin GymOS")).toBeVisible();
    await expect(page.locator("body")).toContainText("CRM de gimnasios");
    await expect(page.locator("body")).toContainText("Nuevo gimnasio");
  });

  test("dueno de gimnasio ve direccion y administracion del tenant", async ({ page }) => {
    await bootAs(page, user({
      console: "TENANT_OWNER",
      roles: ["Dueno del gimnasio"],
      permissions: ownerPermissions(),
    }));

    await page.goto("/dashboard");

    await expect(page.getByRole("heading", { name: /Panel ejecutivo|Dashboard|Metricas/i })).toBeVisible();
    await expect(page.getByLabel("Perfil activo").getByText("Dueño del gimnasio")).toBeVisible();
    await expect(page.locator("body")).toContainText("Administracion del gimnasio");
    await expect(page.locator("body")).toContainText("Datos y sucursales");
  });

  test("gerente conserva menu operativo sin rutas de plataforma", async ({ page }) => {
    await bootAs(page, user({
      console: "TENANT_MANAGER",
      roles: ["Gerente"],
      permissions: managerPermissions(),
    }));

    await page.goto("/operations");

    await expect(page.getByRole("heading", { name: /Operacion diaria/i })).toBeVisible();
    await expect(page.getByLabel("Perfil activo").getByText("Gerente")).toBeVisible();
    await expect(page.locator("body")).toContainText("Administracion diaria");
    await expect(page.locator("body")).toContainText("Mostrador");
    await expect(page.getByRole("link", { name: /Gimnasios cliente/i })).toHaveCount(0);
  });

  test("recepcion entra a mostrador y no ve administracion", async ({ page }) => {
    await bootAs(page, user({
      console: "RECEPTION",
      roles: ["Recepcion"],
      permissions: receptionPermissions(),
    }));

    await page.goto("/reception");

    await expect(page.getByRole("heading", { name: /Recepcion|Revolucion/i })).toBeVisible();
    await expect(page.getByText(/Entraste como Recepcion/i)).toBeVisible();
    await expect(page.getByText(/Cobrar venta o membresia/i)).toBeVisible();
    await expect(page.getByRole("link", { name: /Equipo/i })).toHaveCount(0);
    await expect(page.getByRole("link", { name: /Datos y sucursales/i })).toHaveCount(0);
  });

  test("Admin GymOS en soporte ve banda y regreso a administracion", async ({ page }) => {
    await bootAs(page, user({
      console: "TENANT_OWNER",
      platformAdmin: true,
      supportMode: true,
      roles: ["Administrador GymOS"],
      permissions: ownerPermissions().concat("platform.admin"),
    }));

    await page.goto("/dashboard");

    await expect(page.getByText("Modo soporte GymOS")).toBeVisible();
    await expect(page.getByRole("link", { name: /Volver a administracion de gimnasios/i })).toBeVisible();
    await expect(page.getByLabel("Perfil activo").getByText("Soporte GymOS")).toBeVisible();
  });
});

async function bootAs(page: Page, currentUser: TestUser) {
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/^\/api\/v1/, "");
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(responseFor(path, currentUser)),
    });
  });

  await page.addInitScript(({ user: initUser }) => {
    window.localStorage.setItem("gymos.accessToken", "e2e-access-token");
    window.localStorage.setItem("gymos.refreshToken", "e2e-refresh-token");
    window.localStorage.setItem("gymos.currentUser", JSON.stringify(initUser));
  }, { user: currentUser });
}

function responseFor(path: string, currentUser: TestUser) {
  if (path === "/auth/me") {
    return currentUser;
  }
  if (path === "/organizations") {
    return [{ ...org, branchCount: branches.length, activeBranchCount: branches.length }];
  }
  if (path === "/organizations/current") {
    return org;
  }
  if (path === "/organizations/current/branches" || path.endsWith("/branches")) {
    return branches;
  }
  if (path.startsWith("/reports/owner-dashboard")) {
    return {
      fromDate: "2026-07-01",
      toDate: "2026-07-23",
      paidRevenueCents: 0,
      saleCount: 0,
      newClients: 0,
      activeMemberships: 0,
      bookingCount: 0,
      lowStockItems: 0,
      activeWorkoutAssignments: 0,
      activeProfessionals: 0,
      queuedNotifications: 0,
      enabledAutomations: 0,
    };
  }
  if (path.includes("/reports/summary")) {
    return {
      totals: {},
      salesByStatus: [],
      paymentsByMethod: [],
      recentSales: [],
    };
  }
  return [];
}

function user(overrides: Partial<TestUser>): TestUser {
  return {
    userId: "44444444-4444-4444-8444-444444444444",
    organizationId: org.id,
    branchId: branches[0].id,
    username: "usuario@appex.com",
    fullName: "Usuario Appex",
    permissions: [],
    roles: [],
    platformAdmin: false,
    supportMode: false,
    console: "TENANT_MANAGER",
    organizationName: org.tradeName,
    brandName: org.brandName,
    logoUrl: org.logoUrl,
    primaryColor: org.primaryColor,
    accentColor: org.accentColor,
    surfaceColor: org.surfaceColor,
    ...overrides,
  };
}

function ownerPermissions() {
  return [
    "report.owner.read",
    "report.sales.read",
    "operations.read",
    "inventory.read",
    "booking.read",
    "booking.manage",
    "client.read",
    "client.create",
    "user.read",
    "user.manage",
    "organization.update",
    "branch.update",
    "catalog.read",
    "catalog.manage",
    "resource.read",
    "resource.manage",
    "sale.create",
    "access.read",
    "visit.read",
    "benefit.read",
    "coach.read",
    "professional.read",
    "automation.read",
  ];
}

function managerPermissions() {
  return [
    "operations.read",
    "booking.read",
    "booking.manage",
    "inventory.read",
    "client.read",
    "client.create",
    "user.read",
    "user.manage",
    "catalog.read",
    "sale.create",
    "report.sales.read",
    "benefit.read",
    "coach.read",
  ];
}

function receptionPermissions() {
  return [
    "client.read",
    "client.create",
    "sale.create",
    "payment.manual.create",
    "booking.read",
    "booking.manage",
    "report.sales.read",
    "benefit.read",
  ];
}
