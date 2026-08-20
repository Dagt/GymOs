package com.gymos.payments.api.events;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProviderPaymentCompletedV1(
		String eventKey,
		UUID organizationId,
		UUID branchId,
		String provider,
		UUID providerOperationId,
		String externalOperationId,
		UUID saleId,
		UUID paymentId,
		long amountCents,
		String currency,
		OffsetDateTime occurredAt
) {
}
