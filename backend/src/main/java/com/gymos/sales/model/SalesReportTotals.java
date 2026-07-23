package com.gymos.sales.model;

public record SalesReportTotals(String currency, long saleCount, long subtotalCents, long discountCents, long totalCents, long paidCents, long balanceCents, long paidSales, long partiallyPaidSales, long pendingSales) {
}
