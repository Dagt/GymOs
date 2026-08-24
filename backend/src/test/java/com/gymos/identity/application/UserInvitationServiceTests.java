package com.gymos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.AccountScope;
import com.gymos.common.security.StrongPasswordPolicy;
import com.gymos.identity.infrastructure.IdentityProvisioningRepository;
import com.gymos.identity.infrastructure.IdentityProvisioningRepository.InvitationRecord;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserInvitationServiceTests {
	@Test
	void acceptsAValidTenantInvitationWithoutPersistingThePlainPassword() {
		IdentityProvisioningRepository repository = mock(IdentityProvisioningRepository.class);
		PasswordEncoder encoder = mock(PasswordEncoder.class);
		AuditService audit = mock(AuditService.class);
		UserInvitationService service = new UserInvitationService(
				repository, encoder, new StrongPasswordPolicy(), audit);
		UUID invitationId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		UUID organizationId = UUID.randomUUID();
		String token = "one-time-token";
		String password = "StrongOwnerPass1!";
		when(repository.lockInvitation(UserInvitationService.sha256(token))).thenReturn(Optional.of(
				new InvitationRecord(
						invitationId, userId, "PENDING", OffsetDateTime.now().plusHours(1),
						AccountScope.TENANT, organizationId)));
		when(encoder.encode(password)).thenReturn("encoded-password");
		when(repository.acceptInvitation(eq(invitationId), eq(userId), eq("encoded-password"), any()))
				.thenReturn(1);

		service.accept(token, password, "correlation");

		verify(repository, never()).acceptInvitation(eq(invitationId), eq(userId), eq(password), any());
		verify(audit).recordSystem(
				eq(organizationId), eq(null), eq(userId), eq("USER_INVITATION_ACCEPTED"), eq("USER"),
				eq(userId), eq("PUBLIC_INVITATION"), eq(null), eq("correlation"));
	}

	@Test
	void missingAndConsumedTokensShareTheSameGenericError() {
		IdentityProvisioningRepository repository = mock(IdentityProvisioningRepository.class);
		UserInvitationService service = new UserInvitationService(
				repository, mock(PasswordEncoder.class), new StrongPasswordPolicy(), mock(AuditService.class));
		String password = "StrongOwnerPass1!";
		when(repository.lockInvitation(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.accept("unknown", password, "correlation"))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code()).isEqualTo("USER_INVITATION_INVALID"));
	}
}
