package com.gymos.notifications.api.operations;

import com.gymos.common.time.BusinessTime;
import com.gymos.notifications.api.EmailDeliveryPort;
import com.gymos.notifications.api.EmailDeliveryPort.EmailMessage;
import com.gymos.notifications.application.NotificationProperties;
import com.gymos.notifications.infrastructure.NotificationBrandRepository;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PasswordRecoveryEmailDeliveryAdapter implements PasswordRecoveryEmailDelivery {
	private final List<EmailDeliveryPort> providers;
	private final NotificationProperties.Smtp smtp;
	private final NotificationBrandRepository brands;
	public PasswordRecoveryEmailDeliveryAdapter(List<EmailDeliveryPort> providers, NotificationProperties properties, NotificationBrandRepository brands) {
		this.providers = providers;
		this.smtp = properties.smtp();
		this.brands = brands;
	}

	@Override
	public String deliver(RecoveryEmail email) {
		if (email.recipientEmail() == null || email.recipientEmail().isBlank()) return "NO_EMAIL";
		var provider = providers.stream().filter(item -> "SMTP".equalsIgnoreCase(item.providerKey())).findFirst().orElse(null);
		String from = smtp.preferredFromAddress();
		if (!smtp.available() || from == null || !smtp.allowsFromAddress(from) || provider == null || !provider.available()) return "NOT_CONFIGURED";
		try {
			var brand = brands.emailBrand(email.organizationId());
			String expiration = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(email.expiresAt().atZoneSameInstant(BusinessTime.DEFAULT_ZONE));
			String body = "Hola " + email.recipientName() + ":\n\nUn administrador de tu gimnasio solicitó recuperar tu acceso a GymFort.\n"
					+ "Elige una nueva contraseña con este enlace de un solo uso:\n" + email.url()
					+ "\n\nVence el " + expiration + " (Ciudad de México). Al usarlo se cerrarán tus sesiones anteriores."
					+ "\nSi no reconoces esta solicitud, no uses el enlace y contacta a tu gimnasio.";
			provider.deliver(new EmailMessage(email.id(), email.organizationId(), from, "GymFort", null,
					email.recipientEmail(), "Recupera tu acceso a GymFort", body, null, "password-recovery:" + email.id(),
					brand.brandName(), brand.logoUrl(), brand.primaryColor(), brand.accentColor(), brand.surfaceColor(),
					email.url(), "Elegir nueva contraseña"));
			return "SENT";
		} catch (RuntimeException exception) {
			// Delivery errors may contain the email body. Do not persist or log them.
			return "FAILED";
		}
	}
}
