package com.gymos.payments.application;

import com.gymos.common.observability.TenantTelemetry;
import com.gymos.payments.api.RecurringChargeOperations;
import com.gymos.payments.api.events.RecurringChargeFailedV1;
import com.gymos.payments.infrastructure.PaymentProviderRepository;
import com.gymos.payments.infrastructure.RecurringBillingRepository;
import com.gymos.payments.infrastructure.RecurringBillingRepository.ChargeRecord;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringChargeService implements RecurringChargeOperations {
	private static final Logger LOGGER = LoggerFactory.getLogger(RecurringChargeService.class);
	private final RecurringBillingRepository recurringRepository;
	private final PaymentProviderRepository providerRepository;
	private final StripeRecurringBillingService stripeService;
	private final ApplicationEventPublisher eventPublisher;
	private final TenantTelemetry telemetry;

	@Autowired
	public RecurringChargeService(
			RecurringBillingRepository recurringRepository,
			PaymentProviderRepository providerRepository,
			StripeRecurringBillingService stripeService,
			ApplicationEventPublisher eventPublisher,
			TenantTelemetry telemetry
	) {
		this.recurringRepository = recurringRepository;
		this.providerRepository = providerRepository;
		this.stripeService = stripeService;
		this.eventPublisher = eventPublisher;
		this.telemetry = telemetry;
	}

	RecurringChargeService(
			RecurringBillingRepository recurringRepository,
			PaymentProviderRepository providerRepository,
			StripeRecurringBillingService stripeService,
			ApplicationEventPublisher eventPublisher
	) {
		this(recurringRepository, providerRepository, stripeService, eventPublisher, TenantTelemetry.noOp());
	}

	@Override
	@Transactional
	public void continueCharge(RecurringRenewalSale sale) {
		if (sale == null || sale.chargeId() == null || sale.saleId() == null) return;
		ChargeRecord charge = recurringRepository.charge(sale.chargeId()).orElse(null);
		if (charge == null || !"PROCESSING".equals(charge.status())) {
			return;
		}
		if (charge.saleId() == null) {
			if (!recurringRepository.attachSale(
					charge.id(), sale.saleId(), sale.paymentId(), sale.amountCents(), sale.currency()
			)) return;
			charge = recurringRepository.charge(charge.id()).orElseThrow();
		}
		try {
			if (charge.amountCents() != null && charge.amountCents() == 0) {
				stripeService.completeZeroAmountCharge(charge.id());
				telemetry.increment("gymos.payments.recurring.charges", charge.organizationId(), "outcome", "zero_amount_succeeded");
				return;
			}
			var operation = stripeService.startRecurringPayment(charge);
			recurringRepository.awaitProvider(charge.id(), operation.id());
			telemetry.increment("gymos.payments.recurring.charges", charge.organizationId(), "outcome", "awaiting_provider");
		} catch (RuntimeException exception) {
			handleProcessingFailure(charge, exception);
		}
	}

	@Override
	@Transactional
	public void preparationFailed(UUID chargeId, String error) {
		ChargeRecord charge = recurringRepository.charge(chargeId).orElse(null);
		if (charge == null || !"PROCESSING".equals(charge.status())) {
			return;
		}
		handleProcessingFailure(charge, new IllegalStateException(error));
	}

	@Override
	@Transactional
	public void membershipRenewed(UUID organizationId, UUID membershipId, LocalDate nextChargeOn) {
		recurringRepository.advanceAfterRenewal(organizationId, membershipId, nextChargeOn);
	}

	@Override
	public void membershipPlanChanged(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			UUID membershipId,
			String correlationId
	) {
		stripeService.revokeForPlanChange(
				organizationId, branchId, clientId, membershipId, correlationId
		);
	}

	private void handleProcessingFailure(ChargeRecord charge, RuntimeException exception) {
		var operation = providerRepository.latestOperationForRecurringCharge(
				charge.organizationId(), charge.id()
		).orElse(null);
		if (operation != null && operation.externalOperationId() != null) {
			recurringRepository.awaitProvider(charge.id(), operation.id());
			LOGGER.info(
					"Recurring payment awaits authoritative reconciliation. chargeId={} operationId={}",
					charge.id(), operation.id()
			);
			telemetry.increment("gymos.payments.recurring.charges", charge.organizationId(), "outcome", "awaiting_reconciliation");
			return;
		}
		String error = safeError(exception);
		var result = recurringRepository.failCharge(charge, error, 3, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15));
		if (result.isEmpty()) return;
		ChargeRecord failed = result.get();
		eventPublisher.publishEvent(new RecurringChargeFailedV1(
				"recurring-charge-failed:" + failed.id() + ":" + failed.attemptNumber(),
				failed.organizationId(), failed.branchId(), failed.clientId(), failed.membershipId(),
				failed.mandateId(), failed.id(), failed.planId(), failed.saleId(), failed.dueOn(),
				failed.attemptNumber(), "FAILED".equals(failed.status()), error,
				OffsetDateTime.now(ZoneOffset.UTC)
		));
		telemetry.increment(
				"gymos.payments.recurring.charges", failed.organizationId(),
				"outcome", "FAILED".equals(failed.status()) ? "exhausted" : "retry_scheduled"
		);
		LOGGER.warn("Recurring charge processing failed. chargeId={}", charge.id(), exception);
	}

	private String safeError(RuntimeException exception) {
		String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
		return message.substring(0, Math.min(120, message.length()));
	}
}
