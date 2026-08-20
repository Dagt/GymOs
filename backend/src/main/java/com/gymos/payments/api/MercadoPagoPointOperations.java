package com.gymos.payments.api;

import java.util.UUID;

public interface MercadoPagoPointOperations {
	PointOrderResult createOrder(CreatePointOrderCommand command);

	record CreatePointOrderCommand(
			UUID organizationId,
			UUID branchId,
			UUID saleId,
			UUID paymentId,
			UUID clientId,
			long amountCents,
			String currency,
			String idempotencyKey
	) {
	}

	record PointOrderResult(UUID operationId, String providerOrderId, String status) {
	}
}
