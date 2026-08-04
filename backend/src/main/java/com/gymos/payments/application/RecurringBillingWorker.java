package com.gymos.payments.application;

import com.gymos.common.time.BusinessTime;
import com.gymos.common.observability.TenantTelemetry;
import com.gymos.payments.api.events.RecurringChargeDueV1;
import com.gymos.payments.infrastructure.RecurringBillingRepository;
import com.gymos.payments.infrastructure.RecurringBillingRepository.ChargeRecord;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecurringBillingWorker {
	private final RecurringBillingRepository recurringRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final TenantTelemetry telemetry;

	@Autowired
	public RecurringBillingWorker(
			RecurringBillingRepository recurringRepository,
			ApplicationEventPublisher eventPublisher,
			TenantTelemetry telemetry
	) {
		this.recurringRepository = recurringRepository;
		this.eventPublisher = eventPublisher;
		this.telemetry = telemetry;
	}

	RecurringBillingWorker(
			RecurringBillingRepository recurringRepository,
			ApplicationEventPublisher eventPublisher
	) {
		this(recurringRepository, eventPublisher, TenantTelemetry.noOp());
	}

	@Scheduled(fixedDelayString = "${gymos.payment-providers.recurring-delay-ms:60000}")
	public void processDueCharges() {
		recurringRepository.materializeDueCharges(BusinessTime.today(), 100);
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		for (ChargeRecord candidate : recurringRepository.dueCharges(now, 25)) {
			if (!recurringRepository.claimCharge(candidate.id(), now, now.plusMinutes(5))) {
				telemetry.increment(
						"gymos.payments.recurring.worker", candidate.organizationId(),
						"outcome", "lease_contended"
				);
				continue;
			}
			ChargeRecord charge = recurringRepository.charge(candidate.id()).orElseThrow();
			publishDue(charge);
			telemetry.increment(
					"gymos.payments.recurring.worker", charge.organizationId(),
					"outcome", "published"
			);
		}
	}

	private void publishDue(ChargeRecord charge) {
		eventPublisher.publishEvent(new RecurringChargeDueV1(
				"recurring-charge-due:" + charge.id(), charge.organizationId(), charge.branchId(),
				charge.clientId(), charge.membershipId(), charge.mandateId(), charge.id(),
				charge.planId(), charge.dueOn(), charge.attemptNumber(), OffsetDateTime.now(ZoneOffset.UTC)
		));
	}

}
