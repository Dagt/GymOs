import { expect, test, type Page } from "@playwright/test";

const organizationId = "11111111-1111-4111-8111-111111111111";
const branchId = "22222222-2222-4222-8222-222222222222";
const professionalId = "33333333-3333-4333-8333-333333333333";
const serviceId = "44444444-4444-4444-8444-444444444444";
const clientId = "55555555-5555-4555-8555-555555555555";

test("un profesional externo con consultorio acepta una hora solicitada sin disponibilidad interna", async ({ page }) => {
  let availabilitySlotRequests = 0;
  let appointmentPayload: Record<string, unknown> | null = null;

  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace(/^\/api\/v1/, "");

    if (path.includes("/availability-slots")) {
      availabilitySlotRequests += 1;
      await route.fulfill({ contentType: "application/json", body: "[]" });
      return;
    }

    if (request.method() === "POST" && path === "/wellness/appointments") {
      appointmentPayload = request.postDataJSON();
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify(appointmentResponse(appointmentPayload)),
      });
      return;
    }

    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(responseFor(path, url)),
    });
  });

  await bootAsManager(page);
  await page.goto("/wellness");

  await page.getByRole("button", { name: /Agendar \/ Solicitar Cita/i }).click();

  await expect(page.getByText("Atención Externa / Por Solicitud")).toBeVisible();
  await expect(page.getByText(/Lugar de atención:/i)).toContainText("Consultorio externo 4");
  await expect(page.getByLabel("Hora solicitada")).toBeVisible();
  await expect(page.getByText(/no tiene horarios disponibles configurados/i)).toHaveCount(0);

  await page.getByPlaceholder(/Buscar por nombre/i).fill("Sylvia");
  await page.getByRole("button", { name: /Sylvia Ruiz/i }).click();
  await page.getByLabel("4. Fecha solicitada").fill("2099-08-06");
  await page.getByLabel("Hora solicitada").fill("16:30");
  await page.getByRole("button", { name: "Confirmar Agendamiento" }).click();

  await expect(page.getByText(/Solicitud registrada para Sylvia Ruiz/i)).toBeVisible();
  expect(availabilitySlotRequests).toBe(0);
  expect(appointmentPayload).not.toBeNull();
  expect(appointmentPayload?.professionalId).toBe(professionalId);
  expect(appointmentPayload?.serviceId).toBe(serviceId);
  expect(appointmentPayload?.clientId).toBe(clientId);
  expect(Number.isFinite(Date.parse(String(appointmentPayload?.startsAt)))).toBe(true);
});

async function bootAsManager(page: Page) {
  const user = {
    userId: "66666666-6666-4666-8666-666666666666",
    organizationId,
    branchId,
    username: "gerencia@gymos.test",
    fullName: "Gerencia GymOS",
    permissions: [
      "professional.read",
      "professional.manage",
      "booking.read",
      "booking.manage",
      "client.read",
    ],
    roles: ["Gerente"],
    platformAdmin: false,
    supportMode: false,
    console: "TENANT_MANAGER",
    organizationName: "Gym Wellness",
    brandName: "Gym Wellness",
    logoUrl: null,
    primaryColor: "#164E63",
    accentColor: "#EAB308",
    surfaceColor: "#F4F7FB",
  };

  await page.addInitScript(({ currentUser }) => {
    window.localStorage.setItem("gymos.accessToken", "e2e-access-token");
    window.localStorage.setItem("gymos.refreshToken", "e2e-refresh-token");
    window.localStorage.setItem("gymos.currentUser", JSON.stringify(currentUser));
  }, { currentUser: user });
}

function responseFor(path: string, url: URL) {
  if (path === "/organizations/current") {
    return {
      id: organizationId,
      legalName: "Gym Wellness S.A. de C.V.",
      tradeName: "Gym Wellness",
      timezone: "America/Mexico_City",
      defaultCurrency: "MXN",
      status: "ACTIVE",
    };
  }
  if (path === "/organizations/current/branches") {
    return [{
      id: branchId,
      name: "Centro",
      code: "CENTRO",
      timezone: "America/Mexico_City",
      status: "ACTIVE",
    }];
  }
  if (path === "/professionals") {
    return [{
      id: professionalId,
      displayName: "Eduardo Colunga Ledezma",
      professionalType: "CHIROPRACTOR",
      email: "eduardo@example.com",
      phone: "+524921234567",
      bio: null,
      officeLocation: "Consultorio externo 4",
      engagementType: "EXTERNAL",
      status: "ACTIVE",
      createdAt: "2026-07-30T10:00:00Z",
      updatedAt: "2026-07-30T10:00:00Z",
    }];
  }
  if (path === `/professionals/${professionalId}/services`) {
    return [{
      id: serviceId,
      professionalId,
      name: "Consulta de Quiropráctica",
      serviceType: "CHIROPRACTIC",
      durationMinutes: 30,
      priceCents: 30000,
      currency: "MXN",
      status: "ACTIVE",
      createdAt: "2026-07-30T10:00:00Z",
      updatedAt: "2026-07-30T10:00:00Z",
    }];
  }
  if (path === "/clients" && url.searchParams.has("query")) {
    return [{
      id: clientId,
      firstName: "Sylvia",
      lastName: "Ruiz",
      phone: "+524921111111",
      email: "sylvia@example.com",
      status: "ACTIVE",
      joinedAt: "2026-07-30T10:00:00Z",
      updatedAt: "2026-07-30T10:00:00Z",
    }];
  }
  if (path === "/wellness/appointments") {
    return [];
  }
  return [];
}

function appointmentResponse(payload: Record<string, unknown> | null) {
  return {
    id: "77777777-7777-4777-8777-777777777777",
    organizationId,
    branchId,
    professionalId,
    professionalName: "Eduardo Colunga Ledezma",
    professionalType: "CHIROPRACTOR",
    officeLocation: "Consultorio externo 4",
    professionalEngagementType: "EXTERNAL",
    serviceId,
    serviceName: "Consulta de Quiropráctica",
    durationMinutes: 30,
    priceCents: 30000,
    clientId,
    clientFullName: "Sylvia Ruiz",
    clientEmail: "sylvia@example.com",
    clientPhone: "+524921111111",
    startsAt: payload?.startsAt,
    endsAt: "2099-08-06T23:00:00Z",
    status: "REQUESTED",
    notes: null,
    cancellationReason: null,
    confirmedByUserId: null,
    confirmedAt: null,
    createdAt: "2026-07-30T10:00:00Z",
    updatedAt: "2026-07-30T10:00:00Z",
  };
}
