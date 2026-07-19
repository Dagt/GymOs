package com.gymos.identity.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.identity.application.command.CreateInternalUserCommand;
import com.gymos.identity.application.result.InternalUserSummary;
import com.gymos.identity.application.result.RoleOption;
import com.gymos.identity.infrastructure.UserAdministrationRepository;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
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
		boolean includePlatformRoles = principal.platformAdmin() && !principal.supportMode();
		return repository.findRoles(principal.organizationId(), includePlatformRoles).stream()
				.filter(role -> canAssignRole(principal, role.name()))
				.toList();
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
		if (repository.roleHasPermission(command.roleId(), "platform.admin")) {
			throw new BusinessException("ROLE_NOT_AVAILABLE", "No puedes asignar roles de plataforma desde un gimnasio.");
		}
		requireAssignableRole(principal, command.roleId());
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
					blankToNull(command.maternalLastName()),
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

	@Transactional
	public InternalUserSummary updateStatus(
			GymOsPrincipal principal,
			UUID userId,
			String status,
			String correlationId
	) {
		requirePrincipal(principal);
		String normalizedStatus = normalizeStatus(status);
		if (principal.userId().equals(userId) && !"ACTIVE".equals(normalizedStatus)) {
			throw new BusinessException("SELF_STATUS_CHANGE_NOT_ALLOWED", "No puedes suspender tu propia cuenta.");
		}
		if (!repository.userBelongsToOrganization(principal.organizationId(), userId)) {
			throw new BusinessException("USER_NOT_FOUND", "El usuario no pertenece a este gimnasio.");
		}
		repository.updateUserStatus(principal.organizationId(), userId, normalizedStatus, principal.userId(), OffsetDateTime.now());
		auditService.record(principal, "INTERNAL_USER_STATUS_UPDATED", "USER", userId, "ADMIN", normalizedStatus, correlationId);
		return repository.findUsers(principal.organizationId()).stream()
				.filter(user -> user.id().equals(userId))
				.findFirst()
				.orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "El usuario no esta disponible."));
	}

	@Transactional
	public InternalUserSummary updateRole(
			GymOsPrincipal principal,
			UUID userId,
			UUID roleId,
			String correlationId
	) {
		requirePrincipal(principal);
		if (principal.userId().equals(userId)) {
			throw new BusinessException("SELF_ROLE_CHANGE_NOT_ALLOWED", "No puedes cambiar el rol de tu propia cuenta.");
		}
		if (!repository.userBelongsToOrganization(principal.organizationId(), userId)) {
			throw new BusinessException("USER_NOT_FOUND", "El usuario no pertenece a este gimnasio.");
		}
		if (!repository.roleBelongsToOrganization(principal.organizationId(), roleId)) {
			throw new BusinessException("ROLE_NOT_AVAILABLE", "El rol seleccionado no esta disponible para este gimnasio.");
		}
		if (repository.roleHasPermission(roleId, "platform.admin")) {
			throw new BusinessException("ROLE_NOT_AVAILABLE", "No puedes asignar roles de plataforma desde un gimnasio.");
		}
		requireAssignableRole(principal, roleId);
		repository.replaceRoles(userId, roleId);
		auditService.record(principal, "INTERNAL_USER_ROLE_UPDATED", "USER", userId, "ADMIN", roleId.toString(), correlationId);
		return repository.findUsers(principal.organizationId()).stream()
				.filter(user -> user.id().equals(userId))
				.findFirst()
				.orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "El usuario no esta disponible."));
	}

	private void requirePrincipal(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private String normalizeStatus(String status) {
		String normalized = status == null ? "" : status.trim().toUpperCase();
		if (!List.of("ACTIVE", "SUSPENDED", "INACTIVE").contains(normalized)) {
			throw new BusinessException("USER_STATUS_NOT_AVAILABLE", "Usa un estado valido: ACTIVE, SUSPENDED o INACTIVE.");
		}
		return normalized;
	}

	private void requireAssignableRole(GymOsPrincipal principal, UUID roleId) {
		String roleName = repository.roleName(roleId);
		if (!canAssignRole(principal, roleName)) {
			throw new BusinessException("ROLE_NOT_ALLOWED", "Tu perfil no puede asignar ese rol.");
		}
	}

	private boolean canAssignRole(GymOsPrincipal principal, String targetRoleName) {
		String target = normalizeRoleName(targetRoleName);
		if (target.contains("admin") && target.contains("gymos")) {
			return false;
		}
		if (principal.platformAdmin()) {
			return true;
		}
		boolean targetIsOwner = isOwnerRole(target);
		boolean targetIsReception = target.contains("recepcion") || target.contains("caja");
		boolean targetIsCoach = target.contains("coach");
		if (isPrincipalOwner(principal)) {
			return !targetIsOwner;
		}
		if (isPrincipalManager(principal)) {
			return targetIsReception || targetIsCoach;
		}
		return false;
	}

	private boolean isPrincipalOwner(GymOsPrincipal principal) {
		return principal.roles().stream()
				.map(this::normalizeRoleName)
				.anyMatch(this::isOwnerRole);
	}

	private boolean isPrincipalManager(GymOsPrincipal principal) {
		return principal.roles().stream()
				.map(this::normalizeRoleName)
				.anyMatch(role -> role.contains("gerente") || role.contains("gerencia"));
	}

	private boolean isOwnerRole(String roleName) {
		return roleName.equals("administrador")
				|| roleName.contains("dueno")
				|| roleName.contains("owner");
	}

	private String normalizeRoleName(String value) {
		String normalized = value == null ? "" : value.trim();
		return Normalizer.normalize(normalized, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT);
	}
}
