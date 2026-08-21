package com.gymos.notifications.infrastructure;

import com.gymos.notifications.api.EmailDeliveryPort;
import com.gymos.notifications.application.NotificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
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
			throw new DeliveryException("El proveedor " + properties.displayName() + " no esta configurado.", true, null);
		}
		if (!properties.allowsFromAddress(message.fromEmail())) {
			throw new DeliveryException(
					"El remitente no esta autorizado en la configuracion SMTP del servidor.", false, null
			);
		}
		try {
			MimeMessage mimeMessage = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
			if (message.fromName() == null) {
				helper.setFrom(message.fromEmail());
			} else {
				helper.setFrom(message.fromEmail(), message.fromName());
			}
			helper.setTo(message.recipient());
			helper.setSubject(message.subject());
			if (message.replyTo() != null) helper.setReplyTo(message.replyTo());
			String html = message.bodyHtml() == null ? brandedHtml(message) : message.bodyHtml();
			helper.setText(message.bodyText(), html);
			String stableMessageId = "<notification-" + message.notificationId() + "@" + senderDomain(message.fromEmail()) + ">";
			mimeMessage.setHeader("Message-ID", stableMessageId);
			mimeMessage.setHeader("X-GymOS-Notification-Id", message.notificationId().toString());
			mimeMessage.setHeader(
					"X-GymOS-Idempotency-Key",
					message.idempotencyKey() == null ? message.notificationId().toString() : message.idempotencyKey()
			);
			mailSender.send(mimeMessage);
			return new DeliveryResult(stableMessageId);
		} catch (MailAuthenticationException exception) {
			throw new DeliveryException(
					properties.displayName() + " rechazo las credenciales SMTP. Revisa el usuario y la contrasena del Agent.",
					false, exception
			);
		} catch (MailParseException | MessagingException | UnsupportedEncodingException exception) {
			throw new DeliveryException("El correo contiene una direccion o contenido invalido.", false, exception);
		} catch (MailException exception) {
			throw new DeliveryException("No fue posible entregar el correo mediante " + properties.displayName() + ".", true, exception);
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
		javaMail.put("mail.smtp.starttls.required", smtp.startTlsRequired());
		javaMail.put("mail.smtp.ssl.enable", smtp.ssl());
		javaMail.put("mail.smtp.ssl.checkserveridentity", smtp.checkServerIdentity());
		javaMail.put("mail.smtp.connectiontimeout", smtp.connectionTimeoutMs());
		javaMail.put("mail.smtp.timeout", smtp.readTimeoutMs());
		javaMail.put("mail.smtp.writetimeout", smtp.writeTimeoutMs());
		return sender;
	}

	private String senderDomain(String fromEmail) {
		int separator = fromEmail == null ? -1 : fromEmail.lastIndexOf('@');
		return separator >= 0 && separator + 1 < fromEmail.length()
				? fromEmail.substring(separator + 1).replaceAll("[^A-Za-z0-9.-]", "")
				: "gymfort.mx";
	}

	private String brandedHtml(EmailMessage message) {
		String sender = message.fromName() == null ? "Tu gimnasio" : escape(message.fromName());
		String content = escape(message.bodyText()).replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>");
		return """
				<!doctype html>
				<html lang="es"><body style="margin:0;background:#f2f6f4;font-family:Arial,sans-serif;color:#17342d">
				<table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="padding:32px 16px;background:#f2f6f4"><tr><td align="center">
				<table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:620px;background:#ffffff;border:1px solid #dbe7e2;border-radius:16px;overflow:hidden">
				<tr><td style="height:6px;background:#d8f000"></td></tr>
				<tr><td style="padding:30px 34px 12px"><div style="font-size:13px;font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:#477168">%s</div></td></tr>
				<tr><td style="padding:10px 34px 34px;font-size:16px;line-height:1.7;color:#284b43">%s</td></tr>
				<tr><td style="padding:18px 34px;background:#102d27;color:#d7e8e3;font-size:12px;line-height:1.5">Mensaje enviado de forma segura con GymFort.</td></tr>
				</table></td></tr></table></body></html>
				""".formatted(sender, content);
	}

	private String escape(String value) {
		if (value == null) return "";
		return value.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&#39;");
	}
}
