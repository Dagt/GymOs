package com.gymos.common.events;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentRejectedV1(
		String eventKey,
		UUID organizationId,
		UUID branchId,
		UUID saleId,
		UUID paymentId,
		String reason,
		OffsetDateTime occurredAt
) implements Serializable {
}
