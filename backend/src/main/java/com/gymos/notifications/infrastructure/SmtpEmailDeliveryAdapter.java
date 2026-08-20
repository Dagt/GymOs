package com.gymos.notifications.infrastructure;

import com.gymos.notifications.api.EmailDeliveryPort;
import com.gymos.notifications.application.NotificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailDeliveryAdapter implements EmailDeliveryPort {
	private final NotificationProperties.Smtp properties;
	private final JavaMailSenderImpl mailSender;

	@Autowired
	public SmtpEmailDeliveryAdapter(NotificationProperties notificationProperties) {
		this.properties = notificationProperties.smtp();
		this.mailSender = sender(properties);
	}

	SmtpEmailDeliveryAdapter(NotificationProperties.Smtp properties, JavaMailSenderImpl mailSender) {
		this.properties = properties;
		this.mailSender = mailSender;
	}

	@Override
	public String providerKey() {
		return "SMTP";
	}

	@Override
	public boolean available() {
		return properties.available();
	}

	@Override
	public DeliveryResult deliver(EmailMessage message) {
		if (!available()) {
			throw new DeliveryException("El proveedor SMTP no esta configurado.", true, null);
		}
		try {
			MimeMessage mimeMessage = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
			if (message.fromName() == null) {
				helper.setFrom(message.fromEmail());
			} else {
				helper.setFrom(message.fromEmail(), message.fromName());
			}
			helper.setTo(message.recipient());
			helper.setSubject(message.subject());
			if (message.replyTo() != null) helper.setReplyTo(message.replyTo());
			if (message.bodyHtml() == null) {
				helper.setText(message.bodyText(), false);
			} else {
				helper.setText(message.bodyText(), message.bodyHtml());
			}
			String stableMessageId = "<notification-" + message.notificationId() + "@gymos.local>";
			mimeMessage.setHeader("Message-ID", stableMessageId);
			mimeMessage.setHeader("X-GymOS-Notification-Id", message.notificationId().toString());
			mimeMessage.setHeader(
					"X-GymOS-Idempotency-Key",
					message.idempotencyKey() == null ? message.notificationId().toString() : message.idempotencyKey()
			);
			mailSender.send(mimeMessage);
			return new DeliveryResult(stableMessageId);
		} catch (MessagingException | UnsupportedEncodingException | MailException exception) {
			throw new DeliveryException("No fue posible entregar el correo por SMTP.", true, exception);
		}
	}

	private JavaMailSenderImpl sender(NotificationProperties.Smtp smtp) {
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(smtp.host());
		sender.setPort(smtp.port());
		sender.setUsername(smtp.username());
		sender.setPassword(smtp.password());
		sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
		Properties javaMail = sender.getJavaMailProperties();
		javaMail.put("mail.smtp.auth", smtp.auth());
		javaMail.put("mail.smtp.starttls.enable", smtp.startTls());
		javaMail.put("mail.smtp.ssl.enable", smtp.ssl());
		javaMail.put("mail.smtp.connectiontimeout", smtp.connectionTimeoutMs());
		javaMail.put("mail.smtp.timeout", smtp.readTimeoutMs());
		javaMail.put("mail.smtp.writetimeout", smtp.writeTimeoutMs());
		return sender;
	}
}
