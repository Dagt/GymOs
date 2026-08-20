package com.gymos.notifications.application;

import com.gymos.common.observability.TenantTelemetry;
import com.gymos.notifications.infrastructure.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationOperationsMonitor {
	private static final Logger log = LoggerFactory.getLogger(NotificationOperationsMonitor.class);
	private final NotificationRepository repository;
	private final TenantTelemetry telemetry;

	public NotificationOperationsMonitor(NotificationRepository repository, TenantTelemetry telemetry) {
		this.repository = repository;
		this.telemetry = telemetry;
	}

	@Scheduled(fixedDelayString = "${gymos.notifications.operations-monitor-delay-ms:60000}")
	public void publish() {
		for (var operations : repository.notificationOperations()) {
			telemetry.gauge("gymos.notification.queue.pending", operations.organizationId(), operations.pending());
			telemetry.gauge("gymos.notification.queue.dead_letters", operations.organizationId(), operations.deadLetters());
			telemetry.gauge("gymos.notification.queue.expired_leases", operations.organizationId(), operations.expiredLeases());
			if (operations.deadLetters() > 0 || operations.expiredLeases() > 0) {
				log.warn(
						"Notification delivery requires attention. organizationId={} deadLetters={} expiredLeases={}",
						operations.organizationId(), operations.deadLetters(), operations.expiredLeases()
				);
			}
		}
	}
}
