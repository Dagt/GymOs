package com.gymos.payments.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface StripeRecurringOperations {
	RecurringTerms terms(RecurringMembershipContext context);

	MandateSetupResult beginSetup(BeginMandateSetupCommand command);

	Optional<RecurringMandateView> mandate(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			UUID membershipId
	);

	RecurringMandateView revoke(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			UUID membershipId,
			String reason,
			String correlationId
	);

	record RecurringMembershipContext(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			UUID membershipId,
			UUID planId,
			String planName,
			long currentPriceCents,
			String currency,
			LocalDate nextChargeOn
	) {}

	record RecurringTerms(
			String termsVersion,
			String consentText,
			String amountRule,
			long currentPriceCents,
			String currency,
			LocalDate nextChargeOn
	) {}

	record BeginMandateSetupCommand(
			RecurringMembershipContext context,
			String termsVersion,
			boolean accepted,
			String idempotencyKey,
			String consentOrigin,
			String correlationId,
			boolean replaceExisting
	) {
		public BeginMandateSetupCommand(
				RecurringMembershipContext context, String termsVersion, boolean accepted,
				String idempotencyKey, String consentOrigin, String correlationId
		) {
			this(context, termsVersion, accepted, idempotencyKey, consentOrigin, correlationId, false);
		}
	}

	record MandateSetupResult(
			UUID mandateId,
			String status,
			UUID providerOperationId,
			String providerSessionId,
			String checkoutUrl,
			String termsVersion,
			LocalDate nextChargeOn
	) {}

	record RecurringMandateView(
			UUID id,
			UUID membershipId,
			UUID planId,
			String status,
			String provider,
			String brand,
			String lastFour,
			Integer expiresMonth,
			Integer expiresYear,
			String termsVersion,
			String amountRule,
			OffsetDateTime consentedAt,
			LocalDate nextChargeOn,
			int consecutiveFailures,
			String lastError,
			OffsetDateTime revokedAt,
			boolean canReplacePaymentMethod,
			String recoveryMessage
	) {}
}
