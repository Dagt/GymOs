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
		requireBranch(principal);
		return requiredConfiguration(principal.organizationId(), principal.branchId());
	}

	@Transactional
	public ClientLifecycleConfiguration update(
			GymOsPrincipal principal,
			UpdateClientLifecycleConfiguration command,
			String correlationId
	) {
		requireOrganization(principal);
		requireBranch(principal);
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
		if (command.inactivityMonths() < 1 || command.inactivityMonths() > 120) {
			throw new BusinessException(
					"CLIENT_INACTIVITY_MONTHS_INVALID",
					"El periodo de inactividad debe estar entre 1 y 120 meses."
			);
		}
		ClientLifecycleConfiguration saved = repository.save(
				principal.organizationId(), principal.branchId(), principal.userId(), command.registrationFeeEnabled(),
				command.registrationFeeCents(), label, command.autoInactivationEnabled(),
				command.inactivityMonths()
		);
		auditService.record(
				principal, "CLIENT_LIFECYCLE_CONFIGURATION_UPDATED", "BRANCH",
				principal.branchId(), "BACKOFFICE",
				"Inscripción y baja automática de socios", correlationId
		);
		return saved;
	}

	@Override
	@Transactional(readOnly = true)
	public RegistrationFee registrationFee(UUID organizationId, UUID branchId, UUID clientId, LocalDate effectiveOn) {
		ClientLifecycleConfiguration configuration = requiredConfiguration(organizationId, branchId);
		if (!configuration.registrationFeeEnabled() || configuration.registrationFeeCents() <= 0) {
			return RegistrationFee.none(configuration.currency(), configuration.inactivityMonths());
		}
		LocalDate latestMembershipEnd = repository.latestMembershipEnd(organizationId, clientId).orElse(null);
		LocalDate latestVisitPackageEnd = repository.latestVisitPackageEnd(organizationId, clientId).orElse(null);
		LocalDate latestAccessEnd = latestMembershipEnd == null ? latestVisitPackageEnd
				: latestVisitPackageEnd == null ? latestMembershipEnd
				: latestMembershipEnd.isAfter(latestVisitPackageEnd) ? latestMembershipEnd : latestVisitPackageEnd;
		boolean due = latestAccessEnd == null
				|| !latestAccessEnd.plusMonths(configuration.inactivityMonths()).isAfter(effectiveOn);
		if (!due) {
			return RegistrationFee.none(configuration.currency(), configuration.inactivityMonths());
		}
		return new RegistrationFee(
				true, configuration.registrationFeeCents(), configuration.registrationFeeLabel(),
				configuration.currency(), configuration.inactivityMonths()
		);
	}

	private ClientLifecycleConfiguration requiredConfiguration(UUID organizationId, UUID branchId) {
		return repository.configuration(organizationId, branchId).orElseThrow(() -> new BusinessException(
				"BRANCH_NOT_FOUND", "La sucursal no está disponible."
		));
	}

	private void requireBranch(GymOsPrincipal principal) {
		if (principal == null || principal.branchId() == null) {
			throw new BusinessException("BRANCH_CONTEXT_REQUIRED", "Selecciona una sucursal.");
		}
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
			boolean autoInactivationEnabled,
			int inactivityMonths
	) {
	}
}
