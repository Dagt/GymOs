package com.gymos.identity.api.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.AccountScope;
import com.gymos.common.security.StrongPasswordPolicy;
import com.gymos.identity.api.operations.PlatformAdministratorBootstrap.BootstrapCommand;
import com.gymos.identity.infrastructure.IdentityProvisioningRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PlatformAdministratorBootstrapTests {
	private final IdentityProvisioningRepository repository = mock(IdentityProvisioningRepository.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final StrongPasswordPolicy passwordPolicy = new StrongPasswordPolicy();
	private final AuditService auditService = mock(AuditService.class);
	private final PlatformAdministratorBootstrap service = new PlatformAdministratorBootstrapAdapter(
			repository, passwordEncoder, passwordPolicy, auditService);

	@Test
	void disabledBootstrapDoesNothing() {
		assertThat(service.bootstrap(new BootstrapCommand(false, null, null, null))).isFalse();
		verifyNoInteractions(repository, passwordEncoder, auditService);
	}

	@Test
	void existingPlatformAdministratorIsNeverModifiedOrReset() {
		when(repository.platformAdministratorExists()).thenReturn(true);

		assertThat(service.bootstrap(new BootstrapCommand(
				true, "platform@gymfort.mx", "DifferentSecret1!", "Admin GymFort"))).isFalse();

		verify(repository).platformAdministratorExists();
		verifyNoInteractions(passwordEncoder, auditService);
	}

	@Test
	void createsAPlatformIdentityWithoutTenantAndAuditsNoSecret() {
		UUID personId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID roleId = UUID.randomUUID();
		String secret = "VeryStrongSecret1!";
		when(repository.platformAdministratorExists()).thenReturn(false);
		when(repository.accountScopeForUsername("platform@gymfort.mx")).thenReturn(Optional.empty());
		when(passwordEncoder.encode(secret)).thenReturn("bcrypt-hash");
		when(repository.createPerson(
				eq(AccountScope.PLATFORM), eq(null), eq("Admin"), eq("GymFort"),
				eq("platform@gymfort.mx"), eq(null), eq(null), any(OffsetDateTime.class)))
				.thenReturn(personId);
		when(repository.createUser(
				eq(AccountScope.PLATFORM), eq(null), eq(personId), eq("platform@gymfort.mx"),
				eq("bcrypt-hash"), eq("ACTIVE"), eq(null), any(OffsetDateTime.class)))
				.thenReturn(userId);
		when(repository.platformAdministratorRoleId()).thenReturn(roleId);

		assertThat(service.bootstrap(new BootstrapCommand(
				true, "Platform@GymFort.mx", secret, "Admin GymFort"))).isTrue();

		verify(repository).assignRole(userId, roleId);
		verify(auditService).recordSystem(
				eq(null), eq(null), eq(userId), eq("PLATFORM_ADMIN_BOOTSTRAPPED"), eq("USER"),
				eq(userId), eq("SYSTEM_BOOTSTRAP"), eq(null), eq("platform-bootstrap"));
		verify(auditService, never()).recordSystem(
				any(), any(), any(), any(), any(), any(), any(), eq(secret), any());
	}

	@Test
	void weakBootstrapPasswordFailsBeforeWritingIdentity() {
		when(repository.platformAdministratorExists()).thenReturn(false);

		assertThatThrownBy(() -> service.bootstrap(new BootstrapCommand(
				true, "platform@gymfort.mx", "weak", "Admin GymFort")))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("PASSWORD_POLICY_NOT_MET"));

		verify(repository, never()).createPerson(any(), any(), any(), any(), any(), any(), any(), any());
	}
}
