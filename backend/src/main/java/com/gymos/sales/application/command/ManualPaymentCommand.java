package com.gymos.sales.application.command;

public record ManualPaymentCommand(
		String method,
		long amountCents,
		String reference,
		String receiptObjectKey,
		boolean validateNow,
		String notes,
		String idempotencyKey
) {
}
