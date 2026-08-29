package com.gymos.clients.infrastructure;

import com.gymos.common.api.PageResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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

	public Optional<ClientDetail> findDetail(UUID organizationId, UUID branchId, UUID clientId) {
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
				  AND (
				      c.home_branch_id IS NULL
				      OR c.home_branch_id = :branchId
				      OR EXISTS (
				          SELECT 1
				          FROM membership m
				          JOIN membership_plan mp ON mp.id = m.plan_id
				          WHERE m.organization_id = c.organization_id
				            AND m.client_id = c.id
				            AND m.status = 'ACTIVE'
				            AND m.deleted_at IS NULL
				            AND (
				                m.branch_id = :branchId
				                OR mp.multi_branch_access = true
				            )
				      )
				  )
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
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
		SelfServiceIdentifiers identifiers = selfServiceIdentifiers(identifier);
		return jdbcClient.sql("""
				SELECT c.id AS client_id, p.id AS person_id,
				       TRIM(p.first_name || ' ' || p.last_name || ' ' || COALESCE(p.maternal_last_name, '')) AS full_name,
				       p.email, p.phone, c.status, c.inactivation_reason
				FROM client c
				JOIN person p ON p.id = c.person_id
				WHERE c.organization_id = :organizationId
				  AND (
				      c.status = 'ACTIVE'
				      OR (c.status = 'INACTIVE' AND c.inactivation_reason = 'MEMBERSHIP_INACTIVITY')
				  )
				  AND c.deleted_at IS NULL
				  AND p.deleted_at IS NULL
				  AND p.birth_date = :birthDate
				  AND (
				      (:emailIdentifier <> '' AND LOWER(TRIM(COALESCE(p.email, ''))) = :emailIdentifier)
				      OR (
				          :phoneIdentifier <> ''
				          AND REGEXP_REPLACE(COALESCE(p.phone, ''), '[^0-9]', '', 'g') IN (
				              :phoneIdentifier, :localPhoneIdentifier, :legacyPhoneIdentifier
				          )
				      )
				  )
				ORDER BY c.joined_at DESC
				LIMIT 1
				""")
				.param("organizationId", organizationId)
				.param("emailIdentifier", identifiers.email())
				.param("phoneIdentifier", identifiers.phone())
				.param("localPhoneIdentifier", identifiers.localPhone())
				.param("legacyPhoneIdentifier", identifiers.legacyPhone())
				.param("birthDate", birthDate)
				.query((resultSet, rowNumber) -> new SelfServiceClientRecord(
						resultSet.getObject("client_id", UUID.class),
						resultSet.getObject("person_id", UUID.class),
						resultSet.getString("full_name"),
						resultSet.getString("email"),
						resultSet.getString("phone"),
						resultSet.getString("status"),
						resultSet.getString("inactivation_reason")
				))
				.optional();
	}

	public boolean branchExists(UUID organizationId, UUID branchId) {
		return jdbcClient.sql("""
				SELECT EXISTS (
				    SELECT 1
				    FROM branch
				    WHERE organization_id = :organizationId
				      AND id = :branchId
				      AND status = 'ACTIVE'
				      AND deleted_at IS NULL
				)
				""")
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.query(Boolean.class)
				.single();
	}

	static SelfServiceIdentifiers selfServiceIdentifiers(String identifier) {
		String normalized = identifier == null ? "" : identifier.trim().toLowerCase();
		if (normalized.contains("@")) {
			return new SelfServiceIdentifiers(normalized, "", "", "");
		}
		String digits = normalized.replaceAll("\\D", "");
		if (digits.length() == 10) {
			return new SelfServiceIdentifiers("", "52" + digits, digits, "521" + digits);
		}
		if (digits.length() == 12 && digits.startsWith("52")) {
			String local = digits.substring(2);
			return new SelfServiceIdentifiers("", digits, local, "521" + local);
		}
		if (digits.length() == 13 && digits.startsWith("521")) {
			String local = digits.substring(3);
			return new SelfServiceIdentifiers("", "52" + local, local, digits);
		}
		return new SelfServiceIdentifiers("", digits, digits, digits);
	}

	public Optional<SelfServiceClientRecord> selfServiceProfile(UUID organizationId, UUID clientId) {
		return jdbcClient.sql("""
				SELECT c.id AS client_id, p.id AS person_id,
				       TRIM(p.first_name || ' ' || p.last_name || ' ' || COALESCE(p.maternal_last_name, '')) AS full_name,
				       p.email, p.phone
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
						resultSet.getObject("person_id", UUID.class),
						resultSet.getString("full_name"),
						resultSet.getString("email"),
						resultSet.getString("phone")
				))
				.optional();
	}

	public List<ClientSummary> search(UUID organizationId, UUID branchId, String query, String status, int limit) {
		return searchItems(clientFilter(organizationId, branchId, query, status), limit, 0);
	}

	public PageResponse<ClientSummary> searchPage(UUID organizationId, UUID branchId, String query, String status, int page, int size) {
		int safePage = Math.max(0, page);
		int safeSize = Math.min(100, Math.max(1, size));
		ClientFilter filter = clientFilter(organizationId, branchId, query, status);
		long total = jdbcClient.sql("SELECT COUNT(*) FROM client c JOIN person p ON p.id = c.person_id " + filter.predicate())
				.params(filter.parameters()).query(Long.class).single();
		return PageResponse.of(searchItems(filter, safeSize, (long) safePage * safeSize), safePage, safeSize, total);
	}

	private List<ClientSummary> searchItems(ClientFilter filter, int limit, long offset) {
		String sql = """
				SELECT c.id AS client_id, p.first_name, p.last_name, p.maternal_last_name, p.phone, p.email,
				       c.status AS client_status, current_membership.plan_name AS current_plan_name,
				       current_membership.membership_status, c.joined_at, c.updated_at
				FROM client c
				JOIN person p ON p.id = c.person_id
				LEFT JOIN LATERAL (
				    SELECT product.name AS plan_name,
				           CASE
				               WHEN m.status = 'ACTIVE' AND m.grace_ends_on >= CURRENT_DATE THEN 'ACTIVE'
				               ELSE 'EXPIRED'
				           END AS membership_status
				    FROM membership m
				    JOIN membership_plan mp
				      ON mp.id = m.plan_id
				     AND mp.organization_id = m.organization_id
				     AND mp.deleted_at IS NULL
				    JOIN catalog_product product
				      ON product.id = mp.product_id
				     AND product.organization_id = m.organization_id
				     AND product.deleted_at IS NULL
				    WHERE m.organization_id = c.organization_id
				      AND m.client_id = c.id
				      AND m.status IN ('ACTIVE', 'EXPIRED')
				      AND m.deleted_at IS NULL
				    ORDER BY
				        CASE
				            WHEN m.status = 'ACTIVE' AND m.grace_ends_on >= CURRENT_DATE THEN 0
				            WHEN m.status = 'ACTIVE' THEN 1
				            ELSE 2
				        END,
				        m.grace_ends_on DESC,
				        m.activated_at DESC
				    LIMIT 1
				) current_membership ON TRUE
				""" + filter.predicate() + " ORDER BY c.updated_at DESC, c.id DESC LIMIT :limit OFFSET :offset";
		return jdbcClient.sql(sql).params(filter.parameters()).param("limit", limit).param("offset", offset)
				.query((resultSet, rowNumber) -> new ClientSummary(
						resultSet.getObject("client_id", UUID.class),
						resultSet.getString("first_name"),
						resultSet.getString("last_name"),
						resultSet.getString("maternal_last_name"),
						resultSet.getString("phone"),
						resultSet.getString("email"),
						resultSet.getString("client_status"),
						resultSet.getString("current_plan_name"),
						resultSet.getString("membership_status"),
						resultSet.getObject("joined_at", OffsetDateTime.class),
						resultSet.getObject("updated_at", OffsetDateTime.class)
				)).list();
	}

	private ClientFilter clientFilter(UUID organizationId, UUID branchId, String query, String status) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("organizationId", organizationId);
		parameters.put("branchId", branchId);
		StringBuilder sql = new StringBuilder("""
				WHERE c.organization_id = :organizationId
				  AND c.deleted_at IS NULL
				  AND p.deleted_at IS NULL
				  AND (
				      c.home_branch_id IS NULL
				      OR c.home_branch_id = :branchId
				      OR EXISTS (
				          SELECT 1
				          FROM membership m
				          JOIN membership_plan mp ON mp.id = m.plan_id
				          WHERE m.organization_id = c.organization_id
				            AND m.client_id = c.id
				            AND m.status = 'ACTIVE'
				            AND m.deleted_at IS NULL
				            AND (
				                m.branch_id = :branchId
				                OR mp.multi_branch_access = true
				            )
				      )
				  )
				""");
		if (status != null && !status.isBlank()) {
			sql.append(" AND c.status = :status\n");
			parameters.put("status", status.trim().toUpperCase(Locale.ROOT));
		}
		if (query != null && !query.isBlank()) {
			parameters.put("query", "%" + query.trim().toLowerCase(Locale.ROOT) + "%");
			sql.append("""
					AND (
					    LOWER(p.first_name || ' ' || p.last_name || ' ' || COALESCE(p.maternal_last_name, '')) LIKE :query
					    OR LOWER(COALESCE(p.email, '')) LIKE :query
					    OR LOWER(COALESCE(p.phone, '')) LIKE :query
					)
					""");
		}
		return new ClientFilter(sql.toString(), parameters);
	}

	private record ClientFilter(String predicate, Map<String, Object> parameters) { }

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
			String currentPlanName,
			String membershipStatus,
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

	public record SelfServiceClientRecord(
			UUID id,
			UUID personId,
			String fullName,
			String email,
			String phone,
			String status,
			String inactivationReason
	) {
		public SelfServiceClientRecord(UUID id, UUID personId, String fullName, String email, String phone) {
			this(id, personId, fullName, email, phone, "ACTIVE", null);
		}
	}

	record SelfServiceIdentifiers(String email, String phone, String localPhone, String legacyPhone) {
	}
}
