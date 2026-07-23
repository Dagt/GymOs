package com.gymos.clients.application;

import com.gymos.audit.api.AuditService;
import com.gymos.clients.application.command.ImportClientsCommand;
import com.gymos.clients.application.result.ClientImportResult;
import com.gymos.clients.application.result.ClientImportRowResult;
import com.gymos.clients.infrastructure.ClientImportRepository;
import com.gymos.clients.infrastructure.ClientImportRepository.ClientImportBatchRecord;
import com.gymos.clients.infrastructure.ClientImportRepository.ImportedClientRecord;
import com.gymos.clients.infrastructure.ClientRepository;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ClientImportService {
	private static final int MAX_IMPORT_ROWS = 1_000;

	private final ClientImportRepository importRepository;
	private final ClientRepository clientRepository;
	private final AuditService auditService;
	private final TransactionTemplate transactionTemplate;

	public ClientImportService(
			ClientImportRepository importRepository,
			ClientRepository clientRepository,
			AuditService auditService,
			TransactionTemplate transactionTemplate
	) {
		this.importRepository = importRepository;
		this.clientRepository = clientRepository;
		this.auditService = auditService;
		this.transactionTemplate = transactionTemplate;
	}

	public ClientImportResult importCsv(GymOsPrincipal principal, ImportClientsCommand command, String correlationId) {
		requirePrincipal(principal);
		requireBranch(principal);
		if (command.csvText() == null || command.csvText().isBlank()) {
			throw new BusinessException("CLIENT_IMPORT_EMPTY", "El archivo de importacion no tiene contenido.");
		}
		List<ImportedClientRow> rows = parse(command.csvText());
		if (rows.size() > MAX_IMPORT_ROWS) {
			throw new BusinessException("CLIENT_IMPORT_TOO_LARGE", "La importacion permite hasta 1000 filas por lote.");
		}

		UUID batchId = UUID.randomUUID();
		OffsetDateTime startedAt = OffsetDateTime.now();
		List<ClientImportRowResult> results = new ArrayList<>();
		transactionTemplate.executeWithoutResult(status -> insertBatch(
				batchId,
				principal,
				command.fileName(),
				command.dryRun(),
				rows.size(),
				startedAt
		));

		Map<String, Integer> emailsInFile = new HashMap<>();
		Map<String, Integer> phonesInFile = new HashMap<>();
		for (ImportedClientRow row : rows) {
			results.add(processRow(batchId, principal, row, command.dryRun(), emailsInFile, phonesInFile));
		}

		int created = count(results, "CREATED");
		int skipped = count(results, "SKIPPED");
		int errors = count(results, "ERROR");
		String batchStatus = errors > 0 ? (created > 0 ? "COMPLETED_WITH_ERRORS" : "FAILED") : "COMPLETED";
		transactionTemplate.executeWithoutResult(status -> importRepository.completeBatch(batchId, batchStatus, created, skipped, errors));
		auditService.record(principal, "CLIENT_IMPORT_COMPLETED", "CLIENT_IMPORT_BATCH", batchId, "BACKOFFICE", null, correlationId);

		return new ClientImportResult(
				batchId,
				command.dryRun(),
				batchStatus,
				rows.size(),
				created,
				skipped,
				errors,
				startedAt,
				results
		);
	}

	private ClientImportRowResult processRow(
			UUID batchId,
			GymOsPrincipal principal,
			ImportedClientRow row,
			boolean dryRun,
			Map<String, Integer> emailsInFile,
			Map<String, Integer> phonesInFile
	) {
		ClientImportRowResult result = validateRow(principal, row, dryRun, emailsInFile, phonesInFile);
		if (!"VALID".equals(result.status())) {
			transactionTemplate.executeWithoutResult(status -> importRepository.insertRow(batchId, principal.organizationId(), result));
			return result;
		}
		if (dryRun) {
			transactionTemplate.executeWithoutResult(status -> importRepository.insertRow(batchId, principal.organizationId(), result));
			return result;
		}
		ClientImportRowResult created = transactionTemplate.execute(status -> {
			UUID clientId = insertClient(principal, row);
			return new ClientImportRowResult(
					row.rowNumber(),
					"CREATED",
					"Cliente creado.",
					clientId,
					row.firstName(),
					row.lastName(),
					row.email(),
					row.phone()
			);
		});
		ClientImportRowResult safeCreated = created == null ? result : created;
		transactionTemplate.executeWithoutResult(status -> importRepository.insertRow(batchId, principal.organizationId(), safeCreated));
		return safeCreated;
	}

	private ClientImportRowResult validateRow(
			GymOsPrincipal principal,
			ImportedClientRow row,
			boolean dryRun,
			Map<String, Integer> emailsInFile,
			Map<String, Integer> phonesInFile
	) {
		if (isBlank(row.firstName()) || isBlank(row.lastName())) {
			return rowResult(row, "ERROR", "firstName y lastName son obligatorios.", null);
		}
		if (!isBlank(row.email()) && !row.email().contains("@")) {
			return rowResult(row, "ERROR", "Email invalido.", null);
		}
		if (!isBlank(row.birthDate())) {
			try {
				LocalDate.parse(row.birthDate());
			} catch (RuntimeException exception) {
				return rowResult(row, "ERROR", "birthDate debe usar formato YYYY-MM-DD.", null);
			}
		}
		String emailKey = lower(row.email());
		String phoneKey = normalize(row.phone());
		if (emailKey != null && emailsInFile.putIfAbsent(emailKey, row.rowNumber()) != null) {
			return rowResult(row, "SKIPPED", "Email duplicado dentro del archivo.", null);
		}
		if (phoneKey != null && phonesInFile.putIfAbsent(phoneKey, row.rowNumber()) != null) {
			return rowResult(row, "SKIPPED", "Telefono duplicado dentro del archivo.", null);
		}
		if (clientRepository.emailExists(principal.organizationId(), emailKey, null)) {
			return rowResult(row, "SKIPPED", "Ya existe una persona con ese email.", null);
		}
		if (clientRepository.phoneExists(principal.organizationId(), phoneKey, null)) {
			return rowResult(row, "SKIPPED", "Ya existe una persona con ese telefono.", null);
		}
		return rowResult(row, dryRun ? "VALID" : "VALID", dryRun ? "Lista para importar." : "Lista para crear.", null);
	}

	private UUID insertClient(GymOsPrincipal principal, ImportedClientRow row) {
		OffsetDateTime now = OffsetDateTime.now();
		return importRepository.insertClient(new ImportedClientRecord(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				row.firstName().trim(),
				row.lastName().trim(),
				parseDate(row.birthDate()),
				normalize(row.gender()),
				normalize(row.phone()),
				lower(row.email()),
				normalize(row.notes()),
				now
		));
	}

	private void insertBatch(
			UUID batchId,
			GymOsPrincipal principal,
			String fileName,
			boolean dryRun,
			int totalRows,
			OffsetDateTime startedAt
	) {
		importRepository.insertBatch(new ClientImportBatchRecord(
				batchId,
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				normalize(fileName),
				dryRun,
				totalRows,
				startedAt
		));
	}

	private List<ImportedClientRow> parse(String csvText) {
		List<List<String>> records = parseRecords(csvText);
		if (records.isEmpty()) {
			return List.of();
		}
		Map<String, Integer> headers = headers(records.getFirst());
		List<ImportedClientRow> rows = new ArrayList<>();
		for (int index = 1; index < records.size(); index++) {
			List<String> record = records.get(index);
			if (record.stream().allMatch(this::isBlank)) {
				continue;
			}
			rows.add(new ImportedClientRow(
					index + 1,
					value(record, headers, "firstName"),
					value(record, headers, "lastName"),
					value(record, headers, "birthDate"),
					value(record, headers, "gender"),
					value(record, headers, "phone"),
					value(record, headers, "email"),
					value(record, headers, "notes")
			));
		}
		return rows;
	}

	private List<List<String>> parseRecords(String csvText) {
		List<List<String>> records = new ArrayList<>();
		List<String> current = new ArrayList<>();
		StringBuilder value = new StringBuilder();
		boolean quoted = false;
		for (int index = 0; index < csvText.length(); index++) {
			char character = csvText.charAt(index);
			if (character == '"') {
				if (quoted && index + 1 < csvText.length() && csvText.charAt(index + 1) == '"') {
					value.append('"');
					index++;
				} else {
					quoted = !quoted;
				}
			} else if (character == ',' && !quoted) {
				current.add(value.toString().trim());
				value.setLength(0);
			} else if ((character == '\n' || character == '\r') && !quoted) {
				if (character == '\r' && index + 1 < csvText.length() && csvText.charAt(index + 1) == '\n') {
					index++;
				}
				current.add(value.toString().trim());
				value.setLength(0);
				records.add(current);
				current = new ArrayList<>();
			} else {
				value.append(character);
			}
		}
		current.add(value.toString().trim());
		records.add(current);
		return records;
	}

	private Map<String, Integer> headers(List<String> row) {
		Map<String, Integer> headers = new LinkedHashMap<>();
		for (int index = 0; index < row.size(); index++) {
			headers.put(normalizeHeader(row.get(index)), index);
		}
		return headers;
	}

	private String value(List<String> row, Map<String, Integer> headers, String header) {
		Integer index = headers.get(normalizeHeader(header));
		return index == null || index >= row.size() ? null : normalize(row.get(index));
	}

	private String normalizeHeader(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace("_", "");
	}

	private ClientImportRowResult rowResult(ImportedClientRow row, String status, String reason, UUID clientId) {
		return new ClientImportRowResult(
				row.rowNumber(),
				status,
				reason,
				clientId,
				row.firstName(),
				row.lastName(),
				row.email(),
				row.phone()
		);
	}

	private int count(List<ClientImportRowResult> rows, String status) {
		return (int) rows.stream().filter(row -> status.equals(row.status())).count();
	}

	private LocalDate parseDate(String value) {
		return isBlank(value) ? null : LocalDate.parse(value);
	}

	private String normalize(String value) {
		return isBlank(value) ? null : value.trim();
	}

	private String lower(String value) {
		return isBlank(value) ? null : value.trim().toLowerCase(Locale.ROOT);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private void requirePrincipal(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
	}

	private void requireBranch(GymOsPrincipal principal) {
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para importar socios.");
		}
	}

	private record ImportedClientRow(
			int rowNumber,
			String firstName,
			String lastName,
			String birthDate,
			String gender,
			String phone,
			String email,
			String notes
	) {
	}
}
