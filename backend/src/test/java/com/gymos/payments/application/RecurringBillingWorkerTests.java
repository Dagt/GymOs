package com.gymos.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.payments.api.events.RecurringChargeDueV1;
import com.gymos.payments.infrastructure.RecurringBillingRepository;
import com.gymos.payments.infrastructure.RecurringBillingRepository.ChargeRecord;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class RecurringBillingWorkerTests {
	private final RecurringBillingRepository recurringRepository = mock(RecurringBillingRepository.class);
	private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
	private final RecurringBillingWorker worker = new RecurringBillingWorker(
			recurringRepository, eventPublisher
	);

	@Test
	void dueOccurrenceIsClaimedAndPublishedWithStableIdentity() {
		ChargeRecord charge = charge();
		when(recurringRepository.dueCharges(any(), eq(25))).thenReturn(List.of(charge));
		when(recurringRepository.claimCharge(eq(charge.id()), any(), any())).thenReturn(true);
		when(recurringRepository.charge(charge.id())).thenReturn(Optional.of(charge));

		worker.processDueCharges();

		verify(recurringRepository).materializeDueCharges(any(LocalDate.class), eq(100));
		ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(published.capture());
		assertThat(published.getValue()).isInstanceOfSatisfying(
				RecurringChargeDueV1.class,
				event -> assertThat(event.eventKey()).isEqualTo("recurring-charge-due:" + charge.id())
		);
	}

	private ChargeRecord charge() {
		return new ChargeRecord(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "cus_test", UUID.randomUUID(),
				"pm_test", LocalDate.now(), "PROCESSING", null, null, null, null, null,
				1, OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(5), null
		);
	}
}
