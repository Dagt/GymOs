package com.gymos.security.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.AccountScope;
import com.gymos.common.security.JwtService;
import com.gymos.security.infrastructure.AuthQueryRepository;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;

class AuthServiceTests {
	@Test
	void selectingBranchWithoutUserAccessIsDenied() {
		AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
		AuthQueryRepository authQueryRepository = mock(AuthQueryRepository.class);
		JwtService jwtService = mock(JwtService.class);
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		AuditService auditService = mock(AuditService.class);
		AuthService authService = new AuthService(authenticationManager, authQueryRepository, jwtService, redisTemplate, auditService);

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

		assertThatThrownBy(() -> authService.selectBranch(principal, foreignBranchId, "test-correlation"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("BRANCH_ACCESS_DENIED"));

		verify(authQueryRepository).branchBelongsToOrganization(foreignBranchId, organizationId);
		verifyNoInteractions(auditService);
	}

	@Test
	void platformAdminCurrentUserStartsInPlatformConsole() {
		AuthService authService = new AuthService(
				mock(AuthenticationManager.class),
				mock(AuthQueryRepository.class),
				mock(JwtService.class),
				mock(StringRedisTemplate.class),
				mock(AuditService.class)
		);
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
		AuthService authService = new AuthService(
				mock(AuthenticationManager.class),
				mock(AuthQueryRepository.class),
				mock(JwtService.class),
				mock(StringRedisTemplate.class),
				mock(AuditService.class)
		);
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
		AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
		AuthQueryRepository authQueryRepository = mock(AuthQueryRepository.class);
		JwtService jwtService = mock(JwtService.class);
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
		AuditService auditService = mock(AuditService.class);
		AuthService authService = new AuthService(authenticationManager, authQueryRepository, jwtService, redisTemplate, auditService);

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
		when(authQueryRepository.findActiveUserById(userId)).thenReturn(java.util.Optional.of(new AuthQueryRepository.AuthenticatedUser(
				userId,
				AccountScope.PLATFORM,
				null,
				"admin@gymos.local",
				"hash",
				"Admin GymOS",
				Set.of("platform.admin", "branch.read"),
				Set.of("Administrador GymOS")
		)));
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(jwtService.issueTokens(org.mockito.ArgumentMatchers.any(GymOsPrincipal.class), org.mockito.ArgumentMatchers.anyString()))
				.thenReturn(new JwtService.TokenPair("access", "refresh", 3600));

		var result = authService.selectBranch(principal, branchId, "support-correlation");

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
