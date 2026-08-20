package com.gymos.payments.api;

import java.time.LocalDate;
import java.util.UUID;

public interface RecurringChargeOperations {
	void continueCharge(RecurringRenewalSale sale);

	void preparationFailed(UUID chargeId, String error);

	void membershipRenewed(UUID organizationId, UUID membershipId, LocalDate nextChargeOn);

	void membershipPlanChanged(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			UUID membershipId,
			String correlationId
	);

	record RecurringRenewalSale(
			UUID chargeId,
			UUID saleId,
			UUID paymentId,
			long amountCents,
			String currency
	) {}
}
