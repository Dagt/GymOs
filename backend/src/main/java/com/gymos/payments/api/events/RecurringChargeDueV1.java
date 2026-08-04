package com.gymos.payments.api.events;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecurringChargeDueV1(
		String eventKey,
		UUID organizationId,
		UUID branchId,
		UUID clientId,
		UUID membershipId,
		UUID mandateId,
		UUID chargeId,
		UUID planId,
		LocalDate dueOn,
		int attemptNumber,
		OffsetDateTime occurredAt
) implements Serializable {}
