import { apiFetch, normalizePayload } from "./client";
import type {
  InventoryMovement,
  InventoryMovementPayload,
  InventorySettingsPayload,
  InventoryStock,
} from "./inventory-types";

export async function getInventoryStock(accessToken: string): Promise<InventoryStock[]> {
  return apiFetch<InventoryStock[]>("/inventory", accessToken);
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
