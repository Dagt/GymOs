package com.gymos.sales.api.events;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SaleItemsRefundedV1(
		UUID organizationId,
		UUID branchId,
		UUID saleId,
		UUID refundId,
		UUID actorUserId,
		List<SaleItemsRefundedLineV1> items,
		OffsetDateTime occurredAt,
		UUID correlationId
) implements Serializable {
	public SaleItemsRefundedV1 {
		items = items == null ? List.of() : List.copyOf(items);
	}
}
