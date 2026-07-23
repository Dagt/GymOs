package com.gymos.selfservice.application.result;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SelfServicePendingPaymentView(
		UUID id,
		UUID saleId,
		String saleNumber,
		String method,
		String status,
		long amountCents,
		String currency,
		OffsetDateTime receivedAt
) {
}
