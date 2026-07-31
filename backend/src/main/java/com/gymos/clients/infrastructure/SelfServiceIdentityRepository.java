package com.gymos.clients.infrastructure;

import com.gymos.clients.application.SelfServiceIdentifierNormalizer.NormalizedIdentifier;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SelfServiceIdentityRepository {
	private final JdbcClient jdbcClient;

	public SelfServiceIdentityRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public Optional<ClientRepository.SelfServiceClientRecord> verify(
			UUID organizationId,
			UUID branchId,
			NormalizedIdentifier identifier,
			LocalDate birthDate
	) {
		String contactPredicate = switch (identifier.type()) {
			case EMAIL -> "LOWER(TRIM(COALESCE(p.email, ''))) = :identifier";
			case PHONE -> "RIGHT(REGEXP_REPLACE(COALESCE(p.phone, ''), '[^0-9]', '', 'g'), 10) = :identifier";
		};
		return jdbcClient.sql("""
				SELECT c.id AS client_id, p.id AS person_id,
				       TRIM(p.first_name || ' ' || p.last_name || ' ' || COALESCE(p.maternal_last_name, '')) AS full_name,
				       p.email, p.phone
				FROM client c
				JOIN person p ON p.id = c.person_id
				WHERE c.organization_id = :organizationId
				  AND c.status = 'ACTIVE'
				  AND c.deleted_at IS NULL
				  AND p.deleted_at IS NULL
				  AND p.birth_date = :birthDate
				  AND %s
				  AND EXISTS (
				      SELECT 1
				      FROM membership m
				      WHERE m.organization_id = c.organization_id
				        AND m.client_id = c.id
				        AND m.deleted_at IS NULL
				        AND (
				            m.branch_id = :branchId
				            OR EXISTS (
				                SELECT 1
				                FROM membership_plan mp
				                WHERE mp.id = m.plan_id
				                  AND mp.multi_branch_access = true
				            )
				        )
				  )
				ORDER BY c.joined_at DESC
				LIMIT 1
				""".formatted(contactPredicate))
				.param("organizationId", organizationId)
				.param("branchId", branchId)
				.param("identifier", identifier.value())
				.param("birthDate", birthDate)
				.query((resultSet, rowNumber) -> new ClientRepository.SelfServiceClientRecord(
						resultSet.getObject("client_id", UUID.class),
						resultSet.getObject("person_id", UUID.class),
						resultSet.getString("full_name"),
						resultSet.getString("email"),
						resultSet.getString("phone")
				))
				.optional();
	}
}
