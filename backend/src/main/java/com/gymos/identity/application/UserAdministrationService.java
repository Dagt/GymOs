package com.gymos.identity.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.identity.application.command.CreateInternalUserCommand;
import com.gymos.identity.application.result.InternalUserSummary;
import com.gymos.identity.application.result.RoleOption;
import com.gymos.identity.infrastructure.UserAdministrationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdministrationService {
	private final UserAdministrationRepository repository;
	private final PasswordEncoder passwordEncoder;
	private final AuditService auditService;

	public UserAdministrationService(
			UserAdministrationRepository repository,
			PasswordEncoder passwordEncoder,
			AuditService auditService
	) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<RoleOption> roles(GymOsPrincipal principal) {
		requirePrincipal(principal);
		return repository.findRoles(principal.organizationId());
	}

	@Transactional(readOnly = true)
	public List<InternalUserSummary> users(GymOsPrincipal principal) {
		requirePrincipal(principal);
		return repository.findUsers(principal.organizationId());
	}

	@Transactional
	public InternalUserSummary createUser(
			GymOsPrincipal principal,
			CreateInternalUserCommand command,
			String correlationId
	) {
		requirePrincipal(principal);
		if (!repository.roleBelongsToOrganization(principal.organizationId(), command.roleId())) {
			throw new BusinessException("ROLE_NOT_AVAILABLE", "El rol seleccionado no esta disponible para este gimnasio.");
		}
		if (repository.roleHasPermission(command.roleId(), "platform.admin") && !principal.permissions().contains("platform.admin")) {
			throw new BusinessException("ROLE_NOT_AVAILABLE", "No puedes asignar roles de plataforma desde este gimnasio.");
		}
		List<UUID> branchIds = command.branchIds() == null ? List.of() : command.branchIds().stream().distinct().toList();
		for (UUID branchId : branchIds) {
			if (!repository.branchBelongsToOrganization(principal.organizationId(), branchId)) {
				throw new BusinessException("BRANCH_NOT_AVAILABLE", "Una sucursal seleccionada no pertenece a este gimnasio.");
			}
		}

		OffsetDateTime now = OffsetDateTime.now();
		UUID userId;
		try {
			UUID personId = repository.createPerson(
					principal.organizationId(),
					command.firstName().trim(),
					command.lastName().trim(),
					blankToNull(command.email()),
					blankToNull(command.phone()),
					principal.userId(),
					now
			);
			userId = repository.createUser(
					principal.organizationId(),
					personId,
					command.username().trim().toLowerCase(),
					passwordEncoder.encode(command.temporaryPassword()),
					principal.userId(),
					now
			);
		} catch (DuplicateKeyException exception) {
			throw new BusinessException("USER_ALREADY_EXISTS", "Ya existe una persona o usuario con esos datos.");
		}

		repository.assignRole(userId, command.roleId());
		for (UUID branchId : branchIds) {
			repository.assignBranch(userId, branchId, "BRANCH");
		}
		auditService.record(principal, "INTERNAL_USER_CREATED", "USER", userId, "ADMIN", null, correlationId);
		return repository.findUsers(principal.organizationId()).stream()
				.filter(user -> user.id().equals(userId))
				.findFirst()
				.orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "El usuario creado no esta disponible."));
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
