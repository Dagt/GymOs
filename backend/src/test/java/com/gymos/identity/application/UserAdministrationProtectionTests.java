package com.gymos.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.coaching.api.operations.CoachProfileProvisioning;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.identity.application.result.InternalUserSummary;
import com.gymos.identity.application.result.RoleOption;
import com.gymos.identity.infrastructure.UserAdministrationRepository;
import com.gymos.identity.infrastructure.UserAdministrationRepository.UserCoachProfileData;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserAdministrationProtectionTests {
	private final UserAdministrationRepository repository = mock(UserAdministrationRepository.class);
	private final CoachProfileProvisioning coachProfiles = mock(CoachProfileProvisioning.class);
	private final UserAdministrationService service = new UserAdministrationService(
			repository,
			mock(PasswordEncoder.class),
			mock(AuditService.class),
			coachProfiles
	);

	@Test
	void ownerCannotChangeAnotherOwnerRole() {
		GymOsPrincipal principal = ownerPrincipal();
		UUID targetUserId = UUID.randomUUID();
		when(repository.userBelongsToOrganization(principal.organizationId(), targetUserId)).thenReturn(true);
		when(repository.findUsers(principal.organizationId())).thenReturn(List.of(ownerUser(targetUserId)));

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.updateRole(principal, targetUserId, UUID.randomUUID(), "correlation")
		);

		assertEquals("USER_NOT_MANAGEABLE", exception.code());
	}

	@Test
	void ownerCannotSuspendAnotherOwner() {
		GymOsPrincipal principal = ownerPrincipal();
		UUID targetUserId = UUID.randomUUID();
		when(repository.userBelongsToOrganization(principal.organizationId(), targetUserId)).thenReturn(true);
		when(repository.findUsers(principal.organizationId())).thenReturn(List.of(ownerUser(targetUserId)));

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.updateStatus(principal, targetUserId, "SUSPENDED", "correlation")
		);

		assertEquals("USER_NOT_MANAGEABLE", exception.code());
	}

	@Test
	void managerCanOnlyAssignReceptionAndCoachRoles() {
		GymOsPrincipal principal = managerPrincipal();
		UUID receptionRoleId = UUID.randomUUID();
		UUID coachRoleId = UUID.randomUUID();
		when(repository.findRoles(principal.organizationId(), false)).thenReturn(List.of(
				new RoleOption(UUID.randomUUID(), "Dueno del gimnasio", "Owner", false, 50),
				new RoleOption(UUID.randomUUID(), "Gerente", "Manager", false, 30),
				new RoleOption(receptionRoleId, "Recepcion", "Reception", false, 20),
				new RoleOption(coachRoleId, "Coach", "Coach", false, 10),
				new RoleOption(UUID.randomUUID(), "Administrador GymOS", "Platform", true, 99)
		));

		List<RoleOption> roles = service.roles(principal);

		assertEquals(List.of("Recepcion", "Coach"), roles.stream().map(RoleOption::name).toList());
	}

	@Test
	void assigningCoachRoleProvisionsOperationalProfiles() {
		GymOsPrincipal principal = ownerPrincipal();
		UUID targetUserId = UUID.randomUUID();
		UUID roleId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		InternalUserSummary target = new InternalUserSummary(
				targetUserId,
				"Coach Uno",
				"coach.uno",
				"coach@example.com",
				"+521234567890",
				"ACTIVE",
				List.of("Recepcion"),
				List.of("Centro")
		);
		when(repository.userBelongsToOrganization(principal.organizationId(), targetUserId)).thenReturn(true);
		when(repository.findUsers(principal.organizationId())).thenReturn(List.of(target));
		when(repository.roleBelongsToOrganization(principal.organizationId(), roleId)).thenReturn(true);
		when(repository.roleHasPermission(roleId, "platform.admin")).thenReturn(false);
		when(repository.roleName(roleId)).thenReturn("Coach");
		when(repository.userCoachProfileData(principal.organizationId(), targetUserId)).thenReturn(java.util.Optional.of(
				new UserCoachProfileData(
						targetUserId,
						"Coach Uno",
						"coach@example.com",
						"+521234567890",
						List.of(branchId)
				)
		));

		service.updateRole(principal, targetUserId, roleId, "correlation");

		verify(repository).replaceRoles(targetUserId, roleId);
		verify(coachProfiles).synchronize(
				principal.organizationId(),
				targetUserId,
				"Coach Uno",
				"coach@example.com",
				"+521234567890",
				List.of(branchId),
				true,
				principal.userId()
		);
	}

	private GymOsPrincipal ownerPrincipal() {
		return new GymOsPrincipal(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"owner",
				"Owner",
				Set.of("users.manage"),
				Set.of("Dueno del gimnasio"),
				false
		);
	}

	private GymOsPrincipal managerPrincipal() {
		return new GymOsPrincipal(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"manager",
				"Manager",
				Set.of("user.read", "user.manage"),
				Set.of("Gerente"),
				false
		);
	}

	private InternalUserSummary ownerUser(UUID userId) {
		return new InternalUserSummary(
				userId,
				"Otro Dueño",
				"owner.two",
				"owner2@example.com",
				null,
				"ACTIVE",
				List.of("Dueno del gimnasio"),
				List.of("Centro")
		);
	}
}
