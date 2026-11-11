import { test, expect } from "@playwright/test";
import { bootPagination, branchId, stock, json, paged } from "./pagination-fixtures";

test("inventario y transferencia conservan operaciones y contienen nombres largos", async ({ page }, info) => {
  const longName = "Proteína de recuperación presentación profesional sabor chocolate con ingredientes y descripción extensa para sucursales";
  let created: Record<string, unknown> | undefined;
  await bootPagination(page, async (route, path, url) => {
    if (path === "/branches" || path === "/organizations/current/branches" || path === "/organizations/current/branches/all") { await json(route, [{ id: branchId, name: "Centro" }, { id: "44444444-4444-4444-8444-444444444444", name: "Norte" }]); return true; }
    if (path === "/inventory/search") { await json(route, paged([{ ...stock[0], productName: longName }], url)); return true; }
    if (path === "/inventory/transfers" && route.request().method() === "POST") { created = route.request().postDataJSON(); await json(route, { id: "qa-transfer", status: "REQUESTED" }); return true; }
    return false;
  });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto("/inventory");
  await page.getByRole("region", { name: "Listado de existencias" }).getByRole("button").first().click();
  const summary = page.locator("section").filter({ has: page.getByRole("heading", { name: longName, exact: true }) }).last();
  await expect(summary.getByRole("button", { name: "Registrar Movimiento" })).toBeEnabled();
  await expect(summary.getByRole("button", { name: "Conteo Físico", exact: true })).toHaveCount(0);
  await expect(summary.getByRole("button", { name: "Transferencia", exact: true })).toHaveCount(0);
  await page.screenshot({ fullPage: true, path: info.outputPath("inventario.png") });
  await page.setViewportSize({ width: 390, height: 900 });
  await page.screenshot({ fullPage: true, path: info.outputPath("inventario-movil.png") });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await summary.getByRole("button", { name: "Registrar Movimiento" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("dialog").getByRole("button", { name: "Cerrar", exact: true }).click();
  await page.getByRole("button", { name: /Gestión de Transferencias/ }).click();
  await page.getByRole("button", { name: "Crear Transferencia", exact: true }).click();
  const drawer = page.getByRole("dialog");
  await page.keyboard.press("Shift+Tab");
  await expect(drawer.getByLabel("Notas / Motivo")).toBeFocused();
  await page.keyboard.press("Tab");
  await expect(drawer.getByRole("button", { name: "Cerrar", exact: true })).toBeFocused();
  await drawer.getByLabel("Sucursal Destino").selectOption("44444444-4444-4444-8444-444444444444");
  await drawer.getByRole("button", { name: "Seleccionar producto" }).click();
  const search = drawer.getByLabel("Buscar producto en todo el inventario");
  await expect(search).toBeFocused();
  for (const term of ["Proteína", "SKU-001", "75000000001"]) { await search.fill(term); await expect(drawer.getByRole("list", { name: "Opciones de producto" }).getByRole("button")).toHaveCount(1); }
  await page.screenshot({ fullPage: true, path: info.outputPath("transferencia-selector.png") });
  await search.press("Escape");
  await expect(drawer).toBeVisible();
  await drawer.getByRole("button", { name: "Seleccionar producto" }).click();
  await search.press("ArrowDown");
  await page.keyboard.press("Enter");
  await expect(drawer.getByText("Disponible: 2 u.")).toBeVisible();
  await page.screenshot({ fullPage: true, path: info.outputPath("transferencia-seleccion.png") });
  await drawer.getByLabel("Cantidad").fill("3");
  await drawer.getByRole("button", { name: "Agregar", exact: true }).click();
  await expect(drawer.getByText(/No es posible transferir/)).toBeVisible();
  await drawer.getByLabel("Cantidad").fill("1");
  await drawer.getByRole("button", { name: "Agregar", exact: true }).click();
  await expect(drawer.getByText("1 unidades", { exact: true })).toBeVisible();
  await page.screenshot({ fullPage: true, path: info.outputPath("transferencia-partidas.png") });
  for (const width of [768, 390]) {
    await page.setViewportSize({ width, height: 900 });
    expect(await drawer.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
    await page.screenshot({ fullPage: true, path: info.outputPath(`transferencia-${width}.png`) });
  }
  await drawer.getByLabel("Notas / Motivo").fill("Reposición QA");
  await drawer.getByRole("button", { name: "Crear Solicitud de Transferencia" }).click();
  await expect.poll(() => created).toMatchObject({ items: [{ productId: stock[0].productId, requestedQuantity: 1 }] });
});

test("catálogo mantiene filtros, limpieza y distribución responsiva", async ({ page }, info) => {
  await bootPagination(page, async (route, path) => {
    if (path !== "/catalog/categories") return false;
    await json(route, [{ id: "qa-category", name: "Suplementos", status: "ACTIVE" }]);
    return true;
  });
  await page.goto("/catalog");
  const filters = page.getByRole("region", { name: "Filtros del catálogo" });
  await filters.getByLabel("Tipo", { exact: true }).selectOption("PHYSICAL");
  await expect.poll(() => new URL(page.url()).searchParams.get("type")).toBe("PHYSICAL");
  await filters.getByLabel("Categoría", { exact: true }).selectOption("qa-category");
  await expect(page).toHaveURL(/categoryId=qa-category/);
  await filters.getByLabel("Estado", { exact: true }).selectOption("ACTIVE");
  await expect.poll(() => new URL(page.url()).searchParams.get("status")).toBe("ACTIVE");
  await filters.getByLabel("Inventario", { exact: true }).selectOption("true");
  await expect.poll(() => new URL(page.url()).searchParams.get("inventoryTracked")).toBe("true");
  await filters.getByLabel("Orden", { exact: true }).selectOption("name,desc");
  await expect.poll(() => new URL(page.url()).searchParams.get("sort")).toBe("name,desc");
  await filters.getByLabel("Archivados", { exact: true }).click();
  await expect(page).toHaveURL(/includeArchived=true/);
  for (const width of [1440, 768, 390]) {
    await page.setViewportSize({ width, height: 1000 });
    expect(await filters.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
    await filters.screenshot({ path: info.outputPath(`catalogo-avanzado-${width}.png`) });
  }
  await page.setViewportSize({ width: 1440, height: 1000 });
  await filters.locator("summary").click();
  await filters.screenshot({ path: info.outputPath("catalogo-principal.png") });
  await filters.getByRole("button", { name: "Limpiar", exact: true }).click();
  await expect(page).not.toHaveURL(/includeArchived|status=|inventoryTracked|type=/);
});
