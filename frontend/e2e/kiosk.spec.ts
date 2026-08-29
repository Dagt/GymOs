import { expect, test, type Page, type Route } from "@playwright/test";

const sessionId = "11111111-1111-4111-8111-111111111111";
const membershipId = "22222222-2222-4222-8222-222222222222";
const planId = "33333333-3333-4333-8333-333333333333";
const visitPackagePlanId = "66666666-6666-4666-8666-666666666666";

test("el kiosco aplica capacidades, no ofrece pago manual y actualiza contacto", async ({ page }) => {
  let contactPayload: Record<string, unknown> | null = null;
  await page.addInitScript(() => {
    window.sessionStorage.setItem("gymos.accessToken", "reception-access-token");
    window.sessionStorage.setItem("gymos.refreshToken", "reception-refresh-token");
    window.sessionStorage.setItem("gymos.currentUser", JSON.stringify({
      id: "77777777-7777-4777-8777-777777777777",
      username: "recepcion",
      fullName: "Recepcion Prueba",
      organizationId: "88888888-8888-4888-8888-888888888888",
      branchId: "99999999-9999-4999-8999-999999999999",
      permissions: ["selfservice.session.launch"],
      roles: ["Recepcion"],
    }));
  });
  await mockKiosk(page, async (route, path) => {
    const method = route.request().method();
    if (method === "POST" && path === "/self-service/kiosk-sessions") {
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
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/visit-package`) {
      await route.fulfill({
        status: 500,
        contentType: "application/json",
        body: JSON.stringify({
          code: "INTERNAL_ERROR",
          message: "Ocurrió un error inesperado.",
          correlationId: "existing-member-package-read-failure",
        }),
      });
      return true;
    }
    if (method === "PATCH" && path === `/self-service/sessions/${sessionId}/contact-data`) {
      contactPayload = route.request().postDataJSON() as Record<string, unknown>;
      return fulfill(route, {
        clientId: "55555555-5555-4555-8555-555555555555",
        displayName: "Andrea Socia",
        email: "nu***@example.com",
        phone: "***7890",
      });
    }
    return false;
  });

  await openLaunchedKiosk(page);
  await page.getByPlaceholder("Correo o teléfono").fill("andrea@example.com");
  await page.getByLabel("Fecha de nacimiento en formato día mes año").fill("10/05/1994");
  await page.getByRole("button", { name: "Continuar de forma segura" }).click();

  await expect(page.getByText(/Los pagos en línea todavía no están disponibles/i)).toBeVisible();
  await expect(page.getByText("Activa", { exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Membresía actual" })).toBeVisible();
  await expect(page.locator(".kiosk-feedback.error")).toHaveCount(0);
  await expect(page.getByText(/INTERNAL_ERROR|HTTP en \/self-service|Código:/)).toHaveCount(0);
  await expect(page.getByText("Vigente hasta 31 jul 2026")).toBeVisible();
  await expect(page.getByText("Profesionales de bienestar")).toHaveCount(0);
  await expect(page.getByLabel("Referencia de pago")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Revisar renovación de Plan mensual" })).toBeEnabled();

  await page.getByRole("button", { name: /^Mis datos /i }).click();
  await page.getByLabel("Nuevo correo").fill("nuevo@example.com");
  await page.getByLabel("Nuevo teléfono en formato +52").fill("+521234567890");
  await page.getByRole("button", { name: "Guardar datos" }).click();

  await expect(page.getByText("Tus datos de contacto se actualizaron correctamente.")).toBeVisible();
  expect(contactPayload).toEqual({
    email: "nuevo@example.com",
    phone: "+521234567890",
  });
});

test("una apertura directa explica que no existe un ID de computadora que capturar", async ({ page }) => {
  await page.goto("/kiosk/browser");

  await expect(page.getByRole("heading", { name: "Abre esta pantalla desde recepción" })).toBeVisible();
  await expect(page.getByText(/No necesitas registrar esta computadora ni capturar un identificador de terminal/i)).toBeVisible();
  await expect(page.getByRole("link", { name: "Ir a iniciar sesión" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Continuar de forma segura" })).toHaveCount(0);
});

test("los paquetes configurados siguen visibles con Stripe pendiente y no permiten Checkout", async ({ page }) => {
  const packageSummary = {
    ...summaryResponse(),
    capabilities: {
      ...capabilities(),
      membershipRenewalAllowed: false,
      membershipPlanChangeAllowed: false,
      visitPackagePurchaseAllowed: true,
    },
    memberships: [],
  };
  await mockKiosk(page, async (route, path) => {
    const method = route.request().method();
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/verify`) {
      return fulfill(route, packageSummary);
    }
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/visit-package-options`) {
      return fulfill(route, [{
        id: visitPackagePlanId,
        productId: "77777777-7777-4777-8777-777777777777",
        sku: "VISITAS-10",
        name: "10 visitas",
        priceCents: 50000,
        currency: "MXN",
        totalVisits: 10,
        validityDays: 30,
        branchScope: "ALL_BRANCHES",
        branchIds: ["99999999-9999-4999-8999-999999999999"],
        benefitIds: ["88888888-8888-4888-8888-888888888888"],
        selfServiceAcquisitionEnabled: true,
        registrationFeePolicy: "DEFAULT",
      }]);
    }
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/visit-package-quotes/${visitPackagePlanId}`) {
      return fulfill(route, {
        clientId: packageSummary.profile.clientId,
        packagePlanId: visitPackagePlanId,
        packageName: "10 visitas",
        totalVisits: 10,
        validityDays: 30,
        packagePriceCents: 50000,
        registrationFeeCents: 0,
        registrationFeeLabel: null,
        totalCents: 50000,
        currency: "MXN",
        branchIds: ["99999999-9999-4999-8999-999999999999"],
        benefitIds: ["88888888-8888-4888-8888-888888888888"],
      });
    }
    return false;
  });

  await openLaunchedKiosk(page);
  await page.getByPlaceholder("Correo o teléfono").fill("andrea@example.com");
  await page.getByLabel("Fecha de nacimiento en formato día mes año").fill("10/05/1994");
  await page.getByRole("button", { name: "Continuar de forma segura" }).click();

  await expect(page.getByRole("heading", { name: "Paquetes de visitas" })).toBeVisible();
  await expect(page.getByText("10 visitas · 30 días")).toBeVisible();
  await page.getByRole("button", { name: "Revisar paquete" }).click();
  await expect(page.getByText("$500.00").first()).toBeVisible();
  await expect(page.getByRole("button", { name: "Pago pendiente de Stripe" })).toBeDisabled();
});

test("un fallo de verificación mantiene el formulario centrado y oculta el detalle técnico", async ({ page }) => {
  let resetRequests = 0;
  const verificationSession = {
    ...sessionResponse(),
    capabilities: { ...capabilities(), clientRegistrationAllowed: true },
  };
  await page.addInitScript((storedSession) => {
    window.sessionStorage.setItem("gymos.kioskBrowserSession", JSON.stringify(storedSession));
  }, verificationSession);
  await mockKiosk(page, async (route, path) => {
    const method = route.request().method();
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/reset`) {
      resetRequests += 1;
      return fulfill(route, verificationSession);
    }
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/verify`) {
      await route.fulfill({
        status: 400,
        contentType: "application/json",
        body: JSON.stringify({
          code: "SELF_SERVICE_VERIFICATION_FAILED",
          message: "No fue posible verificar la sesión con los datos proporcionados.",
          correlationId: "513486c1-9ea8-4d9c-bf0b-dcb8acc53c40",
        }),
      });
      return true;
    }
    return false;
  });

  await page.goto("/kiosk/reception");
  const card = page.locator(".kiosk-verify");
  const selector = page.locator(".kiosk-entry-selector");
  const cardBefore = await card.boundingBox();
  const selectorBox = await selector.boundingBox();
  expect(cardBefore).not.toBeNull();
  expect(selectorBox).not.toBeNull();
  if (!cardBefore || !selectorBox) throw new Error("No fue posible medir el formulario del kiosco.");
  expect(Math.abs(selectorBox.x + selectorBox.width / 2 - (cardBefore.x + cardBefore.width / 2))).toBeLessThan(1);

  await page.getByPlaceholder("Correo o teléfono").fill("nadie@example.com");
  await page.getByLabel("Fecha de nacimiento en formato día mes año").fill("21/07/2015");
  await page.getByRole("button", { name: "Continuar de forma segura" }).click();

  await expect(card.getByRole("alert")).toContainText("No pudimos verificar tus datos.");
  await expect(card.getByRole("alert")).toContainText("Quiero registrarme");
  await expect(page.getByText(/SELF_SERVICE_VERIFICATION_FAILED|CorrelationId|HTTP en/)).toHaveCount(0);
  const cardAfter = await card.boundingBox();
  expect(cardAfter).not.toBeNull();
  if (!cardAfter) throw new Error("No fue posible medir el formulario después del error.");
  expect(Math.abs(cardAfter.x - cardBefore.x)).toBeLessThan(1);

  const resetRequestsBeforeModeChange = resetRequests;
  await page.getByRole("tab", { name: "Quiero registrarme" }).click();
  await expect(page.getByRole("heading", { name: "Crea tu expediente" })).toBeVisible();
  await expect.poll(() => resetRequests).toBe(resetRequestsBeforeModeChange + 1);
  await expect(card.getByRole("alert")).toHaveCount(0);
});

test("un socio nuevo se registra en el kiosco y continúa a su primer plan", async ({ page }) => {
  let registrationPayload: Record<string, unknown> | null = null;
  const registrationSession = {
    ...sessionResponse(),
    capabilities: { ...capabilities(), clientRegistrationAllowed: true },
  };
  await page.addInitScript((storedSession) => {
    window.sessionStorage.setItem("gymos.kioskBrowserSession", JSON.stringify(storedSession));
  }, registrationSession);
  await mockKiosk(page, async (route, path) => {
    const method = route.request().method();
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/reset`) {
      return fulfill(route, registrationSession);
    }
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/registrations`) {
      registrationPayload = route.request().postDataJSON() as Record<string, unknown>;
      return fulfill(route, {
        ...summaryResponse(),
        memberships: [],
        capabilities: {
          ...capabilities(),
          clientRegistrationAllowed: true,
          membershipEnrollmentAllowed: true,
        },
      });
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
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/visit-package`) {
      await route.fulfill({
        status: 500,
        contentType: "application/json",
        body: JSON.stringify({
          code: "INTERNAL_ERROR",
          message: "Ocurrió un error inesperado.",
          correlationId: "55440a2c-8a1f-422e-9d5b-886fd1212e97",
        }),
      });
      return true;
    }
    return false;
  });

  await page.goto("/kiosk/reception");
  await page.getByRole("tab", { name: "Quiero registrarme" }).click();
  await expect(page.locator(".kiosk-step > span")).toHaveText("1");
  await page.getByLabel("Nombre", { exact: true }).fill("Lucía");
  await page.getByLabel("Apellido paterno").fill("Nueva");
  await page.getByLabel("Teléfono", { exact: true }).fill("5512345678");
  await page.getByPlaceholder("DD/MM/AAAA").fill("15/04/1998");
  await page.getByRole("button", { name: "Crear expediente y continuar" }).click();

  await expect(page.getByText("Tu registro quedó listo. Ahora puedes elegir tu primera membresía.")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Contrata una membresía" })).toBeVisible();
  await expect(page.locator(".kiosk-feedback.error")).toHaveCount(0);
  await expect(page.getByText(/INTERNAL_ERROR|HTTP en \/self-service|Código:/)).toHaveCount(0);
  await expect(page.getByText("Tu registro quedó listo. Ahora puedes elegir tu primera membresía.")).toBeHidden({ timeout: 6_000 });
  expect(registrationPayload).toEqual({
    firstName: "Lucía",
    lastName: "Nueva",
    maternalLastName: null,
    birthDate: "1998-04-15",
    gender: null,
    phone: "+525512345678",
    email: null,
  });
});

test("muestra planes y cotización pero bloquea checkout cuando Stripe está pendiente", async ({ page }) => {
  let enrollmentRequests = 0;
  await mockKiosk(page, async (route, path) => {
    const method = route.request().method();
    if (method === "POST" && path === "/self-service/kiosk-sessions") return fulfill(route, sessionResponse());
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/verify`) {
      return fulfill(route, {
        ...summaryResponse(),
        memberships: [],
        capabilities: {
          ...capabilities(),
          membershipEnrollmentAllowed: true,
          membershipRenewalAllowed: false,
          membershipPlanChangeAllowed: false,
          autopayAllowed: false,
          onlineCardPaymentsReady: false,
        },
      });
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
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/membership-quotes/${planId}`) {
      return fulfill(route, membershipQuote());
    }
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/membership-enrollments`) {
      enrollmentRequests += 1;
      return fulfill(route, {});
    }
    return false;
  });

  await openLaunchedKiosk(page);
  await page.getByPlaceholder("Correo o teléfono").fill("andrea@example.com");
  await page.getByLabel("Fecha de nacimiento en formato día mes año").fill("10/05/1994");
  await page.getByRole("button", { name: "Continuar de forma segura" }).click();

  await expect(page.getByText("Plan mensual", { exact: true })).toBeVisible();
  await expect(page.getByText(/Los pagos en línea todavía no están disponibles/i)).toBeVisible();
  await expect(page.getByText(/No hay planes disponibles actualmente/i)).toHaveCount(0);
  await page.getByRole("button", { name: "Revisar contratación de Plan mensual" }).click();
  await expect(page.getByLabel("Revisión de membresía")).toBeVisible();
  await expect(page.getByRole("button", { name: "Pago pendiente de Stripe" })).toBeDisabled();
  expect(enrollmentRequests).toBe(0);
});

test("distingue contratación deshabilitada de catálogo vacío", async ({ page }) => {
  let optionsRequests = 0;
  await mockKiosk(page, async (route, path) => {
    const method = route.request().method();
    if (method === "POST" && path === "/self-service/kiosk-sessions") return fulfill(route, sessionResponse());
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/verify`) {
      return fulfill(route, {
        ...summaryResponse(),
        memberships: [],
        capabilities: {
          ...capabilities(),
          membershipEnrollmentAllowed: false,
          membershipRenewalAllowed: false,
          membershipPlanChangeAllowed: false,
        },
      });
    }
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/membership-options`) {
      optionsRequests += 1;
      return fulfill(route, []);
    }
    return false;
  });

  await openLaunchedKiosk(page);
  await page.getByPlaceholder("Correo o teléfono").fill("andrea@example.com");
  await page.getByLabel("Fecha de nacimiento en formato día mes año").fill("10/05/1994");
  await page.getByRole("button", { name: "Continuar de forma segura" }).click();

  await expect(page.getByText(/La contratación de membresías desde el kiosco no está habilitada/i)).toBeVisible();
  await expect(page.getByText(/Puedes contratar una nueva/i)).toHaveCount(0);
  await expect(page.getByText(/No hay planes disponibles actualmente/i)).toHaveCount(0);
  expect(optionsRequests).toBe(0);
});

test("explica cuando la contratación está activa pero el catálogo está vacío", async ({ page }) => {
  await mockKiosk(page, async (route, path) => {
    const method = route.request().method();
    if (method === "POST" && path === "/self-service/kiosk-sessions") return fulfill(route, sessionResponse());
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/verify`) {
      return fulfill(route, {
        ...summaryResponse(),
        memberships: [],
        capabilities: {
          ...capabilities(),
          membershipEnrollmentAllowed: true,
          membershipRenewalAllowed: false,
          membershipPlanChangeAllowed: false,
          onlineCardPaymentsReady: true,
        },
      });
    }
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/membership-options`) {
      return fulfill(route, []);
    }
    return false;
  });

  await openLaunchedKiosk(page);
  await page.getByPlaceholder("Correo o teléfono").fill("andrea@example.com");
  await page.getByLabel("Fecha de nacimiento en formato día mes año").fill("10/05/1994");
  await page.getByRole("button", { name: "Continuar de forma segura" }).click();

  await expect(page.getByText("No hay planes disponibles actualmente. Solicita apoyo en recepción.")).toBeVisible();
  await expect(page.getByText(/pagos en línea todavía no están disponibles/i)).toHaveCount(0);
});

test("una membresia multi-sucursal permite reservar pero se administra en su sucursal de origen", async ({ page }) => {
  await mockKiosk(page, async (route, path) => {
    const method = route.request().method();
    if (method === "POST" && path === "/self-service/kiosk-sessions") return fulfill(route, sessionResponse());
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/verify`) {
      const summary = summaryResponse();
      return fulfill(route, {
        ...summary,
        capabilities: {
          ...summary.capabilities,
          resourceBookingAllowed: true,
          autopayAllowed: true,
          onlineCardPaymentsReady: true,
          recurringCardPaymentsReady: true,
        },
        memberships: summary.memberships.map((membership) => ({
          ...membership,
          managedAtCurrentBranch: false,
        })),
      });
    }
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/membership-options`) {
      return fulfill(route, []);
    }
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/booking-resources`) {
      return fulfill(route, []);
    }
    return false;
  });

  await openLaunchedKiosk(page);
  await page.getByPlaceholder(/Correo o tel/).fill("andrea@example.com");
  await page.getByLabel(/Fecha de nacimiento/).fill("10/05/1994");
  await page.getByRole("button", { name: "Continuar de forma segura" }).click();

  await expect(page.getByText(/membresía multi-sucursal es válida aquí para acceso y reservas/i)).toBeVisible();
  await expect(page.getByRole("button", { name: /^Reservar /i })).toBeVisible();
  await expect(page.getByRole("button", { name: /Revisar renovación/i })).toHaveCount(0);
  await expect(page.getByRole("button", { name: /Cambiar a otro plan/i })).toHaveCount(0);
  await expect(page.getByRole("button", { name: /^Domiciliación /i })).toHaveCount(0);
});

test("una recarga recupera el modo kiosco y borra al socio anterior", async ({ page }) => {
  let resetRequests = 0;
  await page.addInitScript((storedSession) => {
    window.sessionStorage.setItem("gymos.kioskBrowserSession", JSON.stringify(storedSession));
  }, sessionResponse());
  await mockKiosk(page, async (route, path) => {
    if (route.request().method() === "POST" && path === `/self-service/sessions/${sessionId}/reset`) {
      resetRequests += 1;
      return fulfill(route, sessionResponse());
    }
    return false;
  });

  await page.goto("/kiosk/browser");

  await expect(page.getByRole("heading", { name: "Verifica tu identidad" })).toBeVisible();
  await expect(page.getByPlaceholder("Correo o teléfono")).toHaveValue("");
  await expect(page.getByRole("tab", { name: "Quiero registrarme" })).toHaveCount(0);
  expect(resetRequests).toBeGreaterThan(0);
});

test("un socio sin membresia puede abrir Stripe Checkout con una intencion idempotente", async ({ page }) => {
  let enrollmentPayload: Record<string, unknown> | null = null;
  let paymentCompleted = false;
  await page.addInitScript(() => {
    window.sessionStorage.setItem("gymos.accessToken", "reception-access-token");
    window.sessionStorage.setItem("gymos.refreshToken", "reception-refresh-token");
    window.sessionStorage.setItem("gymos.currentUser", JSON.stringify({
      id: "77777777-7777-4777-8777-777777777777",
      username: "recepcion",
      fullName: "Recepcion Prueba",
      organizationId: "88888888-8888-4888-8888-888888888888",
      branchId: "99999999-9999-4999-8999-999999999999",
      permissions: ["selfservice.session.launch"],
      roles: ["Recepcion"],
    }));
  });
  await mockKiosk(page, async (route, path) => {
    const method = route.request().method();
    if (method === "POST" && path === "/self-service/kiosk-sessions") {
      return fulfill(route, sessionResponse());
    }
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/verify`) {
      return fulfill(route, {
        ...summaryResponse(),
        memberships: [],
        capabilities: {
          ...capabilities(),
          membershipEnrollmentAllowed: true,
          membershipRenewalAllowed: false,
          membershipPlanChangeAllowed: false,
          wellnessBookingAllowed: false,
          autopayAllowed: false,
          onlineCardPaymentsReady: true,
        },
      });
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
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/membership-quotes/${planId}`) {
      return fulfill(route, membershipQuote());
    }
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/summary`) {
      return fulfill(route, {
        ...summaryResponse(),
        memberships: [],
        paymentActivity: paymentCompleted ? [{
          id: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
          saleId: "66666666-6666-4666-8666-666666666666",
          saleNumber: "V-001",
          method: "CARD_ONLINE",
          status: "COMPLETED",
          amountCents: 94900,
          currency: "MXN",
          receivedAt: "2026-08-20T15:00:00Z",
          provider: "STRIPE",
          receiptUrl: "https://pay.stripe.com/receipts/test",
          checkoutUrl: null,
          providerUpdatedAt: "2026-08-20T15:00:01Z",
        }] : [],
      });
    }
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/membership-enrollments`) {
      enrollmentPayload = route.request().postDataJSON() as Record<string, unknown>;
      paymentCompleted = true;
      return fulfill(route, {
        saleId: "66666666-6666-4666-8666-666666666666",
        saleNumber: "V-001",
        saleStatus: "PENDING_PAYMENT",
        totalCents: 94900,
        paidCents: 0,
        balanceCents: 94900,
        currency: "MXN",
        membership: null,
        paymentId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        providerOperationId: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
        checkoutUrl: "http://127.0.0.1:3000/kiosk/checkout/result?status=success&session_id=cs_payment",
        checkoutStatus: "OPEN",
      });
    }
    return false;
  });

  await openLaunchedKiosk(page);
  await page.getByPlaceholder("Correo o teléfono").fill("andrea@example.com");
  await page.getByLabel("Fecha de nacimiento en formato día mes año").fill("10/05/1994");
  await page.getByRole("button", { name: "Continuar de forma segura" }).click();
  await expect(page.getByRole("heading", { name: "Contrata una membresía" })).toBeVisible();
  await expect(page.getByText("No tienes una membresía activa")).toBeVisible();
  await page.getByRole("button", { name: "Revisar contratación de Plan mensual" }).click();
  const review = page.getByLabel("Revisión de membresía");
  await expect(review.getByRole("heading", { name: "Contratación de membresía" })).toBeVisible();
  await expect(review.getByText("Inscripción")).toBeVisible();
  await expect(review.getByText("$150.00")).toBeVisible();
  await expect(review.getByText("Total a pagar")).toBeVisible();
  await expect(review.getByText("$949.00")).toBeVisible();
  await page.getByRole("button", { name: "Confirmar y continuar a Stripe" }).click();

  await expect(page.getByRole("heading", { name: "Pago confirmado" })).toBeVisible();
  await expect(page.getByText("$949.00")).toBeVisible();
  expect(enrollmentPayload?.planId).toBe(planId);
  expect(enrollmentPayload?.idempotencyKey).toEqual(expect.stringMatching(/^kiosk-enrollment:[0-9a-f-]+$/));
  const persistedKeys = await page.evaluate(() => window.sessionStorage.getItem("gymos.kioskOperationKeys"));
  expect(persistedKeys).toContain("kiosk-enrollment");
});

test("si falla la cotización explica que no hubo cargo y conserva una referencia de soporte", async ({ page }) => {
  await mockKiosk(page, async (route, path) => {
    const method = route.request().method();
    if (method === "POST" && path === "/self-service/kiosk-sessions") return fulfill(route, sessionResponse());
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/verify`) {
      return fulfill(route, {
        ...summaryResponse(),
        memberships: [],
        capabilities: {
          ...capabilities(),
          membershipEnrollmentAllowed: true,
          membershipRenewalAllowed: false,
          membershipPlanChangeAllowed: false,
          autopayAllowed: false,
          onlineCardPaymentsReady: true,
        },
      });
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
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/membership-quotes/${planId}`) {
      await route.fulfill({
        status: 500,
        contentType: "application/json",
        body: JSON.stringify({
          code: "INTERNAL_ERROR",
          message: "Ocurrió un error inesperado.",
          correlationId: "0486f7d4-7960-479b-8f61-7cda536baa52",
        }),
      });
      return true;
    }
    return false;
  });

  await openLaunchedKiosk(page);
  await page.getByPlaceholder("Correo o teléfono").fill("andrea@example.com");
  await page.getByLabel("Fecha de nacimiento en formato día mes año").fill("10/05/1994");
  await page.getByRole("button", { name: "Continuar de forma segura" }).click();
  await page.getByRole("button", { name: "Revisar contratación de Plan mensual" }).click();

  const membershipError = page.locator(".kiosk-membership-error");
  await expect(membershipError).toContainText("No se realizó ningún cargo ni cambio en tu membresía");
  await expect(membershipError).toContainText("0486f7d4-7960-479b-8f61-7cda536baa52");
  await expect(membershipError).not.toContainText("/membership-quotes/");
});

test("muestra comprobante y permite recuperar una domiciliacion PAST_DUE con otra tarjeta", async ({ page }) => {
  let setupPayload: Record<string, unknown> | null = null;
  await page.addInitScript(() => {
    window.sessionStorage.setItem("gymos.accessToken", "reception-access-token");
    window.sessionStorage.setItem("gymos.refreshToken", "reception-refresh-token");
    window.sessionStorage.setItem("gymos.currentUser", JSON.stringify({
      id: "77777777-7777-4777-8777-777777777777",
      username: "recepcion",
      fullName: "Recepcion Prueba",
      organizationId: "88888888-8888-4888-8888-888888888888",
      branchId: "99999999-9999-4999-8999-999999999999",
      permissions: ["selfservice.session.launch"],
      roles: ["Recepcion"],
    }));
  });
  await mockKiosk(page, async (route, path) => {
    const method = route.request().method();
    if (method === "POST" && path === "/self-service/kiosk-sessions") return fulfill(route, sessionResponse());
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/verify`) {
      return fulfill(route, {
        ...summaryResponse(),
        capabilities: { ...capabilities(), recurringCardPaymentsReady: true, onlineCardPaymentsReady: true },
        paymentActivity: [{
          id: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
          saleId: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
          saleNumber: "V-0099",
          method: "CARD_ONLINE",
          status: "COMPLETED",
          amountCents: 79900,
          currency: "MXN",
          receivedAt: "2026-08-03T10:00:00Z",
          provider: "STRIPE",
          receiptUrl: "https://pay.stripe.com/receipts/test",
          checkoutUrl: null,
          providerUpdatedAt: "2026-08-03T10:01:00Z",
        }],
      });
    }
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/membership-options`) return fulfill(route, []);
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/autopay/${membershipId}`) {
      return fulfill(route, {
        id: "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
        membershipId,
        planId,
        status: "PAST_DUE",
        provider: "STRIPE",
        brand: "visa",
        lastFour: "4242",
        expiresMonth: 12,
        expiresYear: 2030,
        termsVersion: "2026-08-03",
        amountRule: "CURRENT_MEMBERSHIP_RENEWAL_TOTAL",
        consentedAt: "2026-07-01T10:00:00Z",
        nextChargeOn: "2026-08-31",
        consecutiveFailures: 2,
        lastError: "requires_action",
        revokedAt: null,
        canReplacePaymentMethod: true,
        recoveryMessage: "El ultimo cargo no se completo.",
      });
    }
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/autopay/${membershipId}/terms`) {
      return fulfill(route, { ...membershipQuote(), termsVersion: "2026-08-03", consentText: "Autorizo los cargos recurrentes.", amountRule: "CURRENT_MEMBERSHIP_RENEWAL_TOTAL", currentPriceCents: 79900, nextChargeOn: "2026-08-31" });
    }
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/autopay/${membershipId}/setup`) {
      setupPayload = route.request().postDataJSON() as Record<string, unknown>;
      return fulfill(route, { mandateId: "dddddddd-dddd-4ddd-8ddd-dddddddddddd", status: "PENDING_SETUP", providerOperationId: null, providerSessionId: "cs_replace", checkoutUrl: "http://127.0.0.1:3000/kiosk/checkout/result?status=autopay-success&session_id=cs_replace", termsVersion: "2026-08-03", nextChargeOn: "2026-08-31" });
    }
    return false;
  });

  await openLaunchedKiosk(page);
  await page.getByPlaceholder(/Correo o tel/).fill("andrea@example.com");
  await page.getByLabel(/Fecha de nacimiento/).fill("10/05/1994");
  await page.getByRole("button", { name: "Continuar de forma segura" }).click();
  await page.getByRole("button", { name: /Mis pagos/i }).click();
  await expect(page.getByRole("link", { name: "Ver comprobante" })).toHaveAttribute("href", "https://pay.stripe.com/receipts/test");
  await page.getByRole("button", { name: /^Domiciliaci/ }).click();
  await page.getByText(/Acepto nuevamente los t/).click();
  await page.getByRole("button", { name: "Actualizar tarjeta en Stripe" }).click();
  expect(setupPayload?.replaceExisting).toBe(true);
  expect(setupPayload?.idempotencyKey).toEqual(expect.stringContaining("kiosk-autopay-replace"));
});

test("el socio acepta terminos versionados antes de tokenizar la domiciliacion", async ({ page }) => {
  let setupPayload: Record<string, unknown> | null = null;
  let setupStarted = false;
  await page.addInitScript(() => {
    window.sessionStorage.setItem("gymos.accessToken", "reception-access-token");
    window.sessionStorage.setItem("gymos.refreshToken", "reception-refresh-token");
    window.sessionStorage.setItem("gymos.currentUser", JSON.stringify({
      id: "77777777-7777-4777-8777-777777777777",
      username: "recepcion",
      fullName: "Recepcion Prueba",
      organizationId: "88888888-8888-4888-8888-888888888888",
      branchId: "99999999-9999-4999-8999-999999999999",
      permissions: ["selfservice.session.launch"],
      roles: ["Recepcion"],
    }));
  });
  await mockKiosk(page, async (route, path) => {
    const method = route.request().method();
    if (method === "POST" && path === "/self-service/kiosk-sessions") {
      return fulfill(route, sessionResponse());
    }
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/verify`) {
      return fulfill(route, {
        ...summaryResponse(),
        capabilities: {
          ...capabilities(),
          wellnessBookingAllowed: false,
          recurringCardPaymentsReady: true,
          onlineCardPaymentsReady: true,
        },
      });
    }
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/membership-options`) {
      return fulfill(route, []);
    }
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/autopay/${membershipId}`) {
      return fulfill(route, setupStarted ? {
        id: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        membershipId,
        planId,
        status: "ACTIVE",
        provider: "STRIPE",
        brand: "visa",
        lastFour: "4242",
        expiresMonth: 12,
        expiresYear: 2030,
        termsVersion: "2026-08-03",
        amountRule: "CURRENT_MEMBERSHIP_RENEWAL_TOTAL",
        consentedAt: "2026-08-03T10:00:00Z",
        nextChargeOn: "2026-08-31",
        consecutiveFailures: 0,
        lastError: null,
        revokedAt: null,
        canReplacePaymentMethod: true,
        recoveryMessage: null,
      } : null);
    }
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/autopay/${membershipId}/terms`) {
      return fulfill(route, {
        termsVersion: "2026-08-03",
        consentText: "Autorizo cargos automáticos para renovar mi membresía.",
        amountRule: "CURRENT_MEMBERSHIP_RENEWAL_TOTAL",
        currentPriceCents: 79900,
        currency: "MXN",
        nextChargeOn: "2026-07-31",
      });
    }
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/autopay/${membershipId}/setup`) {
      setupPayload = route.request().postDataJSON() as Record<string, unknown>;
      setupStarted = true;
      return fulfill(route, {
        mandateId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        status: "PENDING_SETUP",
        providerOperationId: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
        providerSessionId: "cs_setup",
        checkoutUrl: "http://127.0.0.1:3000/kiosk/checkout/result?status=autopay-success&session_id=cs_setup",
        termsVersion: "2026-08-03",
        nextChargeOn: "2026-07-31",
      });
    }
    return false;
  });

  await openLaunchedKiosk(page);
  await page.getByPlaceholder("Correo o teléfono").fill("andrea@example.com");
  await page.getByLabel("Fecha de nacimiento en formato día mes año").fill("10/05/1994");
  await page.getByRole("button", { name: "Continuar de forma segura" }).click();
  await page.getByRole("button", { name: "Administrar domiciliación" }).click();

  await expect(page.getByText("Autorizo cargos automáticos para renovar mi membresía.")).toBeVisible();
  const continueButton = page.getByRole("button", { name: "Continuar a captura segura de tarjeta" });
  await expect(continueButton).toBeDisabled();
  await page.getByText("Acepto expresamente esta autorización").click();
  await continueButton.click();

  await expect(page.getByRole("heading", { name: "Domiciliación activada" })).toBeVisible();
  await expect(page.getByText("VISA •••• 4242")).toBeVisible();
  expect(setupPayload?.accepted).toBe(true);
  expect(setupPayload?.termsVersion).toBe("2026-08-03");
  expect(setupPayload?.idempotencyKey).toEqual(expect.stringMatching(/^kiosk-autopay:[0-9a-f-]+$/));
});

test("el kiosco agenda únicamente servicios wellness gratuitos", async ({ page }) => {
  const professionalId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
  const serviceId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
  const startsAt = "2099-08-01T16:00:00Z";
  let appointmentPayload: Record<string, unknown> | null = null;

  await mockKiosk(page, async (route, path) => {
    const method = route.request().method();
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/verify`) {
      return fulfill(route, {
        ...summaryResponse(),
        capabilities: { ...capabilities(), wellnessBookingAllowed: true },
      });
    }
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/membership-options`) {
      return fulfill(route, []);
    }
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/wellness/professionals`) {
      return fulfill(route, [{
        id: professionalId,
        displayName: "Dra. Ana Ruiz",
        professionalType: "NUTRITIONIST",
        bio: null,
        officeLocation: "Consultorio 1",
        engagementType: "STAFF",
      }]);
    }
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/wellness/professionals/${professionalId}/services`) {
      return fulfill(route, [{
        id: serviceId,
        professionalId,
        name: "Orientación inicial",
        serviceType: "CONSULTATION",
        durationMinutes: 30,
        priceCents: 0,
        currency: "MXN",
      }]);
    }
    if (method === "GET" && path === `/self-service/sessions/${sessionId}/wellness/professionals/${professionalId}/availability`) {
      return fulfill(route, appointmentPayload ? [] : [{ startsAt, endsAt: "2099-08-01T16:30:00Z", available: true }]);
    }
    if (method === "POST" && path === `/self-service/sessions/${sessionId}/wellness/appointments`) {
      appointmentPayload = route.request().postDataJSON() as Record<string, unknown>;
      return fulfill(route, {
        id: "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
        professionalId,
        professionalName: "Dra. Ana Ruiz",
        serviceId,
        serviceName: "Orientación inicial",
        priceCents: 0,
        startsAt,
        endsAt: "2099-08-01T16:30:00Z",
        status: "CONFIRMED",
      });
    }
    return false;
  });

  await openLaunchedKiosk(page);
  await page.getByPlaceholder(/Correo o tel/).fill("andrea@example.com");
  await page.getByLabel(/Fecha de nacimiento/).fill("10/05/1994");
  await page.getByRole("button", { name: "Continuar de forma segura" }).click();

  await page.getByRole("button", { name: /^Wellness/i }).click();

  await expect(page.getByRole("heading", { name: "Agenda una cita wellness gratuita" })).toBeVisible();
  await expect(page.getByLabel("Servicio gratuito")).toContainText("Orientación inicial");
  await page.getByRole("button", { name: /\d{2}:\d{2}/ }).click();
  await page.getByRole("button", { name: "Confirmar cita gratuita" }).click();

  await expect(page.getByText("Tu cita con Dra. Ana Ruiz fue creada correctamente.")).toBeVisible();
  expect(appointmentPayload).toEqual({
    professionalId,
    serviceId,
    startsAt,
  });
});

async function mockKiosk(
  page: Page,
  handler: (route: Route, path: string) => Promise<boolean>,
) {
  await page.route("**/api/v1/**", async (route) => {
    const path = new URL(route.request().url()).pathname.replace("/api/v1", "");
    if (await handler(route, path)) return;
    if (route.request().method() === "POST" && path === `/self-service/sessions/${sessionId}/reset`) {
      await fulfill(route, sessionResponse());
      return;
    }
    if (route.request().method() === "GET" && path === `/self-service/sessions/${sessionId}/visit-package`) {
      await route.fulfill({ status: 204, body: "" });
      return;
    }
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
    clientRegistrationAllowed: false,
    membershipEnrollmentAllowed: false,
    visitPackagePurchaseAllowed: false,
    membershipRenewalAllowed: true,
    membershipPlanChangeAllowed: true,
    resourceBookingAllowed: false,
    wellnessBookingAllowed: false,
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
    expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    verified: false,
    capabilities: capabilities(),
  };
}

async function openLaunchedKiosk(page: Page) {
  await page.addInitScript((storedSession) => {
    window.sessionStorage.setItem("gymos.kioskBrowserSession", JSON.stringify(storedSession));
  }, sessionResponse());
  await page.goto("/kiosk/reception");
}

function summaryResponse() {
  return {
    sessionId,
    expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    clientName: "Andrea Socia",
    profile: {
      clientId: "55555555-5555-4555-8555-555555555555",
      displayName: "Andrea Socia",
      email: "an***@example.com",
      phone: "***5555",
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
      managedAtCurrentBranch: true,
    }],
    pendingPayments: [],
    paymentActivity: [],
  };
}

function membershipQuote() {
  return {
    clientId: "55555555-5555-4555-8555-555555555555",
    planId,
    planName: "Plan mensual",
    planPriceCents: 79900,
    registrationFeeCents: 15000,
    registrationFeeLabel: "Inscripción",
    listPriceCents: 94900,
    discountCents: 0,
    totalCents: 94900,
    currency: "MXN",
    appliedBenefits: [],
  };
}
