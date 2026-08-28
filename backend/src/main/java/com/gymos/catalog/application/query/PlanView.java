package com.gymos.catalog.application.query;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PlanView(
		UUID id,
		UUID productId,
		String sku,
		String name,
		long priceCents,
		String currency,
		int durationDays,
		int graceDays,
		String benefitsJson,
		boolean multiBranchAccess,
		boolean selfServiceAcquisitionEnabled,
		String registrationFeePolicy,
		String status,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
	public PlanView(
			UUID id, UUID productId, String sku, String name, long priceCents, String currency,
			int durationDays, int graceDays, String benefitsJson, boolean multiBranchAccess,
			String status, OffsetDateTime createdAt, OffsetDateTime updatedAt
	) {
		this(id, productId, sku, name, priceCents, currency, durationDays, graceDays,
				benefitsJson, multiBranchAccess, true, "DEFAULT", status, createdAt, updatedAt);
	}
}
