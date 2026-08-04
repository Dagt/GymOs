package com.gymos.payments.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.api.PageResponse;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.observability.TenantTelemetry;
import java.time.OffsetDateTime;
import com.gymos.payments.infrastructure.PaymentProviderRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository.WebhookHistoryRecord;
import com.gymos.payments.infrastructure.RecurringBillingRepository;
import com.gymos.payments.infrastructure.RecurringBillingRepository.ChargeHistoryRecord;
import com.gymos.payments.infrastructure.RecurringBillingRepository.MandateHistoryRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentOperationsHistoryService {
	private final RecurringBillingRepository recurringBillingRepository;
	private final PaymentProviderRepository paymentProviderRepository;
	private final TenantTelemetry telemetry;

	public PaymentOperationsHistoryService(
			RecurringBillingRepository recurringBillingRepository,
			PaymentProviderRepository paymentProviderRepository,
			TenantTelemetry telemetry
	) {
		this.recurringBillingRepository = recurringBillingRepository;
		this.paymentProviderRepository = paymentProviderRepository;
		this.telemetry = telemetry;
	}

	@Transactional(readOnly = true)
	public PageResponse<MandateHistoryRecord> mandates(GymOsPrincipal principal, int page, int size) {
		requireTenant(principal);
		return recurringBillingRepository.mandateHistory(
				principal.organizationId(), boundedPage(page), boundedSize(size)
		);
	}

	@Transactional(readOnly = true)
	public PageResponse<ChargeHistoryRecord> charges(GymOsPrincipal principal, int page, int size) {
		requireTenant(principal);
		return recurringBillingRepository.chargeHistory(
				principal.organizationId(), boundedPage(page), boundedSize(size)
		);
	}

	@Transactional(readOnly = true)
	public PageResponse<WebhookHistoryRecord> webhooks(GymOsPrincipal principal, int page, int size) {
		requireTenant(principal);
		return paymentProviderRepository.webhookHistory(
				principal.organizationId(), boundedPage(page), boundedSize(size)
		);
	}

	@Transactional(readOnly = true)
	public PaymentOperationsView operations(GymOsPrincipal principal) {
		requireTenant(principal);
		var recurring = recurringBillingRepository.operations(principal.organizationId(), OffsetDateTime.now());
		var provider = paymentProviderRepository.operations(
				principal.organizationId(), OffsetDateTime.now().minusMinutes(15)
		);
		String alertLevel = provider.failedWebhooks() > 0 || recurring.expiredChargeLeases() > 0
				? "CRITICAL"
				: recurring.failedCharges() > 0 || recurring.pastDueMandates() > 0 || provider.staleOperations() > 0
						? "WARNING" : "HEALTHY";
		telemetry.gauge("gymos.payment.operations", principal.organizationId(), provider.failedWebhooks(), "state", "failed_webhooks");
		telemetry.gauge("gymos.payment.operations", principal.organizationId(), provider.staleOperations(), "state", "stale");
		telemetry.gauge("gymos.recurring.charges", principal.organizationId(), recurring.failedCharges(), "state", "failed");
		telemetry.gauge("gymos.recurring.mandates", principal.organizationId(), recurring.pastDueMandates(), "state", "past_due");
		return new PaymentOperationsView(
				recurring.pastDueMandates(), recurring.pausedMandates(), recurring.retryCharges(),
				recurring.failedCharges(), recurring.expiredChargeLeases(), provider.failedWebhooks(),
				provider.staleOperations(), alertLevel
		);
	}

	private int boundedPage(int page) {
		return Math.max(0, page);
	}

	private int boundedSize(int size) {
		return Math.max(1, Math.min(size, 100));
	}

	private void requireTenant(GymOsPrincipal principal) {
		if (principal == null || principal.organizationId() == null) {
			throw new BusinessException("ORGANIZATION_REQUIRED", "Selecciona un gimnasio para continuar.");
		}
	}

	public record PaymentOperationsView(
			long pastDueMandates, long pausedMandates, long retryCharges, long failedCharges,
			long expiredChargeLeases, long failedWebhooks, long staleOperations, String alertLevel
	) {}
}
