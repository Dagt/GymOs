package com.gymos.clients.api.operations;

import java.time.LocalDate;
import java.util.UUID;

public interface ClientLifecycleOperations {
	RegistrationFee registrationFee(UUID organizationId, UUID branchId, UUID clientId, LocalDate effectiveOn);

	record RegistrationFee(
			boolean due,
			long amountCents,
			String label,
			String currency,
			int inactivityMonths
	) {
		public static RegistrationFee none(String currency, int inactivityMonths) {
			return new RegistrationFee(false, 0, null, currency, inactivityMonths);
		}
	}
}
