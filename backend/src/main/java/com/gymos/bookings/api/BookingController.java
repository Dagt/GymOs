package com.gymos.bookings.api;

import com.gymos.bookings.application.BookingService;
import com.gymos.bookings.application.command.CreateBookingCommand;
import com.gymos.bookings.application.command.CreateResourceCommand;
import com.gymos.bookings.infrastructure.BookingRepository.AvailabilitySlot;
import com.gymos.bookings.infrastructure.BookingRepository.BookingRecord;
import com.gymos.bookings.infrastructure.BookingRepository.ResourceRecord;
import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class BookingController {
	private final BookingService bookingService;

	public BookingController(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	@GetMapping("/resources")
	List<ResourceRecord> resources(@AuthenticationPrincipal GymOsPrincipal principal) {
		return bookingService.resources(principal);
	}

	@PostMapping("/resources")
	ResourceRecord createResource(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateResourceRequest request,
			HttpServletRequest servletRequest
	) {
		return bookingService.createResource(principal, request.toCommand(), correlationId(servletRequest));
	}

	@GetMapping("/resources/{resourceId}/availability")
	List<AvailabilitySlot> availability(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID resourceId,
			@RequestParam(required = false) LocalDate date
	) {
		return bookingService.availability(principal, resourceId, date);
	}

	@GetMapping("/bookings")
	List<BookingRecord> bookings(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(required = false) LocalDate date,
			@RequestParam(defaultValue = "80") int limit
	) {
		return bookingService.bookings(principal, date, limit);
	}

	@GetMapping("/bookings/{bookingId}")
	BookingRecord booking(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID bookingId) {
		return bookingService.detail(principal, bookingId);
	}

	@PostMapping("/bookings")
	BookingRecord createBooking(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@Valid @RequestBody CreateBookingRequest request,
			HttpServletRequest servletRequest
	) {
		return bookingService.createBooking(principal, request.toCommand(), correlationId(servletRequest));
	}

	@PostMapping("/bookings/{bookingId}/confirm")
	BookingRecord confirm(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID bookingId, HttpServletRequest servletRequest) {
		return bookingService.transition(principal, bookingId, "CONFIRMED", correlationId(servletRequest));
	}

	@PostMapping("/bookings/{bookingId}/check-in")
	BookingRecord checkIn(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID bookingId, HttpServletRequest servletRequest) {
		return bookingService.transition(principal, bookingId, "CHECKED_IN", correlationId(servletRequest));
	}

	@PostMapping("/bookings/{bookingId}/complete")
	BookingRecord complete(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID bookingId, HttpServletRequest servletRequest) {
		return bookingService.transition(principal, bookingId, "COMPLETED", correlationId(servletRequest));
	}

	@PostMapping("/bookings/{bookingId}/cancel")
	BookingRecord cancel(@AuthenticationPrincipal GymOsPrincipal principal, @PathVariable UUID bookingId, HttpServletRequest servletRequest) {
		return bookingService.transition(principal, bookingId, "CANCELLED", correlationId(servletRequest));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}

	public record CreateResourceRequest(
			@NotBlank @Size(max = 140) String name,
			@NotBlank @Size(max = 40) String resourceType,
			@Min(1) int capacity,
			@Min(15) int slotMinutes,
			LocalTime opensAt,
			LocalTime closesAt,
			@Size(max = 2000) String notes
	) {
		private CreateResourceCommand toCommand() {
			return new CreateResourceCommand(name, resourceType, capacity, slotMinutes, opensAt, closesAt, notes);
		}
	}

	public record CreateBookingRequest(
			@NotNull UUID resourceId,
			@NotNull UUID clientId,
			@NotNull OffsetDateTime startsAt,
			@NotNull OffsetDateTime endsAt,
			@Size(max = 2000) String notes
	) {
		private CreateBookingCommand toCommand() {
			return new CreateBookingCommand(resourceId, clientId, startsAt, endsAt, notes);
		}
	}
}
