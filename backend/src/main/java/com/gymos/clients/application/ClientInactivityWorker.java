package com.gymos.clients.application;

import com.gymos.audit.api.AuditService;
import com.gymos.clients.api.events.ClientAccessStatusChangedV1;
import com.gymos.clients.infrastructure.ClientLifecycleRepository;
import com.gymos.common.time.BusinessTime;
import java.time.OffsetDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ClientInactivityWorker {
	private final ClientLifecycleRepository repository;
	private final AuditService auditService;
	private final ApplicationEventPublisher eventPublisher;

	ClientInactivityWorker(
			ClientLifecycleRepository repository,
			AuditService auditService,
			ApplicationEventPublisher eventPublisher
	) {
		this.repository = repository;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
	}

	@Scheduled(fixedDelayString = "${gymos.clients.inactivity-delay-ms:3600000}")
	@Transactional
	public void inactivateExpiredClients() {
		OffsetDateTime now = OffsetDateTime.now();
		for (var candidate : repository.automaticInactivityCandidates(BusinessTime.today(), 500)) {
			if (!repository.markAutomaticallyInactive(candidate.organizationId(), candidate.clientId(), now)) {
				continue;
			}
			auditService.recordSystem(
					candidate.organizationId(), candidate.homeBranchId(), null,
					"CLIENT_AUTOMATICALLY_INACTIVATED", "CLIENT", candidate.clientId(),
					"MEMBERSHIP_INACTIVITY", "Última vigencia: " + candidate.lastActivityOn(),
					"client-inactivity:" + candidate.clientId() + ":" + candidate.lastActivityOn()
			);
			eventPublisher.publishEvent(new ClientAccessStatusChangedV1(
					"client-auto-inactivated:" + candidate.clientId() + ":" + candidate.lastActivityOn(),
					candidate.organizationId(), candidate.clientId(), "ACTIVE", "INACTIVE", now
			));
		}
	}
}
