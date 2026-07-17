package com.gymos.bookings.application;

import com.gymos.audit.api.AuditService;
import com.gymos.bookings.application.command.CreateBookingCommand;
import com.gymos.bookings.application.command.CreateResourceCommand;
import com.gymos.bookings.application.command.LinkBookingPaymentCommand;
import com.gymos.bookings.infrastructure.BookingRepository;
import com.gymos.bookings.infrastructure.BookingRepository.AvailabilitySlot;
import com.gymos.bookings.infrastructure.BookingRepository.BookingRecord;
import com.gymos.bookings.infrastructure.BookingRepository.CreateBookingData;
import com.gymos.bookings.infrastructure.BookingRepository.CreateResourceData;
import com.gymos.bookings.infrastructure.BookingRepository.ResourceRecord;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
	private static final Set<String> ACTIVE_STATUSES = Set.of("RESERVED", "CONFIRMED", "CHECKED_IN");

	private final BookingRepository bookingRepository;
	private final AuditService auditService;

	public BookingService(BookingRepository bookingRepository, AuditService auditService) {
		this.bookingRepository = bookingRepository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<ResourceRecord> resources(GymOsPrincipal principal) {
		requireBranch(principal);
		return bookingRepository.resources(principal.organizationId(), principal.branchId());
	}

	@Transactional
	public ResourceRecord createResource(GymOsPrincipal principal, CreateResourceCommand command, String correlationId) {
		requireBranch(principal);
		LocalTime opensAt = command.opensAt() == null ? LocalTime.of(6, 0) : command.opensAt();
		LocalTime closesAt = command.closesAt() == null ? LocalTime.of(22, 0) : command.closesAt();
		if (!closesAt.isAfter(opensAt)) {
			throw new BusinessException("RESOURCE_HOURS_INVALID", "La hora de cierre debe ser posterior a la apertura.");
		}
		int capacity = Math.max(1, command.capacity());
		int slotMinutes = Math.max(15, command.slotMinutes());
		UUID resourceId = bookingRepository.createResource(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				new CreateResourceData(command.name().trim(), command.resourceType().trim().toUpperCase(), capacity, slotMinutes, opensAt, closesAt, blankToNull(command.notes()))
		);
		auditService.record(principal, "RESOURCE_CREATED", "RESOURCE", resourceId, "RECEPTION", null, correlationId);
		return bookingRepository.resource(principal.organizationId(), principal.branchId(), resourceId).orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<AvailabilitySlot> availability(GymOsPrincipal principal, UUID resourceId, LocalDate date) {
		requireBranch(principal);
		LocalDate resolvedDate = date == null ? LocalDate.now() : date;
		ResourceRecord resource = activeResource(principal, resourceId);
		List<AvailabilitySlot> slots = new ArrayList<>();
		LocalTime cursor = resource.opensAt();
		while (!cursor.plusMinutes(resource.slotMinutes()).isAfter(resource.closesAt())) {
			OffsetDateTime startsAt = resolvedDate.atTime(cursor).atOffset(ZoneOffset.UTC);
			OffsetDateTime endsAt = startsAt.plusMinutes(resource.slotMinutes());
			int reserved = bookingRepository.overlappingBookings(principal.organizationId(), principal.branchId(), resourceId, startsAt, endsAt);
			slots.add(new AvailabilitySlot(startsAt, endsAt, reserved, resource.capacity(), reserved < resource.capacity()));
			cursor = cursor.plusMinutes(resource.slotMinutes());
		}
		return slots;
	}

	@Transactional(readOnly = true)
	public List<BookingRecord> bookings(GymOsPrincipal principal, LocalDate date, int limit) {
		requireBranch(principal);
		return bookingRepository.bookings(principal.organizationId(), principal.branchId(), date == null ? LocalDate.now() : date, Math.max(1, Math.min(limit, 200)));
	}

	@Transactional(readOnly = true)
	public BookingRecord detail(GymOsPrincipal principal, UUID bookingId) {
		requireBranch(principal);
		return bookingRepository.booking(principal.organizationId(), principal.branchId(), bookingId)
				.orElseThrow(() -> new BusinessException("BOOKING_NOT_FOUND", "La reserva no existe o no esta disponible."));
	}

	@Transactional
	public BookingRecord createBooking(GymOsPrincipal principal, CreateBookingCommand command, String correlationId) {
		requireBranch(principal);
		ResourceRecord resource = activeResource(principal, command.resourceId());
		if (!bookingRepository.clientExists(principal.organizationId(), command.clientId())) {
			throw new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta activo.");
		}
		if (command.startsAt() == null || command.endsAt() == null || !command.endsAt().isAfter(command.startsAt())) {
			throw new BusinessException("BOOKING_RANGE_INVALID", "La reserva requiere un rango valido.");
		}
		LocalTime startsTime = command.startsAt().toLocalTime();
		LocalTime endsTime = command.endsAt().toLocalTime();
		if (startsTime.isBefore(resource.opensAt()) || endsTime.isAfter(resource.closesAt())) {
			throw new BusinessException("BOOKING_OUTSIDE_RESOURCE_HOURS", "La reserva esta fuera del horario del recurso.");
		}
		int reserved = bookingRepository.overlappingBookings(principal.organizationId(), principal.branchId(), command.resourceId(), command.startsAt(), command.endsAt());
		if (reserved >= resource.capacity()) {
			throw new BusinessException("BOOKING_CONFLICT", "El recurso no tiene disponibilidad en ese horario.");
		}
		UUID bookingId = bookingRepository.createBooking(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				new CreateBookingData(command.resourceId(), command.clientId(), command.startsAt(), command.endsAt(), blankToNull(command.notes()))
		);
		auditService.record(principal, "BOOKING_CREATED", "BOOKING", bookingId, "RECEPTION", null, correlationId);
		return detail(principal, bookingId);
	}

	@Transactional
	public BookingRecord transition(GymOsPrincipal principal, UUID bookingId, String targetStatus, String correlationId) {
		BookingRecord current = detail(principal, bookingId);
		if ("CANCELLED".equals(current.status()) || "COMPLETED".equals(current.status())) {
			throw new BusinessException("BOOKING_ALREADY_CLOSED", "La reserva ya esta cerrada.");
		}
		if ("CHECKED_IN".equals(targetStatus) && !"CONFIRMED".equals(current.status())) {
			throw new BusinessException("BOOKING_NOT_CONFIRMABLE", "Solo una reserva confirmada puede hacer check-in.");
		}
		if ("COMPLETED".equals(targetStatus) && !"CHECKED_IN".equals(current.status())) {
			throw new BusinessException("BOOKING_NOT_COMPLETABLE", "Solo una reserva con check-in puede completarse.");
		}
		if ("CONFIRMED".equals(targetStatus) && !ACTIVE_STATUSES.contains(current.status())) {
			throw new BusinessException("BOOKING_NOT_CONFIRMABLE", "La reserva no puede confirmarse.");
		}
		bookingRepository.updateStatus(principal.organizationId(), principal.branchId(), bookingId, targetStatus, principal.userId());
		auditService.record(principal, "BOOKING_" + targetStatus, "BOOKING", bookingId, "RECEPTION", null, correlationId);
		return detail(principal, bookingId);
	}

	@Transactional
	public BookingRecord linkPayment(GymOsPrincipal principal, UUID bookingId, LinkBookingPaymentCommand command, String correlationId) {
		BookingRecord current = detail(principal, bookingId);
		if ("CANCELLED".equals(current.status()) || "COMPLETED".equals(current.status())) {
			throw new BusinessException("BOOKING_ALREADY_CLOSED", "La reserva ya esta cerrada.");
		}
		if (!bookingRepository.saleExists(principal.organizationId(), principal.branchId(), command.saleId())) {
			throw new BusinessException("SALE_NOT_FOUND", "La venta no existe o no esta disponible en esta sucursal.");
		}
		String status = command.status().trim().toUpperCase();
		bookingRepository.upsertBookingPayment(principal.organizationId(), principal.branchId(), bookingId, command.saleId(), status, command.expiresAt());
		auditService.record(principal, "BOOKING_PAYMENT_LINKED", "BOOKING", bookingId, "RECEPTION", status, correlationId);
		if ("PAID".equals(status) && "RESERVED".equals(current.status())) {
			bookingRepository.updateStatus(principal.organizationId(), principal.branchId(), bookingId, "CONFIRMED", principal.userId());
			auditService.record(principal, "BOOKING_CONFIRMED", "BOOKING", bookingId, "RECEPTION", "Pago vinculado", correlationId);
		}
		return detail(principal, bookingId);
	}

	private ResourceRecord activeResource(GymOsPrincipal principal, UUID resourceId) {
		ResourceRecord resource = bookingRepository.resource(principal.organizationId(), principal.branchId(), resourceId)
				.orElseThrow(() -> new BusinessException("RESOURCE_NOT_FOUND", "El recurso no existe o no esta disponible."));
		if (!"ACTIVE".equals(resource.status())) {
			throw new BusinessException("RESOURCE_NOT_ACTIVE", "El recurso no esta activo.");
		}
		return resource;
	}

	private void requireBranch(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
		}
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
