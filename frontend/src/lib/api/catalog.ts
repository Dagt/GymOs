import { apiFetch, normalizePayload } from "./client";
import { DEFAULT_PAGE_SIZE } from "../pagination";
import type {
  MembershipPlanPayload,
  CatalogCategory,
  CatalogCategoryPayload,
  CatalogSummary,
  PlanPayload,
  PlanSummary,
  ProductDetail,
  ProductPayload,
  ProductSummary,
  ProductPage,
  ProductSearchParams,
  UpdateMembershipPlanPayload,
} from "./catalog-types";

export async function getProducts(accessToken: string): Promise<ProductSummary[]> {
  return apiFetch<ProductSummary[]>("/catalog/products", accessToken);
}

export async function getCatalogSummary(accessToken: string): Promise<CatalogSummary> {
  return apiFetch<CatalogSummary>("/catalog/products/summary", accessToken);
}

export async function getProductDetail(accessToken: string, productId: string): Promise<ProductDetail> {
  return apiFetch<ProductDetail>(`/catalog/products/${productId}/detail`, accessToken);
}

export async function getProductByBarcode(accessToken: string, barcode: string): Promise<ProductSummary> {
  return apiFetch<ProductSummary>(`/catalog/products/by-barcode/${encodeURIComponent(barcode)}`, accessToken);
}

export async function searchProducts(accessToken: string, params: ProductSearchParams = {}): Promise<ProductPage> {
  const search = new URLSearchParams({ size: String(DEFAULT_PAGE_SIZE) });
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== "") search.set(key, String(value));
  });
  const suffix = search.size ? `?${search.toString()}` : "";
  return apiFetch<ProductPage>(`/catalog/products/search${suffix}`, accessToken);
}

export async function createProduct(accessToken: string, payload: ProductPayload): Promise<ProductSummary> {
  return apiFetch<ProductSummary>("/catalog/products", accessToken, { method: "POST", body: JSON.stringify(normalizePayload(payload)) });
}

export async function updateProduct(accessToken: string, productId: string, payload: ProductPayload): Promise<ProductSummary> {
  return apiFetch<ProductSummary>(`/catalog/products/${productId}`, accessToken, { method: "PUT", body: JSON.stringify(normalizePayload(payload)) });
}

export async function changeProductStatus(accessToken: string, productId: string, status: ProductSummary["status"]): Promise<ProductSummary> {
  return apiFetch<ProductSummary>(`/catalog/products/${productId}/status`, accessToken, { method: "PATCH", body: JSON.stringify({ status }) });
}

export async function archiveProduct(accessToken: string, productId: string): Promise<void> {
  return apiFetch<void>(`/catalog/products/${productId}`, accessToken, { method: "DELETE" });
}

export async function restoreProduct(accessToken: string, productId: string): Promise<ProductSummary> {
  return apiFetch<ProductSummary>(`/catalog/products/${productId}/restore`, accessToken, { method: "POST" });
}

export async function uploadProductImage(accessToken: string, productId: string, file: File): Promise<ProductSummary> {
  const body = new FormData(); body.append("file", file);
  return apiFetch<ProductSummary>(`/catalog/products/${productId}/image`, accessToken, { method: "POST", body });
}

export async function removeProductImage(accessToken: string, productId: string): Promise<ProductSummary> {
  return apiFetch<ProductSummary>(`/catalog/products/${productId}/image`, accessToken, { method: "DELETE" });
}

export async function getCatalogCategories(accessToken: string, includeInactive = false): Promise<CatalogCategory[]> {
  return apiFetch<CatalogCategory[]>(`/catalog/categories?includeInactive=${includeInactive}`, accessToken);
}

export async function createCatalogCategory(accessToken: string, payload: CatalogCategoryPayload): Promise<CatalogCategory> {
  return apiFetch<CatalogCategory>("/catalog/categories", accessToken, { method: "POST", body: JSON.stringify(normalizePayload(payload)) });
}

export async function updateCatalogCategory(accessToken: string, categoryId: string, payload: CatalogCategoryPayload): Promise<CatalogCategory> {
  return apiFetch<CatalogCategory>(`/catalog/categories/${categoryId}`, accessToken, { method: "PATCH", body: JSON.stringify(normalizePayload(payload)) });
}

export async function archiveCatalogCategory(accessToken: string, categoryId: string): Promise<void> {
  return apiFetch<void>(`/catalog/categories/${categoryId}`, accessToken, { method: "DELETE" });
}

export async function getPlans(accessToken: string): Promise<PlanSummary[]> { return apiFetch<PlanSummary[]>("/catalog/plans", accessToken); }
export async function createPlan(accessToken: string, payload: PlanPayload): Promise<PlanSummary> { return apiFetch<PlanSummary>("/catalog/plans", accessToken, { method: "POST", body: JSON.stringify(normalizePayload(payload)) }); }
export async function createMembershipPlan(accessToken: string, payload: MembershipPlanPayload): Promise<PlanSummary> { return apiFetch<PlanSummary>("/catalog/membership-plans", accessToken, { method: "POST", body: JSON.stringify(normalizePayload(payload)) }); }
export async function updateMembershipPlan(accessToken: string, planId: string, payload: UpdateMembershipPlanPayload): Promise<PlanSummary> { return apiFetch<PlanSummary>(`/catalog/plans/${planId}`, accessToken, { method: "PATCH", body: JSON.stringify(normalizePayload(payload)) }); }
