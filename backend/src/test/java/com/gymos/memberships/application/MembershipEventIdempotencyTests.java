package com.gymos.memberships.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.memberships.infrastructure.MembershipRepository;
import com.gymos.sales.api.events.PaymentCompletedV1;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class MembershipEventIdempotencyTests {
	@Test
	void duplicatePaymentCompletedEventIsIgnoredBeforeMembershipMutation() {
		MembershipRepository membershipRepository = mock(MembershipRepository.class);
		AuditService auditService = mock(AuditService.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		MembershipActivationService activationService = new MembershipActivationService(
				membershipRepository,
				auditService,
				new MembershipActivationWindowCalculator(membershipRepository),
				new MembershipActivationEventPublisher(eventPublisher)
		);

		UUID organizationId = UUID.randomUUID();
		String eventKey = "payment-completed:" + UUID.randomUUID();
		PaymentCompletedV1 duplicateEvent = new PaymentCompletedV1(
				eventKey,
				organizationId,
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"S-TEST",
				"MEMBERSHIP_RENEWAL",
				UUID.randomUUID(),
				null,
				List.of(),
				OffsetDateTime.now()
		);
		when(membershipRepository.markEventProcessed(
				organizationId,
				eventKey,
				PaymentCompletedV1.class.getSimpleName()
		)).thenReturn(false);

		activationService.processPaymentCompleted(duplicateEvent);

		verify(membershipRepository).markEventProcessed(
				organizationId,
				eventKey,
				PaymentCompletedV1.class.getSimpleName()
		);
		verifyNoInteractions(auditService, eventPublisher);
	}
}
