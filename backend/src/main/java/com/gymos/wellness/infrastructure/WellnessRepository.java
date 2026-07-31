package com.gymos.wellness.infrastructure;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class WellnessRepository {
	private final JdbcClient jdbcClient;

	public WellnessRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<ProfessionalRecord> professionals(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT id, display_name, professional_type, email, phone, bio, office_location,
				       engagement_type, status, created_at, updated_at
				FROM professional
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND deleted_at IS NULL
				ORDER BY display_name
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(this::mapProfessional)
				.list();
	}

	public Optional<ProfessionalRecord> professional(UUID organizationId, UUID branchId, UUID professionalId) {
		return jdbcClient.sql("""
				SELECT id, display_name, professional_type, email, phone, bio, office_location,
				       engagement_type, status, created_at, updated_at
				FROM professional
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :professionalId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("professionalId", professionalId)
				.query(this::mapProfessional)
				.optional();
	}

	public UUID createProfessional(UUID organizationId, UUID branchId, UUID actorUserId, CreateProfessionalData data) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID professionalId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO professional (
				    id, organization_id, branch_id, display_name, professional_type, email, phone,
				    bio, office_location, engagement_type, status, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :displayName, :professionalType, :email, :phone,
				    :bio, :officeLocation, :engagementType, 'ACTIVE', :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", professionalId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("displayName", data.displayName())
				.param("professionalType", data.professionalType())
				.param("email", data.email())
				.param("phone", data.phone())
				.param("bio", data.bio())
				.param("officeLocation", data.officeLocation())
				.param("engagementType", data.engagementType())
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.update();
		return professionalId;
	}

	public void updateProfessional(UUID organizationId, UUID branchId, UUID professionalId, UUID actorUserId, UpdateProfessionalData data) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE professional
				SET display_name = :displayName,
				    professional_type = :professionalType,
				    email = :email,
				    phone = :phone,
				    bio = :bio,
				    office_location = :officeLocation,
				    engagement_type = :engagementType,
				    status = :status,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :professionalId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("professionalId", professionalId)
				.param("displayName", data.displayName())
				.param("professionalType", data.professionalType())
				.param("email", data.email())
				.param("phone", data.phone())
				.param("bio", data.bio())
				.param("officeLocation", data.officeLocation())
				.param("engagementType", data.engagementType())
				.param("status", data.status())
				.param("updatedAt", now)
				.param("updatedBy", actorUserId)
				.update();
	}

	public void deleteProfessional(UUID organizationId, UUID branchId, UUID professionalId) {
		OffsetDateTime now = OffsetDateTime.now();

		jdbcClient.sql("""
				UPDATE professional
				SET deleted_at = :now, updated_at = :now
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :professionalId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("professionalId", professionalId)
				.param("now", now)
				.update();

		jdbcClient.sql("""
				UPDATE professional_service
				SET deleted_at = :now, updated_at = :now
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND professional_id = :professionalId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("professionalId", professionalId)
				.param("now", now)
				.update();

		jdbcClient.sql("""
				UPDATE professional_availability
				SET deleted_at = :now, updated_at = :now
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND professional_id = :professionalId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("professionalId", professionalId)
				.param("now", now)
				.update();

		jdbcClient.sql("""
				UPDATE wellness_appointment
				SET deleted_at = :now,
				    cancellation_reason = 'Profesional eliminado del sistema',
				    updated_at = :now
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND professional_id = :professionalId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("professionalId", professionalId)
				.param("now", now)
				.update();
	}

	public List<ProfessionalServiceRecord> services(UUID organizationId, UUID branchId, UUID professionalId) {
		return jdbcClient.sql("""
				SELECT id, professional_id, name, service_type, duration_minutes, price_cents,
				       currency, status, created_at, updated_at
				FROM professional_service
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND professional_id = :professionalId
				  AND deleted_at IS NULL
				ORDER BY name
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("professionalId", professionalId)
				.query(this::mapService)
				.list();
	}

	public Optional<ProfessionalServiceRecord> service(UUID organizationId, UUID branchId, UUID professionalId, UUID serviceId) {
		return jdbcClient.sql("""
				SELECT id, professional_id, name, service_type, duration_minutes, price_cents,
				       currency, status, created_at, updated_at
				FROM professional_service
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND professional_id = :professionalId
				  AND id = :serviceId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("professionalId", professionalId)
				.param("serviceId", serviceId)
				.query(this::mapService)
				.optional();
	}

	public UUID createService(UUID organizationId, UUID branchId, UUID professionalId, UUID actorUserId, CreateServiceData data) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID serviceId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO professional_service (
				    id, organization_id, branch_id, professional_id, name, service_type,
				    duration_minutes, price_cents, currency, status, created_at, updated_at,
				    created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :professionalId, :name, :serviceType,
				    :durationMinutes, :priceCents, :currency, 'ACTIVE', :createdAt, :updatedAt,
				    :createdBy, :updatedBy, 0
				)
				""")
				.param("id", serviceId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("professionalId", professionalId)
				.param("name", data.name())
				.param("serviceType", data.serviceType())
				.param("durationMinutes", data.durationMinutes())
				.param("priceCents", data.priceCents())
				.param("currency", data.currency())
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.update();
		return serviceId;
	}

	public void updateService(UUID organizationId, UUID branchId, UUID professionalId, UUID serviceId, UUID actorUserId, UpdateServiceData data) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE professional_service
				SET name = :name,
				    service_type = :serviceType,
				    duration_minutes = :durationMinutes,
				    price_cents = :priceCents,
				    currency = :currency,
				    status = :status,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND professional_id = :professionalId
				  AND id = :serviceId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("professionalId", professionalId)
				.param("serviceId", serviceId)
				.param("name", data.name())
				.param("serviceType", data.serviceType())
				.param("durationMinutes", data.durationMinutes())
				.param("priceCents", data.priceCents())
				.param("currency", data.currency())
				.param("status", data.status())
				.param("updatedAt", now)
				.param("updatedBy", actorUserId)
				.update();
	}

	public List<AvailabilityRecord> availability(UUID organizationId, UUID branchId, UUID professionalId) {
		return jdbcClient.sql("""
				SELECT id, professional_id, day_of_week, opens_at, closes_at, status
				FROM professional_availability
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND professional_id = :professionalId
				  AND deleted_at IS NULL
				ORDER BY day_of_week, opens_at
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("professionalId", professionalId)
				.query(this::mapAvailability)
				.list();
	}

	public void deleteAvailabilityForDay(UUID organizationId, UUID branchId, UUID professionalId, int dayOfWeek) {
		jdbcClient.sql("""
				UPDATE professional_availability
				SET deleted_at = NOW()
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND professional_id = :professionalId
				  AND day_of_week = :dayOfWeek
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("professionalId", professionalId)
				.param("dayOfWeek", dayOfWeek)
				.update();
	}

	public UUID createAvailability(UUID organizationId, UUID branchId, UUID professionalId, UUID actorUserId, CreateAvailabilityData data) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID availabilityId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO professional_availability (
				    id, organization_id, branch_id, professional_id, day_of_week, opens_at, closes_at,
				    status, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :professionalId, :dayOfWeek, :opensAt, :closesAt,
				    'ACTIVE', :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", availabilityId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("professionalId", professionalId)
				.param("dayOfWeek", data.dayOfWeek())
				.param("opensAt", data.opensAt())
				.param("closesAt", data.closesAt())
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.update();
		return availabilityId;
	}

	public UUID createAppointment(UUID organizationId, UUID branchId, UUID actorUserId, CreateAppointmentData data) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID appointmentId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO wellness_appointment (
				    id, organization_id, branch_id, professional_id, service_id, client_id,
				    starts_at, ends_at, status, notes, confirmed_by_user_id, confirmed_at,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :professionalId, :serviceId, :clientId,
				    :startsAt, :endsAt, :status, :notes, :confirmedByUserId, :confirmedAt,
				    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", appointmentId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("professionalId", data.professionalId())
				.param("serviceId", data.serviceId())
				.param("clientId", data.clientId())
				.param("startsAt", data.startsAt())
				.param("endsAt", data.endsAt())
				.param("status", data.status())
				.param("notes", data.notes())
				.param("confirmedByUserId", data.confirmedByUserId())
				.param("confirmedAt", data.confirmedAt())
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.update();
		return appointmentId;
	}

	public List<AppointmentRecord> appointments(UUID organizationId, UUID branchId, LocalDate date, UUID professionalId, String status) {
		StringBuilder sql = new StringBuilder("""
				SELECT a.id, a.organization_id, a.branch_id, a.professional_id,
				       p.display_name AS professional_name, p.professional_type, p.office_location,
				       p.engagement_type AS professional_engagement_type,
				       a.service_id, ps.name AS service_name, ps.duration_minutes, ps.price_cents,
				       a.client_id, (cp.first_name || ' ' || cp.last_name) AS client_full_name, cp.email AS client_email, cp.phone AS client_phone,
				       a.starts_at, a.ends_at, a.status, a.notes, a.cancellation_reason, a.confirmed_by_user_id, a.confirmed_at,
				       a.created_at, a.updated_at
				FROM wellness_appointment a
				JOIN professional p ON p.id = a.professional_id
				LEFT JOIN professional_service ps ON ps.id = a.service_id
				JOIN client c ON c.id = a.client_id
				JOIN person cp ON cp.id = c.person_id
				WHERE a.organization_id = :organizationId
				  AND a.branch_id = :branchId
				  AND a.deleted_at IS NULL
				""");

		if (date != null) {
			sql.append(" AND DATE(a.starts_at AT TIME ZONE 'UTC') = :date");
		}
		if (professionalId != null) {
			sql.append(" AND a.professional_id = :professionalId");
		}
		if (status != null && !status.isBlank()) {
			sql.append(" AND a.status = :status");
		}
		sql.append(" ORDER BY a.starts_at ASC");

		var query = jdbcClient.sql(sql.toString())
				.param("organizationId", organizationId)
				.param("branchId", branchId);

		if (date != null) {
			query = query.param("date", date);
		}
		if (professionalId != null) {
			query = query.param("professionalId", professionalId);
		}
		if (status != null && !status.isBlank()) {
			query = query.param("status", status.trim().toUpperCase());
		}

		return query.query(this::mapAppointment).list();
	}

	public Optional<AppointmentRecord> appointment(UUID organizationId, UUID branchId, UUID appointmentId) {
		return jdbcClient.sql("""
				SELECT a.id, a.organization_id, a.branch_id, a.professional_id,
				       p.display_name AS professional_name, p.professional_type, p.office_location,
				       p.engagement_type AS professional_engagement_type,
				       a.service_id, ps.name AS service_name, ps.duration_minutes, ps.price_cents,
				       a.client_id, (cp.first_name || ' ' || cp.last_name) AS client_full_name, cp.email AS client_email, cp.phone AS client_phone,
				       a.starts_at, a.ends_at, a.status, a.notes, a.cancellation_reason, a.confirmed_by_user_id, a.confirmed_at,
				       a.created_at, a.updated_at
				FROM wellness_appointment a
				JOIN professional p ON p.id = a.professional_id
				LEFT JOIN professional_service ps ON ps.id = a.service_id
				JOIN client c ON c.id = a.client_id
				JOIN person cp ON cp.id = c.person_id
				WHERE a.organization_id = :organizationId
				  AND a.branch_id = :branchId
				  AND a.id = :appointmentId
				  AND a.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("appointmentId", appointmentId)
				.query(this::mapAppointment)
				.optional();
	}

	public int overlappingAppointmentsCount(UUID organizationId, UUID branchId, UUID professionalId, OffsetDateTime startsAt, OffsetDateTime endsAt) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM wellness_appointment
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND professional_id = :professionalId
				  AND status IN ('REQUESTED', 'CONFIRMED')
				  AND deleted_at IS NULL
				  AND starts_at < :endsAt
				  AND ends_at > :startsAt
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("professionalId", professionalId)
				.param("startsAt", startsAt)
				.param("endsAt", endsAt)
				.query(Integer.class)
				.single();
		return count == null ? 0 : count;
	}

	public void updateAppointmentScheduleAndStatus(
			UUID organizationId, UUID branchId, UUID appointmentId,
			OffsetDateTime startsAt, OffsetDateTime endsAt, String notes,
			String nextStatus, UUID actorUserId
	) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE wellness_appointment
				SET starts_at = :startsAt,
				    ends_at = :endsAt,
				    notes = COALESCE(:notes, notes),
				    status = :nextStatus,
				    confirmed_by_user_id = :actorUserId,
				    confirmed_at = :now,
				    updated_at = :now,
				    updated_by = :actorUserId,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :appointmentId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("appointmentId", appointmentId)
				.param("startsAt", startsAt)
				.param("endsAt", endsAt)
				.param("notes", notes)
				.param("nextStatus", nextStatus)
				.param("actorUserId", actorUserId)
				.param("now", now)
				.update();
	}

	public void updateAppointmentStatus(UUID organizationId, UUID branchId, UUID appointmentId, String nextStatus, String cancellationReason, UUID actorUserId) {
		OffsetDateTime now = OffsetDateTime.now();
		OffsetDateTime confirmedAt = "CONFIRMED".equals(nextStatus) ? now : null;
		UUID confirmedBy = "CONFIRMED".equals(nextStatus) ? actorUserId : null;

		jdbcClient.sql("""
				UPDATE wellness_appointment
				SET status = :nextStatus,
				    cancellation_reason = COALESCE(:cancellationReason, cancellation_reason),
				    confirmed_by_user_id = COALESCE(:confirmedBy, confirmed_by_user_id),
				    confirmed_at = COALESCE(:confirmedAt, confirmed_at),
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :appointmentId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("appointmentId", appointmentId)
				.param("nextStatus", nextStatus)
				.param("cancellationReason", cancellationReason)
				.param("confirmedBy", confirmedBy)
				.param("confirmedAt", confirmedAt)
				.param("updatedAt", now)
				.param("updatedBy", actorUserId)
				.update();
	}

	private ProfessionalRecord mapProfessional(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new ProfessionalRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getString("display_name"),
				resultSet.getString("professional_type"),
				resultSet.getString("email"),
				resultSet.getString("phone"),
				resultSet.getString("bio"),
				resultSet.getString("office_location"),
				resultSet.getString("engagement_type"),
				resultSet.getString("status"),
				resultSet.getObject("created_at", OffsetDateTime.class),
				resultSet.getObject("updated_at", OffsetDateTime.class)
		);
	}

	private ProfessionalServiceRecord mapService(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new ProfessionalServiceRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("professional_id", UUID.class),
				resultSet.getString("name"),
				resultSet.getString("service_type"),
				resultSet.getInt("duration_minutes"),
				resultSet.getLong("price_cents"),
				resultSet.getString("currency"),
				resultSet.getString("status"),
				resultSet.getObject("created_at", OffsetDateTime.class),
				resultSet.getObject("updated_at", OffsetDateTime.class)
		);
	}

	private AvailabilityRecord mapAvailability(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new AvailabilityRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("professional_id", UUID.class),
				resultSet.getInt("day_of_week"),
				resultSet.getObject("opens_at", LocalTime.class),
				resultSet.getObject("closes_at", LocalTime.class),
				resultSet.getString("status")
		);
	}

	private AppointmentRecord mapAppointment(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new AppointmentRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("organization_id", UUID.class),
				resultSet.getObject("branch_id", UUID.class),
				resultSet.getObject("professional_id", UUID.class),
				resultSet.getString("professional_name"),
				resultSet.getString("professional_type"),
				resultSet.getString("office_location"),
				resultSet.getString("professional_engagement_type"),
				resultSet.getObject("service_id", UUID.class),
				resultSet.getString("service_name"),
				resultSet.getInt("duration_minutes"),
				resultSet.getLong("price_cents"),
				resultSet.getObject("client_id", UUID.class),
				resultSet.getString("client_full_name"),
				resultSet.getString("client_email"),
				resultSet.getString("client_phone"),
				resultSet.getObject("starts_at", OffsetDateTime.class),
				resultSet.getObject("ends_at", OffsetDateTime.class),
				resultSet.getString("status"),
				resultSet.getString("notes"),
				resultSet.getString("cancellation_reason"),
				resultSet.getObject("confirmed_by_user_id", UUID.class),
				resultSet.getObject("confirmed_at", OffsetDateTime.class),
				resultSet.getObject("created_at", OffsetDateTime.class),
				resultSet.getObject("updated_at", OffsetDateTime.class)
		);
	}

	public record CreateProfessionalData(String displayName, String professionalType, String email, String phone, String bio, String officeLocation, String engagementType) {
	}

	public record UpdateProfessionalData(String displayName, String professionalType, String email, String phone, String bio, String officeLocation, String engagementType, String status) {
	}

	public record CreateServiceData(String name, String serviceType, int durationMinutes, long priceCents, String currency) {
	}

	public record UpdateServiceData(String name, String serviceType, int durationMinutes, long priceCents, String currency, String status) {
	}

	public record CreateAvailabilityData(int dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
	}

	public record CreateAppointmentData(UUID professionalId, UUID serviceId, UUID clientId, OffsetDateTime startsAt, OffsetDateTime endsAt, String status, String notes, UUID confirmedByUserId, OffsetDateTime confirmedAt) {
	}

	public record ProfessionalRecord(UUID id, String displayName, String professionalType, String email, String phone, String bio, String officeLocation, String engagementType, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	public record ProfessionalServiceRecord(UUID id, UUID professionalId, String name, String serviceType, int durationMinutes, long priceCents, String currency, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	public record AvailabilityRecord(UUID id, UUID professionalId, int dayOfWeek, LocalTime opensAt, LocalTime closesAt, String status) {
	}

	public record AppointmentRecord(
			UUID id,
			UUID organizationId,
			UUID branchId,
			UUID professionalId,
			String professionalName,
			String professionalType,
			String officeLocation,
			String professionalEngagementType,
			UUID serviceId,
			String serviceName,
			int durationMinutes,
			long priceCents,
			UUID clientId,
			String clientFullName,
			String clientEmail,
			String clientPhone,
			OffsetDateTime startsAt,
			OffsetDateTime endsAt,
			String status,
			String notes,
			String cancellationReason,
			UUID confirmedByUserId,
			OffsetDateTime confirmedAt,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt
	) {
	}
}
