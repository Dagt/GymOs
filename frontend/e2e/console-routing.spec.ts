import { mockStaffSession } from "./session-fixtures";
import { expect, test, type Page, type Route } from "@playwright/test";
import type { PlatformOwnerAccess } from "../src/lib/api/platform-customer-types";

type TestUser = {
  userId: string;
  accountScope: "PLATFORM" | "TENANT";
  organizationId: string | null;
  branchId: string | null;
  username: string;
  fullName: string;
  permissions: string[];
  roles: string[];
  platformAdmin: boolean;
  supportMode: boolean;
  supportExpiresAt: string | null;
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
  test("Admin GymFort entra al CRM de gimnasios cliente", async ({ page }) => {
    await bootAs(page, user({
      console: "PLATFORM_OWNER",
      platformAdmin: true,
      roles: ["Administrador GymOS"],
      permissions: ["platform.admin"],
      branchId: null,
    }));

    await page.goto("/platform/gymnasiums");

    await expect(page.getByRole("heading", { name: /CRM de gimnasios cliente/i })).toBeVisible();
    await expect(page.getByText("Admin GymFort", { exact: true })).toBeVisible();
    await expect(page.locator("body")).toContainText("CRM de gimnasios");
    await expect(page.locator("body")).toContainText("Nuevo gimnasio");
  });

  test("el resumen de cartera evita accesos duplicados al menu lateral", async ({ page }) => {
    await bootAs(page, user({
      console: "PLATFORM_OWNER",
      platformAdmin: true,
      roles: ["Administrador GymOS"],
      permissions: ["platform.admin"],
      branchId: null,
    }));

    await page.goto("/platform");

    await expect(page.getByRole("heading", { name: "Cartera de gimnasios" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Exportar cartera CSV" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Gestionar clientes" })).toHaveCount(0);
    await expect(page.getByRole("heading", { name: "Administración diaria" })).toHaveCount(0);
    await expect(page.getByText("Atajos", { exact: true })).toHaveCount(0);
  });

  test("alta de gimnasio confirma el tenant, envía la invitación y no consulta la colección tenant", async ({ page }) => {
    let legacyLogoRequests = 0;
    let legacyOrganizationReads = 0;
    let platformCustomerCreates = 0;
    let managedLogoUploads = 0;
    let created = false;
    const createdOrganization = {
      ...org,
      id: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
      tradeName: "Nuevo Gym",
      legalName: "Nuevo Gym S.A. de C.V.",
      taxId: "NGYM260101AB1",
      branchCount: 0,
      activeBranchCount: 0,
    };
    const createdCustomer = platformCustomerFor(createdOrganization, {
      userId: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
      ownerName: "Ana Propietaria",
      ownerEmail: "ana@nuevogym.test",
      accountStatus: "INVITED",
      invitationId: "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
      invitationStatus: "PENDING",
      invitationExpiresAt: "2026-08-28T12:00:00Z",
      emailDeliveryStatus: "SENT",
      lastEmailAttemptAt: "2026-08-25T12:00:00Z",
      lastEmailSentAt: "2026-08-25T12:00:00Z",
    });
    await page.route("**/api/tenant-logo", async (route) => {
      legacyLogoRequests += 1;
      await route.fulfill({ status: 500, body: "legacy route must not be used" });
    });
    await bootAs(page, user({
      console: "PLATFORM_OWNER",
      platformAdmin: true,
      roles: ["Administrador GymOS"],
      permissions: ["platform.admin"],
      branchId: null,
    }), async (route, path) => {
      if (route.request().method() === "GET" && path === "/organizations") {
        legacyOrganizationReads += 1;
        await route.fulfill({ status: 403, body: "legacy route must not be used" });
        return true;
      }
      if (route.request().method() === "GET" && path === "/platform/customers") {
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify(platformPortfolio(created
            ? [platformCustomerFor(org), createdCustomer]
            : [platformCustomerFor(org)])),
        });
        return true;
      }
      if (route.request().method() === "POST" && path === "/platform/customers") {
        platformCustomerCreates += 1;
        created = true;
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify({
            customer: createdCustomer,
            invitationId: createdCustomer.ownerAccess.invitationId,
            ownerName: createdCustomer.ownerAccess.ownerName,
            ownerEmail: createdCustomer.ownerAccess.ownerEmail,
            invitationUrl: "https://gymfort.test/accept-invitation?token=one-time-token",
            invitationExpiresAt: createdCustomer.ownerAccess.invitationExpiresAt,
            emailDeliveryStatus: "SENT",
          }),
        });
        return true;
      }
      if (
        route.request().method() === "POST"
        && path === `/organizations/${createdOrganization.id}/logo`
      ) {
        managedLogoUploads += 1;
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify({
            ...createdOrganization,
            logoUrl: `/api/tenant-logo/${createdOrganization.id}?v=test`,
          }),
        });
        return true;
      }
      return false;
    });

    await page.goto("/platform/gymnasiums");
    await page.getByRole("button", { name: "Nuevo gimnasio" }).click();
    await page.getByLabel("Nombre comercial").fill(createdOrganization.tradeName);
    await page.getByLabel("Nombre legal").fill(createdOrganization.legalName);
    await page.getByLabel("RFC").fill(createdOrganization.taxId);
    await page.getByLabel("Email contacto").fill("contacto@nuevogym.test");
    await page.getByLabel("Telefono contacto").fill("+524921234567");
    await page.getByLabel("Telefono de sucursal").fill("+524921234567");
    await page.locator('input[name="ownerFirstName"]').fill("Ana");
    await page.getByLabel("Apellidos").fill("Propietaria");
    await page.getByLabel("Correo de acceso").fill("ana@nuevogym.test");
    await page.getByLabel("Telefono del dueño").fill("+524921234568");
    await page.locator('.logo-upload-field input[type="file"]').setInputFiles({
      name: "logo.png",
      mimeType: "image/png",
      buffer: Buffer.from(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2nWQAAAAASUVORK5CYII=",
        "base64",
      ),
    });

    await expect(page.getByText("Logo seleccionado")).toBeVisible();
    expect(legacyLogoRequests).toBe(0);
    await page.getByRole("button", { name: "Crear gimnasio" }).click();

    await expect(page.getByText(/Gimnasio, sucursal y dueño creados/i)).toBeVisible();
    await expect(page.getByRole("heading", { name: "Invitación enviada por ZeptoMail" })).toBeVisible();
    await expect(page.getByText("Aceptado por ZeptoMail")).toBeVisible();
    expect(platformCustomerCreates).toBe(1);
    expect(managedLogoUploads).toBe(1);
    expect(legacyLogoRequests).toBe(0);
    expect(legacyOrganizationReads).toBe(0);
  });

  test("Admin GymFort regenera una invitación vencida desde la ficha del gimnasio", async ({ page }) => {
    let invitationReissues = 0;
    let reissued = false;
    const expiredOwner: PlatformOwnerAccess = {
      userId: "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
      ownerName: "Ana Propietaria",
      ownerEmail: "ana@appex.com",
      accountStatus: "INVITED",
      invitationId: "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee",
      invitationStatus: "EXPIRED",
      invitationExpiresAt: "2026-08-24T12:00:00Z",
      emailDeliveryStatus: "SENT",
      lastEmailAttemptAt: "2026-08-21T12:00:00Z",
      lastEmailSentAt: "2026-08-21T12:00:00Z",
    };
    const reissuedOwner: PlatformOwnerAccess = {
      ...expiredOwner,
      invitationId: "ffffffff-ffff-4fff-8fff-ffffffffffff",
      invitationStatus: "PENDING",
      invitationExpiresAt: "2026-08-28T12:00:00Z",
      lastEmailAttemptAt: "2026-08-25T12:00:00Z",
      lastEmailSentAt: "2026-08-25T12:00:00Z",
    };
    await bootAs(page, user({
      console: "PLATFORM_OWNER",
      platformAdmin: true,
      roles: ["Administrador GymOS"],
      permissions: ["platform.admin"],
      branchId: null,
    }), async (route, path) => {
      if (route.request().method() === "GET" && path === "/platform/customers") {
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify(platformPortfolio([
            platformCustomerFor(org, reissued ? reissuedOwner : expiredOwner),
          ])),
        });
        return true;
      }
      if (
        route.request().method() === "POST"
        && path === `/platform/customers/${org.id}/owner-invitation`
      ) {
        invitationReissues += 1;
        reissued = true;
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify({
            organizationId: org.id,
            organizationName: org.tradeName,
            organizationContactEmail: org.contactEmail,
            invitationId: reissuedOwner.invitationId,
            ownerUserId: reissuedOwner.userId,
            ownerName: reissuedOwner.ownerName,
            ownerEmail: reissuedOwner.ownerEmail,
            invitationUrl: "https://gymfort.test/accept-invitation?token=reissued-token",
            invitationExpiresAt: reissuedOwner.invitationExpiresAt,
            emailDeliveryStatus: "SENT",
          }),
        });
        return true;
      }
      return false;
    });

    await page.goto("/platform/gymnasiums");
    await expect(page.getByText("Invitación vencida")).toBeVisible();
    await page.getByRole("button", { name: "Generar y enviar invitación" }).click();

    await expect(page.getByRole("heading", { name: "Invitación enviada por ZeptoMail" })).toBeVisible();
    await expect(page.getByText("Invitación pendiente")).toBeVisible();
    expect(invitationReissues).toBe(1);
  });

  test("dueno de gimnasio ve direccion y administracion del tenant", async ({ page }) => {
    await bootAs(page, user({
      console: "TENANT_OWNER",
      roles: ["Dueno del gimnasio"],
      permissions: ownerPermissions(),
    }));

    await page.goto("/dashboard");

    await expect(page.getByRole("heading", { name: /Panel ejecutivo|Dashboard|Metricas/i })).toBeVisible();
    await expect(page.getByText("Dueño del gimnasio", { exact: true })).toBeVisible();
    await expect(page.getByRole("img", { name: "GymFort" })).toBeVisible();
    await expect(page.locator("body")).toContainText("Gestión operativa");
    await expect(page.locator("body")).toContainText("Administración");
    await expect(page.locator("body")).toContainText("Configuración");
    await expect(page.getByText("Inteligencia estratégica", { exact: true })).toBeVisible();
    await expect(page.locator("body")).not.toContainText(String.fromCharCode(0xc3));
    await expect(page.getByRole("link", { name: "Operación diaria" })).toHaveCount(0);
  });

  test("gerente conserva menu operativo sin la pagina redundante de operacion diaria", async ({ page }) => {
    await bootAs(page, user({
      console: "TENANT_MANAGER",
      roles: ["Gerente"],
      permissions: managerPermissions(),
    }));

    await page.goto("/dashboard");

    await expect(page).toHaveURL(/\/reports\/sales/);
    await expect(page.getByRole("heading", { name: "Reporte de Ventas y Pagos" })).toBeVisible();
    await expect(page.getByText("Gerente", { exact: true })).toBeVisible();
    await expect(page.getByRole("link", { name: "Operación diaria" })).toHaveCount(0);
    await expect(page.locator("body")).toContainText("Gestión operativa");
    await expect(page.locator("body")).toContainText("Administración");
    await expect(page.locator("body")).toContainText("Mostrador");
    await page.getByText("Administración", { exact: true }).click();
    await expect(page.getByRole("link", { name: /Automatizaciones/i })).toBeVisible();
    await expect(page.getByRole("link", { name: /Beneficios/i })).toBeVisible();
    await expect(page.getByRole("link", { name: /Gimnasios cliente/i })).toHaveCount(0);
    await page.locator('a[href="/settings/organization"]').click();
    await expect(page).toHaveURL(/\/settings\/organization/);
    await expect(page.getByRole("heading", { name: "Configuración del gimnasio" })).toBeVisible();
    await expect(page.getByRole("button", { name: /Vincular Stripe|Continuar configuración de Stripe/i })).toHaveCount(0);
    await expect(page.getByText(/cuenta de Stripe sólo puede vincularla o modificarla el dueño/i)).toBeVisible();
    await page.goto("/bookings");
    await expect(page.getByRole("button", { name: "Crear recurso" })).toBeVisible();
    await page.goto("/benefits");
    await expect(page.getByRole("button", { name: "Crear beneficio" })).toBeVisible();
    await page.goto("/clients");
    await expect(page.getByRole("button", { name: /Registrar Nuevo Socio/i })).toBeVisible();
  });

  test("configuracion de kiosco muestra una sola vez la sucursal seleccionada", async ({ page }) => {
    await bootAs(page, user({
      console: "TENANT_OWNER",
      roles: ["Dueno del gimnasio"],
      permissions: ownerPermissions().concat("selfservice.settings.manage"),
    }), async (route, path) => {
      if (path === "/kiosk-settings/branches") {
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify(branches.map((branch) => ({
            branchId: branch.id,
            branchName: branch.name,
            status: "ACTIVE",
            capabilities: {
              clientRegistrationAllowed: false,
              membershipEnrollmentAllowed: false,
              membershipRenewalAllowed: false,
              membershipPlanChangeAllowed: false,
              resourceBookingAllowed: true,
              wellnessBookingAllowed: false,
              profileUpdateAllowed: true,
              autopayAllowed: false,
              onlineCardPaymentsReady: false,
              recurringCardPaymentsReady: false,
            },
            updatedAt: null,
          }))),
        });
        return true;
      }
      return false;
    });

    await page.goto("/settings/organization#kiosk-settings");
    await page.getByRole("button", { name: /Kiosco web/i }).click();

    const kioskSettings = page.locator("#kiosk-settings");
    await expect(kioskSettings.getByRole("heading", { name: "Centro" })).toBeVisible();
    await expect(kioskSettings.getByRole("heading", { name: "Revolucion 115" })).toHaveCount(0);
  });

  test("configuración de socios compacta sus bloques y autosave revierte errores", async ({ page }) => {
    let lifecycle = {
      organizationId: org.id,
      registrationFeeEnabled: false,
      registrationFeeCents: 15000,
      registrationFeeLabel: "Inscripción",
      currency: "MXN",
      autoInactivationEnabled: false,
      inactivityMonths: 6,
      updatedAt: null as string | null,
      version: 0,
    };
    const savedPayloads: Array<Record<string, unknown>> = [];
    let failNextSave = false;
    let saveResponse = Promise.withResolvers<void>();
    await bootAs(page, user({
      console: "TENANT_OWNER",
      roles: ["Dueno del gimnasio"],
      permissions: ownerPermissions(),
    }), async (route, path) => {
      if (path === "/client-lifecycle-settings" && route.request().method() === "GET") {
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify(lifecycle),
        });
        return true;
      }
      if (path === "/client-lifecycle-settings" && route.request().method() === "PUT") {
        const payload = route.request().postDataJSON() as Record<string, unknown>;
        savedPayloads.push(payload);
        if (failNextSave) {
          failNextSave = false;
          await route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ message: "Fallo simulado de socios" }) });
          return true;
        }
        await saveResponse.promise;
        lifecycle = { ...lifecycle, ...payload, updatedAt: "2026-08-26T12:00:00Z", version: lifecycle.version + 1 };
        await route.fulfill({ contentType: "application/json", body: JSON.stringify(lifecycle) });
        return true;
      }
      return false;
    });

    await page.goto("/settings/organization#client-lifecycle-settings");

    await expect(page.getByRole("button", { name: "Inscripciones" })).toHaveAttribute("aria-pressed", "true");
    await expect(page.getByRole("heading", { name: "Socios e inscripción" })).toBeVisible();
    await expect(page.getByLabel("Periodo de inactividad")).toHaveValue("6");
    await expect(page.getByText("Volver a cobrar después de")).toHaveCount(0);
    await expect(page.getByText("Dar de baja después de")).toHaveCount(0);
    await expect(page.locator("#client-lifecycle-settings article")).toHaveCount(2);
    await expect(page.getByRole("button", { name: "Guardar concepto, importe y periodo" })).toHaveCount(0);
    await expect(page.getByText("Los toggles se guardan automáticamente.")).toHaveCount(0);

    await page.getByLabel("Concepto").fill("Alta anual");
    await page.getByLabel("Importe (MXN)").fill("175");
    await page.getByLabel("Periodo de inactividad").fill("8");
    await expect(page.getByText("Guardando...")).toBeVisible();
    saveResponse.resolve();
    await expect(page.getByText("Guardado")).toBeVisible();
    expect(savedPayloads.at(-1)).toMatchObject({
      registrationFeeLabel: "Alta anual",
      registrationFeeCents: 17500,
      inactivityMonths: 8,
    });

    const registrationFee = page.getByRole("switch", { name: "Cobrar inscripción" });
    const automaticInactivation = page.getByRole("switch", { name: "Dar de baja automáticamente" });
    saveResponse = Promise.withResolvers<void>();
    await registrationFee.click();
    await automaticInactivation.click();
    await expect(page.getByText("Guardando...")).toBeVisible();
    saveResponse.resolve();
    await expect(page.getByText("Guardado")).toBeVisible();
    expect(savedPayloads.at(-1)).toMatchObject({
      registrationFeeEnabled: true,
      autoInactivationEnabled: true,
      inactivityMonths: 8,
    });

    failNextSave = true;
    await automaticInactivation.click();
    await expect(page.getByText("Fallo simulado de socios")).toBeVisible();
    await expect(automaticInactivation).toHaveAttribute("aria-checked", "true");
  });

  test("medios de cobro usa autosave serializado y rollback", async ({ page }) => {
    const savedPayloads: Array<Record<string, unknown>> = [];
    let failNextSave = false;
    const saveResponse = Promise.withResolvers<void>();
    let paymentConfiguration = {
      receptionManualTerminalEnabled: false,
      receptionMercadoPagoEnabled: false,
      mercadoPagoAccountId: null,
      mercadoPagoTerminalId: null,
      mercadoPagoConnected: false,
      mercadoPagoReady: false,
      stripeSelfCheckoutEnabled: false,
      stripeRecurringEnabled: false,
      stripeAccountId: "acct_test",
      stripeConnected: true,
      stripeCheckoutReady: false,
      stripeRecurringReady: false,
    };
    await bootAs(page, user({
      console: "TENANT_OWNER",
      roles: ["Dueno del gimnasio"],
      permissions: ownerPermissions().concat("payment.settings.manage", "payment.provider.stripe.manage"),
    }), async (route, path) => {
      if (path === "/payment-settings" && route.request().method() === "GET") {
        await route.fulfill({ contentType: "application/json", body: JSON.stringify(paymentConfiguration) });
        return true;
      }
      if (path === "/payment-settings" && route.request().method() === "PUT") {
        const payload = route.request().postDataJSON() as Record<string, unknown>;
        savedPayloads.push(payload);
        if (failNextSave) {
          failNextSave = false;
          await route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ message: "Fallo simulado de cobros" }) });
          return true;
        }
        await saveResponse.promise;
        paymentConfiguration = {
          ...paymentConfiguration,
          ...payload,
          stripeCheckoutReady: Boolean(payload.stripeSelfCheckoutEnabled && paymentConfiguration.stripeConnected),
          stripeRecurringReady: Boolean(payload.stripeRecurringEnabled && payload.stripeSelfCheckoutEnabled && paymentConfiguration.stripeConnected),
        };
        await route.fulfill({ contentType: "application/json", body: JSON.stringify(paymentConfiguration) });
        return true;
      }
      return false;
    });

    await page.goto("/settings/organization#payment-settings");
    const manualTerminal = page.getByRole("switch", { name: "Terminal independiente" });
    const stripeCheckout = page.getByRole("switch", { name: "Self checkout con Stripe" });
    const recurring = page.getByRole("switch", { name: "Domiciliación con Stripe" });
    await expect(manualTerminal).toBeVisible();
    await manualTerminal.click();
    await stripeCheckout.click();
    await expect(page.getByText("Guardando...")).toBeVisible();
    saveResponse.resolve();
    await expect(page.getByText("Guardado")).toBeVisible();
    expect(savedPayloads.at(-1)).toMatchObject({
      receptionManualTerminalEnabled: true,
      stripeSelfCheckoutEnabled: true,
    });
    await expect(page.getByRole("button", { name: "Guardar configuración de cobro" })).toHaveCount(0);

    failNextSave = true;
    await recurring.click();
    await expect(page.getByText("Fallo simulado de cobros")).toBeVisible();
    await expect(recurring).toHaveAttribute("aria-checked", "false");
  });

  test("toggles del kiosco conservan intención, autosave serializa y revierte errores", async ({ page }) => {
    const savedPayloads: Array<Record<string, boolean>> = [];
    let failNextSave = false;
    const saveResponse = Promise.withResolvers<void>();
    const settingsFrom = (payload: Record<string, boolean>) => ({
      branchId: branches[0].id,
      branchName: branches[0].name,
      status: "ACTIVE",
      capabilities: {
        clientRegistrationAllowed: payload.clientRegistration,
        membershipEnrollmentAllowed: payload.membershipEnrollment,
        membershipRenewalAllowed: payload.membershipRenewal,
        membershipPlanChangeAllowed: payload.membershipPlanChange,
        resourceBookingAllowed: payload.resourceBooking,
        wellnessBookingAllowed: payload.wellnessBooking,
        profileUpdateAllowed: payload.profileUpdate,
        autopayAllowed: payload.autopay,
        onlineCardPaymentsReady: false,
        recurringCardPaymentsReady: false,
      },
      updatedAt: "2026-08-26T12:00:00Z",
    });
    const initial = {
      clientRegistration: false,
      membershipEnrollment: true,
      membershipRenewal: true,
      membershipPlanChange: true,
      resourceBooking: true,
      wellnessBooking: false,
      profileUpdate: true,
      autopay: true,
    };
    await bootAs(page, user({
      console: "TENANT_OWNER",
      roles: ["Dueno del gimnasio"],
      permissions: ownerPermissions().concat("selfservice.settings.manage"),
    }), async (route, path) => {
      if (path === "/kiosk-settings/branches" && route.request().method() === "GET") {
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify([settingsFrom(initial)]),
        });
        return true;
      }
      if (path === `/kiosk-settings/branches/${branches[0].id}` && route.request().method() === "PUT") {
        const payload = route.request().postDataJSON() as Record<string, boolean>;
        savedPayloads.push(payload);
        if (failNextSave) {
          failNextSave = false;
          await route.fulfill({
            status: 500,
            contentType: "application/json",
            body: JSON.stringify({ message: "Fallo simulado de autosave" }),
          });
          return true;
        }
        await saveResponse.promise;
        await route.fulfill({ contentType: "application/json", body: JSON.stringify(settingsFrom(payload)) });
        return true;
      }
      return false;
    });

    await page.goto("/settings/organization#kiosk-settings");
    const enrollment = page.getByRole("switch", { name: `Comprar membresía en ${branches[0].name}` });
    const registration = page.getByRole("switch", { name: `Registrar nuevos socios en ${branches[0].name}` });
    const resourceBooking = page.getByRole("switch", { name: `Reservas gratuitas en ${branches[0].name}` });
    const profileUpdate = page.getByRole("switch", { name: `Actualizar contacto en ${branches[0].name}` });

    await expect(enrollment).toHaveAttribute("aria-checked", "true");
    await expect(enrollment).toBeEnabled();
    const enrollmentRow = page.locator("#kiosk-settings article").filter({ hasText: "Comprar membresía" });
    await expect(enrollmentRow.getByText("Stripe pendiente")).toBeVisible();
    await expect(page.getByRole("button", { name: "Guardar configuración del kiosco" })).toHaveCount(0);

    await enrollment.click();
    await expect(enrollmentRow.getByText("Stripe pendiente")).toHaveCount(0);
    await expect(enrollmentRow.getByText(/Función deshabilitada/)).toBeVisible();
    await enrollment.click();

    await registration.click();
    await resourceBooking.click();
    await expect(page.getByText("Guardando...")).toBeVisible();
    saveResponse.resolve();
    await expect(page.getByText("Guardado")).toBeVisible();

    const latest = savedPayloads.at(-1);
    expect(latest).toMatchObject({
      clientRegistration: true,
      membershipEnrollment: true,
      membershipRenewal: true,
      membershipPlanChange: true,
      resourceBooking: false,
      autopay: true,
    });

    failNextSave = true;
    await profileUpdate.click();
    await expect(page.getByText("Fallo simulado de autosave")).toBeVisible();
    await expect(profileUpdate).toHaveAttribute("aria-checked", "true");
  });

  test("recepcion entra a mostrador y no ve administracion", async ({ page }) => {
    await bootAs(page, user({
      console: "RECEPTION",
      roles: ["Recepcion"],
      permissions: receptionPermissions(),
    }));

    await page.goto("/reception");

    await expect(page.getByRole("heading", { name: /Recepcion|Revolucion/i })).toBeVisible();
    await expect(page.locator("strong").filter({ hasText: /^Recepcion$/ }).first()).toBeVisible();
    await expect(page.getByText(/Cobrar venta o membres/i)).toBeVisible();
    await expect(page.getByRole("heading", { name: /Renovar, asignar o cambiar plan/i })).toBeVisible();
    await expect(page.locator('a[href="/reception/renewals"]')).toHaveCount(0);
    await expect(page.getByRole("link", { name: /Equipo/i })).toHaveCount(0);
    await expect(page.getByRole("link", { name: /Configuración/i })).toHaveCount(0);
    await expect(page.getByRole("link", { name: "Pantalla para socios", exact: true })).toBeVisible();
    await page.goto("/clients");
    await expect(page.getByRole("button", { name: /Registrar Nuevo Socio/i })).toBeVisible();
  });

  test("Admin GymFort en soporte ve banda y regreso a administracion", async ({ page }) => {
    await bootAs(page, user({
      console: "TENANT_OWNER",
      platformAdmin: true,
      supportMode: true,
      roles: ["Administrador GymOS"],
      permissions: ownerPermissions().concat("platform.admin"),
    }));

    await page.goto("/dashboard");

    await expect(page.getByText("Modo soporte GymFort")).toBeVisible();
    await expect(page.getByRole("button", { name: /Volver a administración GymFort/i })).toBeVisible();
    await expect(page.getByText("Soporte GymFort", { exact: true })).toBeVisible();
  });

  test("dueno configura el remitente SMTP y encola un correo de prueba", async ({ page }) => {
    let savedConfiguration: Record<string, unknown> | null = null;
    let testRecipient: string | null = null;
    let emailConfiguration = {
      organizationId: org.id,
      emailEnabled: false,
      providerKey: "SMTP",
      fromEmail: null,
      fromName: null,
      replyTo: null,
      providerAvailable: true,
      ready: false,
      providerName: "ZeptoMail",
      preferredFromEmail: "notificaciones@appex.com",
      fromEmailRestricted: true,
      allowedFromEmails: ["notificaciones@appex.com"],
      lastVerifiedAt: null as string | null,
      updatedAt: null,
    };
    await bootAs(page, user({
      console: "TENANT_OWNER",
      roles: ["Dueno del gimnasio"],
      permissions: ownerPermissions().concat("notification.read", "notification.manage", "notification.settings.manage"),
    }), async (route, path) => {
      if (path === "/notifications/email-configuration" && route.request().method() === "GET") {
        await route.fulfill({ contentType: "application/json", body: JSON.stringify(emailConfiguration) });
        return true;
      }
      if (path === "/notifications/email-configuration" && route.request().method() === "PUT") {
        savedConfiguration = route.request().postDataJSON() as Record<string, unknown>;
        emailConfiguration = {
          ...emailConfiguration,
          emailEnabled: true,
          fromEmail: String(savedConfiguration.fromEmail),
          fromName: String(savedConfiguration.fromName),
          replyTo: savedConfiguration.replyTo ? String(savedConfiguration.replyTo) : null,
          ready: true,
        };
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify(emailConfiguration),
        });
        return true;
      }
      if (path === "/notifications/email-configuration/test" && route.request().method() === "POST") {
        testRecipient = (route.request().postDataJSON() as { recipient: string }).recipient;
        emailConfiguration = { ...emailConfiguration, lastVerifiedAt: new Date().toISOString() };
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify({
            id: "99999999-9999-4999-8999-999999999999",
            channel: "EMAIL",
            recipient: testRecipient,
            subject: "Prueba de correo de GymFort",
            body: "Mensaje",
            bodyHtml: null,
            status: "SENT",
            category: "TRANSACTIONAL",
            recipientType: "DIRECT",
            recipientId: null,
            triggerType: "MANUAL_TEST",
            referenceType: "ORGANIZATION",
            referenceId: org.id,
            scheduledAt: null,
            sentAt: new Date().toISOString(),
            errorMessage: null,
            providerKey: null,
            providerMessageId: null,
            attemptCount: 0,
            availableAt: new Date().toISOString(),
            lockedUntil: null,
            lastAttemptAt: null,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          }),
        });
        return true;
      }
      return false;
    });

    await page.goto("/settings/organization");
    await page.getByRole("button", { name: /Correo electrónico/ }).click();
    await expect(page.getByRole("heading", { name: "Correo real con ZeptoMail" })).toBeVisible();
    await page.getByLabel("Nombre del remitente").fill("Appex Fitness");
    await page.getByLabel("Correo remitente").selectOption("notificaciones@appex.com");
    await page.getByText("Habilitar entregas por correo").click();
    await page.getByRole("button", { name: "Guardar remitente" }).click();
    await expect(page.getByText("Correo habilitado y listo para entregar.")).toBeVisible();
    expect(savedConfiguration).toMatchObject({
      enabled: true,
      providerKey: "SMTP",
      fromEmail: "notificaciones@appex.com",
      fromName: "Appex Fitness",
    });

    await page.getByLabel("Destinatario de prueba").fill("qa@appex.com");
    await page.getByRole("button", { name: "Enviar y verificar" }).click();
    await expect(page.getByText(/aceptó el correo de prueba/)).toBeVisible();
    expect(testRecipient).toBe("qa@appex.com");
  });
});

async function bootAs(
  page: Page,
  currentUser: TestUser,
  override?: (route: Route, path: string) => Promise<boolean>,
) {
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/^\/api\/v1/, "");
    if (override && await override(route, path)) return;
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(responseFor(path, currentUser)),
    });
  });

  await mockStaffSession(page, "e2e-access-token", currentUser);
}

function responseFor(path: string, currentUser: TestUser) {
  if (path === "/system/info") return { capabilities: ["operational-pagination-v1"] };
  if (path === "/clients/search") {
    return { items: [], page: 0, size: 25, totalElements: 0, totalPages: 0 };
  }
  if (path === "/auth/me") {
    return currentUser;
  }
  if (path === "/organizations") {
    return [{ ...org, branchCount: branches.length, activeBranchCount: branches.length }];
  }
  if (path === "/platform/customers") {
    return platformPortfolio([platformCustomerFor(org)]);
  }
  if (path === "/organizations/current") {
    return org;
  }
  if (path === "/organizations/current/branches" || path.endsWith("/branches")) {
    return branches;
  }
  if (path === "/notifications/email-configuration") {
    return {
      organizationId: org.id,
      emailEnabled: false,
      providerKey: "SMTP",
      fromEmail: null,
      fromName: null,
      replyTo: null,
      providerAvailable: false,
      ready: false,
      providerName: "ZeptoMail",
      preferredFromEmail: null,
      fromEmailRestricted: false,
      allowedFromEmails: [],
      lastVerifiedAt: null,
      updatedAt: null,
    };
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
  if (path === "/inventory/summary") {
    return {
      totalValuationCents: 0,
      totalTrackedProducts: 0,
      lowStockCount: 0,
      outOfStockCount: 0,
      allowNegativeStock: false,
    };
  }
  if (path === "/reports/coaches") {
    return { coachCount: 0, workoutCount: 0, activeAssignments: 0, assignmentsLast30Days: 0 };
  }
  if (path.startsWith("/reports/coaches/workout-assignments/trend")) {
    return { from: "2026-07-01", to: "2026-07-31", weeks: 4, points: [] };
  }
  if (path.startsWith("/reports/coaches/workout-assignments/summary")) {
    return {
      from: "2026-07-01",
      to: "2026-07-31",
      assignments: 0,
      uniqueClients: 0,
      activeCoaches: 0,
      coachesWithoutAssignments: 0,
      previousFrom: "2026-06-01",
      previousTo: "2026-06-30",
      previousAssignments: 0,
      previousUniqueClients: 0,
      assignmentChange: 0,
      uniqueClientChange: 0,
      page: 0,
      size: 20,
      totalDetails: 0,
      totalPages: 0,
      coaches: [],
      details: [],
    };
  }
  if (path.includes("/reports/summary")) {
    return {
      fromDate: "2026-07-01",
      toDate: "2026-07-31",
      currency: "MXN",
      sales: {
        currency: "MXN",
        saleCount: 0,
        subtotalCents: 0,
        discountCents: 0,
        totalCents: 0,
        paidCents: 0,
        balanceCents: 0,
        paidSales: 0,
        partiallyPaidSales: 0,
        pendingSales: 0,
      },
      payments: {
        paymentCount: 0,
        completedPayments: 0,
        pendingPayments: 0,
        rejectedPayments: 0,
        completedCents: 0,
        pendingCents: 0,
        rejectedCents: 0,
      },
      salesByStatus: [],
      salesByBusinessContext: [],
      paymentsByMethod: [],
      recentSales: [],
    };
  }
  return [];
}

function platformCustomerFor(
  organization: typeof org & { branchCount?: number; activeBranchCount?: number },
  ownerAccess: PlatformOwnerAccess = {
    userId: "77777777-7777-4777-8777-777777777777",
    ownerName: "Ana Propietaria",
    ownerEmail: "ana@appex.com",
    accountStatus: "ACTIVE",
    invitationId: "88888888-8888-4888-8888-888888888888",
    invitationStatus: "ACCEPTED",
    invitationExpiresAt: "2026-08-24T12:00:00Z",
    emailDeliveryStatus: "SENT",
    lastEmailAttemptAt: "2026-08-21T12:00:00Z",
    lastEmailSentAt: "2026-08-21T12:00:00Z",
  },
) {
  return {
    organizationId: organization.id,
    tradeName: organization.tradeName,
    legalName: organization.legalName,
    taxId: organization.taxId,
    organizationStatus: organization.status,
    contactEmail: organization.contactEmail,
    contactPhone: organization.contactPhone,
    brandName: organization.brandName,
    logoUrl: organization.logoUrl,
    branchCount: organization.branchCount ?? branches.length,
    activeBranchCount: organization.activeBranchCount ?? branches.length,
    lifecycleStatus: "ONBOARDING" as const,
    accountOwner: null,
    billingEmail: organization.contactEmail,
    billingPhone: organization.contactPhone,
    contractStartedOn: null,
    renewalOn: null,
    paymentTermsDays: 0,
    nextFollowUpOn: null,
    notes: null,
    version: 0,
    updatedAt: null,
    activeMembers: 0,
    monthlyRecurringRevenue: 0,
    overdueBranchCount: 0,
    unconfiguredBranchCount: 1,
    implementationOutstanding: 0,
    nextBillingDate: null,
    healthStatus: "SETUP_REQUIRED" as const,
    ownerAccess,
  };
}

function platformPortfolio(customers: ReturnType<typeof platformCustomerFor>[]) {
  return {
    summary: {
      customerCount: customers.length,
      activeCustomerCount: 0,
      onboardingCustomerCount: customers.length,
      atRiskCustomerCount: 0,
      suspendedCustomerCount: 0,
      overdueBranchCount: 0,
      followUpsDueCount: 0,
      monthlyRecurringRevenue: 0,
    },
    customers,
  };
}

function user(overrides: Partial<TestUser>): TestUser {
  const platformAdmin = overrides.platformAdmin ?? false;
  const supportMode = overrides.supportMode ?? false;
  const accountScope = overrides.accountScope ?? (platformAdmin ? "PLATFORM" : "TENANT");
  return {
    userId: "44444444-4444-4444-8444-444444444444",
    accountScope,
    organizationId: accountScope === "PLATFORM" && !supportMode ? null : org.id,
    branchId: accountScope === "PLATFORM" && !supportMode ? null : branches[0].id,
    username: "usuario@appex.com",
    fullName: "Usuario Appex",
    permissions: [],
    roles: [],
    platformAdmin,
    supportMode,
    supportExpiresAt: supportMode ? "2026-08-25T18:00:00Z" : null,
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
    "selfservice.session.launch",
  ];
}

function managerPermissions() {
  return [
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
    "benefit.manage",
    "resource.read",
    "resource.manage",
    "coach.read",
    "automation.read",
    "payment.settings.manage",
    "selfservice.settings.manage",
    "notification.settings.manage",
    "selfservice.session.launch",
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
    "selfservice.session.launch",
  ];
}
