package com.gymos.sales.application.command;

import java.util.List;
import java.util.UUID;

public record CreateSaleCommand(
		UUID clientId,
		List<SaleItemCommand> items,
		long discountCents,
		String notes,
		ManualPaymentCommand payment,
		String idempotencyKey
) {
}
