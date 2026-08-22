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
		String primaryText = contrastText(primary);
		String logo = safeLogo(message.logoUrl(), brand);
		String assistance = message.replyTo() == null
				? "Este mensaje fue generado automáticamente por tu gimnasio."
				: "¿Tienes alguna duda? Responde directamente a este correo para contactar al gimnasio.";

		return """
				<!doctype html>
				<html lang="es">
				<head>
				<meta name="viewport" content="width=device-width,initial-scale=1">
				<meta name="color-scheme" content="light">
				<meta name="supported-color-schemes" content="light">
				</head>
				<body style="margin:0;padding:0;background:#eef2f1;font-family:Arial,Helvetica,sans-serif;color:#17211f">
				<table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;background:#eef2f1">
				<tr><td align="center" style="padding:34px 14px">
				<table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;max-width:660px;background:#ffffff;border:1px solid #dce5e2;border-radius:18px;overflow:hidden">
				<tr><td style="height:7px;background:%s;font-size:0;line-height:0">&nbsp;</td></tr>
				<tr><td style="padding:23px 32px;background:%s">
				<table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0"><tr>
				<td style="vertical-align:middle">%s<span style="display:inline-block;vertical-align:middle;color:%s;font-size:16px;font-weight:800;line-height:1.2">%s</span></td>
				</tr></table>
				</td></tr>
				<tr><td style="padding:36px 36px 18px;background:#ffffff">
				<div style="color:%s;font-size:11px;line-height:1.2;font-weight:800;letter-spacing:1.4px;text-transform:uppercase">Mensaje de %s</div>
				<h1 style="margin:11px 0 0;color:#101817;font-size:29px;line-height:1.22;font-weight:800;letter-spacing:-0.3px">%s</h1>
				<div style="margin-top:16px;width:42px;height:4px;border-radius:4px;background:%s;font-size:0;line-height:0">&nbsp;</div>
				</td></tr>
				<tr><td style="padding:8px 36px 30px;background:#ffffff;color:#384946;font-size:16px;line-height:1.75">%s</td></tr>
				<tr><td style="padding:0 36px 34px;background:#ffffff">
				<table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="width:100%%;background:%s;border:1px solid #e1e8e6;border-radius:12px">
				<tr><td style="padding:15px 17px;color:#53635f;font-size:12px;line-height:1.55">%s</td></tr>
				</table>
				</td></tr>
				<tr><td style="padding:19px 32px;background:%s;border-top:1px solid #dfe7e4">
				<table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0"><tr>
				<td style="color:%s;font-size:12px;font-weight:800;line-height:1.5">%s</td>
				<td align="right" style="color:#73817e;font-size:10px;line-height:1.5">Enviado mediante <strong>GymFort</strong></td>
				</tr></table>
				</td></tr>
				</table>
				<div style="max-width:660px;padding:14px 8px 0;color:#899491;font-size:10px;line-height:1.45;text-align:center">Correo transaccional enviado por solicitud de %s.</div>
				</td></tr></table>
				</body></html>
				""".formatted(
				accent,
				primary,
				logo,
				primaryText,
				brand,
				primary,
				brand,
				subject,
				accent,
				content,
				surface,
				escape(assistance),
				surface,
				primary,
				brand,
				brand
		);
	}

	private String safeLogo(String logoUrl, String brand) {
		if (logoUrl == null || logoUrl.isBlank()) return "";
		String normalized = logoUrl.trim();
		if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) return "";
		return "<span style=\"display:inline-block;vertical-align:middle;margin-right:12px;padding:6px;background:#ffffff;border-radius:10px\"><img src=\""
				+ escape(normalized) + "\" alt=\"" + brand
				+ "\" width=\"36\" height=\"36\" style=\"display:block;width:36px;height:36px;object-fit:contain;border:0\"></span>";
	}

	private String safeColor(String value, String fallback) {
		return value != null && HEX_COLOR.matcher(value.trim()).matches() ? value.trim() : fallback;
	}

	private String contrastText(String color) {
		if (color == null || !HEX_COLOR.matcher(color).matches()) return "#ffffff";
		int red = Integer.parseInt(color.substring(1, 3), 16);
		int green = Integer.parseInt(color.substring(3, 5), 16);
		int blue = Integer.parseInt(color.substring(5, 7), 16);
		double luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0;
		return luminance > 0.62 ? "#101817" : "#ffffff";
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
