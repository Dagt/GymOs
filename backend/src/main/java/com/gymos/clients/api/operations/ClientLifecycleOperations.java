package com.gymos.clients.api.operations;

import java.time.LocalDate;
import java.util.UUID;

public interface ClientLifecycleOperations {
	RegistrationFee registrationFee(UUID organizationId, UUID clientId, LocalDate effectiveOn);

	record RegistrationFee(
			boolean due,
			long amountCents,
			String label,
			String currency,
			int rechargeMonths
	) {
		public static RegistrationFee none(String currency, int rechargeMonths) {
			return new RegistrationFee(false, 0, null, currency, rechargeMonths);
		}
	}
}
