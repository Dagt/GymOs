package com.gymos.bookings.application;

import com.gymos.audit.api.AuditService;
import com.gymos.bookings.api.events.BookingCreatedV1;
import com.gymos.bookings.api.events.BookingStatusChangedV1;
import com.gymos.bookings.application.ResourceScheduleResolver.ResourceHours;
import com.gymos.bookings.application.command.CreateBookingCommand;
import com.gymos.bookings.application.command.CreateResourceCommand;
import com.gymos.bookings.application.command.LinkBookingPaymentCommand;
import com.gymos.bookings.infrastructure.BookingPlanRepository;
import com.gymos.bookings.infrastructure.BookingResourceRepository;
import com.gymos.bookings.infrastructure.BookingResourceRepository.ResourceData;
import com.gymos.bookings.infrastructure.BookingResourceRepository.ResourceRecord;
import com.gymos.bookings.infrastructure.BookingRepository;
import com.gymos.bookings.infrastructure.BookingRepository.AvailabilitySlot;
import com.gymos.bookings.infrastructure.BookingRepository.BookingRecord;
import com.gymos.bookings.infrastructure.BookingRepository.CreateBookingData;
import com.gymos.bookings.infrastructure.ClientBookingConflictRepository;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.time.BusinessTime;
import com.gymos.eligibility.api.EligibilityPolicy;
import com.gymos.eligibility.api.EligibilityPolicy.EligibilityDecision;
import com.gymos.eligibility.api.EligibilityPolicy.EligibilityRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

@Service
public class BookingService {
	private static final Set<String> ACTIVE_STATUSES = Set.of("RESERVED", "CONFIRMED", "CHECKED_IN");

	private final BookingRepository bookingRepository;
	private final BookingResourceRepository bookingResourceRepository;
	private final BookingPlanRepository bookingPlanRepository;
	private final EligibilityPolicy eligibilityPolicy;
	private final ClientBookingConflictRepository clientBookingConflictRepository;
	private final ResourceScheduleResolver resourceScheduleResolver;
	private final AuditService auditService;
	private final ApplicationEventPublisher eventPublisher;

	public BookingService(
			BookingRepository bookingRepository,
			BookingResourceRepository bookingResourceRepository,
			BookingPlanRepository bookingPlanRepository,
			EligibilityPolicy eligibilityPolicy,
			ClientBookingConflictRepository clientBookingConflictRepository,
			ResourceScheduleResolver resourceScheduleResolver,
			AuditService auditService,
			ApplicationEventPublisher eventPublisher
	) {
		this.bookingRepository = bookingRepository;
		this.bookingResourceRepository = bookingResourceRepository;
		this.bookingPlanRepository = bookingPlanRepository;
		this.eligibilityPolicy = eligibilityPolicy;
		this.clientBookingConflictRepository = clientBookingConflictRepository;
		this.resourceScheduleResolver = resourceScheduleResolver;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
	}

	@Transactional(readOnly = true)
	public List<ResourceRecord> resources(GymOsPrincipal principal) {
		requireBranch(principal);
		return bookingResourceRepository.resources(principal.organizationId(), principal.branchId());
	}

	@Transactional
	public ResourceRecord createResource(GymOsPrincipal principal, CreateResourceCommand command, String correlationId) {
		requireBranch(principal);
		ResourceMutation mutation = resourceMutation(principal, command);
		UUID resourceId = bookingResourceRepository.createResource(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				mutation.data()
		);
		bookingResourceRepository.replaceAllowedPlans(principal.organizationId(), resourceId, mutation.allowedPlanIds());
		auditService.record(principal, "RESOURCE_CREATED", "RESOURCE", resourceId, "RECEPTION", null, correlationId);
		return bookingResourceRepository.resource(principal.organizationId(), principal.branchId(), resourceId).orElseThrow();
	}

	@Transactional
	public ResourceRecord updateResource(GymOsPrincipal principal, UUID resourceId, CreateResourceCommand command, String correlationId) {
		requireBranch(principal);
		activeResource(principal, resourceId);
		ResourceMutation mutation = resourceMutation(principal, command);
		bookingResourceRepository.updateResource(principal.organizationId(), principal.branchId(), resourceId, principal.userId(), mutation.data());
		bookingResourceRepository.replaceAllowedPlans(principal.organizationId(), resourceId, mutation.allowedPlanIds());
		auditService.record(principal, "RESOURCE_UPDATED", "RESOURCE", resourceId, "RECEPTION", null, correlationId);
		return activeResource(principal, resourceId);
	}

	@Transactional
	public void deleteResource(GymOsPrincipal principal, UUID resourceId, String correlationId) {
		requireBranch(principal);
		activeResource(principal, resourceId);
		if (bookingResourceRepository.activeBookingCount(principal.organizationId(), principal.branchId(), resourceId) > 0) {
			throw new BusinessException("RESOURCE_HAS_ACTIVE_BOOKINGS", "No puedes eliminar un recurso con reservas vigentes.");
		}
		bookingResourceRepository.replaceAllowedPlans(principal.organizationId(), resourceId, List.of());
		bookingResourceRepository.deleteResource(principal.organizationId(), principal.branchId(), resourceId, principal.userId());
		auditService.record(principal, "RESOURCE_DELETED", "RESOURCE", resourceId, "RECEPTION", null, correlationId);
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
		LocalDateTime cursor = resolvedDate.atTime(hours.get().opensAt());
		LocalDateTime closesAt = resolvedDate.atTime(hours.get().closesAt());
		while (!cursor.plusMinutes(resource.slotMinutes()).isAfter(closesAt)) {
			OffsetDateTime startsAt = cursor.atZone(BusinessTime.DEFAULT_ZONE).toOffsetDateTime();
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
		if (command.startsAt() == null || command.endsAt() == null || !command.endsAt().isAfter(command.startsAt())) {
			throw new BusinessException("BOOKING_RANGE_INVALID", "La reserva requiere un rango valido.");
		}
		if (!command.startsAt().isAfter(OffsetDateTime.now())) {
			throw new BusinessException("BOOKING_START_IN_PAST", "No puedes crear una reserva en una hora que ya paso.");
		}

		OffsetDateTime localStart = command.startsAt().atZoneSameInstant(BusinessTime.DEFAULT_ZONE).toOffsetDateTime();
		OffsetDateTime localEnd = command.endsAt().atZoneSameInstant(BusinessTime.DEFAULT_ZONE).toOffsetDateTime();
		EligibilityDecision eligibility = eligibilityPolicy.evaluate(EligibilityRequest.booking(
				principal.organizationId(),
				principal.branchId(),
				command.clientId(),
				localStart.toLocalDate(),
				new LinkedHashSet<>(resource.allowedPlanIds())
		));
		if (!eligibility.eligible()) {
			throw bookingEligibilityException(eligibility);
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
		BookingRecord created = detail(principal, bookingId);
		OffsetDateTime occurredAt = OffsetDateTime.now();
		eventPublisher.publishEvent(new BookingCreatedV1(
				"booking-created:" + bookingId,
				principal.organizationId(), principal.branchId(), bookingId,
				created.clientId(), created.resourceId(), created.startsAt(), created.endsAt(), occurredAt
		));
		return created;
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
		BookingRecord updated = detail(principal, bookingId);
		publishStatusChanged(principal, current, updated);
		return updated;
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
		BookingRecord updated = detail(principal, bookingId);
		if (!current.status().equals(updated.status())) {
			publishStatusChanged(principal, current, updated);
		}
		return updated;
	}

	private void publishStatusChanged(GymOsPrincipal principal, BookingRecord previous, BookingRecord updated) {
		OffsetDateTime occurredAt = OffsetDateTime.now();
		eventPublisher.publishEvent(new BookingStatusChangedV1(
				"booking-status:" + updated.id() + ":" + updated.status(),
				principal.organizationId(), principal.branchId(), updated.id(),
				updated.clientId(), updated.resourceId(), previous.status(), updated.status(),
				updated.startsAt(), updated.endsAt(), occurredAt
		));
	}

	private ResourceRecord activeResource(GymOsPrincipal principal, UUID resourceId) {
		ResourceRecord resource = bookingResourceRepository.resource(principal.organizationId(), principal.branchId(), resourceId)
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

	private ResourceMutation resourceMutation(GymOsPrincipal principal, CreateResourceCommand command) {
		LocalTime opensAt = command.opensAt() == null ? LocalTime.of(6, 0) : command.opensAt();
		LocalTime closesAt = command.closesAt() == null ? LocalTime.of(22, 0) : command.closesAt();
		if (!closesAt.isAfter(opensAt)) {
			throw new BusinessException("RESOURCE_HOURS_INVALID", "La hora de cierre debe ser posterior a la apertura.");
		}
		int capacity = Math.max(1, command.capacity());
		int slotMinutes = Math.max(15, command.slotMinutes());
		List<UUID> allowedPlanIds = command.allowedPlanIds() == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(command.allowedPlanIds()));
		if (!allowedPlanIds.isEmpty() && bookingPlanRepository.activePlanCount(principal.organizationId(), allowedPlanIds) != allowedPlanIds.size()) {
			throw new BusinessException("RESOURCE_ALLOWED_PLAN_INVALID", "Selecciona solo planes activos del gimnasio.");
		}
		ResourceData data = new ResourceData(
				command.name().trim(),
				command.resourceType().trim().toUpperCase(),
				capacity,
				slotMinutes,
				opensAt,
				closesAt,
				blankToNull(command.notes())
		);
		return new ResourceMutation(data, allowedPlanIds);
	}

	private record ResourceMutation(ResourceData data, List<UUID> allowedPlanIds) {
	}

	private BusinessException bookingEligibilityException(EligibilityDecision eligibility) {
		return switch (eligibility.reason()) {
			case CLIENT_NOT_FOUND -> new BusinessException(
					"CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible.");
			case CLIENT_NOT_ACTIVE -> new BusinessException(
					"CLIENT_NOT_ACTIVE", "El cliente no esta activo.");
			case ACTIVE_MEMBERSHIP_REQUIRED, PLAN_NOT_ALLOWED -> new BusinessException(
					"BOOKING_PLAN_NOT_ALLOWED",
					"El socio no tiene una membresia permitida para reservar este recurso.");
			default -> new BusinessException(
					"BOOKING_NOT_ELIGIBLE", "El socio no cumple la politica de elegibilidad para reservar.");
		};
	}
}
