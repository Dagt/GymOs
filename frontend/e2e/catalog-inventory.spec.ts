import { expect, test, type Page, type Route } from "@playwright/test";

type TestUser = {
  userId: string;
  organizationId: string;
  branchId: string;
  username: string;
  fullName: string;
  permissions: string[];
  roles: string[];
  platformAdmin: boolean;
  supportMode: boolean;
  console: "TENANT_OWNER";
  organizationName: string;
  brandName: string;
  logoUrl: string;
  primaryColor: string;
  accentColor: string;
  surfaceColor: string;
};

const product = {
  id: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  sku: "AGUA-001",
  name: "Agua",
  description: "Botella de agua",
  productType: "PHYSICAL",
  commercialSubtype: "GENERAL_PRODUCT",
  priceCents: 2500,
  currency: "MXN",
  taxable: true,
  status: "ACTIVE",
  barcode: "750000000001",
  brand: "GymOS",
  presentation: "600 ml",
  unitOfMeasure: "PIECE",
  inventoryTracked: true,
  displayOrder: 1,
  imageObjectKey: "catalog/product.webp",
  supplierName: "Proveedor protegido",
  referenceCostCents: 1200,
  promotionalPriceCents: null,
  promotionStartsAt: null,
  promotionEndsAt: null,
  categoryId: null,
  categoryName: null,
  tags: ["bebida"],
  archived: false,
  archivedAt: null,
  createdAt: "2026-07-23T12:00:00Z",
  updatedAt: "2026-07-23T12:00:00Z",
  version: 4,
};

const summary = {
  activeProducts: 1,
  activeServices: 0,
  activePlans: 0,
  inactiveProducts: 0,
  archivedProducts: 0,
  inventoryTrackedProducts: 1,
  uncategorizedProducts: 1,
  productsWithoutBarcode: 0,
  productsWithoutImage: 0,
  lowStockProducts: 0,
};

test.describe("catalogo e inventario", () => {
  test("filtros persisten en URL y la edicion envia version optimista", async ({ page }) => {
    let updatePayload: Record<string, unknown> | null = null;
    await bootCatalog(page, owner(), async (route, path) => {
      if (route.request().method() === "PUT" && path === `/catalog/products/${product.id}`) {
        updatePayload = route.request().postDataJSON() as Record<string, unknown>;
        await route.fulfill({ contentType: "application/json", body: JSON.stringify({ ...product, name: "Agua mineral", version: 5 }) });
        return true;
      }
      return false;
    });

    await page.goto("/catalog");
    await expect(page.getByRole("heading", { name: "Catalogo" })).toBeVisible();
    await page.getByPlaceholder("Nombre, SKU, etiqueta o codigo").fill("bebida");
    await expect(page).toHaveURL(/q=bebida/);
    await page.getByRole("button", { name: "Editar", exact: true }).click();
    await expect(page.getByLabel("Costo de referencia")).toBeVisible();
    await page.getByLabel("Nombre").fill("Agua mineral");
    await page.getByRole("button", { name: "Guardar cambios" }).click();

    await expect.poll(() => updatePayload?.version).toBe(4);
  });

  test("un editor sin catalog.cost.read no ve costos ni proveedor", async ({ page }) => {
    await bootCatalog(page, editorWithoutCosts());
    await page.goto("/catalog");
    await page.getByRole("button", { name: "Editar", exact: true }).click();

    await expect(page.getByLabel("Costo de referencia")).toHaveCount(0);
    await expect(page.getByLabel("Proveedor principal")).toHaveCount(0);
    await expect(page.getByText("El producto tiene una imagen almacenada.")).toHaveCount(0);
  });

  test("POS consulta el catalogo paginado y no descarga el endpoint completo", async ({ page }) => {
    let paginatedSearches = 0;
    let completeCatalogRequests = 0;
    await bootCatalog(page, owner(), async (route, path) => {
      if (path === "/catalog/products") completeCatalogRequests += 1;
      if (path === "/catalog/products/search") paginatedSearches += 1;
      return false;
    });

    await page.goto("/pos");
    await expect(page.getByRole("heading", { name: "Caja / POS" })).toBeVisible();
    await expect(page.getByRole("button", { name: /Agua/ })).toBeVisible();
    expect(paginatedSearches).toBeGreaterThan(0);
    expect(completeCatalogRequests).toBe(0);
  });
});

async function bootCatalog(
  page: Page,
  currentUser: TestUser,
  override?: (route: Route, path: string) => Promise<boolean>,
) {
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/^\/api\/v1/, "");
    if (override && await override(route, path)) return;
    await route.fulfill({ contentType: "application/json", body: JSON.stringify(responseFor(path)) });
  });
  await page.addInitScript(({ user }) => {
    window.localStorage.setItem("gymos.accessToken", "catalog-e2e-access");
    window.localStorage.setItem("gymos.refreshToken", "catalog-e2e-refresh");
    window.localStorage.setItem("gymos.currentUser", JSON.stringify(user));
  }, { user: currentUser });
}

function responseFor(path: string) {
  if (path === "/catalog/products/search") {
    return { items: [product], page: 0, size: 25, totalElements: 1, totalPages: 1 };
  }
  if (path === "/catalog/products/summary") return summary;
  if (path === "/catalog/categories" || path === "/catalog/plans") return [];
  if (path.startsWith("/sales")) return [];
  if (path === "/organizations/current/branches") return [];
  return [];
}

function owner(): TestUser {
  return user([
    "catalog.read", "catalog.create", "catalog.update", "catalog.status.manage", "catalog.archive",
    "catalog.category.manage", "catalog.image.manage", "catalog.cost.read", "sale.create", "sale.read",
  ]);
}

function editorWithoutCosts(): TestUser {
  return user(["catalog.read", "catalog.update"]);
}

function user(permissions: string[]): TestUser {
  return {
    userId: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
    organizationId: "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
    branchId: "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
    username: "owner@gymos.test",
    fullName: "Owner GymOS",
    permissions,
    roles: ["Dueno del gimnasio"],
    platformAdmin: false,
    supportMode: false,
    console: "TENANT_OWNER",
    organizationName: "GymOS Test",
    brandName: "GymOS Test",
    logoUrl: "",
    primaryColor: "#164E63",
    accentColor: "#EAB308",
    surfaceColor: "#F4F7FB",
  };
}
