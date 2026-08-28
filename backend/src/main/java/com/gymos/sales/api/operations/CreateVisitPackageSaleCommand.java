package com.gymos.sales.api.operations;

import java.util.UUID;

public record CreateVisitPackageSaleCommand(
		UUID organizationId,
		UUID branchId,
		UUID actorUserId,
		UUID clientId,
		UUID visitPackagePlanId,
		UUID productId,
		String packageName,
		long packagePriceCents,
		long registrationFeeCents,
		String registrationFeeLabel,
		long totalCents,
		String currency,
		String method,
		String reference,
		boolean validateNow,
		String notes,
		String idempotencyKey,
		String correlationId
) {}
