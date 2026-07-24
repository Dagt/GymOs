package com.gymos.security.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.JwtService;
import com.gymos.security.infrastructure.AuthQueryRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;

class AuthRefreshSecurityTests {
	@Test
	void refreshRotatesTokenAndPreservesTenantContext() {
		AuthQueryRepository repository = mock(AuthQueryRepository.class);
		JwtService jwtService = mock(JwtService.class);
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		AuthService service = new AuthService(
				mock(AuthenticationManager.class), repository, jwtService, redis, mock(AuditService.class));

		UUID userId = UUID.randomUUID();
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		String previousToken = "previous-refresh";
		String storedSession = userId + "|" + organizationId + "|" + branchId + "|false";
		AuthQueryRepository.AuthenticatedUser user = user(userId, organizationId, Set.of("branch.read"));

		when(redis.opsForValue()).thenReturn(values);
		when(values.get("gymos:refresh:" + previousToken)).thenReturn(storedSession);
		when(repository.findActiveUserById(userId)).thenReturn(Optional.of(user));
		when(repository.organizationIsActive(organizationId)).thenReturn(true);
		when(repository.branchBelongsToOrganization(branchId, organizationId)).thenReturn(true);
		when(repository.userHasBranchAccess(userId, branchId)).thenReturn(true);
		when(jwtService.issueTokens(any(GymOsPrincipal.class), anyString()))
				.thenAnswer(invocation -> new JwtService.TokenPair("access", invocation.getArgument(1), 900));

		var result = service.refresh(previousToken);

		assertThat(result.user().organizationId()).isEqualTo(organizationId);
		assertThat(result.user().branchId()).isEqualTo(branchId);
		assertThat(result.refreshToken()).isNotEqualTo(previousToken);
		verify(values).set("gymos:refresh:" + result.refreshToken(), storedSession, Duration.ofDays(7));
		verify(values).set(
				"gymos:refresh:" + previousToken,
				"rotated:" + result.refreshToken(),
				Duration.ofSeconds(15)
		);
	}

	@Test
	void ownerCanRefreshHomeOrganizationWithoutBranchAssignment() {
		AuthQueryRepository repository = mock(AuthQueryRepository.class);
		JwtService jwtService = mock(JwtService.class);
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		AuthService service = new AuthService(
				mock(AuthenticationManager.class), repository, jwtService, redis, mock(AuditService.class));

		UUID userId = UUID.randomUUID();
		UUID organizationId = UUID.randomUUID();
		String refreshToken = "owner-refresh";
		String storedSession = userId + "|" + organizationId + "||false";

		when(redis.opsForValue()).thenReturn(values);
		when(values.get("gymos:refresh:" + refreshToken)).thenReturn(storedSession);
		when(repository.findActiveUserById(userId)).thenReturn(Optional.of(new AuthQueryRepository.AuthenticatedUser(
				userId, organizationId, "owner@gymos.local", "hash", "Owner", Set.of("organization.read"), Set.of("Dueño"))));
		when(repository.organizationIsActive(organizationId)).thenReturn(true);
		when(jwtService.issueTokens(any(GymOsPrincipal.class), anyString()))
				.thenAnswer(invocation -> new JwtService.TokenPair("access", invocation.getArgument(1), 900));

		var result = service.refresh(refreshToken);

		assertThat(result.user().organizationId()).isEqualTo(organizationId);
		assertThat(result.user().branchId()).isNull();
		verify(repository, never()).userHasOrganizationAccess(userId, organizationId);
	}

	@Test
	void concurrentRefreshWithinGraceReturnsTheReplacementToken() {
		AuthQueryRepository repository = mock(AuthQueryRepository.class);
		JwtService jwtService = mock(JwtService.class);
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		AuthService service = new AuthService(
				mock(AuthenticationManager.class), repository, jwtService, redis, mock(AuditService.class));

		UUID userId = UUID.randomUUID();
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		String previousToken = "previous-refresh";
		String replacementToken = "replacement-refresh";
		String storedSession = userId + "|" + organizationId + "|" + branchId + "|false";

		when(redis.opsForValue()).thenReturn(values);
		when(values.get("gymos:refresh:" + previousToken)).thenReturn("rotated:" + replacementToken);
		when(values.get("gymos:refresh:" + replacementToken)).thenReturn(storedSession);
		when(repository.findActiveUserById(userId)).thenReturn(Optional.of(user(userId, organizationId, Set.of("branch.read"))));
		when(repository.organizationIsActive(organizationId)).thenReturn(true);
		when(repository.branchBelongsToOrganization(branchId, organizationId)).thenReturn(true);
		when(repository.userHasBranchAccess(userId, branchId)).thenReturn(true);
		when(jwtService.issueTokens(any(GymOsPrincipal.class), eq(replacementToken)))
				.thenReturn(new JwtService.TokenPair("access", replacementToken, 900));

		var result = service.refresh(previousToken);

		assertThat(result.refreshToken()).isEqualTo(replacementToken);
		assertThat(result.user().organizationId()).isEqualTo(organizationId);
		assertThat(result.user().branchId()).isEqualTo(branchId);
	}

	@Test
	void expiredRotationGraceCannotBeUsedAgain() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		AuthService service = new AuthService(
				mock(AuthenticationManager.class), mock(AuthQueryRepository.class), mock(JwtService.class), redis, mock(AuditService.class));

		when(redis.opsForValue()).thenReturn(values);
		when(values.get("gymos:refresh:expired-refresh")).thenReturn(null);

		assertThatThrownBy(() -> service.refresh("expired-refresh"))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("REFRESH_TOKEN_INVALID"));
	}

	@Test
	void logoutDoesNotRevokeRefreshTokenOwnedByAnotherUser() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		AuditService auditService = mock(AuditService.class);
		AuthService service = new AuthService(
				mock(AuthenticationManager.class), mock(AuthQueryRepository.class), mock(JwtService.class), redis, auditService);

		UUID currentUserId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		UUID organizationId = UUID.randomUUID();
		String token = "foreign-refresh";
		GymOsPrincipal principal = new GymOsPrincipal(
				currentUserId, organizationId, null, "current@gymos.local", "Current User", Set.of());

		when(redis.opsForValue()).thenReturn(values);
		when(values.get("gymos:refresh:" + token))
				.thenReturn(otherUserId + "|" + organizationId + "||false");

		service.logout(principal, token, "logout-correlation");

		verify(redis, never()).delete("gymos:refresh:" + token);
		verify(auditService).record(
				principal, "AUTH_LOGOUT", "USER", currentUserId, "BACKOFFICE", null, "logout-correlation");
	}

	@Test
	void refreshRejectsBranchNoLongerAccessibleToUser() {
		AuthQueryRepository repository = mock(AuthQueryRepository.class);
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		AuthService service = new AuthService(
				mock(AuthenticationManager.class), repository, mock(JwtService.class), redis, mock(AuditService.class));

		UUID userId = UUID.randomUUID();
		UUID organizationId = UUID.randomUUID();
		UUID revokedBranchId = UUID.randomUUID();
		String token = "revoked-branch-refresh";
		when(redis.opsForValue()).thenReturn(values);
		when(values.get("gymos:refresh:" + token))
				.thenReturn(userId + "|" + organizationId + "|" + revokedBranchId + "|false");
		when(repository.findActiveUserById(userId)).thenReturn(Optional.of(user(userId, organizationId, Set.of())));
		when(repository.organizationIsActive(organizationId)).thenReturn(true);
		when(repository.branchBelongsToOrganization(revokedBranchId, organizationId)).thenReturn(true);
		when(repository.userHasBranchAccess(userId, revokedBranchId)).thenReturn(false);

		assertThatThrownBy(() -> service.refresh(token))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("REFRESH_TOKEN_INVALID"));
	}

	private AuthQueryRepository.AuthenticatedUser user(UUID userId, UUID organizationId, Set<String> permissions) {
		return new AuthQueryRepository.AuthenticatedUser(
				userId,
				organizationId,
				"user@gymos.local",
				"hash",
				"GymOS User",
				permissions,
				Set.of("Gerente")
		);
	}
}
