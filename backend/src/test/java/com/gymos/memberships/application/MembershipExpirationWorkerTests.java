package com.gymos.memberships.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.memberships.api.events.MembershipExpiredV1;
import com.gymos.memberships.api.events.MembershipExpiringV1;
import com.gymos.memberships.infrastructure.MembershipRepository;
import com.gymos.memberships.infrastructure.MembershipRepository.MembershipLifecycleRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class MembershipExpirationWorkerTests {
	@Test
	void publishesClaimedExpiringAndExpiredMemberships() {
		MembershipRepository memberships = mock(MembershipRepository.class);
		ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
		MembershipExpirationWorker worker = new MembershipExpirationWorker(memberships, events, 30);
		MembershipLifecycleRecord expiring = membership(LocalDate.now().plusDays(3));
		MembershipLifecycleRecord expired = membership(LocalDate.now().minusDays(5));
		when(memberships.unpublishedExpiringMemberships(any(), eq(30), eq(1000)))
				.thenReturn(List.of(expiring));
		when(memberships.recordExpiringOccurrence(eq(expiring), any())).thenReturn(true);
		when(memberships.claimUnpublishedExpiredMemberships(1000)).thenReturn(List.of(expired));

		worker.expireDueMemberships();

		ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
		verify(events, times(2)).publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getAllValues())
				.anySatisfy(event -> assertThat(event).isInstanceOf(MembershipExpiringV1.class))
				.anySatisfy(event -> assertThat(event).isInstanceOf(MembershipExpiredV1.class));
		verify(memberships).expireAllDueMemberships(any());
	}

	private MembershipLifecycleRecord membership(LocalDate endsOn) {
		return new MembershipLifecycleRecord(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), endsOn, endsOn.plusDays(2)
		);
	}
}
