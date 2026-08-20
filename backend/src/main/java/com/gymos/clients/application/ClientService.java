package com.gymos.clients.application;

import com.gymos.audit.api.AuditService;
import com.gymos.clients.api.operations.ClientLookupOperations;
import com.gymos.clients.api.operations.ClientVerificationResult;
import com.gymos.clients.api.operations.SelfServiceClientProfile;
import com.gymos.clients.api.operations.UpdateSelfServiceContactCommand;
import com.gymos.clients.api.operations.VerifyClientIdentityCommand;
import com.gymos.clients.application.command.CreateClientCommand;
import com.gymos.clients.application.command.UpdateClientCommand;
import com.gymos.clients.application.result.ClientDetailResult;
import com.gymos.clients.infrastructure.ClientCommandRepository;
import com.gymos.clients.infrastructure.ClientCommandRepository.CreateClientRecord;
import com.gymos.clients.infrastructure.ClientCommandRepository.UpdateClientRecord;
import com.gymos.clients.infrastructure.ClientRepository;
import com.gymos.clients.infrastructure.ClientRepository.ClientDetail;
import com.gymos.clients.infrastructure.ClientRepository.ClientSummary;
import com.gymos.clients.infrastructure.ClientRepository.ClientTimelineEntry;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientService implements ClientLookupOperations {
	private final ClientRepository clientRepository;
	private final ClientCommandRepository commandRepository;
	private final AuditService auditService;

	public ClientService(ClientRepository clientRepository, ClientCommandRepository commandRepository, AuditService auditService) {
		this.clientRepository = clientRepository;
		this.commandRepository = commandRepository;
		this.auditService = auditService;
	}

	@Transactional
	public ClientDetailResult create(GymOsPrincipal principal, CreateClientCommand request, String correlationId) {
		requirePrincipal(principal);
		requireBranch(principal);
		requireBirthDate(request.birthDate());
		String email = blankToNull(request.email());
		String phone = blankToNull(request.phone());
		ensureUniqueContact(principal.organizationId(), email, phone, null);

		OffsetDateTime now = OffsetDateTime.now();
		UUID personId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		commandRepository.createClient(new CreateClientRecord(
				principal.organizationId(),
				principal.branchId(),
				principal.userId(),
				personId,
				clientId,
				request.firstName().trim(),
				request.lastName().trim(),
				blankToNull(request.maternalLastName()),
				request.birthDate(),
				blankToNull(request.gender()),
				phone,
				email,
				blankToNull(request.photoReference()),
				blankToNull(request.notes()),
				now
		));
		auditService.record(principal, "CLIENT_REGISTERED", "CLIENT", clientId, "RECEPTION", null, correlationId);
		return detail(principal, clientId);
	}

	@Transactional(readOnly = true)
	public List<ClientSummary> search(GymOsPrincipal principal, String query, String status) {
		requirePrincipal(principal);
		requireBranch(principal);
		return clientRepository.search(principal.organizationId(), principal.branchId(), query, status, 50);
	}

	@Transactional(readOnly = true)
	public ClientDetailResult detail(GymOsPrincipal principal, UUID clientId) {
		requirePrincipal(principal);
		requireBranch(principal);
		ClientDetail client = clientRepository.findDetail(principal.organizationId(), principal.branchId(), clientId)
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
		requireBranch(principal);
		ClientDetail existing = clientRepository.findDetail(principal.organizationId(), principal.branchId(), clientId)
				.orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible."));
		requireBirthDate(request.birthDate());
		String email = blankToNull(request.email());
		String phone = blankToNull(request.phone());
		ensureUniqueContact(principal.organizationId(), email, phone, existing.personId());

		OffsetDateTime now = OffsetDateTime.now();
		commandRepository.updateClient(new UpdateClientRecord(
				principal.organizationId(),
				principal.userId(),
				existing.personId(),
				clientId,
				request.firstName().trim(),
				request.lastName().trim(),
				blankToNull(request.maternalLastName()),
				request.birthDate(),
				blankToNull(request.gender()),
				phone,
				email,
				blankToNull(request.photoReference()),
				request.status().trim().toUpperCase(),
				blankToNull(request.notes()),
				now
		));
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
				.map(this::toSelfServiceProfile);
	}

	@Override
	@Transactional
	public SelfServiceClientProfile updateSelfServiceContact(UpdateSelfServiceContactCommand command) {
		var existing = clientRepository.selfServiceProfile(command.organizationId(), command.clientId())
				.orElseThrow(() -> new BusinessException(
						"CLIENT_NOT_FOUND",
						"El socio no existe o no esta disponible."
				));
		String email = command.email() == null ? existing.email() : blankToNull(command.email());
		String phone = command.phone() == null ? existing.phone() : blankToNull(command.phone());
		if (email == null && phone == null) {
			throw new BusinessException(
					"SELF_SERVICE_CONTACT_REQUIRED",
					"Conserva al menos un correo o telefono de contacto."
			);
		}
		ensureUniqueContact(command.organizationId(), email, phone, existing.personId());
		commandRepository.updateSelfServiceContact(
				command.organizationId(),
				existing.personId(),
				email,
				phone,
				OffsetDateTime.now()
		);
		return selfServiceProfile(command.organizationId(), command.clientId()).orElseThrow();
	}

	private SelfServiceClientProfile toSelfServiceProfile(ClientRepository.SelfServiceClientRecord client) {
		return new SelfServiceClientProfile(
				client.id(),
				client.fullName(),
				maskEmail(client.email()),
				maskPhone(client.phone())
		);
	}

	private String maskEmail(String email) {
		if (email == null || email.isBlank()) {
			return null;
		}
		int separator = email.indexOf('@');
		if (separator <= 0) {
			return "***";
		}
		String local = email.substring(0, separator);
		String visible = local.substring(0, Math.min(2, local.length()));
		return visible + "***" + email.substring(separator);
	}

	private String maskPhone(String phone) {
		if (phone == null || phone.isBlank()) {
			return null;
		}
		String digits = phone.replaceAll("\\D", "");
		if (digits.length() < 4) {
			return "***";
		}
		return "***" + digits.substring(digits.length() - 4);
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

	private void requireBranch(GymOsPrincipal principal) {
		if (principal.branchId() == null) {
			throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para continuar.");
		}
	}

	private void requireBirthDate(LocalDate birthDate) {
		if (birthDate == null) {
			throw new BusinessException("CLIENT_BIRTH_DATE_REQUIRED", "La fecha de nacimiento es obligatoria.");
		}
		if (birthDate.isAfter(LocalDate.now())) {
			throw new BusinessException("CLIENT_BIRTH_DATE_INVALID", "La fecha de nacimiento no puede estar en el futuro.");
		}
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
