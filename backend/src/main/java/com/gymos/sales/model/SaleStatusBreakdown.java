package com.gymos.sales.model;

public record SaleStatusBreakdown(String status, long saleCount, long totalCents, long paidCents, long balanceCents) {
}
