package com.gymos.payments.api.events;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecurringChargeFailedV1(
		String eventKey,
		UUID organizationId,
		UUID branchId,
		UUID clientId,
		UUID membershipId,
		UUID mandateId,
		UUID chargeId,
		UUID planId,
		UUID saleId,
		LocalDate dueOn,
		int attemptNumber,
		boolean exhausted,
		String errorCode,
		OffsetDateTime occurredAt
) implements Serializable {}
