import { expect, test, type Page, type Route } from "@playwright/test";

type TestUser = {
  userId: string;
  accountScope: "TENANT";
  organizationId: string;
  branchId: string;
  username: string;
  fullName: string;
  permissions: string[];
  roles: string[];
  platformAdmin: boolean;
  supportMode: boolean;
  supportExpiresAt: null;
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
  barcodes: ["750000000001", "750000000009"],
  brand: "GymOS",
  presentation: "600 ml",
  familyId: "ffffffff-ffff-4fff-8fff-ffffffffffff",
  familyName: "Agua",
  variantName: "600 ml",
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
    await expect(page.getByRole("heading", { name: "Catálogo" })).toBeVisible();
    await page.getByPlaceholder("Nombre, SKU o código").fill("bebida");
    await expect(page).toHaveURL(/q=bebida/);
    await page.getByRole("button", { name: "Editar", exact: true }).click();
    await page.getByText("Costo y proveedor", { exact: true }).click();
    await expect(page.getByLabel("Costo", { exact: true })).toBeVisible();
    await page.getByPlaceholder("Agua 600 ml").fill("Agua mineral");
    await page.getByRole("button", { name: "Guardar cambios" }).click();

    await expect.poll(() => updatePayload?.version).toBe(4);
  });

  test("un editor sin catalog.cost.read no ve costos ni proveedor", async ({ page }) => {
    await bootCatalog(page, editorWithoutCosts());
    await page.goto("/catalog");
    await page.getByRole("button", { name: "Editar", exact: true }).click();

    await expect(page.getByLabel("Costo", { exact: true })).toHaveCount(0);
    await expect(page.getByLabel("Proveedor", { exact: true })).toHaveCount(0);
    await expect(page.getByText("El producto tiene una imagen almacenada.")).toHaveCount(0);
  });

  test("alta de producto usa un selector cerrado de unidades comunes", async ({ page }) => {
    await bootCatalog(page, owner());
    await page.goto("/catalog");
    await page.getByRole("button", { name: /Nuevo art.culo o cargo/i }).click();
    await page.getByText(/M.s datos/i).click();

    const unit = page.getByLabel("Unidad");
    await expect(unit).toHaveJSProperty("tagName", "SELECT");
    await expect(unit).toHaveValue("PIECE");
    for (const label of ["Pieza", "Botella", "Lata", "Paquete", "Litro", "Kilogramo"]) {
      await expect(unit).toContainText(label);
    }
  });

  test("alta masiva hereda datos comunes y permite personalizar sólo una variante", async ({ page }) => {
    let batchPayload: Record<string, unknown> | null = null;
    await bootCatalog(page, ownerWithInventory(), async (route, path) => {
      if (route.request().method() === "POST" && path === "/catalog/product-families/variants") {
        batchPayload = route.request().postDataJSON() as Record<string, unknown>;
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify({ id: "family-psychotic", name: "Psychotic", brand: "Insane Labz", variants: [{ ...product, id: "variant-apple", name: "Psychotic · Manzana", sku: "PSY-MAN" }, { ...product, id: "variant-red", name: "Psychotic · Frutos rojos", sku: "PSY-FRU" }, { ...product, id: "variant-gum", name: "Psychotic · Chicle", sku: "PSY-CHI" }] }),
        });
        return true;
      }
      return false;
    });

    await page.goto("/catalog");
    await page.getByRole("button", { name: "Producto con variantes" }).click();
    await page.getByLabel("Nombre del producto").fill("Psychotic");
    await page.getByLabel("Marca").fill("Insane Labz");
    await page.getByLabel("Presentación común").fill("Bote de 35 scoops");
    await page.getByLabel("Precio común").fill("550");
    await page.getByLabel("Costo común").fill("320");
    await page.getByLabel("Proveedor").fill("Distribuidora Norte");
    await page.getByLabel("Stock mínimo común").fill("5");

    await page.getByLabel("Sabor o variante").nth(0).fill("Manzana");
    await expect(page.locator('input[name="variantSku-0"]')).toHaveValue("PSY-MAN");
    await page.getByLabel("Código de barras").nth(0).fill("750100000001");
    await page.getByRole("button", { name: "+ Agregar otro código" }).nth(0).click();
    await page.locator('input[name="variantAlias-0-0"]').fill("750100000009");

    await page.getByLabel("Sabor o variante").nth(1).fill("Frutos rojos");
    await expect(page.locator('input[name="variantSku-1"]')).toHaveValue("PSY-FRU");
    await page.getByLabel("Código de barras").nth(1).fill("750100000002");
    await page.getByRole("button", { name: "Personalizar" }).nth(1).click();
    await page.getByLabel("Presentación diferente").fill("Bote de 40 scoops");
    await page.getByLabel("Precio diferente").fill("580");
    await page.getByLabel("Costo diferente").fill("335");

    await page.getByRole("button", { name: "+ Agregar variante" }).click();
    await page.getByLabel("Sabor o variante").nth(2).fill("Chicle");
    await expect(page.locator('input[name="variantSku-2"]')).toHaveValue("PSY-CHI");
    await page.getByLabel("Código de barras").nth(2).fill("750100000003");
    await page.getByRole("button", { name: "Crear 3 variantes" }).click();

    await expect.poll(() => (batchPayload?.variants as Array<Record<string, unknown>> | undefined)?.length).toBe(3);
    expect(batchPayload).toMatchObject({
      commonPresentation: "Bote de 35 scoops",
      commonPriceCents: 55000,
      commonReferenceCostCents: 32000,
      commonReorderPoint: 5,
      supplierName: "Distribuidora Norte",
    });
    expect((batchPayload?.variants as Array<Record<string, unknown>>)[0]).toMatchObject({
      variantName: "Manzana",
      sku: "PSY-MAN",
      primaryBarcode: "750100000001",
      additionalBarcodes: ["750100000009"],
      priceCentsOverride: null,
    });
    expect((batchPayload?.variants as Array<Record<string, unknown>>)[1]).toMatchObject({
      variantName: "Frutos rojos",
      presentationOverride: "Bote de 40 scoops",
      priceCentsOverride: 58000,
      referenceCostCentsOverride: 33500,
    });
    await expect(page.getByText("3 variantes de Psychotic creadas.")).toBeVisible();
  });

  test("Enter del escáner mueve el foco sin enviar accidentalmente el formulario", async ({ page }) => {
    let submissions = 0;
    await bootCatalog(page, owner(), async (route, path) => {
      if (route.request().method() === "POST" && path === "/catalog/product-families/variants") {
        submissions += 1;
        await route.fulfill({ contentType: "application/json", body: JSON.stringify({ id: "family", name: "Psychotic", brand: null, variants: [] }) });
        return true;
      }
      return false;
    });
    await page.goto("/catalog");
    await page.getByRole("button", { name: "Producto con variantes" }).click();
    await page.getByLabel("Nombre del producto").fill("Psychotic");
    await page.getByLabel("Precio común").fill("550");
    await page.getByLabel("Sabor o variante").nth(0).fill("Manzana");
    await page.locator('input[name="variantSku-0"]').fill("SKU-MANUAL");
    await page.getByLabel("Sabor o variante").nth(0).fill("Manzana verde");
    await expect(page.locator('input[name="variantSku-0"]')).toHaveValue("SKU-MANUAL");
    await page.getByLabel("Código de barras").nth(0).fill("750100000001");
    await page.getByLabel("Código de barras").nth(0).press("Enter");

    await expect(page.getByLabel("Sabor o variante").nth(1)).toBeFocused();
    expect(submissions).toBe(0);

    await page.getByLabel("Sabor o variante").nth(1).fill("Chicle");
    await page.getByLabel("Código de barras").nth(1).fill("750100000002");
    await page.getByLabel("Código de barras").nth(1).press("Enter");
    await expect(page.getByRole("button", { name: "+ Agregar variante" })).toBeFocused();
    expect(submissions).toBe(0);
  });

  test("alta de diez variantes conserva un único bloque de datos comunes", async ({ page }) => {
    let batchSize = 0;
    await bootCatalog(page, owner(), async (route, path) => {
      if (route.request().method() === "POST" && path === "/catalog/product-families/variants") {
        const payload = route.request().postDataJSON() as { variants: unknown[] };
        batchSize = payload.variants.length;
        await route.fulfill({ contentType: "application/json", body: JSON.stringify({ id: "family", name: "Psychotic", brand: null, variants: payload.variants.map((_, index) => ({ ...product, id: `variant-${index}` })) }) });
        return true;
      }
      return false;
    });
    await page.goto("/catalog");
    await page.getByRole("button", { name: "Producto con variantes" }).click();
    await page.getByLabel("Nombre del producto").fill("Psychotic");
    await page.getByLabel("Presentación común").fill("Bote");
    await page.getByLabel("Precio común").fill("550");
    for (let index = 2; index < 10; index += 1) await page.getByRole("button", { name: "+ Agregar variante" }).click();
    for (let index = 0; index < 10; index += 1) {
      await page.getByLabel("Sabor o variante").nth(index).fill(`Sabor ${index + 1}`);
      await page.getByLabel("Código de barras").nth(index).fill(`7501000000${String(index).padStart(2, "0")}`);
    }
    await page.getByRole("button", { name: "Crear 10 variantes" }).click();
    await expect.poll(() => batchSize).toBe(10);
  });

  test("una familia existente recibe nuevas variantes sin duplicar nombre y marca", async ({ page }) => {
    let batchPayload: Record<string, unknown> | null = null;
    await bootCatalog(page, owner(), async (route, path) => {
      if (route.request().method() === "GET" && path === "/catalog/product-families") {
        await route.fulfill({ contentType: "application/json", body: JSON.stringify([{ id: "family-psychotic", name: "Psychotic", brand: "Insane Labz", description: null, activeVariants: 3, totalVariants: 3 }]) });
        return true;
      }
      if (route.request().method() === "POST" && path === "/catalog/product-families/variants") {
        batchPayload = route.request().postDataJSON() as Record<string, unknown>;
        await route.fulfill({ contentType: "application/json", body: JSON.stringify({ id: "family-psychotic", name: "Psychotic", brand: "Insane Labz", variants: [{ ...product, id: "variant-grape" }, { ...product, id: "variant-watermelon" }] }) });
        return true;
      }
      return false;
    });
    await page.goto("/catalog");
    await page.getByRole("button", { name: "Producto con variantes" }).click();
    await page.getByLabel("Familia comercial").selectOption("family-psychotic");
    await expect(page.getByText("Agregarás nuevas variantes a esta familia.")).toBeVisible();
    await page.getByLabel("Precio común").fill("550");
    await page.getByLabel("Sabor o variante").nth(0).fill("Uva");
    await page.getByLabel("Código de barras").nth(0).fill("750100000011");
    await page.getByLabel("Sabor o variante").nth(1).fill("Sandía");
    await page.getByLabel("Código de barras").nth(1).fill("750100000012");
    await page.getByRole("button", { name: "Crear 2 variantes" }).click();
    await expect.poll(() => batchPayload?.familyId).toBe("family-psychotic");
    expect(batchPayload).toMatchObject({ familyId: "family-psychotic", familyName: null, brand: null });
  });

  test("un creador sin permiso de costos no ve campos comunes ni overrides protegidos", async ({ page }) => {
    await bootCatalog(page, creatorWithoutCosts());
    await page.goto("/catalog");
    await page.getByRole("button", { name: "Producto con variantes" }).click();
    await expect(page.getByLabel("Costo común")).toHaveCount(0);
    await expect(page.getByLabel("Proveedor")).toHaveCount(0);
    await page.getByRole("button", { name: "Personalizar" }).first().click();
    await expect(page.getByLabel("Costo diferente")).toHaveCount(0);
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
    await expect(page.getByRole("heading", { name: "Caja Registradora" })).toBeVisible();
    await expect(page.getByRole("button", { name: /Agua/ })).toBeVisible();
    expect(paginatedSearches).toBeGreaterThan(0);
    expect(completeCatalogRequests).toBe(0);
  });

  test("las tarjetas del POS contienen nombres y SKU extensos sin solaparse", async ({ page }) => {
    const longProduct = {
      ...product,
      id: "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee",
      name: "Proteína aislada sabor chocolate con fórmula de recuperación muscular avanzada",
      sku: "PROTEINA-AISLADA-CHOCOLATE-RECUPERACION-2KG-EDICION-ESPECIAL",
    };
    await page.setViewportSize({ width: 1180, height: 820 });
    await bootCatalog(page, owner(), async (route, path) => {
      if (path === "/catalog/products/search") {
        await route.fulfill({
          contentType: "application/json",
          body: JSON.stringify({ items: [longProduct], page: 0, size: 25, totalElements: 1, totalPages: 1 }),
        });
        return true;
      }
      return false;
    });

    await page.goto("/pos");
    const card = page.getByTestId("pos-product-card").filter({ hasText: "Proteína aislada" });
    const name = card.getByTestId("pos-product-name");
    const sku = card.getByTestId("pos-product-sku");
    await expect(card).toBeVisible();
    await expect(name).toHaveAttribute("title", longProduct.name);
    await expect(sku).toHaveAttribute("title", longProduct.sku);

    const [cardBox, nameBox, skuBox] = await Promise.all([
      card.boundingBox(),
      name.boundingBox(),
      sku.boundingBox(),
    ]);
    expect(cardBox).not.toBeNull();
    expect(nameBox).not.toBeNull();
    expect(skuBox).not.toBeNull();
    expect((nameBox?.x ?? 0) + (nameBox?.width ?? 0)).toBeLessThanOrEqual((cardBox?.x ?? 0) + (cardBox?.width ?? 0) + 1);
    expect((skuBox?.x ?? 0) + (skuBox?.width ?? 0)).toBeLessThanOrEqual((cardBox?.x ?? 0) + (cardBox?.width ?? 0) + 1);
    expect((nameBox?.y ?? 0) + (nameBox?.height ?? 0)).toBeLessThanOrEqual((skuBox?.y ?? 0) + 1);
    const cardOverflow = await card.evaluate((element) => ({
      clientWidth: element.clientWidth,
      scrollWidth: element.scrollWidth,
    }));
    expect(cardOverflow.scrollWidth).toBeLessThanOrEqual(cardOverflow.clientWidth + 1);
  });

  test("lector HID agrega por codigo de barras y acumula cantidad", async ({ page }) => {
    await bootCatalog(page, owner());
    await page.goto("/pos");
    await expect(page.getByRole("button", { name: /Agua/ })).toBeVisible();

    await scanBarcode(page, product.barcodes[1]);
    await expect(page.getByRole("heading", { name: "Carrito (1)" })).toBeVisible();
    const cart = page.locator("form").filter({
      has: page.getByRole("heading", { name: "Carrito (1)" }),
    });
    await expect(cart.locator('input[type="number"]').first()).toHaveValue("1");

    await scanBarcode(page, product.barcode);
    await expect(cart.locator('input[type="number"]').first()).toHaveValue("2");
  });
});

async function scanBarcode(page: Page, barcode: string) {
  await page.evaluate((code) => {
    for (const key of code) {
      window.dispatchEvent(new KeyboardEvent("keydown", { key }));
    }
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter" }));
  }, barcode);
}

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
  if (path === "/system/info") return { capabilities: ["operational-pagination-v1"] };
  if (path === "/catalog/products/search") {
    return { items: [product], page: 0, size: 25, totalElements: 1, totalPages: 1 };
  }
  if (path === "/catalog/products/summary") return summary;
  if (path === "/catalog/categories" || path === "/catalog/plans" || path === "/catalog/product-families") return [];
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

function creatorWithoutCosts(): TestUser {
  return user(["catalog.read", "catalog.create"]);
}

function ownerWithInventory(): TestUser {
  return user([
    "catalog.read", "catalog.create", "catalog.update", "catalog.status.manage", "catalog.archive",
    "catalog.category.manage", "catalog.image.manage", "catalog.cost.read", "inventory.manage",
    "sale.create", "sale.read",
  ]);
}

function user(permissions: string[]): TestUser {
  return {
    userId: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
    accountScope: "TENANT",
    organizationId: "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
    branchId: "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
    username: "owner@gymos.test",
    fullName: "Owner GymOS",
    permissions,
    roles: ["Dueno del gimnasio"],
    platformAdmin: false,
    supportMode: false,
    supportExpiresAt: null,
    console: "TENANT_OWNER",
    organizationName: "GymOS Test",
    brandName: "GymOS Test",
    logoUrl: "",
    primaryColor: "#164E63",
    accentColor: "#EAB308",
    surfaceColor: "#F4F7FB",
  };
}
