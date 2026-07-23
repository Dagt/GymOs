package com.gymos.sales.api.events;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RefundProcessedV1(
		UUID organizationId,
		UUID branchId,
		UUID saleId,
		UUID refundId,
		UUID actorUserId,
		OffsetDateTime occurredAt,
		UUID correlationId
) implements Serializable {
}
