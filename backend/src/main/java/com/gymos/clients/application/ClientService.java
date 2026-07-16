package com.gymos.clients.application;

import com.gymos.audit.api.AuditService;
import com.gymos.clients.api.operations.ClientLookupOperations;
import com.gymos.clients.api.operations.ClientVerificationResult;
import com.gymos.clients.api.operations.SelfServiceClientProfile;
import com.gymos.clients.api.operations.VerifyClientIdentityCommand;
import com.gymos.clients.application.command.CreateClientCommand;
import com.gymos.clients.application.command.UpdateClientCommand;
import com.gymos.clients.application.result.ClientDetailResult;
import com.gymos.clients.infrastructure.ClientRepository;
import com.gymos.clients.infrastructure.ClientRepository.ClientDetail;
import com.gymos.clients.infrastructure.ClientRepository.ClientSummary;
import com.gymos.clients.infrastructure.ClientRepository.ClientTimelineEntry;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientService implements ClientLookupOperations {
	private final JdbcClient jdbcClient;
	private final ClientRepository clientRepository;
	private final AuditService auditService;

	public ClientService(JdbcClient jdbcClient, ClientRepository clientRepository, AuditService auditService) {
		this.jdbcClient = jdbcClient;
		this.clientRepository = clientRepository;
		this.auditService = auditService;
	}

	@Transactional
	public ClientDetailResult create(GymOsPrincipal principal, CreateClientCommand request, String correlationId) {
		requirePrincipal(principal);
		String email = blankToNull(request.email());
		String phone = blankToNull(request.phone());
		ensureUniqueContact(principal.organizationId(), email, phone, null);

		OffsetDateTime now = OffsetDateTime.now();
		UUID personId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO person (
				    id, organization_id, first_name, last_name, birth_date, gender,
				    phone, email, photo_reference, status, created_at, updated_at,
				    created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :firstName, :lastName, :birthDate, :gender,
				    :phone, :email, :photoReference, 'ACTIVE', :createdAt, :updatedAt,
				    :createdBy, :updatedBy, 0
				)
				""")
				.param("id", personId)
				.param("organizationId", principal.organizationId())
				.param("firstName", request.firstName().trim())
				.param("lastName", request.lastName().trim())
				.param("birthDate", request.birthDate())
				.param("gender", blankToNull(request.gender()))
				.param("phone", phone)
				.param("email", email)
				.param("photoReference", blankToNull(request.photoReference()))
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", principal.userId())
				.param("updatedBy", principal.userId())
				.update();
		jdbcClient.sql("""
				INSERT INTO client (
				    id, organization_id, person_id, status, joined_at, notes,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :organizationId, :personId, 'ACTIVE', :joinedAt, :notes,
				    :createdAt, :updatedAt, :createdBy, :updatedBy, 0
				)
				""")
				.param("id", clientId)
				.param("organizationId", principal.organizationId())
				.param("personId", personId)
				.param("joinedAt", now)
				.param("notes", blankToNull(request.notes()))
				.param("createdAt", now)
				.param("updatedAt", now)
				.param("createdBy", principal.userId())
				.param("updatedBy", principal.userId())
				.update();
		auditService.record(principal, "CLIENT_REGISTERED", "CLIENT", clientId, "RECEPTION", null, correlationId);
		return detail(principal, clientId);
	}

	@Transactional(readOnly = true)
	public List<ClientSummary> search(GymOsPrincipal principal, String query, String status) {
		requirePrincipal(principal);
		return clientRepository.search(principal.organizationId(), query, status, 50);
	}

	@Transactional(readOnly = true)
	public ClientDetailResult detail(GymOsPrincipal principal, UUID clientId) {
		requirePrincipal(principal);
		ClientDetail client = clientRepository.findDetail(principal.organizationId(), clientId)
				.orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible."));
		List<ClientTimelineEntry> timeline = clientRepository.timeline(principal.organizationId(), clientId);
		return new ClientDetailResult(client, timeline);
	}

	@Transactional
	public ClientDetailResult update(
			GymOsPrincipal principal,
			UUID clientId,
			UpdateClientCommand request,
			String correlationId
	) {
		requirePrincipal(principal);
		ClientDetail existing = clientRepository.findDetail(principal.organizationId(), clientId)
				.orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible."));
		String email = blankToNull(request.email());
		String phone = blankToNull(request.phone());
		ensureUniqueContact(principal.organizationId(), email, phone, existing.personId());

		OffsetDateTime now = OffsetDateTime.now();
		jdbcClient.sql("""
				UPDATE person
				SET first_name = :firstName,
				    last_name = :lastName,
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
				.param("firstName", request.firstName().trim())
				.param("lastName", request.lastName().trim())
				.param("birthDate", request.birthDate())
				.param("gender", blankToNull(request.gender()))
				.param("phone", phone)
				.param("email", email)
				.param("photoReference", blankToNull(request.photoReference()))
				.param("updatedAt", now)
				.param("updatedBy", principal.userId())
				.param("personId", existing.personId())
				.param("organizationId", principal.organizationId())
				.update();
		jdbcClient.sql("""
				UPDATE client
				SET status = :status,
				    notes = :notes,
				    updated_at = :updatedAt,
				    updated_by = :updatedBy,
				    version = version + 1
				WHERE id = :clientId
				  AND organization_id = :organizationId
				  AND deleted_at IS NULL
				""")
				.param("status", request.status().trim().toUpperCase())
				.param("notes", blankToNull(request.notes()))
				.param("updatedAt", now)
				.param("updatedBy", principal.userId())
				.param("clientId", clientId)
				.param("organizationId", principal.organizationId())
				.update();
		auditService.record(principal, "CLIENT_UPDATED", "CLIENT", clientId, "RECEPTION", null, correlationId);
		return detail(principal, clientId);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ClientVerificationResult> verifyForSelfService(VerifyClientIdentityCommand command) {
		if (command.birthDate() == null || command.identifier() == null || command.identifier().isBlank()) {
			return Optional.empty();
		}
		return clientRepository.verifyForSelfService(
						command.organizationId(),
						command.branchId(),
						command.identifier(),
						command.birthDate()
				)
				.map(client -> new ClientVerificationResult(client.id(), client.fullName()));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<SelfServiceClientProfile> selfServiceProfile(UUID organizationId, UUID clientId) {
		return clientRepository.selfServiceProfile(organizationId, clientId)
				.map(client -> new SelfServiceClientProfile(client.id(), client.fullName()));
	}

	private void ensureUniqueContact(UUID organizationId, String email, String phone, UUID excludedPersonId) {
		if (clientRepository.emailExists(organizationId, email, excludedPersonId)) {
			throw new BusinessException("PERSON_EMAIL_ALREADY_EXISTS", "Ya existe una persona con ese email.");
		}
		if (clientRepository.phoneExists(organizationId, phone, excludedPersonId)) {
			throw new BusinessException("PERSON_PHONE_ALREADY_EXISTS", "Ya existe una persona con ese telefono.");
		}
	}

	private void requirePrincipal(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
