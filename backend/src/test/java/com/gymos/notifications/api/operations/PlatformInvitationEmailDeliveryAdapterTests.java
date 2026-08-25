package com.gymos.notifications.api.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.notifications.api.EmailDeliveryPort;
import com.gymos.notifications.api.EmailDeliveryPort.EmailMessage;
import com.gymos.notifications.application.NotificationProperties;
import com.gymos.notifications.infrastructure.NotificationBrandRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlatformInvitationEmailDeliveryAdapterTests {
	private final EmailDeliveryPort provider = mock(EmailDeliveryPort.class);
	private final NotificationBrandRepository brands = mock(NotificationBrandRepository.class);

	@BeforeEach
	void setUp() {
		when(provider.providerKey()).thenReturn("SMTP");
		when(provider.available()).thenReturn(true);
		when(provider.deliver(any())).thenReturn(new EmailDeliveryPort.DeliveryResult("message-id"));
		when(brands.emailBrand(any())).thenReturn(new NotificationBrandRepository.EmailBrand(
				"Gimnasio Norte", null, "#093d3a", "#18c8b2", "#f2f8f7"));
	}

	@Test
	void sendsTheOneTimeLinkThroughTheConfiguredSmtpProvider() {
		var service = new PlatformInvitationEmailDeliveryAdapter(
				List.of(provider), brands, properties(true));
		var command = command();

		var result = service.deliverOwnerInvitation(command);

		ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
		verify(provider).deliver(message.capture());
		assertThat(result.status()).isEqualTo("SENT");
		assertThat(message.getValue().recipient()).isEqualTo(command.ownerEmail());
		assertThat(message.getValue().bodyText()).contains(command.invitationUrl());
		assertThat(message.getValue().actionUrl()).isEqualTo(command.invitationUrl());
		assertThat(message.getValue().actionLabel()).isEqualTo("Crear mi contraseña");
	}

	@Test
	void reportsMissingConfigurationWithoutAttemptingDelivery() {
		var service = new PlatformInvitationEmailDeliveryAdapter(
				List.of(provider), brands, properties(false));

		assertThat(service.deliverOwnerInvitation(command()).status()).isEqualTo("NOT_CONFIGURED");
		verify(provider, never()).deliver(any());
	}

	@Test
	void providerFailureBecomesAnOperationalStatusInsteadOfFailingProvisioning() {
		when(provider.deliver(any())).thenThrow(
				new EmailDeliveryPort.DeliveryException("provider rejected", false, null));
		var service = new PlatformInvitationEmailDeliveryAdapter(
				List.of(provider), brands, properties(true));

		assertThat(service.deliverOwnerInvitation(command()).status()).isEqualTo("FAILED");
	}

	private PlatformInvitationEmailDelivery.OwnerInvitationEmail command() {
		return new PlatformInvitationEmailDelivery.OwnerInvitationEmail(
				UUID.randomUUID(), UUID.randomUUID(), "Gimnasio Norte", "Ana Dueña",
				"ana@example.com", "contacto@example.com",
				"https://gymfort.mx/accept-invitation?token=one-time-token",
				OffsetDateTime.now().plusHours(72));
	}

	private NotificationProperties properties(boolean enabled) {
		return new NotificationProperties(
				30000, 60000, 50, 120, 5, "https://gymfort.mx", "preference-key",
				new NotificationProperties.Smtp(
						enabled, "ZeptoMail", "smtp.zeptomail.com", 587, "emailapikey", "secret",
						List.of("notificaciones@gymfort.mx"), true, true, true, false, true,
						10000, 10000, 10000));
	}
}
