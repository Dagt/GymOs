package com.gymos.notifications.api;

import java.util.UUID;

public interface EmailDeliveryPort {
	String providerKey();

	boolean available();

	DeliveryResult deliver(EmailMessage message);

	record EmailMessage(
			UUID notificationId,
			UUID organizationId,
			String fromEmail,
			String fromName,
			String replyTo,
			String recipient,
			String subject,
			String bodyText,
			String bodyHtml,
			String idempotencyKey
	) {
	}

	record DeliveryResult(String providerMessageId) {
	}

	final class DeliveryException extends RuntimeException {
		private final boolean retryable;

		public DeliveryException(String message, boolean retryable, Throwable cause) {
			super(message, cause);
			this.retryable = retryable;
		}

		public boolean retryable() {
			return retryable;
		}
	}
}
