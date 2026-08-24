package com.gymos.security.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.AccountScope;
import com.gymos.common.security.JwtService;
import com.gymos.security.infrastructure.AuthQueryRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;

class AuthOrganizationSecurityTests {
	@Test
	void platformAdminCannotSelectInactiveOrUnknownOrganization() {
		AuthQueryRepository repository = mock(AuthQueryRepository.class);
		JwtService jwtService = mock(JwtService.class);
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		AuditService auditService = mock(AuditService.class);
		AuthService service = new AuthService(
				mock(AuthenticationManager.class), repository, jwtService, redisTemplate, auditService
		);
		UUID userId = UUID.randomUUID();
		UUID requestedOrganizationId = UUID.randomUUID();
		GymOsPrincipal principal = new GymOsPrincipal(
				userId,
				AccountScope.PLATFORM,
				null,
				null,
				"platform@gymos.local",
				"Platform Admin",
				Set.of("platform.admin", "organization.read"),
				Set.of("Administrador GymOS"),
				false,
				null
		);
		when(repository.organizationIsActive(requestedOrganizationId)).thenReturn(false);

		assertThatThrownBy(() -> service.selectOrganization(principal, requestedOrganizationId, "correlation"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("ORGANIZATION_SUSPENDED"));

		verify(repository).organizationIsActive(requestedOrganizationId);
		verify(repository, never()).findActiveUserById(userId);
		verifyNoInteractions(jwtService, redisTemplate, auditService);
	}

	@Test
	void supportSessionCannotRefreshAfterOrganizationIsSuspended() {
		AuthQueryRepository repository = mock(AuthQueryRepository.class);
		JwtService jwtService = mock(JwtService.class);
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		AuditService auditService = mock(AuditService.class);
		AuthService service = new AuthService(
				mock(AuthenticationManager.class), repository, jwtService, redisTemplate, auditService
		);
		UUID userId = UUID.randomUUID();
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		String refreshToken = "support-refresh";
		Instant expiresAt = Instant.now().plusSeconds(600);
		String storedSession = "v2|" + userId + "|PLATFORM|" + organizationId + "|" + branchId + "|true|" + expiresAt.getEpochSecond();
		when(redisTemplate.opsForValue()).thenReturn(values);
		when(values.get("gymos:refresh:v2:" + refreshToken)).thenReturn(storedSession);
		when(repository.findActiveUserById(userId)).thenReturn(Optional.of(new AuthQueryRepository.AuthenticatedUser(
				userId,
				AccountScope.PLATFORM,
				null,
				"platform@gymos.local",
				"hash",
				"Platform Admin",
				Set.of("platform.admin", "organization.read"),
				Set.of("Administrador GymOS")
		)));
		when(repository.organizationIsActive(organizationId)).thenReturn(false);

		assertThatThrownBy(() -> service.refresh(refreshToken))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("REFRESH_TOKEN_INVALID"));

		verify(repository).organizationIsActive(organizationId);
		verify(repository, never()).branchBelongsToOrganization(branchId, organizationId);
		verifyNoInteractions(jwtService, auditService);
	}
}
