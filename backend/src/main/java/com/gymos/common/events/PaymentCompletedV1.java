package com.gymos.common.events;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PaymentCompletedV1(
		String eventKey,
		UUID organizationId,
		UUID branchId,
		UUID saleId,
		UUID clientId,
		String saleNumber,
		String businessContext,
		UUID contextMembershipId,
		UUID contextTargetPlanId,
		List<PaymentCompletedLineItemV1> items,
		OffsetDateTime occurredAt
) implements Serializable {
}
