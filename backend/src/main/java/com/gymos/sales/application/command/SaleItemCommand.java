package com.gymos.sales.application.command;

import java.util.UUID;

public record SaleItemCommand(
		UUID productId,
		int quantity,
		long discountCents
) {
}
