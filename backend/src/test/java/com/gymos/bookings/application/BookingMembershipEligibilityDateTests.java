package com.gymos.bookings.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.bookings.application.command.CreateBookingCommand;
import com.gymos.bookings.infrastructure.BookingPlanRepository;
import com.gymos.bookings.infrastructure.BookingResourceRepository;
import com.gymos.bookings.infrastructure.BookingResourceRepository.ResourceRecord;
import com.gymos.bookings.infrastructure.BookingRepository;
import com.gymos.bookings.infrastructure.BookingRepository.BookingRecord;
import com.gymos.bookings.infrastructure.ClientBookingConflictRepository;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.time.BusinessTime;
import com.gymos.eligibility.api.EligibilityPolicy;
import com.gymos.eligibility.api.EligibilityPolicy.EligibilityDecision;
import com.gymos.eligibility.api.EligibilityPolicy.Reason;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BookingMembershipEligibilityDateTests {
	@Test
	void validatesMembershipAgainstLocalBookingDateInsteadOfCurrentDate() {
		BookingRepository bookingRepository = mock(BookingRepository.class);
		BookingResourceRepository bookingResourceRepository = mock(BookingResourceRepository.class);
		BookingPlanRepository bookingPlanRepository = mock(BookingPlanRepository.class);
		EligibilityPolicy eligibilityPolicy = mock(EligibilityPolicy.class);
		ClientBookingConflictRepository conflictRepository = mock(ClientBookingConflictRepository.class);
		AuditService auditService = mock(AuditService.class);
		ResourceScheduleResolver scheduleResolver = new ResourceScheduleResolver(new ObjectMapper());
		BookingService service = new BookingService(
				bookingRepository,
				bookingResourceRepository,
				bookingPlanRepository,
				eligibilityPolicy,
				conflictRepository,
				scheduleResolver,
				auditService
		);

		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID resourceId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID bookingId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				userId,
				organizationId,
				branchId,
				"manager",
				"Manager",
				Set.of()
		);

		LocalDate bookingDate = BusinessTime.today().plusDays(1);
		OffsetDateTime startsAt = bookingDate
				.atTime(10, 0)
				.atZone(BusinessTime.DEFAULT_ZONE)
				.toOffsetDateTime();
		OffsetDateTime endsAt = startsAt.plusHours(1);
		ResourceRecord resource = new ResourceRecord(
				resourceId,
				"Cancha 1",
				"COURT",
				1,
				60,
				"ACTIVE",
				LocalTime.of(6, 0),
				LocalTime.of(22, 0),
				null,
				List.of(),
				""
		);
		BookingRecord created = new BookingRecord(
				bookingId,
				resourceId,
				resource.name(),
				clientId,
				"Socio nuevo",
				"RESERVED",
				startsAt,
				endsAt,
				null,
				null,
				null,
				null,
				null
		);

		when(bookingResourceRepository.resource(organizationId, branchId, resourceId)).thenReturn(Optional.of(resource));
		when(eligibilityPolicy.evaluate(any()))
				.thenReturn(new EligibilityDecision(true, Reason.ELIGIBLE, null, List.of()));
		when(conflictRepository.hasOverlappingBooking(organizationId, branchId, clientId, startsAt, endsAt)).thenReturn(false);
		when(bookingRepository.overlappingBookings(organizationId, branchId, resourceId, startsAt, endsAt)).thenReturn(0);
		when(bookingRepository.createBooking(eq(organizationId), eq(branchId), eq(userId), any())).thenReturn(bookingId);
		when(bookingRepository.booking(organizationId, branchId, bookingId)).thenReturn(Optional.of(created));

		BookingRecord result = service.createBooking(
				principal,
				new CreateBookingCommand(resourceId, clientId, startsAt, endsAt, null),
				"correlation-id"
		);

		assertEquals(bookingId, result.id());
		verify(eligibilityPolicy).evaluate(argThat(request ->
				request.organizationId().equals(organizationId)
						&& request.branchId().equals(branchId)
						&& request.clientId().equals(clientId)
						&& request.effectiveOn().equals(bookingDate)));
	}
}
