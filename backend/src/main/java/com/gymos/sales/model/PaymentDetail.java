package com.gymos.sales.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentDetail(UUID id, String method, String status, long amountCents, String currency, String reference, String receiptObjectKey, OffsetDateTime receivedAt, OffsetDateTime validatedAt, String notes) {
}
