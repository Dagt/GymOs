package com.gymos.common.events;

import java.io.Serializable;
import java.util.UUID;

public record PaymentCompletedLineItemV1(
		UUID productId,
		String itemType,
		int quantity
) implements Serializable {
}
