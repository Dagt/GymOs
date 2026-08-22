package com.gymos.notifications.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymos.notifications.api.EmailDeliveryPort.EmailMessage;
import com.gymos.notifications.application.NotificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpEmailDeliveryAdapterTests {
	@Test
	void smtpAdapterBuildsMessageWithStableDeliveryHeadersAndBranding() throws Exception {
		CapturingMailSender sender = new CapturingMailSender();
		var smtp = new NotificationProperties.Smtp(
				true, "SMTP de prueba", "smtp.test", 587, "user", "secret", List.of("envios@gymos.test"),
				true, true, true, false, true, 10000, 10000, 10000
		);
		var adapter = new SmtpEmailDeliveryAdapter(smtp, sender, "https://gymfort.mx");
		UUID notificationId = UUID.randomUUID();

		var result = adapter.deliver(new EmailMessage(
				notificationId, UUID.randomUUID(), "envios@gymos.test", "Mi gimnasio",
				"recepcion@gymos.test", "socio@example.com", "Tu membresia", "Mensaje", null,
				"notification:key", "Appex Hub Deportivo", "/api/tenant-logo/abc?v=1", "#000000", "#e8ff00", "#ffffff"
		));

		assertThat(adapter.available()).isTrue();
		assertThat(result.providerMessageId()).contains(notificationId.toString());
		assertThat(sender.message.getAllRecipients()[0].toString()).isEqualTo("socio@example.com");
		assertThat(sender.message.getSubject()).isEqualTo("Tu membresia");
		assertThat(sender.message.getHeader("X-GymOS-Notification-Id", null)).isEqualTo(notificationId.toString());
		assertThat(sender.message.getHeader("X-GymOS-Idempotency-Key", null)).isEqualTo("notification:key");
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		sender.message.writeTo(output);
		String rawMessage = output.toString(StandardCharsets.UTF_8);
		assertThat(rawMessage).contains("Appex Hub Deportivo");
		assertThat(rawMessage).contains("Tu membresia");
		assertThat(rawMessage).contains("https://gymfort.mx/api/tenant-logo/abc?v=1");
	}

	@Test
	void smtpAdapterOmitsRelativeLogoWhenFrontendIsOnlyLocal() throws Exception {
		CapturingMailSender sender = new CapturingMailSender();
		var smtp = new NotificationProperties.Smtp(
				true, "SMTP de prueba", "smtp.test", 587, "user", "secret", List.of("envios@gymos.test"),
				true, true, true, false, true, 10000, 10000, 10000
		);
		var adapter = new SmtpEmailDeliveryAdapter(smtp, sender, "http://localhost:3000");

		adapter.deliver(new EmailMessage(
				UUID.randomUUID(), UUID.randomUUID(), "envios@gymos.test", "Mi gimnasio",
				null, "socio@example.com", "Asunto", "Mensaje", null, "notification:key",
				"Mi gimnasio", "/api/tenant-logo/abc?v=1", "#093d3a", "#18c8b2", "#f2f8f7"
		));

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		sender.message.writeTo(output);
		String rawMessage = output.toString(StandardCharsets.UTF_8);
		assertThat(rawMessage).doesNotContain("localhost:3000/api/tenant-logo/abc");
	}

	@Test
	void smtpAdapterRejectsSenderOutsideAuthorizedAliasesWithoutRetry() {
		CapturingMailSender sender = new CapturingMailSender();
		var smtp = new NotificationProperties.Smtp(
				true, "ZeptoMail", "smtp.zeptomail.com", 587, "emailapikey", "secret",
				List.of("envios@gymos.test"), true, true, true, false, true, 10000, 10000, 10000
		);
		var adapter = new SmtpEmailDeliveryAdapter(smtp, sender);

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.deliver(new EmailMessage(
				UUID.randomUUID(), UUID.randomUUID(), "otro@gymos.test", "Mi gimnasio",
				null, "socio@example.com", "Asunto", "Mensaje", null, "notification:key",
				"Mi gimnasio", null, "#093d3a", "#18c8b2", "#f2f8f7"
		))).isInstanceOfSatisfying(
				com.gymos.notifications.api.EmailDeliveryPort.DeliveryException.class,
				exception -> assertThat(exception.retryable()).isFalse()
		);
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
