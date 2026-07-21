import type { PaymentDetail, SaleDetailResponse } from "@/lib/api";

export function formatMoney(cents: number, currency: string) {
  return new Intl.NumberFormat("es-MX", {
    style: "currency",
    currency,
  }).format(cents / 100);
}

export function completedPayments(payments: PaymentDetail[]) {
  return payments.filter((payment) => payment.status === "COMPLETED");
}

/**
 * Presentation-only estimate. The backend remains authoritative and revalidates
 * the refundable amount when the request is submitted.
 */
export function estimatedRefundableCents(data: SaleDetailResponse) {
  const refunded = data.refunds
    .filter((refund) => refund.status === "PROCESSED")
    .reduce((total, refund) => total + refund.amountCents, 0);
  return Math.max(0, data.sale.paidCents - refunded);
}

export function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-MX", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}
