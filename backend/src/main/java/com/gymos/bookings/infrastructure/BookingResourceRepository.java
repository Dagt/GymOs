package com.gymos.bookings.infrastructure;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BookingResourceRepository {
	private final JdbcClient jdbcClient;

	public BookingResourceRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<ResourceRecord> resources(UUID organizationId, UUID branchId) {
		return jdbcClient.sql(resourceSelect() + """
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
		return jdbcClient.sql(resourceSelect() + """
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

	public UUID createResource(UUID organizationId, UUID branchId, UUID actorUserId, ResourceData data) {
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

	public void updateResource(UUID organizationId, UUID branchId, UUID resourceId, UUID actorUserId, ResourceData data) {
		jdbcClient.sql("""
				UPDATE resource
				SET name = :name,
				    resource_type = :resourceType,
				    capacity = :capacity,
				    slot_minutes = :slotMinutes,
				    opens_at = :opensAt,
				    closes_at = :closesAt,
				    notes = :notes,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :resourceId
				  AND deleted_at IS NULL
				""")
				.param("name", data.name())
				.param("resourceType", data.resourceType())
				.param("capacity", data.capacity())
				.param("slotMinutes", data.slotMinutes())
				.param("opensAt", data.opensAt())
				.param("closesAt", data.closesAt())
				.param("notes", data.notes())
				.param("updatedAt", OffsetDateTime.now())
				.param("updatedBy", actorUserId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("resourceId", resourceId)
				.update();
	}

	public void deleteResource(UUID organizationId, UUID branchId, UUID resourceId, UUID actorUserId) {
		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE resource
				SET status = 'INACTIVE',
				    deleted_at = :deletedAt,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND id = :resourceId
				  AND deleted_at IS NULL
				""")
				.param("deletedAt", now)
				.param("updatedAt", now)
				.param("updatedBy", actorUserId)
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("resourceId", resourceId)
				.update();
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

	public int activeBookingCount(UUID organizationId, UUID branchId, UUID resourceId) {
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM booking
				WHERE organization_id = :organizationId
				  AND branch_id = :branchId
				  AND resource_id = :resourceId
				  AND status IN ('RESERVED', 'CONFIRMED', 'CHECKED_IN')
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("resourceId", resourceId)
				.query(Integer.class)
				.single();
		return count;
	}

	private String resourceSelect() {
		return """
				SELECT r.id, r.name, r.resource_type, r.capacity, r.slot_minutes, r.status,
				       r.opens_at, r.closes_at, r.notes,
				       COALESCE(string_agg(rap.plan_id::text, ',' ORDER BY cp.name) FILTER (WHERE rap.plan_id IS NOT NULL), '') AS allowed_plan_ids,
				       COALESCE(string_agg(cp.name, ', ' ORDER BY cp.name) FILTER (WHERE rap.plan_id IS NOT NULL), '') AS allowed_plan_names
				FROM resource r
				LEFT JOIN resource_allowed_membership_plan rap ON rap.resource_id = r.id
				LEFT JOIN membership_plan mp ON mp.id = rap.plan_id AND mp.deleted_at IS NULL
				LEFT JOIN catalog_product cp ON cp.id = mp.product_id AND cp.deleted_at IS NULL
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

	public record ResourceData(String name, String resourceType, int capacity, int slotMinutes, LocalTime opensAt, LocalTime closesAt, String notes) {
	}

	public record ResourceRecord(UUID id, String name, String resourceType, int capacity, int slotMinutes, String status, LocalTime opensAt, LocalTime closesAt, String notes, List<UUID> allowedPlanIds, String allowedPlanNames) {
	}
}
