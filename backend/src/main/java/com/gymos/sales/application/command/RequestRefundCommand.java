package com.gymos.sales.application.command;

import java.util.List;
import java.util.UUID;

public record RequestRefundCommand(
		UUID paymentId,
		long amountCents,
		String reason,
		String idempotencyKey,
		List<RefundItemCommand> items
) {
	public RequestRefundCommand {
		items = items == null ? List.of() : List.copyOf(items);
	}

	public RequestRefundCommand(UUID paymentId, long amountCents, String reason, String idempotencyKey) {
		this(paymentId, amountCents, reason, idempotencyKey, List.of());
	}

	public boolean itemized() {
		return !items.isEmpty();
	}
}
