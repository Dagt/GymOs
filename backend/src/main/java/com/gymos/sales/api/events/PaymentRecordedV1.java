package com.gymos.sales.api.events;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentRecordedV1(
		String eventKey,
		UUID organizationId,
		UUID branchId,
		UUID saleId,
		UUID paymentId,
		String method,
		String status,
		long amountCents,
		String currency,
		OffsetDateTime occurredAt
) implements Serializable {
}
