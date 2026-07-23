package com.gymos.sales.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SaleItemDetail(UUID id, UUID productId, String itemType, String name, int quantity, long unitPriceCents, long discountCents, long totalCents, OffsetDateTime createdAt) {
}
