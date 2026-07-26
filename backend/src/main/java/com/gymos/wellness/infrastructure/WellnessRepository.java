package com.gymos.wellness.infrastructure;

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
				SELECT id, display_name, professional_type, email, phone, bio, status, created_at, updated_at
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
				SELECT id, display_name, professional_type, email, phone, bio, status, created_at, updated_at
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
				    bio, status, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :displayName, :professionalType, :email, :phone,
				    :bio, 'ACTIVE', :createdAt, :updatedAt, :createdBy, :updatedBy, 0
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
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.update();
		return professionalId;
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

	private ProfessionalRecord mapProfessional(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new ProfessionalRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getString("display_name"),
				resultSet.getString("professional_type"),
				resultSet.getString("email"),
				resultSet.getString("phone"),
				resultSet.getString("bio"),
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

	public record CreateProfessionalData(String displayName, String professionalType, String email, String phone, String bio) {
	}

	public record CreateServiceData(String name, String serviceType, int durationMinutes, long priceCents, String currency) {
	}

	public record CreateAvailabilityData(int dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
	}

	public record ProfessionalRecord(UUID id, String displayName, String professionalType, String email, String phone, String bio, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	public record ProfessionalServiceRecord(UUID id, UUID professionalId, String name, String serviceType, int durationMinutes, long priceCents, String currency, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
	}

	public record AvailabilityRecord(UUID id, UUID professionalId, int dayOfWeek, LocalTime opensAt, LocalTime closesAt, String status) {
	}
}
