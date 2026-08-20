package com.gymos.notifications.application;

import com.gymos.common.observability.TenantTelemetry;
import com.gymos.notifications.api.EmailDeliveryPort;
import com.gymos.notifications.api.EmailDeliveryPort.DeliveryException;
import com.gymos.notifications.api.EmailDeliveryPort.EmailMessage;
import com.gymos.notifications.infrastructure.NotificationRepository;
import com.gymos.notifications.infrastructure.NotificationRepository.EmailDeliveryRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class NotificationDeliveryService {
	private final NotificationRepository repository;
	private final NotificationProperties properties;
	private final TenantTelemetry telemetry;
	private final Map<String, EmailDeliveryPort> providers;
	private final NotificationRecipientHasher recipientHasher;

	public NotificationDeliveryService(
			NotificationRepository repository,
			NotificationProperties properties,
			TenantTelemetry telemetry,
			List<EmailDeliveryPort> providers,
			NotificationRecipientHasher recipientHasher
	) {
		this.repository = repository;
		this.properties = properties;
		this.telemetry = telemetry;
		this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
				provider -> provider.providerKey().toUpperCase(Locale.ROOT), Function.identity()
		));
		this.recipientHasher = recipientHasher;
	}

	public int runDueDeliveries() {
		if (providers.values().stream().noneMatch(EmailDeliveryPort::available)) return 0;
		OffsetDateTime now = OffsetDateTime.now();
		int processed = 0;
		for (var notificationId : repository.dueEmailIds(now, properties.batchSize())) {
			if (!repository.claimEmail(notificationId, now, now.plusSeconds(properties.leaseSeconds()))) continue;
			EmailDeliveryRecord delivery = repository.emailDelivery(notificationId).orElse(null);
			if (delivery == null) {
				repository.markDeliveryFailure(
						notificationId, true, now.plusMinutes(1), "No fue posible cargar la entrega.", now
				);
				continue;
			}
			deliver(delivery);
			processed++;
		}
		return processed;
	}

	private void deliver(EmailDeliveryRecord delivery) {
		var sample = telemetry.start();
		OffsetDateTime now = OffsetDateTime.now();
		String outcome = "SENT";
		try {
			if (!repository.emailAllowed(delivery.organizationId(), recipientHasher.hash(delivery.recipient()), delivery.category())) {
				repository.markSuppressed(delivery.id(), now);
				outcome = "SUPPRESSED";
				return;
			}
			EmailDeliveryPort provider = providers.get(delivery.providerKey().toUpperCase(Locale.ROOT));
			if (provider == null || !provider.available()) {
				throw new DeliveryException("El proveedor de correo no esta disponible.", true, null);
			}
			var result = provider.deliver(new EmailMessage(
					delivery.id(), delivery.organizationId(), delivery.fromEmail(), delivery.fromName(),
					delivery.replyTo(), delivery.recipient(), delivery.subject(), delivery.body(),
					delivery.bodyHtml(), delivery.idempotencyKey()
			));
			repository.markSent(delivery.id(), provider.providerKey(), result.providerMessageId(), now);
		} catch (DeliveryException exception) {
			outcome = failure(delivery, exception.retryable(), exception.getMessage(), now);
		} catch (RuntimeException exception) {
			outcome = failure(delivery, true, "Fallo inesperado del proveedor de correo.", now);
		} finally {
			telemetry.increment(
					"gymos.notification.delivery", delivery.organizationId(),
					"channel", "EMAIL", "provider", delivery.providerKey(), "outcome", outcome
			);
			telemetry.stop(
					sample, "gymos.notification.delivery.duration", delivery.organizationId(),
					"channel", "EMAIL", "provider", delivery.providerKey(), "outcome", outcome
			);
		}
	}

	private String failure(EmailDeliveryRecord delivery, boolean retryable, String error, OffsetDateTime now) {
		boolean retry = retryable && delivery.attemptCount() < properties.maxAttempts();
		long delayMinutes = Math.min(60, 1L << Math.min(Math.max(delivery.attemptCount() - 1, 0), 6));
		repository.markDeliveryFailure(
				delivery.id(), retry, retry ? now.plusMinutes(delayMinutes) : now,
				safeError(error), now
		);
		return retry ? "RETRY_SCHEDULED" : "DEAD_LETTER";
	}

	private String safeError(String value) {
		String normalized = value == null || value.isBlank() ? "Fallo del proveedor de correo." : value.trim();
		return normalized.substring(0, Math.min(500, normalized.length()));
	}

}
