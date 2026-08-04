package com.gymos.payments.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.payments.api.RecurringChargeOperations.RecurringRenewalSale;
import com.gymos.payments.infrastructure.PaymentProviderRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository.OperationRecord;
import com.gymos.payments.infrastructure.RecurringBillingRepository;
import com.gymos.payments.infrastructure.RecurringBillingRepository.ChargeRecord;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class RecurringChargeServiceTests {
	private final RecurringBillingRepository recurringRepository = mock(RecurringBillingRepository.class);
	private final PaymentProviderRepository providerRepository = mock(PaymentProviderRepository.class);
	private final StripeRecurringBillingService stripeService = mock(StripeRecurringBillingService.class);
	private final RecurringChargeService service = new RecurringChargeService(
			recurringRepository, providerRepository, stripeService, mock(ApplicationEventPublisher.class)
	);

	@Test
	void preparedRenewalIsAttachedAndSentToStripeOnce() {
		ChargeRecord processing = charge(null, null, null, null);
		UUID saleId = UUID.randomUUID();
		UUID paymentId = UUID.randomUUID();
		ChargeRecord prepared = charge(processing, saleId, paymentId, 75_000L, "MXN");
		OperationRecord operation = new OperationRecord(
				UUID.randomUUID(), processing.organizationId(), processing.branchId(), "STRIPE",
				"RECURRING_PAYMENT_INTENT", saleId, paymentId, processing.clientId(), processing.mandateId(),
				processing.id(), "pi_test", "stripe-recurring:test", "PROCESSING", 75_000, "MXN",
				null, "{}", null
		);
		when(recurringRepository.charge(processing.id())).thenReturn(
				Optional.of(processing), Optional.of(prepared)
		);
		when(recurringRepository.attachSale(processing.id(), saleId, paymentId, 75_000, "MXN"))
				.thenReturn(true);
		when(stripeService.startRecurringPayment(prepared)).thenReturn(operation);

		service.continueCharge(new RecurringRenewalSale(
				processing.id(), saleId, paymentId, 75_000, "MXN"
		));

		verify(recurringRepository).attachSale(processing.id(), saleId, paymentId, 75_000, "MXN");
		verify(stripeService).startRecurringPayment(prepared);
		verify(recurringRepository).awaitProvider(processing.id(), operation.id());
	}

	private ChargeRecord charge(UUID saleId, UUID paymentId, Long amountCents, String currency) {
		return new ChargeRecord(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "cus_test", UUID.randomUUID(),
				"pm_test", LocalDate.now(), "PROCESSING", saleId, paymentId, null, amountCents, currency,
				1, OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(5), null
		);
	}

	private ChargeRecord charge(
			ChargeRecord source,
			UUID saleId,
			UUID paymentId,
			Long amountCents,
			String currency
	) {
		return new ChargeRecord(
				source.id(), source.organizationId(), source.branchId(), source.mandateId(), source.membershipId(),
				source.planId(), source.clientId(), source.paymentCustomerId(), source.externalCustomerId(),
				source.storedPaymentMethodId(), source.externalPaymentMethodId(), source.dueOn(), source.status(),
				saleId, paymentId, source.providerOperationId(), amountCents, currency, source.attemptNumber(),
				source.nextAttemptAt(), source.lockedUntil(), source.lastError()
		);
	}
}
