package com.gymos.sales.api.events;

import java.io.Serializable;
import java.util.UUID;

public record SaleItemsRefundedLineV1(
		UUID saleItemId,
		UUID productId,
		String itemType,
		int quantity,
		boolean restock
) implements Serializable {
}
