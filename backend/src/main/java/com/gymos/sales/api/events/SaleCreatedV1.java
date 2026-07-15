package com.gymos.sales.api.events;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SaleCreatedV1(
		String eventKey,
		UUID organizationId,
		UUID branchId,
		UUID saleId,
		String saleNumber,
		String businessContext,
		long totalCents,
		String currency,
		OffsetDateTime occurredAt
) implements Serializable {
}
