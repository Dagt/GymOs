package com.gymos.clients.infrastructure;

import com.gymos.clients.application.result.ClientImportRowResult;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ClientImportRepository {
	private final JdbcClient jdbcClient;

	public ClientImportRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public UUID insertClient(ImportedClientRecord record) {
		UUID personId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO person (
				    id, organization_id, first_name, last_name, birth_date, gender,
				    phone, email, status, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :firstName, :lastName, :birthDate, :gender,
				    :phone, :email, 'ACTIVE', :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", personId)
				.param("organizationId", record.organizationId())
				.param("firstName", record.firstName())
				.param("lastName", record.lastName())
				.param("birthDate", record.birthDate())
				.param("gender", record.gender())
				.param("phone", record.phone())
				.param("email", record.email())
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
				.param("id", clientId)
				.param("organizationId", record.organizationId())
				.param("homeBranchId", record.branchId())
				.param("personId", personId)
				.param("joinedAt", record.now())
				.param("notes", record.notes())
				.param("createdAt", record.now())
				.param("updatedAt", record.now())
				.param("createdBy", record.userId())
				.param("updatedBy", record.userId())
				.update();
		return clientId;
	}

	public void insertBatch(ClientImportBatchRecord record) {
		jdbcClient.sql("""
				INSERT INTO client_import_batch (
				    id, organization_id, branch_id, file_name, dry_run, status,
				    total_rows, created_at, created_by, version
				)
				VALUES (
				    :id, :organizationId, :branchId, :fileName, :dryRun, 'PROCESSING',
				    :totalRows, :createdAt, :createdBy, 0
				)
				""")
				.param("id", record.batchId())
				.param("organizationId", record.organizationId())
				.param("branchId", record.branchId())
				.param("fileName", record.fileName())
				.param("dryRun", record.dryRun())
				.param("totalRows", record.totalRows())
				.param("createdAt", record.startedAt())
				.param("createdBy", record.userId())
				.update();
	}

	public void insertRow(UUID batchId, UUID organizationId, ClientImportRowResult row) {
		jdbcClient.sql("""
				INSERT INTO client_import_row (
				    id, batch_id, organization_id, row_number, status, reason, client_id,
				    first_name, last_name, email, phone, created_at
				)
				VALUES (
				    :id, :batchId, :organizationId, :rowNumber, :status, :reason, :clientId,
				    :firstName, :lastName, :email, :phone, :createdAt
				)
				""")
				.param("id", UUID.randomUUID())
				.param("batchId", batchId)
				.param("organizationId", organizationId)
				.param("rowNumber", row.rowNumber())
				.param("status", row.status())
				.param("reason", row.reason())
				.param("clientId", row.clientId())
				.param("firstName", row.firstName())
				.param("lastName", row.lastName())
				.param("email", row.email())
				.param("phone", row.phone())
				.param("createdAt", OffsetDateTime.now())
				.update();
	}

	public void completeBatch(UUID batchId, String status, int created, int skipped, int errors) {
		jdbcClient.sql("""
				UPDATE client_import_batch
				SET status = :status,
				    created_count = :createdCount,
				    skipped_count = :skippedCount,
				    error_count = :errorCount,
				    completed_at = :completedAt,
				    version = version + 1
				WHERE id = :batchId
				""")
				.param("status", status)
				.param("createdCount", created)
				.param("skippedCount", skipped)
				.param("errorCount", errors)
				.param("completedAt", OffsetDateTime.now())
				.param("batchId", batchId)
				.update();
	}

	public record ImportedClientRecord(
			UUID organizationId,
			UUID branchId,
			UUID userId,
			String firstName,
			String lastName,
			LocalDate birthDate,
			String gender,
			String phone,
			String email,
			String notes,
			OffsetDateTime now
	) {
	}

	public record ClientImportBatchRecord(
			UUID batchId,
			UUID organizationId,
			UUID branchId,
			UUID userId,
			String fileName,
			boolean dryRun,
			int totalRows,
			OffsetDateTime startedAt
	) {
	}
}
