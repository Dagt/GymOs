import { expect, test, type Page, type Route } from "@playwright/test";

const user = {
  userId: "77777777-7777-4777-8777-777777777777",
  organizationId: "88888888-8888-4888-8888-888888888888",
  branchId: "22222222-2222-4222-8222-222222222222",
  username: "owner@gymos.test",
  fullName: "Owner",
  permissions: ["sale.read", "sale.refund"],
  roles: ["Dueno del gimnasio"],
  platformAdmin: false,
  supportMode: false,
  console: "TENANT_OWNER",
};

const sale = {
  sale: {
    id: "11111111-1111-4111-8111-111111111111",
    saleNumber: "S-REFUND-1",
    branchId: "22222222-2222-4222-8222-222222222222",
    clientId: null,
    clientName: "Venta mostrador",
    status: "PAID",
    subtotalCents: 3000,
    discountCents: 0,
    totalCents: 3000,
    paidCents: 3000,
    balanceCents: 0,
    currency: "MXN",
    notes: null,
    soldAt: "2026-07-23T12:00:00Z",
    createdAt: "2026-07-23T12:00:00Z",
    updatedAt: "2026-07-23T12:00:00Z",
  },
  items: [{
    id: "33333333-3333-4333-8333-333333333333",
    productId: "44444444-4444-4444-8444-444444444444",
    itemType: "PHYSICAL",
    name: "Agua",
    quantity: 3,
    unitPriceCents: 1000,
    discountCents: 0,
    totalCents: 3000,
    refundedQuantity: 0,
    createdAt: "2026-07-23T12:00:00Z",
  }],
  payments: [{
    id: "55555555-5555-4555-8555-555555555555",
    method: "CASH",
    status: "COMPLETED",
    amountCents: 3000,
    currency: "MXN",
    reference: null,
    receiptObjectKey: null,
    receivedAt: "2026-07-23T12:00:00Z",
    validatedAt: "2026-07-23T12:00:00Z",
    notes: null,
  }],
  refunds: [],
  memberships: [],
  appliedBenefits: [],
};

test("registra devolucion por cantidad y solicita reposicion de inventario", async ({ page }) => {
  let refundBody: Record<string, unknown> | null = null;
  await boot(page, async (route, path) => {
    if (path === `/sales/${sale.sale.id}/refunds` && route.request().method() === "POST") {
      refundBody = route.request().postDataJSON();
      await route.fulfill({
        contentType: "application/json",
        body: JSON.stringify({
          ...sale,
          items: [{ ...sale.items[0], refundedQuantity: 2 }],
          refunds: [{
            id: "66666666-6666-4666-8666-666666666666",
            saleId: sale.sale.id,
            paymentId: null,
            status: "PROCESSED",
            amountCents: 2000,
            currency: "MXN",
            reason: "Producto devuelto",
            idempotencyKey: null,
            requestedBy: null,
            requestedAt: "2026-07-23T13:00:00Z",
            processedAt: "2026-07-23T13:00:00Z",
            createdAt: "2026-07-23T13:00:00Z",
            updatedAt: "2026-07-23T13:00:00Z",
          }],
        }),
      });
      return;
    }
    if (path === `/sales/${sale.sale.id}`) {
      await route.fulfill({ contentType: "application/json", body: JSON.stringify(sale) });
      return;
    }
    await route.fulfill({ contentType: "application/json", body: "[]" });
  });

  await page.goto(`/sales/${sale.sale.id}`);
  await expect(page.getByRole("heading", { name: "S-REFUND-1" })).toBeVisible();
  await page.getByLabel("Devolver productos y cantidades especificas").check();
  await page.getByLabel(/Agua/).check();
  await page.getByRole("spinbutton", { name: "Cantidad" }).fill("2");
  await page.getByLabel("Motivo").fill("Producto devuelto");
  await page.getByRole("button", { name: "Registrar reembolso" }).click();

  await expect.poll(() => refundBody).not.toBeNull();
  expect(refundBody).toMatchObject({
    amountCents: 2000,
    reason: "Producto devuelto",
    items: [{
      saleItemId: sale.items[0].id,
      quantity: 2,
      restock: true,
    }],
  });
  await expect(page.getByText("Devuelto 2 de 3")).toBeVisible();
});

async function boot(
  page: Page,
  handler: (route: Route, path: string) => Promise<void>,
) {
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/^\/api\/v1/, "");
    if (path === "/auth/me") {
      await route.fulfill({ contentType: "application/json", body: JSON.stringify(user) });
      return;
    }
    await handler(route, path);
  });
  await page.addInitScript(({ currentUser }) => {
    window.localStorage.setItem("gymos.accessToken", "e2e-access-token");
    window.localStorage.setItem("gymos.refreshToken", "e2e-refresh-token");
    window.localStorage.setItem("gymos.currentUser", JSON.stringify(currentUser));
  }, { currentUser: user });
}
