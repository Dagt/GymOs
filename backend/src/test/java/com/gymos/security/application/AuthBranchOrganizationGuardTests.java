package com.gymos.security.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.security.infrastructure.AuthQueryRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class AuthBranchOrganizationGuardTests {
	@Test
	void selectingBranchFromAnotherOrganizationIsDenied() {
		AuthQueryRepository repository = mock(AuthQueryRepository.class);
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		AuthBranchOrganizationGuard guard = new AuthBranchOrganizationGuard(repository, redisTemplate);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		GymOsPrincipal principal = principal(organizationId);
		when(repository.branchBelongsToOrganization(branchId, organizationId)).thenReturn(false);

		assertThatThrownBy(() -> guard.guardBranchSelection(principal, branchId))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("BRANCH_ACCESS_DENIED"));
	}

	@Test
	void refreshingSessionWithBranchFromAnotherOrganizationIsDenied() {
		AuthQueryRepository repository = mock(AuthQueryRepository.class);
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> values = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(values);
		AuthBranchOrganizationGuard guard = new AuthBranchOrganizationGuard(repository, redisTemplate);
		UUID userId = UUID.randomUUID();
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		String refreshToken = "refresh-test";
		when(values.get("gymos:refresh:v2:" + refreshToken))
				.thenReturn("v2|" + userId + "|TENANT|" + organizationId + "|" + branchId + "|false|");
		when(repository.branchBelongsToOrganization(branchId, organizationId)).thenReturn(false);

		assertThatThrownBy(() -> guard.guardRefreshSession(refreshToken))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("REFRESH_TOKEN_INVALID"));
	}

	private GymOsPrincipal principal(UUID organizationId) {
		return new GymOsPrincipal(
				UUID.randomUUID(),
				organizationId,
				UUID.randomUUID(),
				"owner@gymos.local",
				"Owner",
				Set.of("branch.select")
		);
	}
}
