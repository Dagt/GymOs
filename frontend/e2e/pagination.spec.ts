import { expect, test } from "@playwright/test";
import { bootPagination, clients, exercises, id, json, paged, products } from "./pagination-fixtures";

test("un backend antiguo se explica y no convierte un listado fallido en cero socios", async ({ page }) => {
  let searches = 0;
  await bootPagination(page, async (route, path) => {
    if (path === "/system/info") return json(route, { name: "GymOS", status: "BOOTSTRAPPED" });
    if (path === "/clients/search") searches++;
    return false;
  });
  await page.goto("/clients");
  await expect(page.getByText("Total no disponible", { exact: true })).toBeVisible();
  await expect(page.getByText(/frontend y el backend no están actualizados/).first()).toBeVisible();
  await expect(page.getByText("0 socios", { exact: true })).toHaveCount(0);
  expect(searches).toBe(0);
});

test("el listado se desplaza con teclado dentro de una altura limitada y reinicia al cambiar página", async ({ page }, testInfo) => {
  await bootPagination(page); await page.goto("/inventory");
  const list = page.getByRole("region", { name: "Listado de existencias", exact: true });
  const pager = page.getByRole("navigation", { name: "Paginación de existencias" });
  await expect(pager).toContainText("1–25 de 127 existencias");
  await expect(list).toHaveCSS("overflow-y", "auto"); await expect(list).toHaveCSS("scrollbar-width", "none");
  expect(await list.evaluate((node) => node.clientHeight < innerHeight * .6 && node.scrollHeight > node.clientHeight)).toBe(true);
  await list.focus(); await page.keyboard.press("End");
  await expect.poll(() => list.evaluate((node) => node.scrollTop)).toBeGreaterThan(0);
  await pager.getByRole("button", { name: "Siguiente" }).click();
  await expect(pager).toContainText("26–50 de 127 existencias");
  expect(await list.evaluate((node) => node.scrollTop)).toBe(0);
  await page.screenshot({ path: testInfo.outputPath("inventario-scroll-oculto.png") });
});

test("una consulta de conteos fallida no se presenta como historial vacío y permite reintentar", async ({ page }) => {
  let fail = true;
  await bootPagination(page, async (route, path) => {
    if (path === "/inventory/physical-counts/search" && fail) {
      await route.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ code: "INTERNAL_ERROR", message: "No fue posible consultar conteos." }) });
      return true;
    }
    return false;
  });
  await page.goto("/inventory");
  await page.getByRole("button", { name: "Auditorías", exact: true }).click();
  const failure = page.getByRole("region", { name: "Historial de conteos", exact: true });
  await expect(failure).toContainText("El total no está disponible");
  await expect(page.getByRole("navigation", { name: "Paginación de conteos" })).toHaveCount(0);
  fail = false;
  await failure.getByRole("button", { name: "Reintentar listado" }).click();
  await expect(page.getByRole("navigation", { name: "Paginación de conteos" })).toContainText("1–25 de 127 conteos");
});

test("socios muestra el total real, permite recorrer 127 y busca fuera de los primeros 50", async ({ page }) => {
  await bootPagination(page);
  await page.goto("/clients");
  const pager = page.getByRole("navigation", { name: "Paginación de socios" });
  await expect(pager).toContainText("1–25 de 127 socios");
  await expect(page.getByText("127 socios", { exact: true })).toBeVisible();
  await pager.getByLabel("socios por página").selectOption("50");
  await expect(pager).toContainText("1–50 de 127 socios");
  await pager.getByRole("button", { name: "Siguiente" }).click();
  await expect(pager).toContainText("51–100 de 127 socios");
  await pager.getByRole("button", { name: "Siguiente" }).click();
  await expect(pager).toContainText("101–127 de 127 socios");
  await expect(pager.getByRole("button", { name: "Siguiente" })).toBeDisabled();
  await expect(page.getByRole("link", { name: /Socio 127 Prueba/ })).toBeVisible();
  await page.getByPlaceholder("Busca por nombre, teléfono o email...").fill("Socio 126");
  await page.getByRole("button", { name: "Buscar Socio" }).click();
  await expect(pager).toContainText("1–1 de 1 socios");
  await expect(pager).toContainText("Página 1 de 1");
  await expect(page.getByRole("link", { name: /Socio 126 Prueba/ })).toBeVisible();
  await page.getByPlaceholder("Busca por nombre, teléfono o email...").fill("No existe");
  await page.getByRole("button", { name: "Buscar Socio" }).click();
  await expect(pager).toContainText("0–0 de 0 socios");
});

test("una búsqueda lenta de socios no reemplaza resultados más recientes", async ({ page }) => {
  let releaseOld!: () => void;
  const pending = new Promise<void>((resolve) => { releaseOld = resolve; });
  let oldStarted = false;
  await bootPagination(page, async (route, path, url) => {
    if (path === "/clients/search" && url.searchParams.get("query") === "Socio 001") {
      oldStarted = true;
      await pending;
      return json(route, paged([clients[0]], url));
    }
    return false;
  });
  await page.goto("/clients");
  await expect(page.getByText("127 socios", { exact: true })).toBeVisible();
  const input = page.getByPlaceholder("Busca por nombre, teléfono o email...");
  await input.fill("Socio 001");
  await page.getByRole("button", { name: "Buscar Socio" }).click();
  await expect.poll(() => oldStarted).toBe(true);
  await input.fill("Socio 126");
  await page.getByRole("button", { name: "Buscar Socio" }).click();
  await expect(page.getByRole("link", { name: /Socio 126 Prueba/ })).toBeVisible();
  const oldResponse = page.waitForResponse((response) => new URL(response.url()).searchParams.get("query") === "Socio 001");
  releaseOld();
  await oldResponse;
  await expect(page.getByRole("link", { name: /Socio 001 Prueba/ })).toHaveCount(0);
});

test("catálogo reinicia la página al buscar y al limpiar los filtros", async ({ page }, testInfo) => {
  const searchPages: number[] = [];
  await bootPagination(page, async (_route, path, url) => {
    if (path === "/catalog/products/search" && url.searchParams.get("q")) searchPages.push(Number(url.searchParams.get("page")));
    return false;
  });
  await page.goto("/catalog?page=4");
  const pager = page.getByRole("navigation", { name: "Paginación de artículos" });
  await expect(pager).toContainText("101–125 de 127 artículos");
  await page.getByPlaceholder("Nombre, SKU o código").fill("Artículo 126");
  await expect(pager).toContainText("1–1 de 1 artículos");
  expect(searchPages).not.toHaveLength(0);
  expect(searchPages.every((value) => value === 0)).toBe(true);
  await expect(page).not.toHaveURL(/page=4/);
  await page.getByRole("button", { name: /Limpiar/ }).click();
  await expect(pager).toContainText("1–25 de 127 artículos");
  await expect(page.getByPlaceholder("Nombre, SKU o código")).toHaveValue("");
  await pager.scrollIntoViewIfNeeded();
  await page.screenshot({ path: testInfo.outputPath("catalogo-desktop.png") });
});

test("al archivar el último artículo de una página vuelve a la última página válida", async ({ page }) => {
  let items = products.slice(0, 51);
  await bootPagination(page, async (route, path, url) => {
    if (path === "/catalog/products/search") return json(route, paged(items, url));
    if (path === `/catalog/products/${id(50)}` && route.request().method() === "DELETE") {
      items = items.filter((product) => product.id !== id(50));
      return json(route, { ...products[50], archived: true });
    }
    return false;
  });
  page.on("dialog", (dialog) => dialog.accept());
  await page.goto("/catalog?page=2");
  const pager = page.getByRole("navigation", { name: "Paginación de artículos" });
  await expect(pager).toContainText("51–51 de 51 artículos");
  await page.getByRole("button", { name: "Archivar", exact: true }).click();
  await expect(pager).toContainText("26–50 de 50 artículos");
  await expect(pager).toContainText("Página 2 de 2");
});

test("inventario busca en todos los movimientos y reinicia página con cada filtro", async ({ page }) => {
  await bootPagination(page);
  await page.goto("/inventory");
  await page.getByRole("button", { name: "Historial de Movimientos" }).click();
  const pager = page.getByRole("navigation", { name: "Paginación de movimientos" });
  await expect(pager).toContainText("1–25 de 127 movimientos");
  await pager.getByRole("button", { name: "Siguiente" }).click();
  await expect(pager).toContainText("26–50 de 127 movimientos");
  await page.getByLabel("Tipo de movimiento").selectOption("WASTE");
  await expect(pager).toContainText("1–1 de 1 movimientos");
  await expect(page.getByRole("cell", { name: "Ajuste excepcional" })).toBeVisible();
  await page.getByRole("button", { name: "Limpiar", exact: true }).click();
  await expect(pager).toContainText("1–25 de 127 movimientos");
  await page.getByPlaceholder("Nombre de producto, SKU o motivo...").fill("excepcional");
  await expect(pager).toContainText("1–1 de 1 movimientos");
  await page.getByRole("button", { name: "Limpiar", exact: true }).click();
  await expect(pager).toContainText("1–25 de 127 movimientos");
  await pager.getByRole("button", { name: "Siguiente" }).click();
  await page.getByRole("button", { name: "Todos los productos", exact: true }).click();
  await page.getByLabel("Buscar producto en todo el inventario").fill("SKU-126");
  await page.getByRole("button", { name: /Artículo 126 SKU-126/ }).click();
  await expect(pager).toContainText("1–1 de 1 movimientos");
  await expect(page.getByRole("cell", { name: "Artículo 126", exact: true })).toBeVisible();
});

test("existencias y alertas tienen páginas y los selectores no dependen de la página visible", async ({ page }, testInfo) => {
  await bootPagination(page);
  await page.goto("/inventory");
  const pager = page.getByRole("navigation", { name: "Paginación de existencias" });
  const alerts = page.getByRole("navigation", { name: "Paginación de alertas" });
  await expect(pager).toContainText("1–25 de 127 existencias");
  await expect(alerts).toContainText("1–10 de 127 alertas");
  await pager.scrollIntoViewIfNeeded();
  await page.screenshot({ path: testInfo.outputPath("inventario-desktop.png") });
  await pager.getByRole("button", { name: "Siguiente" }).click();
  await expect(pager).toContainText("26–50 de 127 existencias");
  await page.getByPlaceholder("Nombre, SKU o código de barras", { exact: true }).fill("Artículo 126");
  await expect(pager).toContainText("1–1 de 1 existencias");
  await alerts.getByRole("button", { name: "Siguiente" }).click();
  await expect(alerts).toContainText("11–20 de 127 alertas");
  await page.getByRole("button", { name: /Artículo 011.*Reordenar/ }).click();
  await expect(page.getByRole("heading", { name: "Artículo 011", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Registrar Movimiento", exact: true }).click();
  const drawer = page.getByRole("dialog");
  await drawer.getByRole("button", { name: "Artículo 011 · SKU-011" }).click();
  await drawer.getByLabel("Buscar producto en todo el inventario").fill("SKU-127");
  await drawer.getByRole("button", { name: /Artículo 127 SKU-127/ }).click();
  await expect(drawer.getByRole("button", { name: "Artículo 127 · SKU-127" })).toBeVisible();
});

test("exportar CSV dice página e incluye sólo las filas de la página seleccionada", async ({ page }) => {
  await bootPagination(page);
  await page.goto("/inventory");
  const pager = page.getByRole("navigation", { name: "Paginación de existencias" });
  await expect(pager).toContainText("1–25 de 127 existencias");
  await pager.getByRole("button", { name: "Siguiente" }).click();
  await expect(pager).toContainText("26–50 de 127 existencias");
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Exportar página CSV" }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toContain("_pagina_2_");
  const stream = await download.createReadStream();
  let content = "";
  for await (const chunk of stream!) content += chunk.toString();
  expect(content.trim().split(/\r?\n/)).toHaveLength(26);
  expect(content).toContain("SKU-026");
  expect(content).not.toContain("SKU-001");
});

test("historiales superan 50 registros y los indicadores activos no cambian con la página", async ({ page }) => {
  await bootPagination(page);
  await page.goto("/inventory");
  await page.getByRole("button", { name: "Auditorías", exact: true }).click();
  const counts = page.getByRole("navigation", { name: "Paginación de conteos" });
  await expect(counts).toContainText("1–25 de 127 conteos");
  await expect(page.getByText("Sesión Activa de Conteo en Curso")).toBeVisible();
  await counts.getByLabel("conteos por página").selectOption("100");
  await expect(counts).toContainText("1–100 de 127 conteos");
  await counts.getByRole("button", { name: "Siguiente" }).click();
  await expect(counts).toContainText("101–127 de 127 conteos");
  await expect(page.getByText("Conteo 127", { exact: true })).toHaveCount(2);
  await page.getByRole("button", { name: /Gestión de Transferencias/ }).click();
  const transfers = page.getByRole("navigation", { name: "Paginación de transferencias" });
  await transfers.getByLabel("transferencias por página").selectOption("100");
  await expect(transfers).toContainText("1–100 de 127 transferencias");
  await transfers.getByRole("button", { name: "Siguiente" }).click();
  await expect(transfers).toContainText("101–127 de 127 transferencias");
  await expect(page.getByText("Transferencia 127", { exact: true })).toBeVisible();
});

test("POS navega más de 50 productos sin perder el carrito", async ({ page }) => {
  await bootPagination(page);
  await page.goto("/pos");
  const pager = page.getByRole("navigation", { name: "Paginación de productos" });
  await expect(pager).toContainText("1–25 de 127 productos");
  await page.getByTestId("pos-product-card").filter({ hasText: "Artículo 001" }).click();
  await pager.getByRole("button", { name: "Siguiente" }).click();
  await expect(pager).toContainText("26–50 de 127 productos");
  await page.getByTestId("pos-product-card").filter({ hasText: "Artículo 026" }).click();
  await expect(page.getByRole("heading", { name: "Carrito (2)" })).toBeVisible();
  await pager.getByLabel("productos por página").selectOption("100");
  await expect(pager).toContainText("1–100 de 127 productos");
  await pager.getByRole("button", { name: "Siguiente" }).click();
  await expect(pager).toContainText("101–127 de 127 productos");
  await page.getByPlaceholder("Nombre del producto, SKU o código de barras...").fill("SKU-126");
  await expect(pager).toContainText("1–1 de 1 productos");
  await expect(page.getByTestId("pos-product-card")).toHaveCount(1);
  await expect(page.getByRole("heading", { name: "Carrito (2)" })).toBeVisible();
});

test("actualizar una partida no desmonta la captura del conteo activo", async ({ page }) => {
  let afterSave = false;
  let refreshStarted = false;
  let releaseRefresh!: () => void;
  const pending = new Promise<void>((resolve) => { releaseRefresh = resolve; });
  const count = {
    id: id(126), status: "IN_PROGRESS", createdAt: "2026-08-25T12:00:00Z",
    items: products.slice(0, 2).map((product) => ({
      id: product.id, productId: product.id, productName: product.name, sku: product.sku,
      systemQuantity: 2, countedQuantity: null as number | null, varianceQuantity: null as number | null,
    })),
  };
  await bootPagination(page, async (route, path, url) => {
    if (path === "/inventory/physical-counts/search" && url.searchParams.get("activeOnly") === "true") {
      if (afterSave) { refreshStarted = true; await pending; }
      return json(route, paged([count], url));
    }
    if (path === `/inventory/physical-counts/${count.id}/items/${products[0].id}` && route.request().method() === "PUT") {
      count.items[0].countedQuantity = route.request().postDataJSON().countedQuantity;
      count.items[0].varianceQuantity = count.items[0].countedQuantity! - 2;
      afterSave = true;
      return json(route, count);
    }
    return false;
  });
  await page.goto("/inventory");
  await page.getByRole("button", { name: "Auditorías", exact: true }).click();
  await page.getByRole("button", { name: "Abrir Conteo Físico", exact: true }).click();
  const drawer = page.getByRole("dialog");
  await drawer.getByLabel("Cantidad física de Artículo 001").fill("4");
  const nextItem = drawer.getByLabel("Cantidad física de Artículo 002");
  await nextItem.fill("9");
  try {
    await expect.poll(() => refreshStarted).toBe(true);
    await expect(nextItem).toBeFocused();
    await expect(nextItem).toHaveValue("9");
    const refreshed = page.waitForResponse((response) => response.url().includes("physical-counts/search") && response.url().includes("activeOnly=true"));
    releaseRefresh();
    await refreshed;
    await expect(nextItem).toHaveValue("9");
    await expect(nextItem).toBeFocused();
  } finally {
    releaseRefresh();
  }
});

test("ejercicios busca en todo el catálogo y el editor no usa la página del catálogo", async ({ page }) => {
  let saved: Record<string, unknown> | null = null;
  await bootPagination(page, async (route, path) => {
    if (path === "/workouts" && route.request().method() === "POST") {
      saved = route.request().postDataJSON();
      return json(route, { workout: { id: id(1000), title: "Rutina paginada", status: "DRAFT" }, versions: [] });
    }
    return false;
  }, true);
  await page.goto("/coaches");
  await page.getByRole("button", { name: "Ejercicios", exact: true }).click();
  const pager = page.getByRole("navigation", { name: "Paginación de ejercicios" });
  await expect(pager).toContainText("1–25 de 127 ejercicios");
  await pager.getByLabel("ejercicios por página").selectOption("100");
  await expect(pager).toContainText("1–100 de 127 ejercicios");
  await pager.getByRole("button", { name: "Siguiente" }).click();
  await expect(pager).toContainText("101–127 de 127 ejercicios");
  await page.getByPlaceholder("Buscar nombre, músculo o equipo").fill("Trineo especial");
  await expect(pager).toContainText("1–1 de 1 ejercicios");
  await expect(page.locator(".exercise-catalog-card")).toContainText("Ejercicio 126");
  await page.getByRole("button", { name: "Constructor", exact: true }).click();
  await page.getByLabel("Título").fill("Rutina paginada");
  await page.getByRole("button", { name: "Continuar a días y ejercicios" }).click();
  await page.getByRole("combobox", { name: "Ejercicio", exact: true }).fill("Ejercicio 001");
  await expect(page.locator('datalist option[value="Ejercicio 001"]')).toHaveCount(1);
  await page.getByRole("button", { name: "Revisar rutina" }).click();
  await page.getByRole("button", { name: "Guardar borrador" }).click();
  await expect.poll(() => saved).not.toBeNull();
  const content = JSON.parse(String(saved!.contentJson));
  expect(content.sessions[0].blocks[0].exercises[0].exerciseId).toBe(exercises[0].id);
});

test("paginación de socios sigue visible y sin desbordamiento en móvil", async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await bootPagination(page);
  await page.goto("/clients");
  const pager = page.getByRole("navigation", { name: "Paginación de socios" });
  await expect(pager).toContainText("1–25 de 127 socios");
  await pager.scrollIntoViewIfNeeded();
  await expect(pager.getByRole("button", { name: "Siguiente" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath("socios-mobile.png") });
});
