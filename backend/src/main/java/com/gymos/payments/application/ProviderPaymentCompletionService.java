package com.gymos.payments.application;

import com.gymos.payments.api.events.ProviderPaymentCompletedV1;
import com.gymos.payments.api.events.PointPaymentClosed;
import com.gymos.payments.infrastructure.PaymentProviderRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository.OperationRecord;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProviderPaymentCompletionService {
	private final PaymentProviderRepository repository;
	private final StripeRecurringBillingService recurringBilling;
	private final ApplicationEventPublisher events;

	ProviderPaymentCompletionService(
			PaymentProviderRepository repository, StripeRecurringBillingService recurringBilling,
			ApplicationEventPublisher events
	) {
		this.repository = repository;
		this.recurringBilling = recurringBilling;
		this.events = events;
	}

	@Transactional
	public void closePoint(OperationRecord operation, String status, String payload) {
		var latest = repository.latestOperationForPayment(operation.organizationId(), "MERCADO_PAGO", "POINT_ORDER", operation.paymentId());
		if (latest.filter(candidate -> candidate.id().equals(operation.id())).isEmpty()) return;
		if (repository.markOperationStatus(operation.id(), status, payload, true)) {
			events.publishEvent(new PointPaymentClosed(operation.organizationId(), operation.branchId(),
					operation.saleId(), operation.paymentId(), status));
		}
	}

	@Transactional
	public void complete(OperationRecord operation, String payload, String receiptUrl) {
		if ("RECURRING_PAYMENT_INTENT".equals(operation.operationType())) {
			recurringBilling.completeRecurringCharge(operation, payload);
		}
		if (!repository.markOperationStatus(operation.id(), "COMPLETED", payload, true)) {
			return;
		}
		if (!receiptUrl.isBlank()) {
			repository.saveReceiptUrl(operation.id(), receiptUrl);
		}
		events.publishEvent(new ProviderPaymentCompletedV1(
				"provider-payment-completed:" + operation.provider() + ":" + operation.id(),
				operation.organizationId(), operation.branchId(), operation.provider(), operation.id(),
				operation.externalOperationId(), operation.saleId(), operation.paymentId(),
				operation.amountCents(), operation.currency(), OffsetDateTime.now(ZoneOffset.UTC)
		));
	}
}
