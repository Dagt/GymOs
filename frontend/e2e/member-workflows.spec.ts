import { expect, test, type Page, type Route } from "@playwright/test";

const clientId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const membershipId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
const currentPlanId = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
const targetPlanId = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";

test("la ficha muestra beneficios, asigna descuentos y ejecuta el cambio de plan", async ({ page }) => {
  let currentMembership = membership(currentPlanId, "Plan Base", "2026-08-20");
  let inheritedAccess = [
    effectiveAccess("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee", "ACCESO_GENERAL", "Acceso General", currentPlanId, "Plan Base"),
  ];
  let assignedBenefits: Array<Record<string, unknown>> = [];
  let changePlanPayload: Record<string, unknown> | null = null;
  let assignedBenefitPayload: Record<string, unknown> | null = null;

  await bootMemberWorkflow(page, async (route, path) => {
    const method = route.request().method();
    if (method === "GET" && path === `/clients/${clientId}`) {
      return fulfill(route, {
        client: {
          id: clientId,
          personId: "ffffffff-ffff-4fff-8fff-ffffffffffff",
          firstName: "Andrea",
          lastName: "López",
          maternalLastName: "Ramos",
          birthDate: "1994-05-10",
          gender: "FEMALE",
          phone: "+524921111111",
          email: "andrea@example.test",
          photoReference: null,
          status: "ACTIVE",
          joinedAt: "2026-07-01T12:00:00Z",
          notes: null,
          createdAt: "2026-07-01T12:00:00Z",
          updatedAt: "2026-07-01T12:00:00Z",
        },
        timeline: [],
      });
    }
    if (method === "GET" && path === `/clients/${clientId}/memberships`) {
      return fulfill(route, [currentMembership]);
    }
    if (method === "GET" && path.startsWith("/memberships/") && path.endsWith("/periods")) {
      return fulfill(route, []);
    }
    if (method === "GET" && path === "/catalog/plans") {
      return fulfill(route, [
        plan(currentPlanId, "Plan Base", 50000),
        plan(targetPlanId, "Plan Plus", 80000),
      ]);
    }
    if (method === "POST" && path === "/membership-quotes") {
      const payload = route.request().postDataJSON() as { planId: string };
      const selected = payload.planId === targetPlanId
        ? plan(targetPlanId, "Plan Plus", 80000)
        : plan(currentPlanId, "Plan Base", 50000);
      return fulfill(route, {
        clientId,
        planId: selected.id,
        planName: selected.name,
        listPriceCents: selected.priceCents,
        discountCents: 0,
        totalCents: selected.priceCents,
        currency: "MXN",
        appliedBenefits: [],
      });
    }
    if (method === "GET" && path === `/clients/${clientId}/benefits`) {
      return fulfill(route, assignedBenefits);
    }
    if (method === "GET" && path === `/access/clients/${clientId}/effective-benefits`) {
      return fulfill(route, inheritedAccess);
    }
    if (method === "GET" && path === "/benefits") {
      return fulfill(route, [{
        id: "99999999-9999-4999-8999-999999999999",
        branchId: null,
        code: "ESTUDIANTE",
        name: "Descuento estudiante",
        benefitType: "STUDENT",
        discountType: "PERCENT",
        discountValueCents: null,
        discountPercent: 10,
        requiresEvidence: false,
        combinable: false,
        priority: 1,
        validFrom: null,
        validUntil: null,
        status: "ACTIVE",
        planIds: [],
      }]);
    }
    if (method === "POST" && path === `/clients/${clientId}/benefits`) {
      assignedBenefitPayload = route.request().postDataJSON() as Record<string, unknown>;
      assignedBenefits = [{
        id: "12121212-1212-4212-8212-121212121212",
        clientId,
        benefitId: assignedBenefitPayload.benefitId,
        benefitName: "Descuento estudiante",
        status: "ASSIGNED",
        validFrom: assignedBenefitPayload.validFrom,
        validUntil: assignedBenefitPayload.validUntil,
        verifiedAt: null,
      }];
      return fulfill(route, assignedBenefits[0]);
    }
    if (method === "POST" && path === `/memberships/${membershipId}/change-plan`) {
      changePlanPayload = route.request().postDataJSON() as Record<string, unknown>;
      currentMembership = membership(targetPlanId, "Plan Plus", "2026-08-29");
      inheritedAccess = [
        effectiveAccess("13131313-1313-4313-8313-131313131313", "SAUNA", "Sauna Premium", targetPlanId, "Plan Plus"),
      ];
      return fulfill(route, {
        saleId: currentMembership.saleId,
        saleNumber: currentMembership.saleNumber,
        saleStatus: "PAID",
        totalCents: 80000,
        paidCents: 80000,
        balanceCents: 0,
        currency: "MXN",
        membership: currentMembership,
      });
    }
    return false;
  });

  await page.goto(`/clients/${clientId}`);

  await expect(page.getByRole("heading", { name: "Beneficios y accesos" })).toBeVisible();
  await expect(page.getByText("Acceso General")).toBeVisible();
  await expect(page.getByText(/Heredado de Plan Base/)).toBeVisible();

  await page.getByRole("button", { name: "Cambiar plan" }).click();
  const changeDrawer = page.getByRole("dialog");
  await expect(changeDrawer.getByRole("heading", { name: "Cambiar plan" })).toBeVisible();
  await expect(changeDrawer.getByText(/No transfiere días restantes/i)).toBeVisible();
  await changeDrawer.getByLabel("Plan nuevo").selectOption(targetPlanId);
  await changeDrawer.getByRole("button", { name: "Cambiar plan" }).click();

  await expect(page.getByText(/Plan cambiado a Plan Plus/i)).toBeVisible();
  await expect(page.getByText("Sauna Premium")).toBeVisible();
  expect(changePlanPayload).toMatchObject({
    targetPlanId,
    method: "CASH",
    validateNow: true,
  });

  await page.getByRole("button", { name: "Asignar descuento" }).click();
  const benefitDrawer = page.getByRole("dialog");
  await benefitDrawer.getByLabel("Convenio o beneficio").selectOption(
    "99999999-9999-4999-8999-999999999999",
  );
  await benefitDrawer.getByLabel("Vigente hasta").fill("2026-12-31");
  await benefitDrawer.getByRole("button", { name: "Asignar descuento" }).click();

  await expect(page.getByText(/Se asignó Descuento estudiante/)).toBeVisible();
  await expect(page.getByText("Descuento estudiante", { exact: true })).toBeVisible();
  expect(assignedBenefitPayload).toMatchObject({
    benefitId: "99999999-9999-4999-8999-999999999999",
    validUntil: "2026-12-31",
  });
});

test("la renovación vive en el inicio y la ruta anterior redirige", async ({ page }) => {
  await bootMemberWorkflow(page);

  await page.goto("/reception");
  await expect(page.getByRole("heading", { name: /Renovación y Asignación de Membresía/i })).toBeVisible();
  await expect(page.locator('a[href="/reception/renewals"]')).toHaveCount(0);

  await page.goto("/reception/renewals");
  await expect(page).toHaveURL(/\/reception$/);
  await expect(page.getByRole("heading", { name: /Renovación y Asignación de Membresía/i })).toBeVisible();
});

async function bootMemberWorkflow(
  page: Page,
  override?: (route: Route, path: string) => Promise<boolean>,
) {
  const user = {
    userId: "14141414-1414-4414-8414-141414141414",
    organizationId: "15151515-1515-4515-8515-151515151515",
    branchId: "16161616-1616-4616-8616-161616161616",
    username: "recepcion@example.test",
    fullName: "Recepción Prueba",
    permissions: [
      "access.read",
      "benefit.read",
      "branch.read",
      "catalog.read",
      "client.benefit.assign",
      "client.create",
      "client.read",
      "client.update",
      "membership.change-plan",
      "membership.read",
      "membership.renew",
      "sale.create",
    ],
    roles: ["Recepcion"],
    platformAdmin: false,
    supportMode: false,
    console: "RECEPTION",
    organizationName: "Gym Prueba",
    brandName: "Gym Prueba",
    logoUrl: null,
    primaryColor: "#164E63",
    accentColor: "#F59E0B",
    surfaceColor: "#F4F7FB",
  };

  await page.route("**/api/v1/**", async (route) => {
    const path = new URL(route.request().url()).pathname.replace(/^\/api\/v1/, "");
    if (override && await override(route, path)) return;
    if (path === "/organizations/current") {
      await fulfill(route, {
        id: user.organizationId,
        tradeName: "Gym Prueba",
        brandName: "Gym Prueba",
      });
      return;
    }
    if (path === "/organizations/current/branches") {
      await fulfill(route, [{
        id: user.branchId,
        name: "Centro",
        code: "CENTRO",
        status: "ACTIVE",
        timezone: "America/Mexico_City",
        phone: null,
        accessScope: "FULL",
      }]);
      return;
    }
    if (path === "/catalog/plans") {
      await fulfill(route, [plan(currentPlanId, "Plan Base", 50000)]);
      return;
    }
    await fulfill(route, []);
  });

  await page.addInitScript((currentUser) => {
    window.localStorage.setItem("gymos.accessToken", "member-e2e-access");
    window.localStorage.setItem("gymos.refreshToken", "member-e2e-refresh");
    window.localStorage.setItem("gymos.currentUser", JSON.stringify(currentUser));
  }, user);
}

async function fulfill(route: Route, body: unknown) {
  await route.fulfill({
    contentType: "application/json",
    body: JSON.stringify(body),
  });
  return true;
}

function membership(planId: string, planName: string, endsOn: string) {
  return {
    id: membershipId,
    clientId,
    planId,
    planName,
    status: "ACTIVE",
    activatedAt: "2026-07-01T12:00:00Z",
    startsOn: "2026-07-01",
    endsOn,
    graceEndsOn: "2026-09-03",
    saleId: "17171717-1717-4717-8717-171717171717",
    saleNumber: planId === targetPlanId ? "V-PLAN-002" : "V-PLAN-001",
  };
}

function plan(id: string, name: string, priceCents: number) {
  return {
    id,
    productId: `${id.slice(0, -1)}1`,
    sku: name.toUpperCase().replaceAll(" ", "-"),
    name,
    priceCents,
    currency: "MXN",
    durationDays: 30,
    graceDays: 5,
    benefitsJson: null,
    multiBranchAccess: true,
    status: "ACTIVE",
    createdAt: "2026-07-01T12:00:00Z",
    updatedAt: "2026-07-01T12:00:00Z",
  };
}

function effectiveAccess(
  benefitId: string,
  code: string,
  name: string,
  planId: string,
  planName: string,
) {
  return {
    benefitId,
    code,
    name,
    membershipId,
    planId,
    planName,
  };
}
