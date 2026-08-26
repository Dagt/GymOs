import type { Page, Route } from "@playwright/test";

export const branchId = "22222222-2222-4222-8222-222222222222";
const orgId = "11111111-1111-4111-8111-111111111111";
const userId = "33333333-3333-4333-8333-333333333333";
const now = "2026-08-25T12:00:00Z";
const number = (index: number) => String(index + 1).padStart(3, "0");
export const id = (index: number) => `aaaaaaaa-aaaa-4aaa-8aaa-${String(index + 1).padStart(12, "0")}`;

export const products = Array.from({ length: 127 }, (_, index) => ({
  id: id(index), sku: `SKU-${number(index)}`, name: `Artículo ${number(index)}`, description: null,
  productType: "PHYSICAL", commercialSubtype: "GENERAL_PRODUCT", priceCents: 2500, currency: "MXN",
  taxable: false, status: "ACTIVE", barcode: `75000000${number(index)}`, brand: null, presentation: null,
  unitOfMeasure: "PIECE", inventoryTracked: true, displayOrder: index, imageObjectKey: null,
  supplierName: null, referenceCostCents: 1200, promotionalPriceCents: null, promotionStartsAt: null,
  promotionEndsAt: null, categoryId: null, categoryName: null, tags: [], archived: false,
  archivedAt: null, createdAt: now, updatedAt: now, version: 0,
}));

export const stock = products.map((product) => ({
  stockId: product.id, productId: product.id, sku: product.sku, productName: product.name,
  priceCents: product.priceCents, currency: "MXN", quantityOnHand: 2, reorderPoint: 5,
  averageCostCents: 1000, updatedAt: now, lowStock: true, suggestedReorderQuantity: 3,
}));

export const clients = products.map((product, index) => ({
  id: product.id, firstName: `Socio ${number(index)}`, lastName: "Prueba", maternalLastName: null,
  phone: `+52550000${String(index).padStart(4, "0")}`, email: `socio${number(index)}@example.test`,
  status: "ACTIVE", currentPlanName: "Mensual", membershipStatus: "ACTIVE", joinedAt: now, updatedAt: now,
}));

export const movements = stock.map((product, index) => ({
  id: product.productId, productId: product.productId, sku: product.sku, productName: product.productName,
  movementType: index === 125 ? "WASTE" : "PURCHASE", quantityDelta: 1, quantityBefore: 1,
  quantityAfter: 2, unitCostCents: 1000, referenceType: null, referenceId: null,
  reason: index === 125 ? "Ajuste excepcional" : `Ingreso ${number(index)}`, occurredAt: now,
}));

export const exercises = products.map((product, index) => ({
  id: product.id, organizationId: orgId, name: `Ejercicio ${number(index)}`, description: "Ejercicio de prueba", instructions: null,
  primaryMuscleGroup: index === 125 ? "Músculo excepcional" : "General", secondaryMuscleGroups: null,
  movementPattern: "Empuje", type: "STRENGTH", equipment: index === 125 ? "Trineo especial" : "Barra",
  suggestedLevel: "GENERAL", laterality: null, mediaUrl: null, warnings: null, origin: "COACH",
  status: "ACTIVE", createdBy: userId, createdAt: now, updatedAt: now,
}));

const counts = products.map((product, index) => ({
  id: product.id, organizationId: orgId, branchId, status: index === 126 ? "IN_PROGRESS" : "CANCELLED",
  notes: `Conteo ${number(index)}`, createdAt: now, updatedAt: now, createdBy: userId,
  approvedAt: null, approvedBy: null, items: [],
}));
const transfers = products.map((product, index) => ({
  id: product.id, organizationId: orgId, originBranchId: branchId, originBranchName: "Centro",
  destinationBranchId: "44444444-4444-4444-8444-444444444444", destinationBranchName: "Norte",
  status: index === 126 ? "SHIPPED" : "CANCELLED", notes: `Transferencia ${number(index)}`,
  createdAt: now, updatedAt: now, createdBy: userId, shippedAt: null, shippedBy: null, receivedAt: null, receivedBy: null, items: [],
}));

export function paged<T>(items: T[], url: URL) {
  const page = Number(url.searchParams.get("page") ?? 0);
  const size = Number(url.searchParams.get("size") ?? 25);
  return { items: items.slice(page * size, (page + 1) * size), page, size, totalElements: items.length, totalPages: Math.ceil(items.length / size) };
}

export async function json(route: Route, value: unknown) {
  await route.fulfill({ contentType: "application/json", body: JSON.stringify(value) });
  return true;
}

export async function bootPagination(page: Page, override?: (route: Route, path: string, url: URL) => Promise<boolean>, coach = false) {
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/^\/api\/v1/, "");
    if (override && await override(route, path, url)) return;
    if (path === "/system/info") return void await json(route, { capabilities: ["operational-pagination-v1"] });
    if (path === "/workout-assignments/history") return void await json(route, paged([], url));
    const query = (url.searchParams.get("q") ?? url.searchParams.get("query") ?? "").toLocaleLowerCase("es");
    const matches = (...values: (string | null)[]) => values.some((value) => value?.toLocaleLowerCase("es").includes(query));
    if (path === "/clients/search") return void await json(route, paged(clients.filter((client) => matches(client.firstName, client.email, client.phone)), url));
    if (path === "/catalog/products/search") return void await json(route, paged(products.filter((product) => matches(product.name, product.sku, product.barcode)), url));
    if (path === "/inventory/search") return void await json(route, paged(stock.filter((product) => matches(product.productName, product.sku)), url));
    if (path === "/inventory/movements/search") {
      const type = url.searchParams.get("type");
      const product = url.searchParams.get("productId");
      return void await json(route, paged(movements.filter((movement) => (!type || movement.movementType === type)
        && (!product || movement.productId === product) && matches(movement.productName, movement.sku, movement.reason)), url));
    }
    if (path === "/inventory/physical-counts/search") return void await json(route, paged(counts.filter((count) => url.searchParams.get("activeOnly") !== "true" || count.status === "IN_PROGRESS"), url));
    if (path === "/inventory/transfers/search") return void await json(route, paged(transfers.filter((transfer) => url.searchParams.get("activeOnly") !== "true" || transfer.status === "SHIPPED"), url));
    if (path === "/exercises") return void await json(route, paged(exercises.filter((exercise) => matches(exercise.name, exercise.equipment, exercise.primaryMuscleGroup)), url));
    if (path === "/catalog/products/summary") return void await json(route, { activeProducts: 127, activeServices: 0, activePlans: 0, inactiveProducts: 0, archivedProducts: 0, inventoryTrackedProducts: 127, uncategorizedProducts: 127, productsWithoutBarcode: 0, productsWithoutImage: 127, lowStockProducts: 127 });
    if (path === "/inventory/summary") return void await json(route, { totalValuationCents: 254000, totalTrackedProducts: 127, lowStockCount: 127, outOfStockCount: 0, allowNegativeStock: false });
    if (path === "/inventory/branch-settings") return void await json(route, { branchId, allowNegativeStock: false });
    if (path === "/organizations/current") return void await json(route, { id: orgId, tradeName: "Gimnasio de prueba", brandName: "GymFort QA" });
    if (path === "/branches" || path === "/organizations/current/branches") return void await json(route, [{ id: branchId, name: "Centro" }]);
    if (path === "/coaches") return void await json(route, [{ id: userId, userAccountId: userId, displayName: "Coach de prueba", specialtiesJson: "[]", status: "ACTIVE" }]);
    if (path === "/exercises/equipment-options") return void await json(route, ["Barra", "Trineo especial"]);
    return void await json(route, []);
  });
  await page.addInitScript(({ user }) => {
    localStorage.setItem("gymos.accessToken", "pagination-e2e-only");
    localStorage.setItem("gymos.refreshToken", "pagination-e2e-refresh-only");
    localStorage.setItem("gymos.currentUser", JSON.stringify(user));
  }, { user: {
    userId, accountScope: "TENANT", organizationId: orgId, branchId, username: "pagination@example.test", fullName: "Usuario de prueba",
    permissions: ["client.read", "client.create", "catalog.read", "catalog.create", "catalog.update", "catalog.archive", "inventory.read", "inventory.manage", "sale.create", "sale.read", "branch.read", "organization.read",
      ...(coach ? ["coach.read", "exercise.read", "exercise.create", "exercise.manage", "workout.read.own", "workout.manage.own", "workout.assign"] : [])],
    roles: [coach ? "Coach" : "Dueno del gimnasio"], platformAdmin: false, supportMode: false, supportExpiresAt: null,
    console: coach ? "COACH" : "TENANT_OWNER", organizationName: "Gimnasio de prueba", brandName: "GymFort QA", logoUrl: "", primaryColor: "#173c2a", accentColor: "#EAB308", surfaceColor: "#F4F7FB",
  } });
}
