package com.gymos.notifications.api.operations;

import com.gymos.notifications.api.EmailDeliveryPort;
import com.gymos.notifications.api.EmailDeliveryPort.EmailMessage;
import com.gymos.notifications.application.NotificationProperties;
import com.gymos.notifications.infrastructure.NotificationBrandRepository;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class PlatformInvitationEmailDeliveryAdapter implements PlatformInvitationEmailDelivery {
	private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Mexico_City");
	private static final DateTimeFormatter EXPIRATION_FORMAT =
			DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm", Locale.forLanguageTag("es-MX"));

	private final Map<String, EmailDeliveryPort> providers;
	private final NotificationBrandRepository brandRepository;
	private final NotificationProperties.Smtp smtp;

	PlatformInvitationEmailDeliveryAdapter(
			List<EmailDeliveryPort> providers,
			NotificationBrandRepository brandRepository,
			NotificationProperties properties
	) {
		this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
				provider -> provider.providerKey().toUpperCase(Locale.ROOT), Function.identity()));
		this.brandRepository = brandRepository;
		this.smtp = properties.smtp();
	}

	@Override
	public InvitationEmailResult deliverOwnerInvitation(OwnerInvitationEmail command) {
		EmailDeliveryPort provider = providers.get("SMTP");
		String fromEmail = smtp.preferredFromAddress();
		if (!smtp.available() || provider == null || !provider.available() || fromEmail == null
				|| !smtp.allowsFromAddress(fromEmail)) {
			return InvitationEmailResult.notConfigured();
		}

		var brand = brandRepository.emailBrand(command.organizationId());
		String subject = "Activa tu cuenta de dueño en " + command.organizationName();
		String body = """
				Hola %s:

				GymFort te invitó como dueño de %s.
				Crea tu contraseña con este enlace de un solo uso:
				%s

				El enlace vence el %s (hora de Ciudad de México). Si no esperabas esta invitación, ignora el mensaje.
				""".formatted(
				command.ownerName(), command.organizationName(), command.invitationUrl(),
				EXPIRATION_FORMAT.format(command.expiresAt().atZoneSameInstant(BUSINESS_ZONE)));
		try {
			provider.deliver(new EmailMessage(
					command.invitationId(), command.organizationId(), fromEmail, "GymFort",
					command.replyTo(), command.ownerEmail(), subject, body, null,
					"owner-invitation:" + command.invitationId(),
					brand.brandName(), brand.logoUrl(), brand.primaryColor(), brand.accentColor(),
					brand.surfaceColor(), command.invitationUrl(), "Crear mi contraseña"));
			return InvitationEmailResult.sent();
		} catch (EmailDeliveryPort.DeliveryException exception) {
			return InvitationEmailResult.failed();
		} catch (RuntimeException exception) {
			return InvitationEmailResult.failed();
		}
	}
}
