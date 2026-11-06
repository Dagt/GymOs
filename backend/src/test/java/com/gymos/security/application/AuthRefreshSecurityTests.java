package com.gymos.security.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gymos.common.api.ApiAuthenticationException;
import com.gymos.common.security.AccountScope;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.SessionRegistry;
import com.gymos.security.infrastructure.AuthQueryRepository.AuthenticatedUser;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthRefreshSecurityTests {
	private final AuthSessionTestFixture fixture = new AuthSessionTestFixture();
	private final AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), AccountScope.TENANT,
			UUID.randomUUID(), "coach", "hash", "Coach", Set.of("workout.read.own"), Set.of("Coach"), 0);

	@Test
	void legacyAndManipulatedRefreshNeverReadRedis() {
		String signed = fixture.credentials.issue(UUID.randomUUID(), 0);
		for (String token : Set.of("v2-token", signed.replace(".0.", ".1."))) {
			assertThatThrownBy(() -> fixture.service.refresh(token, UUID.randomUUID(), "test"))
					.isInstanceOf(ApiAuthenticationException.class);
		}
		verifyNoInteractions(fixture.sessions, fixture.users);
	}

	@Test
	void passwordRecoveryDuringLoginCannotIssueANewGenerationTokenUsingOldPassword() {
		var reset = new AuthenticatedUser(user.userId(), AccountScope.TENANT, user.organizationId(),
				user.username(), "newhash", user.fullName(), user.permissions(), user.roles(), 1);
		when(fixture.users.findActiveUserByLoginIdentifier("coach")).thenReturn(Optional.of(user), Optional.of(reset));
		assertThatThrownBy(() -> fixture.service.login("coach", "OldPassword123!", null, "test"))
				.isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
		verifyNoInteractions(fixture.sessions);
	}

	@Test
	void removedPermissionOrDisabledAccountRevokesTheFamily() {
		var reduced = new AuthenticatedUser(user.userId(), AccountScope.TENANT, user.organizationId(),
				user.username(), user.passwordHash(), user.fullName(), Set.of(), user.roles(), user.credentialVersion());
		for (Optional<AuthenticatedUser> lookup : java.util.List.of(Optional.of(reduced), Optional.<AuthenticatedUser>empty())) {
			var active = fixture.session(user, user.toPrincipal(null));
			when(fixture.users.findActiveUserById(user.userId())).thenReturn(lookup);
			assertThatThrownBy(() -> fixture.service.refresh(active.cookie(), UUID.randomUUID(), "test"))
					.isInstanceOf(ApiAuthenticationException.class);
			verify(fixture.sessions).revokeContext(active.id(), 0);
		}
	}

	@Test
	void crossTenantBranchInStoredSessionIsRejected() {
		UUID branch = UUID.randomUUID();
		var active = fixture.session(user, user.toPrincipal(branch));
		when(fixture.users.findActiveUserById(user.userId())).thenReturn(Optional.of(user));
		when(fixture.users.organizationIsActive(user.organizationId())).thenReturn(true);
		when(fixture.users.userHasBranchAccess(user.userId(), branch)).thenReturn(true);
		assertThatThrownBy(() -> fixture.service.refresh(active.cookie(), UUID.randomUUID(), "test"))
				.isInstanceOf(ApiAuthenticationException.class);
		verify(fixture.sessions).revokeContext(active.id(), 0);
	}

	@Test
	void cachedPlatformScopeCannotPromoteATenantAccount() {
		var platform = new GymOsPrincipal(user.userId(), AccountScope.PLATFORM, null, null, user.username(), user.fullName(),
				Set.of("platform.admin"), Set.of("Admin"), false, null);
		var active = fixture.session(user, platform);
		when(fixture.users.findActiveUserById(user.userId())).thenReturn(Optional.of(user));
		assertThatThrownBy(() -> fixture.service.refresh(active.cookie(), UUID.randomUUID(), "test"))
				.isInstanceOf(ApiAuthenticationException.class);
	}

	@Test
	void tenantWithoutBranchCanRenewTheSameOrganization() {
		var active = fixture.session(user, user.toPrincipal(null));
		var operation = UUID.randomUUID();
		when(fixture.users.findActiveUserById(user.userId())).thenReturn(Optional.of(user));
		when(fixture.users.organizationIsActive(user.organizationId())).thenReturn(true);
		when(fixture.sessions.find(active.id())).thenReturn(
				Optional.of(new SessionRegistry.State(0, 0, active.context())),
				Optional.of(new SessionRegistry.State(1, 0, active.context())));
		when(fixture.sessions.rotate(active.id(), 0, 0, operation.toString(), 0, active.context()))
				.thenReturn(SessionRegistry.Rotation.NEXT);
		var renewed = fixture.service.refresh(active.cookie(), operation, "test");
		assertThat(renewed.user().branchId()).isNull();
		assertThat(renewed.user().organizationId()).isEqualTo(user.organizationId());
		assertThat(renewed.refreshToken()).isEqualTo(fixture.credentials.issue(active.id(), 1));
	}

	@Test
	void expiredSupportCannotBeRenewedIntoPlatformAccess() {
		var admin = new AuthenticatedUser(UUID.randomUUID(), AccountScope.PLATFORM, null, "admin", "hash",
				"Admin", Set.of("platform.admin", "branch.read"), Set.of("Admin"));
		var active = fixture.session(admin, admin.toSupportPrincipal(UUID.randomUUID(), null, Instant.EPOCH));
		when(fixture.users.findActiveUserById(admin.userId())).thenReturn(Optional.of(admin));
		assertThatThrownBy(() -> fixture.service.refresh(active.cookie(), UUID.randomUUID(), "test"))
				.isInstanceOf(ApiAuthenticationException.class);
		verify(fixture.sessions).revokeContext(active.id(), 0);
	}
}
