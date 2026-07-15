package com.gymos.sales.api.events;

import java.io.Serializable;
import java.util.UUID;

public record PaymentCompletedLineItemV1(
		UUID productId,
		String itemType,
		int quantity
) implements Serializable {
}
