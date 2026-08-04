package com.gymos.payments.application;

import com.gymos.common.observability.TenantTelemetry;
import com.gymos.payments.infrastructure.PaymentProviderRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentReconciliationWorker {
	private static final Logger LOGGER = LoggerFactory.getLogger(PaymentReconciliationWorker.class);
	private final PaymentProviderRepository repository;
	private final PaymentWebhookService webhookService;
	private final TenantTelemetry telemetry;

	public PaymentReconciliationWorker(
			PaymentProviderRepository repository,
			PaymentWebhookService webhookService,
			TenantTelemetry telemetry
	) {
		this.repository = repository;
		this.webhookService = webhookService;
		this.telemetry = telemetry;
	}

	@Scheduled(fixedDelayString = "${gymos.payment-providers.reconciliation-delay-ms:60000}")
	public void reconcilePendingOperations() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		for (var operation : repository.reconcilableOperations(now.minusSeconds(30), now.minusDays(7), 50)) {
			try {
				webhookService.reconcile(operation);
				telemetry.increment(
						"gymos.payments.reconciliation", operation.organizationId(),
						"provider", operation.provider(), "outcome", "processed"
				);
			} catch (RuntimeException exception) {
				telemetry.increment(
						"gymos.payments.reconciliation", operation.organizationId(),
						"provider", operation.provider(), "outcome", "failed"
				);
				LOGGER.warn("Payment reconciliation failed. provider={} operationId={}",
						operation.provider(), operation.id(), exception);
			}
		}
	}
}
