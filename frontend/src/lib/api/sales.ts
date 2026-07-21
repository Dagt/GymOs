import { apiFetch, normalizePayload, withIdempotency } from "./client";
import type {
  CreateSalePayload,
  ManualPaymentPayload,
  SaleDetailResponse,
  SaleRefundPayload,
  SalesReportResponse,
  SaleSummary,
} from "./sales-types";

export async function getRecentSales(accessToken: string): Promise<SaleSummary[]> {
  return apiFetch<SaleSummary[]>("/sales", accessToken);
}

export async function getSalesReport(
  accessToken: string,
  fromDate?: string,
  toDate?: string,
): Promise<SalesReportResponse> {
  const params = new URLSearchParams();
  if (fromDate) params.set("fromDate", fromDate);
  if (toDate) params.set("toDate", toDate);
  const suffix = params.toString() ? `?${params.toString()}` : "";
  return apiFetch<SalesReportResponse>(`/sales/reports/summary${suffix}`, accessToken);
}

export async function createSale(
  accessToken: string,
  payload: CreateSalePayload,
): Promise<SaleDetailResponse> {
  return apiFetch<SaleDetailResponse>("/sales", accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(withIdempotency(payload, "sale"))),
  });
}

export async function getSale(
  accessToken: string,
  saleId: string,
): Promise<SaleDetailResponse> {
  return apiFetch<SaleDetailResponse>(`/sales/${saleId}`, accessToken);
}

export async function addManualPayment(
  accessToken: string,
  saleId: string,
  payload: ManualPaymentPayload,
): Promise<SaleDetailResponse> {
  return apiFetch<SaleDetailResponse>(`/sales/${saleId}/payments`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(withIdempotency(payload, "manual-payment", saleId))),
  });
}

export async function requestSaleRefund(
  accessToken: string,
  saleId: string,
  payload: SaleRefundPayload,
): Promise<SaleDetailResponse> {
  return apiFetch<SaleDetailResponse>(`/sales/${saleId}/refunds`, accessToken, {
    method: "POST",
    body: JSON.stringify(normalizePayload(withIdempotency(payload, "sale-refund", saleId))),
  });
}

export async function validatePayment(
  accessToken: string,
  paymentId: string,
): Promise<SaleDetailResponse> {
  return apiFetch<SaleDetailResponse>(`/sales/payments/${paymentId}/validate`, accessToken, {
    method: "POST",
  });
}

export async function rejectPayment(
  accessToken: string,
  paymentId: string,
  reason: string,
): Promise<SaleDetailResponse> {
  return apiFetch<SaleDetailResponse>(`/sales/payments/${paymentId}/reject`, accessToken, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
}

export async function uploadPaymentReceipt(
  accessToken: string,
  paymentId: string,
  file: File,
): Promise<SaleDetailResponse> {
  const body = new FormData();
  body.set("file", file);
  return apiFetch<SaleDetailResponse>(`/sales/payments/${paymentId}/receipt`, accessToken, {
    method: "POST",
    body,
  });
}
