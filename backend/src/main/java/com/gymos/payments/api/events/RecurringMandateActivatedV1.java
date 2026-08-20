package com.gymos.payments.api.events;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecurringMandateActivatedV1(
		String eventKey,
		UUID organizationId,
		UUID branchId,
		UUID clientId,
		UUID membershipId,
		UUID mandateId,
		UUID planId,
		LocalDate nextChargeOn,
		OffsetDateTime occurredAt
) implements Serializable {}
