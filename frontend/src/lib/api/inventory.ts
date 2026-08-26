import { apiFetch, normalizePayload } from "./client";
import type { PageResult } from "../pagination";
import type {
  InventoryBranchSettings,
  InventoryHistorySearchParams,
  InventoryMovement,
  InventoryMovementPage,
  InventoryMovementPayload,
  InventoryMovementSearchParams,
  InventoryPhysicalCount,
  InventorySettingsPayload,
  InventoryStock,
  InventoryStockPage,
  InventoryStockSearchParams,
  InventorySummary,
  InventoryTransfer,
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

export async function getInventorySummary(accessToken: string): Promise<InventorySummary> {
  return apiFetch<InventorySummary>("/inventory/summary", accessToken);
}

export async function getInventoryBranchSettings(accessToken: string): Promise<InventoryBranchSettings> {
  return apiFetch<InventoryBranchSettings>("/inventory/branch-settings", accessToken);
}

export async function updateInventoryBranchSettings(
  accessToken: string,
  allowNegativeStock: boolean,
): Promise<InventoryBranchSettings> {
  return apiFetch<InventoryBranchSettings>("/inventory/branch-settings", accessToken, {
    method: "PUT",
    body: JSON.stringify({ allowNegativeStock }),
  });
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

// Physical Counts (Conteos Físicos)
export async function getInventoryPhysicalCounts(accessToken: string): Promise<InventoryPhysicalCount[]> {
  return apiFetch<InventoryPhysicalCount[]>("/inventory/physical-counts", accessToken);
}

export function searchInventoryPhysicalCounts(accessToken: string, params: InventoryHistorySearchParams = {}): Promise<PageResult<InventoryPhysicalCount>> {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => { if (value !== undefined) query.set(key, String(value)); });
  return apiFetch<PageResult<InventoryPhysicalCount>>(`/inventory/physical-counts/search?${query}`, accessToken);
}

export async function getInventoryPhysicalCount(accessToken: string, countId: string): Promise<InventoryPhysicalCount> {
  return apiFetch<InventoryPhysicalCount>(`/inventory/physical-counts/${countId}`, accessToken);
}

export async function createInventoryPhysicalCount(accessToken: string, notes?: string): Promise<InventoryPhysicalCount> {
  return apiFetch<InventoryPhysicalCount>("/inventory/physical-counts", accessToken, {
    method: "POST",
    body: JSON.stringify({ notes }),
  });
}

export async function updateInventoryPhysicalCountItem(
  accessToken: string,
  countId: string,
  productId: string,
  payload: { countedQuantity?: number | null; notes?: string },
): Promise<InventoryPhysicalCount> {
  return apiFetch<InventoryPhysicalCount>(`/inventory/physical-counts/${countId}/items/${productId}`, accessToken, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export async function approveInventoryPhysicalCount(accessToken: string, countId: string): Promise<InventoryPhysicalCount> {
  return apiFetch<InventoryPhysicalCount>(`/inventory/physical-counts/${countId}/approve`, accessToken, {
    method: "POST",
  });
}

export async function cancelInventoryPhysicalCount(accessToken: string, countId: string): Promise<InventoryPhysicalCount> {
  return apiFetch<InventoryPhysicalCount>(`/inventory/physical-counts/${countId}/cancel`, accessToken, {
    method: "POST",
  });
}

// Branch Transfers (Transferencias)
export async function getInventoryTransfers(accessToken: string): Promise<InventoryTransfer[]> {
  return apiFetch<InventoryTransfer[]>("/inventory/transfers", accessToken);
}

export function searchInventoryTransfers(accessToken: string, params: InventoryHistorySearchParams = {}): Promise<PageResult<InventoryTransfer>> {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => { if (value !== undefined) query.set(key, String(value)); });
  return apiFetch<PageResult<InventoryTransfer>>(`/inventory/transfers/search?${query}`, accessToken);
}

export async function getInventoryTransfer(accessToken: string, transferId: string): Promise<InventoryTransfer> {
  return apiFetch<InventoryTransfer>(`/inventory/transfers/${transferId}`, accessToken);
}

export async function createInventoryTransfer(
  accessToken: string,
  payload: { destinationBranchId: string; notes?: string; items: Array<{ productId: string; requestedQuantity: number }> },
): Promise<InventoryTransfer> {
  return apiFetch<InventoryTransfer>("/inventory/transfers", accessToken, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function shipInventoryTransfer(accessToken: string, transferId: string): Promise<InventoryTransfer> {
  return apiFetch<InventoryTransfer>(`/inventory/transfers/${transferId}/ship`, accessToken, {
    method: "POST",
  });
}

export async function receiveInventoryTransfer(accessToken: string, transferId: string): Promise<InventoryTransfer> {
  return apiFetch<InventoryTransfer>(`/inventory/transfers/${transferId}/receive`, accessToken, {
    method: "POST",
  });
}

export async function cancelInventoryTransfer(accessToken: string, transferId: string): Promise<InventoryTransfer> {
  return apiFetch<InventoryTransfer>(`/inventory/transfers/${transferId}/cancel`, accessToken, {
    method: "POST",
  });
}
