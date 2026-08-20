package com.gymos.bookings.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.bookings.api.events.BookingStatusChangedV1;
import com.gymos.bookings.infrastructure.BookingPlanRepository;
import com.gymos.bookings.infrastructure.BookingRepository;
import com.gymos.bookings.infrastructure.BookingRepository.BookingRecord;
import com.gymos.bookings.infrastructure.BookingResourceRepository;
import com.gymos.bookings.infrastructure.ClientBookingConflictRepository;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.eligibility.api.EligibilityPolicy;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class BookingDomainEventTests {
	@Test
	void statusTransitionPublishesAStableDomainEvent() {
		BookingRepository bookings = mock(BookingRepository.class);
		ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
		BookingService service = new BookingService(
				bookings,
				mock(BookingResourceRepository.class),
				mock(BookingPlanRepository.class),
				mock(EligibilityPolicy.class),
				mock(ClientBookingConflictRepository.class),
				new ResourceScheduleResolver(new ObjectMapper()),
				mock(AuditService.class),
				events
		);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID bookingId = UUID.randomUUID();
		UUID resourceId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		OffsetDateTime startsAt = OffsetDateTime.now().plusDays(1);
		BookingRecord reserved = booking(
				bookingId, resourceId, clientId, "RESERVED", startsAt
		);
		BookingRecord confirmed = booking(
				bookingId, resourceId, clientId, "CONFIRMED", startsAt
		);
		GymOsPrincipal principal = new GymOsPrincipal(
				userId, organizationId, branchId, "reception", "Reception", Set.of("booking.manage")
		);
		when(bookings.booking(organizationId, branchId, bookingId))
				.thenReturn(Optional.of(reserved), Optional.of(confirmed));

		BookingRecord result = service.transition(principal, bookingId, "CONFIRMED", "booking-confirm");

		assertThat(result.status()).isEqualTo("CONFIRMED");
		ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
		verify(events).publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getValue()).isInstanceOf(BookingStatusChangedV1.class);
		BookingStatusChangedV1 statusEvent = (BookingStatusChangedV1) eventCaptor.getValue();
		assertThat(statusEvent.eventKey()).isEqualTo("booking-status:" + bookingId + ":CONFIRMED");
		assertThat(statusEvent.organizationId()).isEqualTo(organizationId);
		assertThat(statusEvent.branchId()).isEqualTo(branchId);
		assertThat(statusEvent.previousStatus()).isEqualTo("RESERVED");
		assertThat(statusEvent.status()).isEqualTo("CONFIRMED");
	}

	private BookingRecord booking(
			UUID bookingId,
			UUID resourceId,
			UUID clientId,
			String status,
			OffsetDateTime startsAt
	) {
		return new BookingRecord(
				bookingId, resourceId, "Cancha", clientId, "Socio", status,
				startsAt, startsAt.plusHours(1), null, null, null, null, null
		);
	}
}
