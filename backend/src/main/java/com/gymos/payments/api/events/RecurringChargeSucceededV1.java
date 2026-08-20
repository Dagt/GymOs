package com.gymos.payments.api.events;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecurringChargeSucceededV1(
		String eventKey,
		UUID organizationId,
		UUID branchId,
		UUID clientId,
		UUID membershipId,
		UUID mandateId,
		UUID chargeId,
		UUID planId,
		UUID saleId,
		long amountCents,
		String currency,
		LocalDate dueOn,
		OffsetDateTime occurredAt
) implements Serializable {}
