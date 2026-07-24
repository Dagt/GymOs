package com.gymos.sales.application.command;

import java.util.UUID;

public record RefundItemCommand(
		UUID saleItemId,
		int quantity,
		boolean restock
) {
}
