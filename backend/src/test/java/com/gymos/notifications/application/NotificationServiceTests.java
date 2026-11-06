package com.gymos.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.notifications.api.EmailDeliveryPort;
import com.gymos.notifications.infrastructure.NotificationRepository;
import com.gymos.notifications.infrastructure.NotificationRepository.CreateNotificationData;
import com.gymos.notifications.infrastructure.NotificationRepository.EmailConfigurationRecord;
import com.gymos.notifications.infrastructure.NotificationRepository.RecipientRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationServiceTests {
	private final NotificationRepository repository = mock(NotificationRepository.class);
	private final AuditService audit = mock(AuditService.class);
	private final EmailDeliveryPort provider = mock(EmailDeliveryPort.class);
	private NotificationService service;

	@BeforeEach
	void setUp() {
		when(provider.providerKey()).thenReturn("SMTP");
		when(provider.available()).thenReturn(true);
		NotificationProperties properties = properties();
		service = new NotificationService(
				repository, audit, List.of(provider), new NotificationRecipientHasher(properties), properties,
				mock(com.gymos.clients.api.operations.ClientBranchAccess.class)
		);
	}

	@Test
	void emailForClientResolvesAddressAndQueuesDurableDelivery() {
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();
		when(repository.emailConfiguration(organizationId)).thenReturn(configuration(organizationId, true));
		when(repository.recipient(organizationId, "CLIENT", clientId))
				.thenReturn(Optional.of(new RecipientRecord("SOCIO@EXAMPLE.COM", "Socio")));
		when(repository.emailAllowed(eq(organizationId), any(), eq("TRANSACTIONAL"))).thenReturn(true);
		when(repository.create(eq(organizationId), eq(branchId), eq(null), any())).thenReturn(notificationId);

		UUID result = service.createSystemIdempotent(
				organizationId, branchId, null,
				new NotificationService.CreateNotificationCommand(
						"EMAIL", null, "CLIENT", clientId, "Tu membresia",
						"Se acerca el vencimiento.", "TRANSACTIONAL", "MEMBERSHIP_EXPIRING",
						"MEMBERSHIP", UUID.randomUUID(), null
				),
				"correlation", "notification:key"
		);

		assertThat(result).isEqualTo(notificationId);
		ArgumentCaptor<CreateNotificationData> data = ArgumentCaptor.forClass(CreateNotificationData.class);
		verify(repository).create(eq(organizationId), eq(branchId), eq(null), data.capture());
		assertThat(data.getValue().recipient()).isEqualTo("socio@example.com");
		assertThat(data.getValue().recipientType()).isEqualTo("CLIENT");
		assertThat(data.getValue().status()).isEqualTo("QUEUED");
		assertThat(data.getValue().idempotencyKey()).isEqualTo("notification:key");
	}

	@Test
	void optedOutRecipientIsPersistedAsSuppressed() {
		UUID organizationId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();
		when(repository.emailConfiguration(organizationId)).thenReturn(configuration(organizationId, true));
		when(repository.emailAllowed(eq(organizationId), any(), eq("MARKETING"))).thenReturn(false);
		when(repository.create(eq(organizationId), any(), any(), any())).thenReturn(notificationId);

		service.createSystem(
				organizationId, UUID.randomUUID(), null,
				new NotificationService.CreateNotificationCommand(
						"EMAIL", "socio@example.com", "DIRECT", null, "Promocion", "Mensaje",
						"MARKETING", "MANUAL", null, null, null
				), "correlation"
		);

		ArgumentCaptor<CreateNotificationData> data = ArgumentCaptor.forClass(CreateNotificationData.class);
		verify(repository).create(eq(organizationId), any(), any(), data.capture());
		assertThat(data.getValue().status()).isEqualTo("SUPPRESSED");
	}

	@Test
	void emailCannotQueueUntilTenantChannelAndProviderAreReady() {
		UUID organizationId = UUID.randomUUID();
		when(repository.emailConfiguration(organizationId)).thenReturn(configuration(organizationId, false));

		assertThatThrownBy(() -> service.createSystem(
				organizationId, UUID.randomUUID(), null,
				new NotificationService.CreateNotificationCommand(
						"EMAIL", "socio@example.com", "Asunto", "Mensaje", "MANUAL", null, null, null
				), "correlation"
		)).isInstanceOf(BusinessException.class)
				.hasMessageContaining("no tiene habilitado");
	}

	private EmailConfigurationRecord configuration(UUID organizationId, boolean enabled) {
		return new EmailConfigurationRecord(
				organizationId, enabled, "SMTP", "envios@gymos.test", "GymOS", null, OffsetDateTime.now()
		);
	}

	private NotificationProperties properties() {
		return new NotificationProperties(
				30000, 60000, 50, 120, 5, "http://localhost:3000", "preference-test-key",
				new NotificationProperties.Smtp(
						false, "Zoho Mail", null, 587, null, null, List.of(),
						true, true, true, false, true, 10000, 10000, 10000
				)
		);
	}
}
