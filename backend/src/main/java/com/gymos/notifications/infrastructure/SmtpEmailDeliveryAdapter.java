package com.gymos.notifications.infrastructure;

import com.gymos.notifications.api.EmailDeliveryPort;
import com.gymos.notifications.application.NotificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailDeliveryAdapter implements EmailDeliveryPort {
	private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
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
		String brand = escape(firstNonBlank(message.brandName(), message.fromName(), "Tu gimnasio"));
		String subject = escape(firstNonBlank(message.subject(), "Notificación"));
		String content = escape(message.bodyText()).replace("\r\n", "\n").replace("\r", "\n").replace("\n", "<br>");
		String primary = safeColor(message.primaryColor(), "#093d3a");
		String accent = safeColor(message.accentColor(), "#18c8b2");
		String surface = safeColor(message.surfaceColor(), "#f2f8f7");
		String logo = safeLogo(message.logoUrl(), brand);
		String replyHint = message.replyTo() == null
				? "Este es un mensaje automático."
				: "Puedes responder este correo para contactar al gimnasio.";

		return """
				<!doctype html>
				<html lang="es">
				<body style="margin:0;padding:0;background:#f4f6f5;font-family:Arial,Helvetica,sans-serif;color:#17211f">
				<table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="width:100%%;background:#f4f6f5;padding:32px 14px">
				<tr><td align="center">
				<table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="width:100%%;max-width:640px;background:#ffffff;border:1px solid #dfe7e4;border-radius:16px;overflow:hidden">
				<tr><td style="height:6px;background:%s;font-size:0;line-height:0">&nbsp;</td></tr>
				<tr><td style="padding:28px 32px 12px">
				<table role="presentation" width="100%%" cellspacing="0" cellpadding="0"><tr>
				<td style="vertical-align:middle">%s<div style="display:inline-block;vertical-align:middle;color:%s;font-size:15px;font-weight:800">%s</div></td>
				</tr></table>
				<h1 style="margin:26px 0 0;color:#17211f;font-size:25px;line-height:1.25;font-weight:800">%s</h1>
				</td></tr>
				<tr><td style="padding:8px 32px 34px;color:#40524e;font-size:16px;line-height:1.7">%s</td></tr>
				<tr><td style="padding:18px 32px;background:%s;border-top:1px solid #dfe7e4">
				<div style="color:%s;font-size:12px;font-weight:700;line-height:1.5">%s</div>
				<div style="margin-top:4px;color:#6b7976;font-size:11px;line-height:1.5">%s&nbsp;&nbsp;·&nbsp;&nbsp;Enviado mediante GymFort</div>
				</td></tr>
				</table>
				</td></tr></table>
				</body></html>
				""".formatted(accent, logo, primary, brand, subject, content, surface, primary, brand, replyHint);
	}

	private String safeLogo(String logoUrl, String brand) {
		if (logoUrl == null || logoUrl.isBlank()) return "";
		String normalized = logoUrl.trim();
		if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) return "";
		return "<img src=\"" + escape(normalized) + "\" alt=\"" + brand
				+ "\" width=\"44\" height=\"44\" style=\"display:inline-block;vertical-align:middle;width:44px;height:44px;object-fit:contain;margin-right:12px;border-radius:10px\">";
	}

	private String safeColor(String value, String fallback) {
		return value != null && HEX_COLOR.matcher(value.trim()).matches() ? value.trim() : fallback;
	}

	private String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) return value.trim();
		}
		return "";
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
