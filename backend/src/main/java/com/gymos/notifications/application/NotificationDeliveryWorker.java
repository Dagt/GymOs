package com.gymos.notifications.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationDeliveryWorker {
	private final NotificationDeliveryService deliveryService;

	public NotificationDeliveryWorker(NotificationDeliveryService deliveryService) {
		this.deliveryService = deliveryService;
	}

	@Scheduled(fixedDelayString = "${gymos.notifications.dispatch-delay-ms:30000}")
	public void dispatch() {
		deliveryService.runDueDeliveries();
	}
}
