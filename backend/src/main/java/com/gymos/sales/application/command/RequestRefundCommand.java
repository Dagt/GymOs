package com.gymos.sales.application.command;

import java.util.UUID;

public record RequestRefundCommand(
		UUID paymentId,
		long amountCents,
		String reason,
		String idempotencyKey
) {
}
