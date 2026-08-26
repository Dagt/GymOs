package com.gymos.clients.infrastructure;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ClientCommandRepository {
	private final JdbcClient jdbcClient;

	public ClientCommandRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public void createClient(CreateClientRecord record) {
		jdbcClient.sql("""
				INSERT INTO person (
				    id, organization_id, first_name, last_name, maternal_last_name, birth_date, gender,
				    phone, email, photo_reference, status, created_at, updated_at,
				    created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :firstName, :lastName, :maternalLastName, :birthDate, :gender,
				    :phone, :email, :photoReference, 'ACTIVE', :createdAt, :updatedAt,
				    :createdBy, :updatedBy, 0
				)
				""")
				.param("id", record.personId())
				.param("organizationId", record.organizationId())
				.param("firstName", record.firstName())
				.param("lastName", record.lastName())
				.param("maternalLastName", record.maternalLastName())
				.param("birthDate", record.birthDate())
				.param("gender", record.gender())
				.param("phone", record.phone())
				.param("email", record.email())
				.param("photoReference", record.photoReference())
				.param("createdAt", record.now())
				.param("updatedAt", record.now())
				.param("createdBy", record.userId())
				.param("updatedBy", record.userId())
				.update();
		jdbcClient.sql("""
				INSERT INTO client (
				    id, organization_id, home_branch_id, person_id, status, joined_at, notes,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :homeBranchId, :personId, 'ACTIVE', :joinedAt, :notes,
				    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", record.clientId())
				.param("organizationId", record.organizationId())
				.param("homeBranchId", record.branchId())
				.param("personId", record.personId())
				.param("joinedAt", record.now())
				.param("notes", record.notes())
				.param("createdAt", record.now())
				.param("updatedAt", record.now())
				.param("createdBy", record.userId())
				.param("updatedBy", record.userId())
				.update();
	}

	public void updateClient(UpdateClientRecord record) {
		jdbcClient.sql("""
				UPDATE person
				SET first_name = :firstName,
				    last_name = :lastName,
				    maternal_last_name = :maternalLastName,
				    birth_date = :birthDate,
				    gender = :gender,
				    phone = :phone,
				    email = :email,
				    photo_reference = :photoReference,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE id = :personId
				  AND organization_id = :organizationId
				  AND deleted_at IS NULL
				""")
				.param("firstName", record.firstName())
				.param("lastName", record.lastName())
				.param("maternalLastName", record.maternalLastName())
				.param("birthDate", record.birthDate())
				.param("gender", record.gender())
				.param("phone", record.phone())
				.param("email", record.email())
				.param("photoReference", record.photoReference())
				.param("updatedAt", record.now())
				.param("updatedBy", record.userId())
				.param("personId", record.personId())
				.param("organizationId", record.organizationId())
				.update();
		jdbcClient.sql("""
				UPDATE client
				SET status = :status,
				    inactivated_at = CASE
				        WHEN :status = 'ACTIVE' THEN NULL
				        WHEN status <> :status THEN :updatedAt
				        ELSE inactivated_at
				    END,
				    inactivation_reason = CASE
				        WHEN :status = 'ACTIVE' THEN NULL
				        WHEN status <> :status THEN 'MANUAL'
				        ELSE inactivation_reason
				    END,
				    notes = :notes,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE id = :clientId
				  AND organization_id = :organizationId
				  AND deleted_at IS NULL
				""")
				.param("status", record.status())
				.param("notes", record.notes())
				.param("updatedAt", record.now())
				.param("updatedBy", record.userId())
				.param("clientId", record.clientId())
				.param("organizationId", record.organizationId())
				.update();
	}

	public boolean reactivateClient(UUID organizationId, UUID clientId, UUID branchId, OffsetDateTime now) {
		return jdbcClient.sql("""
				UPDATE client
				SET status = 'ACTIVE',
				    home_branch_id = COALESCE(home_branch_id, :branchId),
				    inactivated_at = NULL,
				    inactivation_reason = NULL,
				    updated_at = :now,
				    version = version + 1
				WHERE organization_id = :organizationId
				  AND id = :clientId
				  AND status = 'INACTIVE'
				  AND inactivation_reason = 'MEMBERSHIP_INACTIVITY'
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.param("clientId", clientId)
				.param("branchId", branchId)
				.param("now", now)
				.update() == 1;
	}

	public void updateSelfServiceContact(
			UUID organizationId,
			UUID personId,
			String email,
			String phone,
			OffsetDateTime now
	) {
		jdbcClient.sql("""
				UPDATE person
				SET email = :email,
				    phone = :phone,
				    updated_at = :updatedAt,
				    version = version + 1
				WHERE id = :personId
				  AND organization_id = :organizationId
				  AND deleted_at IS NULL
				""")
				.param("email", email)
				.param("phone", phone)
				.param("updatedAt", now)
				.param("personId", personId)
				.param("organizationId", organizationId)
				.update();
	}

	public record CreateClientRecord(
			UUID organizationId,
			UUID branchId,
			UUID userId,
			UUID personId,
			UUID clientId,
			String firstName,
			String lastName,
			String maternalLastName,
			LocalDate birthDate,
			String gender,
			String phone,
			String email,
			String photoReference,
			String notes,
			OffsetDateTime now
	) {
	}

	public record UpdateClientRecord(
			UUID organizationId,
			UUID userId,
			UUID personId,
			UUID clientId,
			String firstName,
			String lastName,
			String maternalLastName,
			LocalDate birthDate,
			String gender,
			String phone,
			String email,
			String photoReference,
			String status,
			String notes,
			OffsetDateTime now
	) {
	}
}
