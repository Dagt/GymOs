package com.gymos.sales.model;

public record PaymentReportTotals(long paymentCount, long completedPayments, long pendingPayments, long rejectedPayments, long completedCents, long pendingCents, long rejectedCents) {
}
