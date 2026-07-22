package com.gymos.sales.model;

public record PaymentMethodBreakdown(String method, long paymentCount, long totalCents, long completedCents, long pendingCents, long rejectedCents) {
}
