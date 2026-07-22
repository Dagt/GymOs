package com.gymos.bookings.infrastructure;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
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
				SELECT r.id, r.name, r.resource_type, r.capacity, r.slot_minutes, r.status,
				       r.opens_at, r.closes_at, r.notes,
				       COALESCE(string_agg(rap.plan_id::text, ',' ORDER BY cp.name) FILTER (WHERE rap.plan_id IS NOT NULL), '') AS allowed_plan_ids,
				       COALESCE(string_agg(cp.name, ', ' ORDER BY cp.name) FILTER (WHERE rap.plan_id IS NOT NULL), '') AS allowed_plan_names
				FROM resource r
				LEFT JOIN resource_allowed_membership_plan rap ON rap.resource_id = r.id
				LEFT JOIN membership_plan mp ON mp.id = rap.plan_id AND mp.deleted_at IS NULL
				LEFT JOIN catalog_product cp ON cp.id = mp.product_id AND cp.deleted_at IS NULL
				WHERE r.organization_id = :organizationId
				  AND r.branch_id = :branchId
				  AND r.deleted_at IS NULL
				GROUP BY r.id
				ORDER BY r.name
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(this::mapResource)
				.list();
	}

	public Optional<ResourceRecord> resource(UUID organizationId, UUID branchId, UUID resourceId) {
		return jdbcClient.sql("""
				SELECT r.id, r.name, r.resource_type, r.capacity, r.slot_minutes, r.status,
				       r.opens_at, r.closes_at, r.notes,
				       COALESCE(string_agg(rap.plan_id::text, ',' ORDER BY cp.name) FILTER (WHERE rap.plan_id IS NOT NULL), '') AS allowed_plan_ids,
				       COALESCE(string_agg(cp.name, ', ' ORDER BY cp.name) FILTER (WHERE rap.plan_id IS NOT NULL), '') AS allowed_plan_names
				FROM resource r
				LEFT JOIN resource_allowed_membership_plan rap ON rap.resource_id = r.id
				LEFT JOIN membership_plan mp ON mp.id = rap.plan_id AND mp.deleted_at IS NULL
				LEFT JOIN catalog_product cp ON cp.id = mp.product_id AND cp.deleted_at IS NULL
				WHERE r.organization_id = :organizationId
				  AND r.branch_id = :branchId
				  AND r.id = :resourceId
				  AND r.deleted_at IS NULL
				GROUP BY r.id
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

	public void replaceAllowedPlans(UUID organizationId, UUID resourceId, List<UUID> planIds) {
		jdbcClient.sql("""
				DELETE FROM resource_allowed_membership_plan
				WHERE organization_id = :organizationId
				  AND resource_id = :resourceId
				""")
				.param("organizationId", organizationId)
				.param("resourceId", resourceId)
				.update();
		if (planIds == null || planIds.isEmpty()) {
			return;
		}
		for (UUID planId : planIds) {
			jdbcClient.sql("""
					INSERT INTO resource_allowed_membership_plan (resource_id, plan_id, organization_id, created_at)
					VALUES (:resourceId, :planId, :organizationId, :createdAt)
					ON CONFLICT DO NOTHING
					""")
					.param("resourceId", resourceId)
					.param("planId", planId)
					.param("organizationId", organizationId)
					.param("createdAt", OffsetDateTime.now())
					.update();
		}
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
		return jdbcClient.sql(bookingSelect() + """
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
		return jdbcClient.sql(bookingSelect() + """
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

	public boolean saleExists(UUID organizationId, UUID branchId, UUID saleId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM sale
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :saleId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("saleId", saleId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public void upsertBookingPayment(UUID organizationId, UUID branchId, UUID bookingId, UUID saleId, String status, OffsetDateTime expiresAt) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				INSERT INTO booking_payment (
				    id, organization_id, branch_id, booking_id, sale_id, status, expires_at, created_at, updated_at, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :bookingId, :saleId, :status, :expiresAt, :createdAt, :updatedAt, 0
				)
				ON CONFLICT (organization_id, booking_id)
				DO UPDATE SET
				    sale_id = EXCLUDED.sale_id,
				    status = EXCLUDED.status,
				    expires_at = EXCLUDED.expires_at,
				    updated_at = EXCLUDED.updated_at,
				    version = booking_payment.version + 1
				""")
				.param("id", UUID.randomUUID())
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("bookingId", bookingId)
				.param("saleId", saleId)
				.param("status", status)
				.param("expiresAt", expiresAt)
				.param("createdAt", now)
				.param("updatedAt", now)
				.update();
	}

	private String bookingSelect() {
		return """
				SELECT b.id, b.resource_id, r.name AS resource_name, b.client_id,
				       p.first_name || ' ' || p.last_name AS client_name,
				       b.status, b.starts_at, b.ends_at, b.notes,
				       bp.id AS booking_payment_id, bp.sale_id, bp.status AS payment_status, s.sale_number
				FROM booking b
				JOIN resource r ON r.id = b.resource_id
				JOIN client c ON c.id = b.client_id
				JOIN person p ON p.id = c.person_id
				LEFT JOIN booking_payment bp ON bp.booking_id = b.id AND bp.organization_id = b.organization_id
				LEFT JOIN sale s ON s.id = bp.sale_id
				""";
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
				resultSet.getString("notes"),
				parseUuidList(resultSet.getString("allowed_plan_ids")),
				resultSet.getString("allowed_plan_names")
		);
	}

	private List<UUID> parseUuidList(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		return Arrays.stream(value.split(","))
				.map(String::trim)
				.filter(item -> !item.isBlank())
				.map(UUID::fromString)
				.toList();
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
				resultSet.getString("notes"),
				resultSet.getObject("booking_payment_id", UUID.class),
				resultSet.getObject("sale_id", UUID.class),
				resultSet.getString("payment_status"),
				resultSet.getString("sale_number")
		);
	}

	public record CreateResourceData(String name, String resourceType, int capacity, int slotMinutes, LocalTime opensAt, LocalTime closesAt, String notes) {
	}

	public record CreateBookingData(UUID resourceId, UUID clientId, OffsetDateTime startsAt, OffsetDateTime endsAt, String notes) {
	}

	public record ResourceRecord(UUID id, String name, String resourceType, int capacity, int slotMinutes, String status, LocalTime opensAt, LocalTime closesAt, String notes, List<UUID> allowedPlanIds, String allowedPlanNames) {
	}

	public record BookingRecord(
			UUID id,
			UUID resourceId,
			String resourceName,
			UUID clientId,
			String clientName,
			String status,
			OffsetDateTime startsAt,
			OffsetDateTime endsAt,
			String notes,
			UUID bookingPaymentId,
			UUID saleId,
			String paymentStatus,
			String saleNumber
	) {
	}

	public record AvailabilitySlot(OffsetDateTime startsAt, OffsetDateTime endsAt, int reserved, int capacity, boolean available) {
	}
}
