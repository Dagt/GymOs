package com.gymos.clients.infrastructure;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ClientRepository {
	private final JdbcClient jdbcClient;

	public ClientRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public boolean emailExists(UUID organizationId, String email, UUID excludedPersonId) {
		if (email == null) {
			return false;
		}
		if (excludedPersonId == null) {
			Integer count = jdbcClient.sql("""
					SELECT COUNT(*)
					FROM person
					WHERE organization_id = :organizationId
					  AND LOWER(email) = LOWER(:email)
					  AND deleted_at IS NULL
					""")
					.param("organizationId", organizationId)
					.param("email", email)
					.query(Integer.class)
					.single();
			return count > 0;
		}
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM person
				WHERE organization_id = :organizationId
				  AND LOWER(email) = LOWER(:email)
				  AND id <> :excludedPersonId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("email", email)
				.param("excludedPersonId", excludedPersonId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public boolean phoneExists(UUID organizationId, String phone, UUID excludedPersonId) {
		if (phone == null) {
			return false;
		}
		if (excludedPersonId == null) {
			Integer count = jdbcClient.sql("""
					SELECT COUNT(*)
					FROM person
					WHERE organization_id = :organizationId
					  AND phone = :phone
					  AND deleted_at IS NULL
					""")
					.param("organizationId", organizationId)
					.param("phone", phone)
					.query(Integer.class)
					.single();
			return count > 0;
		}
		Integer count = jdbcClient.sql("""
				SELECT COUNT(*)
				FROM person
				WHERE organization_id = :organizationId
				  AND phone = :phone
				  AND id <> :excludedPersonId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("phone", phone)
				.param("excludedPersonId", excludedPersonId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public Optional<ClientDetail> findDetail(UUID organizationId, UUID clientId) {
		return jdbcClient.sql("""
				SELECT c.id AS client_id, c.status AS client_status, c.joined_at, c.notes,
				       p.id AS person_id, p.first_name, p.last_name, p.maternal_last_name, p.birth_date, p.gender,
				       p.phone, p.email, p.photo_reference, p.status AS person_status,
				       c.created_at, c.updated_at
				FROM client c
				JOIN person p ON p.id = c.person_id
				WHERE c.organization_id = :organizationId
				  AND c.id = :clientId
				  AND c.deleted_at IS NULL
				  AND p.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.query((resultSet, rowNumber) -> new ClientDetail(
						resultSet.getObject("client_id", UUID.class),
						resultSet.getObject("person_id", UUID.class),
						resultSet.getString("first_name"),
						resultSet.getString("last_name"),
						resultSet.getString("maternal_last_name"),
						resultSet.getObject("birth_date", LocalDate.class),
						resultSet.getString("gender"),
						resultSet.getString("phone"),
						resultSet.getString("email"),
						resultSet.getString("photo_reference"),
						resultSet.getString("client_status"),
						resultSet.getObject("joined_at", OffsetDateTime.class),
						resultSet.getString("notes"),
						resultSet.getObject("created_at", OffsetDateTime.class),
						resultSet.getObject("updated_at", OffsetDateTime.class)
				))
				.optional();
	}

	public Optional<SelfServiceClientRecord> verifyForSelfService(
			UUID organizationId,
			UUID branchId,
			String identifier,
			LocalDate birthDate
	) {
		String normalizedIdentifier = identifier == null ? "" : identifier.trim().toLowerCase();
		return jdbcClient.sql("""
				SELECT c.id AS client_id, TRIM(p.first_name || ' ' || p.last_name || ' ' || COALESCE(p.maternal_last_name, '')) AS full_name
				FROM client c
				JOIN person p ON p.id = c.person_id
				WHERE c.organization_id = :organizationId
				  AND c.status = 'ACTIVE'
				  AND c.deleted_at IS NULL
				  AND p.deleted_at IS NULL
				  AND p.birth_date = :birthDate
				  AND (
				      LOWER(COALESCE(p.email, '')) = :identifier
				      OR LOWER(COALESCE(p.phone, '')) = :identifier
				  )
				  AND EXISTS (
				      SELECT 1
				      FROM membership m
				      WHERE m.organization_id = c.organization_id
				        AND (
				            m.branch_id = :branchId
				            OR EXISTS (
				                SELECT 1
				                FROM membership_plan mp
				                WHERE mp.id = m.plan_id
				                  AND mp.multi_branch_access = true
				            )
				        )
				        AND m.client_id = c.id
				        AND m.deleted_at IS NULL
				  )
				ORDER BY c.joined_at DESC
				LIMIT 1
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("identifier", normalizedIdentifier)
				.param("birthDate", birthDate)
				.query((resultSet, rowNumber) -> new SelfServiceClientRecord(
						resultSet.getObject("client_id", UUID.class),
						resultSet.getString("full_name")
				))
				.optional();
	}

	public Optional<SelfServiceClientRecord> selfServiceProfile(UUID organizationId, UUID clientId) {
		return jdbcClient.sql("""
				SELECT c.id AS client_id, TRIM(p.first_name || ' ' || p.last_name || ' ' || COALESCE(p.maternal_last_name, '')) AS full_name
				FROM client c
				JOIN person p ON p.id = c.person_id
				WHERE c.organization_id = :organizationId
				  AND c.id = :clientId
				  AND c.status = 'ACTIVE'
				  AND c.deleted_at IS NULL
				  AND p.deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.query((resultSet, rowNumber) -> new SelfServiceClientRecord(
						resultSet.getObject("client_id", UUID.class),
						resultSet.getString("full_name")
				))
				.optional();
	}

	public List<ClientSummary> search(UUID organizationId, String query, String status, int limit) {
		String normalizedQuery = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
		String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase();
		StringBuilder sql = new StringBuilder("""
				SELECT c.id AS client_id, p.first_name, p.last_name, p.maternal_last_name, p.phone, p.email,
				       c.status AS client_status, c.joined_at, c.updated_at
				FROM client c
				JOIN person p ON p.id = c.person_id
				WHERE c.organization_id = :organizationId
				  AND c.deleted_at IS NULL
				  AND p.deleted_at IS NULL
				""");
		if (normalizedStatus != null) {
			sql.append(" AND c.status = :status\n");
		}
		if (normalizedQuery != null) {
			sql.append("""
					AND (
					    LOWER(p.first_name || ' ' || p.last_name || ' ' || COALESCE(p.maternal_last_name, '')) LIKE :query
					    OR LOWER(COALESCE(p.email, '')) LIKE :query
					    OR LOWER(COALESCE(p.phone, '')) LIKE :query
					)
					""");
		}
		sql.append("""
				ORDER BY c.updated_at DESC
				LIMIT :limit
				""");
		var spec = jdbcClient.sql(sql.toString())
				.param("organizationId", organizationId)
				.param("limit", limit)
				;
		if (normalizedStatus != null) {
			spec = spec.param("status", normalizedStatus);
		}
		if (normalizedQuery != null) {
			spec = spec.param("query", normalizedQuery);
		}
		return spec.query((resultSet, rowNumber) -> new ClientSummary(
						resultSet.getObject("client_id", UUID.class),
						resultSet.getString("first_name"),
						resultSet.getString("last_name"),
						resultSet.getString("maternal_last_name"),
						resultSet.getString("phone"),
						resultSet.getString("email"),
						resultSet.getString("client_status"),
						resultSet.getObject("joined_at", OffsetDateTime.class),
						resultSet.getObject("updated_at", OffsetDateTime.class)
				))
				.list();
	}

	public List<ClientTimelineEntry> timeline(UUID organizationId, UUID clientId) {
		return jdbcClient.sql("""
				SELECT action, origin, reason, occurred_at
				FROM audit_log
				WHERE organization_id = :organizationId
				  AND entity_type = 'CLIENT'
				  AND entity_id = :clientId
				ORDER BY occurred_at DESC
				LIMIT 20
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.query((resultSet, rowNumber) -> new ClientTimelineEntry(
						resultSet.getString("action"),
						resultSet.getString("origin"),
						resultSet.getString("reason"),
						resultSet.getObject("occurred_at", OffsetDateTime.class)
				))
				.list();
	}

	public record ClientSummary(
			UUID id,
			String firstName,
			String lastName,
			String maternalLastName,
			String phone,
			String email,
			String status,
			OffsetDateTime joinedAt,
			OffsetDateTime updatedAt
	) {
		public String fullName() {
			return (firstName + " " + lastName + " " + (maternalLastName == null ? "" : maternalLastName)).trim();
		}
	}

	public record ClientDetail(
			UUID id,
			UUID personId,
			String firstName,
			String lastName,
			String maternalLastName,
			LocalDate birthDate,
			String gender,
			String phone,
			String email,
			String photoReference,
			String status,
			OffsetDateTime joinedAt,
			String notes,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt
	) {
		public String fullName() {
			return (firstName + " " + lastName + " " + (maternalLastName == null ? "" : maternalLastName)).trim();
		}
	}

	public record ClientTimelineEntry(
			String action,
			String origin,
			String reason,
			OffsetDateTime occurredAt
	) {
	}

	public record SelfServiceClientRecord(UUID id, String fullName) {
	}
}
