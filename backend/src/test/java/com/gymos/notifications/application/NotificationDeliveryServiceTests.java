package com.gymos.notifications.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gymos.common.observability.TenantTelemetry;
import com.gymos.notifications.api.EmailDeliveryPort;
import com.gymos.notifications.infrastructure.NotificationBrandRepository;
import com.gymos.notifications.infrastructure.NotificationRepository;
import com.gymos.notifications.infrastructure.NotificationRepository.EmailDeliveryRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationDeliveryServiceTests {
	private final NotificationRepository repository = mock(NotificationRepository.class);
	private final NotificationBrandRepository brandRepository = mock(NotificationBrandRepository.class);
	private final EmailDeliveryPort provider = mock(EmailDeliveryPort.class);
	private NotificationDeliveryService service;

	@BeforeEach
	void setUp() {
		when(provider.providerKey()).thenReturn("SMTP");
		when(provider.available()).thenReturn(true);
		when(brandRepository.emailBrand(any())).thenReturn(new NotificationBrandRepository.EmailBrand(
				"Mi gimnasio", null, "#111111", "#ffee00", "#ffffff"
		));
		NotificationProperties properties = properties();
		service = new NotificationDeliveryService(
				repository, brandRepository, properties, TenantTelemetry.noOp(), List.of(provider),
				new NotificationRecipientHasher(properties)
		);
	}

	@Test
	void claimedEmailIsDeliveredAndMarkedSent() {
		EmailDeliveryRecord delivery = delivery(1);
		when(repository.dueEmailIds(any(), eq(50))).thenReturn(List.of(delivery.id()));
		when(repository.claimEmail(eq(delivery.id()), any(), any())).thenReturn(true);
		when(repository.emailDelivery(delivery.id())).thenReturn(Optional.of(delivery));
		when(repository.emailAllowed(eq(delivery.organizationId()), any(), eq("TRANSACTIONAL"))).thenReturn(true);
		when(provider.deliver(any())).thenReturn(new EmailDeliveryPort.DeliveryResult("message-1"));

		service.runDueDeliveries();

		verify(brandRepository).emailBrand(delivery.organizationId());
		verify(repository).markSent(eq(delivery.id()), eq("SMTP"), eq("message-1"), any());
	}

	@Test
	void preferenceChangedAfterQueueSuppressesWithoutContactingProvider() {
		EmailDeliveryRecord delivery = delivery(1);
		when(repository.dueEmailIds(any(), eq(50))).thenReturn(List.of(delivery.id()));
		when(repository.claimEmail(eq(delivery.id()), any(), any())).thenReturn(true);
		when(repository.emailDelivery(delivery.id())).thenReturn(Optional.of(delivery));
		when(repository.emailAllowed(eq(delivery.organizationId()), any(), eq("TRANSACTIONAL"))).thenReturn(false);

		service.runDueDeliveries();

		verify(repository).markSuppressed(eq(delivery.id()), any());
		verify(provider, never()).deliver(any());
	}

	@Test
	void transientFailureUsesBackoffAndFinalAttemptMovesToDeadLetter() {
		EmailDeliveryRecord delivery = delivery(5);
		when(repository.dueEmailIds(any(), eq(50))).thenReturn(List.of(delivery.id()));
		when(repository.claimEmail(eq(delivery.id()), any(), any())).thenReturn(true);
		when(repository.emailDelivery(delivery.id())).thenReturn(Optional.of(delivery));
		when(repository.emailAllowed(eq(delivery.organizationId()), any(), eq("TRANSACTIONAL"))).thenReturn(true);
		when(provider.deliver(any())).thenThrow(new EmailDeliveryPort.DeliveryException("timeout", true, null));

		service.runDueDeliveries();

		verify(repository).markDeliveryFailure(eq(delivery.id()), eq(false), any(), eq("timeout"), any());
	}

	@Test
	void disconnectedProviderLeavesQueueUntouchedUntilConfigurationReturns() {
		when(provider.available()).thenReturn(false);

		service.runDueDeliveries();

		verifyNoInteractions(repository);
	}

	private EmailDeliveryRecord delivery(int attempt) {
		return new EmailDeliveryRecord(
				UUID.randomUUID(), UUID.randomUUID(), "socio@example.com", "Asunto", "Mensaje", null,
				"TRANSACTIONAL", "notification:key", attempt, "SMTP", "envios@gymos.test", "GymOS", null
		);
	}

	private NotificationProperties properties() {
		return new NotificationProperties(
				30000, 60000, 50, 120, 5, "http://localhost:3000", "preference-test-key",
				new NotificationProperties.Smtp(
						false, "ZeptoMail", null, 587, null, null, List.of(),
						true, true, true, false, true, 10000, 10000, 10000
				)
		);
	}
}
