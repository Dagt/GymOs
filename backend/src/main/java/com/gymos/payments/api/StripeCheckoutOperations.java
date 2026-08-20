package com.gymos.payments.api;

import java.util.UUID;

public interface StripeCheckoutOperations {
	CheckoutSessionResult createCheckout(CreateCheckoutSessionCommand command);

	record CreateCheckoutSessionCommand(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			UUID saleId,
			UUID paymentId,
			long amountCents,
			String currency,
			boolean recurring,
			String description,
			String idempotencyKey
	) {
	}

	record CheckoutSessionResult(UUID operationId, String providerSessionId, String checkoutUrl, String status) {
	}
}
