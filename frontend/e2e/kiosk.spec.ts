import { expect, test, type Page, type Route } from "@playwright/test";

const sessionId = "11111111-1111-4111-8111-111111111111";
const membershipId = "22222222-2222-4222-8222-222222222222";
const planId = "33333333-3333-4333-8333-333333333333";

test("el kiosco aplica capacidades, no ofrece pago manual y actualiza contacto", async ({ page }) => {
  let contactPayload: Record<string, unknown> | null = null;
  await mockKiosk(page, async (route, path) => {
    const method = route.request().method();
    if (method === "POST" && path === "/self-service/terminals/KIOSK-MAIN/sessions") {
      return fulfill(route, sessionResponse());
    }
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/verify`) {
      return fulfill(route, summaryResponse());
    }
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/membership-options`) {
      return fulfill(route, [{
        id: planId,
        productId: "44444444-4444-4444-8444-444444444444",
        name: "Plan mensual",
        priceCents: 79900,
        currency: "MXN",
        durationDays: 30,
        graceDays: 3,
      }]);
    }
    if (method === "PATCH" && path === `/self-service/sessions/${sessionId}/contact-data`) {
      contactPayload = route.request().postDataJSON() as Record<string, unknown>;
      return fulfill(route, {
        clientId: "55555555-5555-4555-8555-555555555555",
        displayName: "Andrea Socia",
        email: contactPayload.email,
        phone: contactPayload.phone,
      });
    }
    return false;
  });

  await page.goto("/kiosk/KIOSK-MAIN");
  await page.getByPlaceholder("Correo o teléfono").fill("andrea@example.com");
  await page.getByLabel("Fecha de nacimiento en formato día mes año").fill("10/05/1994");
  await page.getByRole("button", { name: "Continuar de forma segura" }).click();

  await expect(page.getByText(/exclusivamente tarjeta de crédito o débito/i)).toBeVisible();
  await expect(page.getByText("Profesionales de bienestar")).toBeVisible();
  await expect(page.getByLabel("Referencia de pago")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Renovar con tarjeta" })).toBeDisabled();

  await page.getByLabel("Correo").fill("nuevo@example.com");
  await page.getByLabel("Teléfono en formato +52").fill("+521234567890");
  await page.getByRole("button", { name: "Guardar datos" }).click();

  await expect(page.getByText("Tus datos de contacto se actualizaron correctamente.")).toBeVisible();
  expect(contactPayload).toEqual({
    email: "nuevo@example.com",
    phone: "+521234567890",
  });
});

async function mockKiosk(
  page: Page,
  handler: (route: Route, path: string) => Promise<boolean>,
) {
  await page.route("http://localhost:8080/api/v1/**", async (route) => {
    const path = new URL(route.request().url()).pathname.replace("/api/v1", "");
    if (await handler(route, path)) return;
    await route.fulfill({
      status: 404,
      contentType: "application/json",
      body: JSON.stringify({ message: `Unexpected API request: ${route.request().method()} ${path}` }),
    });
  });
}

async function fulfill(route: Route, body: unknown) {
  await route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify(body),
  });
  return true;
}

function capabilities() {
  return {
    membershipRenewalAllowed: true,
    membershipPlanChangeAllowed: true,
    resourceBookingAllowed: false,
    wellnessBookingAllowed: true,
    profileUpdateAllowed: true,
    autopayAllowed: true,
    onlineCardPaymentsReady: false,
    recurringCardPaymentsReady: false,
  };
}

function sessionResponse() {
  return {
    sessionId,
    sessionToken: "public-session-token",
    terminalName: "Kiosco principal",
    organizationName: "GymOS Prueba",
    brandName: "GymOS Prueba",
    logoUrl: null,
    primaryColor: "#20191A",
    accentColor: "#F5D90A",
    surfaceColor: "#F4F7FB",
    expiresAt: "2026-07-31T03:00:00Z",
    verified: false,
    capabilities: capabilities(),
  };
}

function summaryResponse() {
  return {
    sessionId,
    expiresAt: "2026-07-31T03:00:00Z",
    clientName: "Andrea Socia",
    profile: {
      clientId: "55555555-5555-4555-8555-555555555555",
      displayName: "Andrea Socia",
      email: "andrea@example.com",
      phone: "+525555555555",
    },
    capabilities: capabilities(),
    memberships: [{
      id: membershipId,
      planId,
      planName: "Plan mensual",
      status: "ACTIVE",
      startsOn: "2026-07-01",
      endsOn: "2026-07-31",
      graceEndsOn: "2026-08-03",
    }],
    pendingPayments: [],
  };
}
