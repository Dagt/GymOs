package com.gymos.identity.application;

import com.gymos.audit.api.AuditService;
import com.gymos.coaching.api.operations.CoachProfileProvisioning;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.identity.application.command.CreateInternalUserCommand;
import com.gymos.identity.application.result.InternalUserSummary;
import com.gymos.identity.application.result.RoleOption;
import com.gymos.identity.infrastructure.UserAdministrationRepository;
import com.gymos.identity.infrastructure.UserAdministrationRepository.UserCoachProfileData;
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
	private final CoachProfileProvisioning coachProfileProvisioning;

	public UserAdministrationService(
			UserAdministrationRepository repository,
			PasswordEncoder passwordEncoder,
			AuditService auditService,
			CoachProfileProvisioning coachProfileProvisioning
	) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
		this.auditService = auditService;
		this.coachProfileProvisioning = coachProfileProvisioning;
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
		return repository.findUsers(principal.organizationId()).stream()
				.filter(user -> canViewUser(principal, user))
				.toList();
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
		String roleName = repository.roleName(command.roleId());
		requireAssignableRole(principal, roleName);
		List<UUID> branchIds = command.branchIds() == null ? List.of() : command.branchIds().stream().distinct().toList();
		requireBranchManagement(principal, branchIds);
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
		if (isCoachRole(normalizeRoleName(roleName))) {
			coachProfileProvisioning.synchronize(
					principal.organizationId(),
					userId,
					displayName(command.firstName(), command.lastName(), command.maternalLastName()),
					blankToNull(command.email()),
					blankToNull(command.phone()),
					branchIds,
					command.coachSpecialties(),
					blankToNull(command.coachBio()),
					true,
					principal.userId()
			);
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
		if (!repository.lockUser(principal.organizationId(), userId)) {
			throw new BusinessException("USER_NOT_FOUND", "El usuario no pertenece a este gimnasio.");
		}
		InternalUserSummary targetUser = requireManageableUser(principal, userId);
		repository.updateUserStatus(principal.organizationId(), userId, normalizedStatus, principal.userId(), OffsetDateTime.now());
		if (targetUser.roles().stream().map(this::normalizeRoleName).anyMatch(this::isCoachRole)) {
			synchronizeCoachProfile(principal, userId, "ACTIVE".equals(normalizedStatus));
		}
		auditService.record(principal, "INTERNAL_USER_STATUS_UPDATED", "USER", userId, "ADMIN", normalizedStatus, correlationId);
		return repository.findUsers(principal.organizationId()).stream()
				.filter(user -> user.id().equals(targetUser.id()))
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
		if (!repository.lockUser(principal.organizationId(), userId)) {
			throw new BusinessException("USER_NOT_FOUND", "El usuario no pertenece a este gimnasio.");
		}
		InternalUserSummary targetUser = requireManageableUser(principal, userId);
		if (!repository.roleBelongsToOrganization(principal.organizationId(), roleId)) {
			throw new BusinessException("ROLE_NOT_AVAILABLE", "El rol seleccionado no esta disponible para este gimnasio.");
		}
		if (repository.roleHasPermission(roleId, "platform.admin")) {
			throw new BusinessException("ROLE_NOT_AVAILABLE", "No puedes asignar roles de plataforma desde un gimnasio.");
		}
		String roleName = repository.roleName(roleId);
		requireAssignableRole(principal, roleName);
		repository.replaceRoles(userId, roleId);
		synchronizeCoachProfile(principal, userId, isCoachRole(normalizeRoleName(roleName)) && "ACTIVE".equals(targetUser.status()));
		auditService.record(principal, "INTERNAL_USER_ROLE_UPDATED", "USER", userId, "ADMIN", roleId.toString(), correlationId);
		return repository.findUsers(principal.organizationId()).stream()
				.filter(user -> user.id().equals(targetUser.id()))
				.findFirst()
				.orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "El usuario no esta disponible."));
	}

	private void synchronizeCoachProfile(GymOsPrincipal principal, UUID userId, boolean active) {
		UserCoachProfileData data = repository.userCoachProfileData(principal.organizationId(), userId)
				.orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "El usuario no esta disponible."));
		coachProfileProvisioning.synchronize(
				principal.organizationId(),
				data.userId(),
				data.displayName(),
				data.email(),
				data.phone(),
				data.branchIds(),
				null,
				null,
				active,
				principal.userId()
		);
	}

	private InternalUserSummary requireManageableUser(GymOsPrincipal principal, UUID userId) {
		InternalUserSummary user = repository.findUsers(principal.organizationId()).stream()
				.filter(candidate -> candidate.id().equals(userId))
				.findFirst()
				.orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "El usuario no esta disponible."));
		if (!canManageUser(principal, user.roles())) {
			throw new BusinessException("USER_NOT_MANAGEABLE", "Tu perfil no puede administrar ese usuario.");
		}
		requireBranchManagement(principal, user.branchIds());
		return user;
	}

	@Transactional(readOnly = true)
	public InternalUserSummary passwordRecoveryTarget(GymOsPrincipal principal, UUID userId) {
		requirePrincipal(principal);
		if (!principal.tenantIdentity() || !principal.permissions().contains("user.manage") || principal.userId().equals(userId)) {
			throw new org.springframework.security.access.AccessDeniedException("Staff password recovery is restricted");
		}
		InternalUserSummary user = requireManageableUser(principal, userId);
		if (!"ACTIVE".equals(user.status())) {
			throw new BusinessException("PASSWORD_RESET_USER_INACTIVE", "Reactiva explícitamente al usuario antes de recuperar su acceso.");
		}
		return user;
	}

	private void requirePrincipal(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private String displayName(String firstName, String lastName, String maternalLastName) {
		return String.join(" ", List.of(firstName, lastName, maternalLastName == null ? "" : maternalLastName).stream()
				.filter(value -> value != null && !value.isBlank())
				.map(String::trim)
				.toList());
	}

	private String normalizeStatus(String status) {
		String normalized = status == null ? "" : status.trim().toUpperCase();
		if (!List.of("ACTIVE", "SUSPENDED", "INACTIVE").contains(normalized)) {
			throw new BusinessException("USER_STATUS_NOT_AVAILABLE", "Usa un estado valido: ACTIVE, SUSPENDED o INACTIVE.");
		}
		return normalized;
	}

	private void requireAssignableRole(GymOsPrincipal principal, String roleName) {
		if (!canAssignRole(principal, roleName)) {
			throw new BusinessException("ROLE_NOT_ALLOWED", "Tu perfil no puede asignar ese rol.");
		}
	}

	private boolean canAssignRole(GymOsPrincipal principal, String targetRoleName) {
		String target = normalizeRoleName(targetRoleName);
		if (target.contains("admin") && target.contains("gymos")) {
			return false;
		}
		if (principal.platformSupport()) {
			return true;
		}
		boolean targetIsOwner = isOwnerRole(target);
		boolean targetIsReception = target.contains("recepcion") || target.contains("caja");
		boolean targetIsCoach = isCoachRole(target);
		if (isPrincipalOwner(principal)) {
			return !targetIsOwner;
		}
		if (isPrincipalManager(principal)) {
			return targetIsReception || targetIsCoach;
		}
		return false;
	}

	private boolean canViewUser(GymOsPrincipal principal, InternalUserSummary targetUser) {
		if (principal.platformSupport() || isPrincipalOwner(principal)) {
			return true;
		}
		if (isPrincipalManager(principal)) {
			return principal.branchId() != null && targetUser.branchIds().contains(principal.branchId())
					&& targetUser.roles().stream()
					.map(this::normalizeRoleName)
					.noneMatch(role -> isOwnerRole(role) || isManagerRole(role));
		}
		return principal.userId().equals(targetUser.id());
	}

	private boolean canManageUser(GymOsPrincipal principal, List<String> targetRoles) {
		if (principal.platformSupport()) {
			return true;
		}
		boolean targetIsOwner = targetRoles.stream()
				.map(this::normalizeRoleName)
				.anyMatch(this::isOwnerRole);
		if (targetIsOwner) {
			return false;
		}
		if (isPrincipalOwner(principal)) {
			return true;
		}
		if (isPrincipalManager(principal)) {
			return targetRoles.stream()
					.map(this::normalizeRoleName)
					.noneMatch(role -> isOwnerRole(role) || isManagerRole(role));
		}
		return false;
	}

	private boolean isPrincipalOwner(GymOsPrincipal principal) {
		return principal.roles().stream()
				.map(this::normalizeRoleName)
				.anyMatch(this::isOwnerRole);
	}

	private void requireBranchManagement(GymOsPrincipal principal, List<UUID> targetBranches) {
		if (isPrincipalManager(principal) && !isPrincipalOwner(principal) && !principal.platformSupport()
				&& (principal.branchId() == null || !targetBranches.contains(principal.branchId())
				    || !repository.activeBranchIds(principal.organizationId(), principal.userId()).containsAll(targetBranches))) {
			throw new org.springframework.security.access.AccessDeniedException("User operation exceeds the manager's assigned branches");
		}
	}

	private boolean isPrincipalManager(GymOsPrincipal principal) {
		return principal.roles().stream()
				.map(this::normalizeRoleName)
				.anyMatch(this::isManagerRole);
	}

	private boolean isManagerRole(String roleName) {
		return roleName.contains("gerente") || roleName.contains("gerencia");
	}

	private boolean isCoachRole(String roleName) {
		return roleName.contains("coach");
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
