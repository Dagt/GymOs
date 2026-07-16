package com.gymos.bookings.infrastructure;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRepository {
	private final JdbcClient jdbcClient;

	public BookingRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<ResourceRecord> resources(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT id, name, resource_type, capacity, slot_minutes, status, opens_at, closes_at, notes
				FROM resource
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND deleted_at IS NULL
				ORDER BY name
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(this::mapResource)
				.list();
	}

	public Optional<ResourceRecord> resource(UUID organizationId, UUID branchId, UUID resourceId) {
		return jdbcClient.sql("""
				SELECT id, name, resource_type, capacity, slot_minutes, status, opens_at, closes_at, notes
				FROM resource
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :resourceId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("resourceId", resourceId)
				.query(this::mapResource)
				.optional();
	}

	public UUID createResource(UUID organizationId, UUID branchId, UUID actorUserId, CreateResourceData data) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID resourceId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO resource (
				    id, organization_id, branch_id, name, resource_type, capacity, slot_minutes,
				    status, opens_at, closes_at, notes, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :name, :resourceType, :capacity, :slotMinutes,
				    'ACTIVE', :opensAt, :closesAt, :notes, :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", resourceId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("name", data.name())
				.param("resourceType", data.resourceType())
				.param("capacity", data.capacity())
				.param("slotMinutes", data.slotMinutes())
				.param("opensAt", data.opensAt())
				.param("closesAt", data.closesAt())
				.param("notes", data.notes())
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.update();
		return resourceId;
	}

	public boolean clientExists(UUID organizationId, UUID clientId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM client
				WHERE organization_id = :organizationId
				  AND id = :clientId
				  AND status = 'ACTIVE'
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public int overlappingBookings(UUID organizationId, UUID branchId, UUID resourceId, OffsetDateTime startsAt, OffsetDateTime endsAt) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM booking
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND resource_id = :resourceId
				  AND status IN ('RESERVED', 'CONFIRMED', 'CHECKED_IN')
				  AND starts_at < :endsAt
				  AND ends_at > :startsAt
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("resourceId", resourceId)
				.param("startsAt", startsAt)
				.param("endsAt", endsAt)
				.query(Integer.class)
				.single();
		return count;
	}

	public UUID createBooking(UUID organizationId, UUID branchId, UUID actorUserId, CreateBookingData data) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID bookingId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO booking (
				    id, organization_id, branch_id, resource_id, client_id, status,
				    starts_at, ends_at, notes, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :resourceId, :clientId, 'RESERVED',
				    :startsAt, :endsAt, :notes, :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", bookingId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("resourceId", data.resourceId())
				.param("clientId", data.clientId())
				.param("startsAt", data.startsAt())
				.param("endsAt", data.endsAt())
				.param("notes", data.notes())
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", actorUserId)
				.param("updatedBy", actorUserId)
				.update();
		return bookingId;
	}

	public Optional<BookingRecord> booking(UUID organizationId, UUID branchId, UUID bookingId) {
		return jdbcClient.sql("""
				SELECT b.id, b.resource_id, r.name AS resource_name, b.client_id,
				       p.first_name || ' ' || p.last_name AS client_name,
				       b.status, b.starts_at, b.ends_at, b.notes
				FROM booking b
				JOIN resource r ON r.id = b.resource_id
				JOIN client c ON c.id = b.client_id
				JOIN person p ON p.id = c.person_id
				WHERE b.organization_id = :organizationId
				  AND b.branch_id = :branchId
				  AND b.id = :bookingId
				  AND b.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("bookingId", bookingId)
				.query(this::mapBooking)
				.optional();
	}

	public List<BookingRecord> bookings(UUID organizationId, UUID branchId, LocalDate date, int limit) {
		OffsetDateTime from = date.atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
		OffsetDateTime to = date.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
		return jdbcClient.sql("""
				SELECT b.id, b.resource_id, r.name AS resource_name, b.client_id,
				       p.first_name || ' ' || p.last_name AS client_name,
				       b.status, b.starts_at, b.ends_at, b.notes
				FROM booking b
				JOIN resource r ON r.id = b.resource_id
				JOIN client c ON c.id = b.client_id
				JOIN person p ON p.id = c.person_id
				WHERE b.organization_id = :organizationId
				  AND b.branch_id = :branchId
				  AND b.starts_at >= :from
				  AND b.starts_at < :to
				  AND b.deleted_at IS NULL
				ORDER BY b.starts_at
				LIMIT :limit
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("from", from)
				.param("to", to)
				.param("limit", limit)
				.query(this::mapBooking)
				.list();
	}

	public void updateStatus(UUID organizationId, UUID branchId, UUID bookingId, String status, UUID actorUserId) {
		jdbcClient.sql("""
				UPDATE booking
				SET status = :status,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :bookingId
				  AND deleted_at IS NULL
				""")
				.param("status", status)
				.param("updatedAt", OffsetDateTime.now())
				.param("updatedBy", actorUserId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("bookingId", bookingId)
				.update();
	}

	private ResourceRecord mapResource(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new ResourceRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getString("name"),
				resultSet.getString("resource_type"),
				resultSet.getInt("capacity"),
				resultSet.getInt("slot_minutes"),
				resultSet.getString("status"),
				resultSet.getObject("opens_at", LocalTime.class),
				resultSet.getObject("closes_at", LocalTime.class),
				resultSet.getString("notes")
		);
	}

	private BookingRecord mapBooking(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
		return new BookingRecord(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("resource_id", UUID.class),
				resultSet.getString("resource_name"),
				resultSet.getObject("client_id", UUID.class),
				resultSet.getString("client_name"),
				resultSet.getString("status"),
				resultSet.getObject("starts_at", OffsetDateTime.class),
				resultSet.getObject("ends_at", OffsetDateTime.class),
				resultSet.getString("notes")
		);
	}

	public record CreateResourceData(String name, String resourceType, int capacity, int slotMinutes, LocalTime opensAt, LocalTime closesAt, String notes) {
	}

	public record CreateBookingData(UUID resourceId, UUID clientId, OffsetDateTime startsAt, OffsetDateTime endsAt, String notes) {
	}

	public record ResourceRecord(UUID id, String name, String resourceType, int capacity, int slotMinutes, String status, LocalTime opensAt, LocalTime closesAt, String notes) {
	}

	public record BookingRecord(UUID id, UUID resourceId, String resourceName, UUID clientId, String clientName, String status, OffsetDateTime startsAt, OffsetDateTime endsAt, String notes) {
	}

	public record AvailabilitySlot(OffsetDateTime startsAt, OffsetDateTime endsAt, int reserved, int capacity, boolean available) {
	}
}
