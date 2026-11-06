package com.gymos.security.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.AccountScope;
import com.gymos.common.security.SessionRegistry;
import com.gymos.security.infrastructure.AuthQueryRepository;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthServiceTests {
	@Test
	void selectingBranchWithoutUserAccessIsDenied() {
		var fixture = new AuthSessionTestFixture();
		var authQueryRepository = fixture.users;
		var authService = fixture.service;
		var auditService = fixture.audit;

		UUID userId = UUID.randomUUID();
		UUID organizationId = UUID.randomUUID();
		UUID foreignBranchId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				userId,
				organizationId,
				null,
				"admin@gymos.local",
				"Admin GymOS",
				Set.of("branch.read")
		);
		when(authQueryRepository.branchBelongsToOrganization(foreignBranchId, organizationId)).thenReturn(false);
		when(authQueryRepository.findActiveUserById(userId)).thenReturn(java.util.Optional.of(
				new AuthQueryRepository.AuthenticatedUser(
						userId, AccountScope.TENANT, organizationId, "admin@gymos.local", "hash",
						"Admin GymOS", Set.of("branch.read"), Set.of("Gerente"))));

		assertThatThrownBy(() -> authService.selectBranch(principal, foreignBranchId, null, "test-correlation"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("BRANCH_ACCESS_DENIED"));

		verify(authQueryRepository).branchBelongsToOrganization(foreignBranchId, organizationId);
		verifyNoInteractions(auditService);
	}

	@Test
	void platformAdminCurrentUserStartsInPlatformConsole() {
		AuthService authService = new AuthSessionTestFixture().service;
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(),
				AccountScope.PLATFORM,
				null,
				null,
				"admin@gymos.local",
				"Admin GymOS",
				Set.of("platform.admin", "organization.read"),
				Set.of("Administrador GymOS"),
				false,
				null
		);

		var currentUser = authService.currentUser(principal);

		assertThat(currentUser.platformAdmin()).isTrue();
		assertThat(currentUser.supportMode()).isFalse();
		assertThat(currentUser.console()).isEqualTo("PLATFORM_OWNER");
	}

	@Test
	void platformAdminInSupportModeUsesTenantOwnerConsole() {
		AuthService authService = new AuthSessionTestFixture().service;
		GymOsPrincipal principal = new GymOsPrincipal(
				UUID.randomUUID(),
				AccountScope.PLATFORM,
				UUID.randomUUID(),
				UUID.randomUUID(),
				"admin@gymos.local",
				"Admin GymOS",
				Set.of("organization.read", "user.manage"),
				Set.of("Administrador GymOS"),
				true,
				Instant.now().plusSeconds(600)
		);

		var currentUser = authService.currentUser(principal);

		assertThat(currentUser.platformAdmin()).isTrue();
		assertThat(currentUser.supportMode()).isTrue();
		assertThat(currentUser.console()).isEqualTo("TENANT_OWNER");
	}

	@Test
	void supportModeCanSelectTenantBranchWithoutUserBranchAccess() {
		var fixture = new AuthSessionTestFixture();
		var authQueryRepository = fixture.users;
		var authService = fixture.service;
		var auditService = fixture.audit;

		UUID userId = UUID.randomUUID();
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				userId,
				AccountScope.PLATFORM,
				organizationId,
				null,
				"admin@gymos.local",
				"Admin GymOS",
				Set.of("branch.read"),
				Set.of("Administrador GymOS"),
				true,
				Instant.now().plusSeconds(600)
		);
		when(authQueryRepository.branchBelongsToOrganization(branchId, organizationId)).thenReturn(true);
		when(authQueryRepository.userHasBranchAccess(userId, branchId)).thenReturn(false);
		var user = new AuthQueryRepository.AuthenticatedUser(
				userId,
				AccountScope.PLATFORM,
				null,
				"admin@gymos.local",
				"hash",
				"Admin GymOS",
				Set.of("platform.admin", "branch.read"),
				Set.of("Administrador GymOS")
		);
		when(authQueryRepository.findActiveUserById(userId)).thenReturn(java.util.Optional.of(user));
		var active = fixture.session(user, principal);
		when(fixture.sessions.rotate(org.mockito.ArgumentMatchers.eq(active.id()), org.mockito.ArgumentMatchers.eq(0L),
				org.mockito.ArgumentMatchers.eq(0L), org.mockito.ArgumentMatchers.eq("!"),
				org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyString()))
				.thenReturn(SessionRegistry.Rotation.NEXT);

		var result = authService.selectBranch(active.principal(), branchId, active.cookie(), "support-correlation");

		assertThat(result.user().supportMode()).isTrue();
		assertThat(result.user().branchId()).isEqualTo(branchId);
		verify(auditService).recordSystem(
				organizationId,
				branchId,
				userId,
				"BRANCH_SELECTED",
				"BRANCH",
				branchId,
				"PLATFORM_SUPPORT",
				null,
				"support-correlation"
		);
	}
}
