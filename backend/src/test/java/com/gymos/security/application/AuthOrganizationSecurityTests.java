package com.gymos.security.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gymos.common.api.ApiAuthenticationException;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.AccountScope;
import com.gymos.security.infrastructure.AuthQueryRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthOrganizationSecurityTests {
	@Test
	void platformAdminCannotSelectInactiveOrUnknownOrganization() {
		var fixture = new AuthSessionTestFixture();
		var repository = fixture.users;
		var service = fixture.service;

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

		assertThatThrownBy(() -> service.selectOrganization(principal, requestedOrganizationId, "Validación de soporte", null, "correlation"))
				.isInstanceOfSatisfying(BusinessException.class, exception ->
						assertThat(exception.code()).isEqualTo("ORGANIZATION_SUSPENDED"));

		verify(repository).organizationIsActive(requestedOrganizationId);
		verify(repository, never()).findActiveUserById(userId);
		verifyNoInteractions(fixture.sessions, fixture.audit);
	}

	@Test
	void supportSessionCannotRefreshAfterOrganizationIsSuspended() {
		var fixture = new AuthSessionTestFixture();
		var repository = fixture.users;
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		var user = new AuthQueryRepository.AuthenticatedUser(UUID.randomUUID(), AccountScope.PLATFORM, null,
				"platform", "hash", "Platform Admin", Set.of("platform.admin", "branch.read"), Set.of("Admin"));
		var active = fixture.session(user, user.toSupportPrincipal(organizationId, branchId, Instant.now().plusSeconds(600)));
		when(repository.findActiveUserById(user.userId())).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> fixture.service.refresh(active.cookie(), UUID.randomUUID(), "test"))
				.isInstanceOfSatisfying(ApiAuthenticationException.class,
						exception -> assertThat(exception.code()).isEqualTo("REFRESH_TOKEN_INVALID"));

		verify(repository).organizationIsActive(organizationId);
		verify(repository, never()).branchBelongsToOrganization(branchId, organizationId);
		verify(fixture.sessions).revokeContext(active.id(), 0);
		verifyNoInteractions(fixture.audit);
	}
}
