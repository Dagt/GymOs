package com.gymos.sales.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RefundDetail(UUID id, UUID saleId, UUID paymentId, String status, long amountCents, String currency, String reason, String idempotencyKey, UUID requestedBy, OffsetDateTime requestedAt, OffsetDateTime processedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
