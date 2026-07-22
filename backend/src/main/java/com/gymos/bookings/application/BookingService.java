package com.gymos.bookings.application;

import com.gymos.audit.api.AuditService;
import com.gymos.bookings.application.ResourceScheduleResolver.ResourceHours;
import com.gymos.bookings.application.command.CreateBookingCommand;
import com.gymos.bookings.application.command.CreateResourceCommand;
import com.gymos.bookings.application.command.LinkBookingPaymentCommand;
import com.gymos.bookings.infrastructure.BookingEligibilityRepository;
import com.gymos.bookings.infrastructure.BookingRepository;
import com.gymos.bookings.infrastructure.BookingRepository.AvailabilitySlot;
import com.gymos.bookings.infrastructure.BookingRepository.BookingRecord;
import com.gymos.bookings.infrastructure.BookingRepository.CreateBookingData;
import com.gymos.bookings.infrastructure.BookingRepository.CreateResourceData;
import com.gymos.bookings.infrastructure.BookingRepository.ResourceRecord;
import com.gymos.bookings.infrastructure.ClientBookingConflictRepository;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.time.BusinessTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
	private static final Set<String> ACTIVE_STATUSES = Set.of("RESERVED", "CONFIRMED", "CHECKED_IN");

	private final BookingRepository bookingRepository;
	private final BookingEligibilityRepository bookingEligibilityRepository;
	private final ClientBookingConflictRepository clientBookingConflictRepository;
	private final ResourceScheduleResolver resourceScheduleResolver;
	private final AuditService auditService;

	public BookingService(
			BookingRepository bookingRepository,
			BookingEligibilityRepository bookingEligibilityRepository,
			ClientBookingConflictRepository clientBookingConflictRepository,
			ResourceScheduleResolver resourceScheduleResolver,
			AuditService auditService
	) {
		this.bookingRepository = bookingRepository;
		this.bookingEligibilityRepository = bookingEligibilityRepository;
		this.clientBookingConflictRepository = clientBookingConflictRepository;
		this.resourceScheduleResolver = resourceScheduleResolver;
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
		List<UUID> allowedPlanIds = command.allowedPlanIds() == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(command.allowedPlanIds()));
		if (!allowedPlanIds.isEmpty() && bookingEligibilityRepository.activePlanCount(principal.organizationId(), allowedPlanIds) != allowedPlanIds.size()) {
			throw new BusinessException("RESOURCE_ALLOWED_PLAN_INVALID", "Selecciona solo planes activos del gimnasio.");
		}
		UUID resourceId = bookingRepository.createResource(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				new CreateResourceData(command.name().trim(), command.resourceType().trim().toUpperCase(), capacity, slotMinutes, opensAt, closesAt, blankToNull(command.notes()))
		);
		bookingRepository.replaceAllowedPlans(principal.organizationId(), resourceId, allowedPlanIds);
		auditService.record(principal, "RESOURCE_CREATED", "RESOURCE", resourceId, "RECEPTION", null, correlationId);
		return bookingRepository.resource(principal.organizationId(), principal.branchId(), resourceId).orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<AvailabilitySlot> availability(GymOsPrincipal principal, UUID resourceId, LocalDate date) {
		requireBranch(principal);
		LocalDate resolvedDate = date == null ? BusinessTime.today() : date;
		ResourceRecord resource = activeResource(principal, resourceId);
		var hours = resourceScheduleResolver.resolve(resource, resolvedDate);
		if (hours.isEmpty()) {
			return List.of();
		}
		List<AvailabilitySlot> slots = new ArrayList<>();
		LocalTime cursor = hours.get().opensAt();
		while (!cursor.plusMinutes(resource.slotMinutes()).isAfter(hours.get().closesAt())) {
			OffsetDateTime startsAt = resolvedDate.atTime(cursor).atZone(BusinessTime.DEFAULT_ZONE).toOffsetDateTime();
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
		return bookingRepository.bookings(principal.organizationId(), principal.branchId(), date == null ? BusinessTime.today() : date, Math.max(1, Math.min(limit, 200)));
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
		if (!bookingEligibilityRepository.clientExists(principal.organizationId(), command.clientId())) {
			throw new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta activo.");
		}
		if (command.startsAt() == null || command.endsAt() == null || !command.endsAt().isAfter(command.startsAt())) {
			throw new BusinessException("BOOKING_RANGE_INVALID", "La reserva requiere un rango valido.");
		}
		if (!command.startsAt().isAfter(OffsetDateTime.now())) {
			throw new BusinessException("BOOKING_START_IN_PAST", "No puedes crear una reserva en una hora que ya paso.");
		}

		OffsetDateTime localStart = command.startsAt().atZoneSameInstant(BusinessTime.DEFAULT_ZONE).toOffsetDateTime();
		OffsetDateTime localEnd = command.endsAt().atZoneSameInstant(BusinessTime.DEFAULT_ZONE).toOffsetDateTime();
		if (!bookingEligibilityRepository.hasEligibleMembershipForResource(
				principal.organizationId(),
				principal.branchId(),
				command.resourceId(),
				command.clientId(),
				localStart.toLocalDate()
		)) {
			throw new BusinessException("BOOKING_PLAN_NOT_ALLOWED", "El socio no tiene una membresia permitida para reservar este recurso.");
		}
		if (!localStart.toLocalDate().equals(localEnd.toLocalDate())) {
			throw new BusinessException("BOOKING_RANGE_INVALID", "La reserva debe iniciar y terminar el mismo dia.");
		}
		ResourceHours hours = resourceScheduleResolver.resolve(resource, localStart.toLocalDate())
				.orElseThrow(() -> new BusinessException("BOOKING_OUTSIDE_RESOURCE_HOURS", "El recurso no opera el dia seleccionado."));
		if (localStart.toLocalTime().isBefore(hours.opensAt()) || localEnd.toLocalTime().isAfter(hours.closesAt())) {
			throw new BusinessException("BOOKING_OUTSIDE_RESOURCE_HOURS", "La reserva esta fuera del horario operativo del recurso para ese dia.");
		}

		if (clientBookingConflictRepository.hasOverlappingBooking(
				principal.organizationId(),
				principal.branchId(),
				command.clientId(),
				command.startsAt(),
				command.endsAt()
		)) {
			throw new BusinessException(
					"BOOKING_CLIENT_CONFLICT",
					"El socio ya tiene otra reserva que coincide con este horario."
			);
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
		auditService.record(principal, "BOOKING_" + targetStatus, "BOOKING", bookingId, "RECEPTION", targetStatus, correlationId);
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
