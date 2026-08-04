package com.gymos.notifications.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymos.notifications.api.EmailDeliveryPort.EmailMessage;
import com.gymos.notifications.application.NotificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpEmailDeliveryAdapterTests {
	@Test
	void smtpAdapterBuildsMessageWithStableDeliveryHeaders() throws Exception {
		CapturingMailSender sender = new CapturingMailSender();
		var smtp = new NotificationProperties.Smtp(
				true, "smtp.test", 587, "user", "secret", true, true, false, 10000, 10000, 10000
		);
		var adapter = new SmtpEmailDeliveryAdapter(smtp, sender);
		UUID notificationId = UUID.randomUUID();

		var result = adapter.deliver(new EmailMessage(
				notificationId, UUID.randomUUID(), "envios@gymos.test", "Mi gimnasio",
				"recepcion@gymos.test", "socio@example.com", "Tu membresia", "Mensaje", null,
				"notification:key"
		));

		assertThat(adapter.available()).isTrue();
		assertThat(result.providerMessageId()).contains(notificationId.toString());
		assertThat(sender.message.getAllRecipients()[0].toString()).isEqualTo("socio@example.com");
		assertThat(sender.message.getSubject()).isEqualTo("Tu membresia");
		assertThat(sender.message.getHeader("X-GymOS-Notification-Id", null)).isEqualTo(notificationId.toString());
		assertThat(sender.message.getHeader("X-GymOS-Idempotency-Key", null)).isEqualTo("notification:key");
	}

	private static final class CapturingMailSender extends JavaMailSenderImpl {
		private MimeMessage message;

		@Override
		public void send(MimeMessage mimeMessage) {
			try {
				mimeMessage.saveChanges();
			} catch (MessagingException exception) {
				throw new IllegalStateException(exception);
			}
			this.message = mimeMessage;
		}
	}
}
