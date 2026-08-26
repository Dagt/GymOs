package com.gymos.clients.application;

import com.gymos.audit.api.AuditService;
import com.gymos.clients.api.operations.ClientLifecycleOperations;
import com.gymos.clients.infrastructure.ClientLifecycleRepository;
import com.gymos.clients.infrastructure.ClientLifecycleRepository.ClientLifecycleConfiguration;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClientLifecycleService implements ClientLifecycleOperations {
	private final ClientLifecycleRepository repository;
	private final AuditService auditService;

	public ClientLifecycleService(ClientLifecycleRepository repository, AuditService auditService) {
		this.repository = repository;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public ClientLifecycleConfiguration configuration(GymOsPrincipal principal) {
		requireOrganization(principal);
		return requiredConfiguration(principal.organizationId());
	}

	@Transactional
	public ClientLifecycleConfiguration update(
			GymOsPrincipal principal,
			UpdateClientLifecycleConfiguration command,
			String correlationId
	) {
		requireOrganization(principal);
		String label = command.registrationFeeLabel() == null
				? "Inscripción"
				: command.registrationFeeLabel().trim();
		if (command.registrationFeeEnabled() && command.registrationFeeCents() <= 0) {
			throw new BusinessException(
					"REGISTRATION_FEE_AMOUNT_REQUIRED",
					"Captura un importe mayor a cero para habilitar la inscripción."
			);
		}
		if (command.registrationFeeEnabled() && label.isBlank()) {
			throw new BusinessException(
					"REGISTRATION_FEE_LABEL_REQUIRED",
					"Captura el nombre que aparecerá en la venta de inscripción."
			);
		}
		ClientLifecycleConfiguration saved = repository.save(
				principal.organizationId(), principal.userId(), command.registrationFeeEnabled(),
				command.registrationFeeCents(), label, command.registrationRechargeMonths(),
				command.autoInactivationEnabled(), command.inactivityMonths()
		);
		auditService.record(
				principal, "CLIENT_LIFECYCLE_CONFIGURATION_UPDATED", "ORGANIZATION",
				principal.organizationId(), "BACKOFFICE",
				"Inscripción y baja automática de socios", correlationId
		);
		return saved;
	}

	@Override
	@Transactional(readOnly = true)
	public RegistrationFee registrationFee(UUID organizationId, UUID clientId, LocalDate effectiveOn) {
		ClientLifecycleConfiguration configuration = requiredConfiguration(organizationId);
		if (!configuration.registrationFeeEnabled() || configuration.registrationFeeCents() <= 0) {
			return RegistrationFee.none(configuration.currency(), configuration.registrationRechargeMonths());
		}
		LocalDate latestMembershipEnd = repository.latestMembershipEnd(organizationId, clientId).orElse(null);
		boolean due = latestMembershipEnd == null
				|| !latestMembershipEnd.plusMonths(configuration.registrationRechargeMonths()).isAfter(effectiveOn);
		if (!due) {
			return RegistrationFee.none(configuration.currency(), configuration.registrationRechargeMonths());
		}
		return new RegistrationFee(
				true, configuration.registrationFeeCents(), configuration.registrationFeeLabel(),
				configuration.currency(), configuration.registrationRechargeMonths()
		);
	}

	private ClientLifecycleConfiguration requiredConfiguration(UUID organizationId) {
		return repository.configuration(organizationId).orElseThrow(() -> new BusinessException(
				"ORGANIZATION_NOT_FOUND", "La organización no está disponible."
		));
	}

	private void requireOrganization(GymOsPrincipal principal) {
		if (principal == null || principal.organizationId() == null) {
			throw new BusinessException("ORGANIZATION_CONTEXT_REQUIRED", "Selecciona un gimnasio.");
		}
	}

	public record UpdateClientLifecycleConfiguration(
			boolean registrationFeeEnabled,
			long registrationFeeCents,
			String registrationFeeLabel,
			int registrationRechargeMonths,
			boolean autoInactivationEnabled,
			int inactivityMonths
	) {
	}
}
