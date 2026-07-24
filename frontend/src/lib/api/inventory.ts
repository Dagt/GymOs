import { apiFetch, normalizePayload } from "./client";
import type {
  InventoryMovement,
  InventoryMovementPage,
  InventoryMovementPayload,
  InventoryMovementSearchParams,
  InventorySettingsPayload,
  InventoryStock,
  InventoryStockPage,
  InventoryStockSearchParams,
} from "./inventory-types";

export async function getInventoryStock(accessToken: string): Promise<InventoryStock[]> {
  return apiFetch<InventoryStock[]>("/inventory", accessToken);
}

export async function getInventoryStockDetail(
  accessToken: string,
  productId: string,
): Promise<InventoryStock> {
  return apiFetch<InventoryStock>(`/inventory/${productId}`, accessToken);
}

export async function searchInventoryStock(
  accessToken: string,
  params: InventoryStockSearchParams = {},
): Promise<InventoryStockPage> {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== "") search.set(key, String(value));
  });
  return apiFetch<InventoryStockPage>(`/inventory/search?${search.toString()}`, accessToken);
}

export async function getLowInventoryStock(accessToken: string): Promise<InventoryStock[]> {
  return apiFetch<InventoryStock[]>("/inventory/low-stock", accessToken);
}

export async function getInventoryMovements(
  accessToken: string,
  productId?: string,
  limit = 50,
): Promise<InventoryMovement[]> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (productId) params.set("productId", productId);
  return apiFetch<InventoryMovement[]>(`/inventory/movements?${params.toString()}`, accessToken);
}

export async function searchInventoryMovements(
  accessToken: string,
  params: InventoryMovementSearchParams = {},
): Promise<InventoryMovementPage> {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== "") search.set(key, String(value));
  });
  return apiFetch<InventoryMovementPage>(`/inventory/movements/search?${search.toString()}`, accessToken);
}

export async function createInventoryMovement(
  accessToken: string,
  payload: InventoryMovementPayload,
): Promise<InventoryStock> {
  return apiFetch<InventoryStock>("/inventory/movements", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(payload)),
  });
}

export async function updateInventorySettings(
  accessToken: string,
  productId: string,
  payload: InventorySettingsPayload,
): Promise<InventoryStock> {
  return apiFetch<InventoryStock>(`/inventory/products/${productId}/settings`, accessToken, {
    method: "PUT",
    body: JSON.stringify(normalizePayload(payload)),
  });
}
