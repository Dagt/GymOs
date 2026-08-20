package com.gymos.payments.application;

import com.gymos.common.observability.TenantTelemetry;
import com.gymos.payments.infrastructure.PaymentProviderRepository;
import com.gymos.payments.infrastructure.RecurringBillingRepository;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentOperationsMonitor {
	private static final Logger LOGGER = LoggerFactory.getLogger(PaymentOperationsMonitor.class);
	private final PaymentProviderRepository providerRepository;
	private final RecurringBillingRepository recurringRepository;
	private final TenantTelemetry telemetry;

	public PaymentOperationsMonitor(
			PaymentProviderRepository providerRepository,
			RecurringBillingRepository recurringRepository,
			TenantTelemetry telemetry
	) {
		this.providerRepository = providerRepository;
		this.recurringRepository = recurringRepository;
		this.telemetry = telemetry;
	}

	@Scheduled(fixedDelayString = "${gymos.payment-providers.operations-monitor-delay-ms:60000}")
	public void publish() {
		OffsetDateTime now = OffsetDateTime.now();
		for (var organizationId : providerRepository.activeOrganizationIds()) {
			var provider = providerRepository.operations(organizationId, now.minusMinutes(15));
			var recurring = recurringRepository.operations(organizationId, now);
			telemetry.gauge("gymos.payment.operations", organizationId, provider.failedWebhooks(), "state", "failed_webhooks");
			telemetry.gauge("gymos.payment.operations", organizationId, provider.staleOperations(), "state", "stale");
			telemetry.gauge("gymos.recurring.charges", organizationId, recurring.failedCharges(), "state", "failed");
			telemetry.gauge("gymos.recurring.charges", organizationId, recurring.retryCharges(), "state", "retry_scheduled");
			telemetry.gauge("gymos.recurring.mandates", organizationId, recurring.pastDueMandates(), "state", "past_due");
			telemetry.gauge("gymos.recurring.expired_leases", organizationId, recurring.expiredChargeLeases());
			if (provider.failedWebhooks() > 0 || recurring.expiredChargeLeases() > 0) {
				LOGGER.warn(
						"Payment operational alert organizationId={} failedWebhooks={} expiredChargeLeases={} staleOperations={}",
						organizationId, provider.failedWebhooks(), recurring.expiredChargeLeases(), provider.staleOperations()
				);
			}
		}
	}
}
