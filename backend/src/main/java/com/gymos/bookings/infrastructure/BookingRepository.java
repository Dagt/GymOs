package com.gymos.bookings.infrastructure;

import java.time.LocalDate;
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

	public boolean upsertBookingPayment(UUID organizationId, UUID branchId, UUID bookingId, UUID saleId, String status, OffsetDateTime expiresAt) {
		OffsetDateTime now = OffsetDateTime.now();
		return jdbcClient.sql("""
				INSERT INTO booking_payment (
				    id, organization_id, branch_id, booking_id, sale_id, status, expires_at, created_at, updated_at, version
				)
				SELECT :id, b.organization_id, b.branch_id, b.id, s.id, :status, :expiresAt, :createdAt, :updatedAt, 0
				FROM booking b
				JOIN sale s ON s.organization_id = b.organization_id AND s.branch_id = b.branch_id
				 AND s.client_id = b.client_id AND s.id = :saleId AND s.deleted_at IS NULL
				WHERE b.organization_id = :organizationId AND b.branch_id = :branchId
				  AND b.id = :bookingId AND b.deleted_at IS NULL
				ON CONFLICT (organization_id, booking_id)
				DO UPDATE SET
				    branch_id = EXCLUDED.branch_id,
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
				.update() == 1;
	}

	private String bookingSelect() {
		return """
				SELECT b.id, b.resource_id, r.name AS resource_name, b.client_id,
				       p.first_name || ' ' || p.last_name AS client_name,
				       b.status, b.starts_at, b.ends_at, b.notes,
				       bp.id AS booking_payment_id, bp.sale_id, bp.status AS payment_status, s.sale_number
				FROM booking b
				JOIN resource r ON r.id = b.resource_id AND r.organization_id = b.organization_id AND r.branch_id = b.branch_id
				JOIN client c ON c.id = b.client_id AND c.organization_id = b.organization_id
				JOIN person p ON p.id = c.person_id AND p.organization_id = b.organization_id
				LEFT JOIN (booking_payment bp
				    JOIN sale s ON s.id = bp.sale_id AND s.organization_id = bp.organization_id
				     AND s.branch_id = bp.branch_id AND s.deleted_at IS NULL)
				  ON bp.booking_id = b.id AND bp.organization_id = b.organization_id AND bp.branch_id = b.branch_id
				  AND s.client_id = b.client_id
				""";
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

	public record CreateBookingData(UUID resourceId, UUID clientId, OffsetDateTime startsAt, OffsetDateTime endsAt, String notes) {
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
