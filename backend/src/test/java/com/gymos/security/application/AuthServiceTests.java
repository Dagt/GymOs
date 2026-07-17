package com.gymos.security.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.JwtService;
import com.gymos.security.infrastructure.AuthQueryRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
		when(authQueryRepository.userHasBranchAccess(userId, foreignBranchId)).thenReturn(false);

		assertThatThrownBy(() -> authService.selectBranch(principal, foreignBranchId, "test-correlation"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo("BRANCH_ACCESS_DENIED"));

		verify(authQueryRepository).userHasBranchAccess(userId, foreignBranchId);
		verifyNoInteractions(auditService);
	}
}

