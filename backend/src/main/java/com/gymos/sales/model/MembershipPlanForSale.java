package com.gymos.sales.model;

import java.util.UUID;

public record MembershipPlanForSale(UUID planId, UUID productId, int durationDays, int graceDays) {
}
