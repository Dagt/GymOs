package com.gymos.sales.model;

import java.util.UUID;

public record RefundItemSnapshot(
		UUID saleItemId,
		int quantity,
		boolean restock
) {
}
