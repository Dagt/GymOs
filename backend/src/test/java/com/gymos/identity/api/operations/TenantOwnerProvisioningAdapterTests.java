package com.gymos.identity.api.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.common.api.BusinessException;
import com.gymos.identity.application.IdentityProperties;
import com.gymos.identity.application.UserInvitationService;
import com.gymos.identity.infrastructure.IdentityProvisioningRepository;
import com.gymos.identity.infrastructure.IdentityProvisioningRepository.LatestInvitationRecord;
import com.gymos.identity.infrastructure.IdentityProvisioningRepository.OwnerAccountRecord;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class TenantOwnerProvisioningAdapterTests {
	private final IdentityProvisioningRepository repository = mock(IdentityProvisioningRepository.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final TenantOwnerProvisioningAdapter service = new TenantOwnerProvisioningAdapter(
			repository, passwordEncoder, new IdentityProperties(72, 60, "https://gymfort.mx"));

	@BeforeEach
	void setUp() {
		when(repository.accountScopeForUsername(any())).thenReturn(Optional.empty());
		when(repository.createPerson(any(), any(), any(), any(), any(), any(), any(), any()))
				.thenReturn(UUID.randomUUID());
		when(repository.createUser(any(), any(), any(), any(), any(), any(), any(), any()))
				.thenReturn(UUID.randomUUID());
		when(repository.tenantOwnerRoleId(any())).thenReturn(UUID.randomUUID());
		when(passwordEncoder.encode(any())).thenReturn("unusable-password-hash");
	}

	@Test
	void provisionsOnlyTheInvitationHashAndReturnsTheOneTimeLink() {
		UUID organizationId = UUID.randomUUID();
		var invitation = service.provisionOwner(new TenantOwnerProvisioning.ProvisionOwner(
				organizationId, UUID.randomUUID(), UUID.randomUUID(), "Ana", "Dueña",
				"ANA@EXAMPLE.COM", "+525512345678", OffsetDateTime.now()));

		String token = URI.create(invitation.invitationUrl()).getQuery().substring("token=".length());
		ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
		verify(repository).createInvitation(
				eq(invitation.invitationId()), eq(invitation.userId()), hash.capture(),
				eq(invitation.expiresAt()), any(), any());
		assertThat(hash.getValue()).isEqualTo(UserInvitationService.sha256(token));
		assertThat(hash.getValue()).doesNotContain(token);
		assertThat(invitation.ownerEmail()).isEqualTo("ana@example.com");
		assertThat(invitation.emailDeliveryStatus()).isEqualTo("PENDING");
	}

	@Test
	void expiredInvitationIsRevokedBeforeCreatingANewToken() {
		UUID organizationId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		when(repository.lockOwnerAccount(organizationId)).thenReturn(Optional.of(
				new OwnerAccountRecord(userId, organizationId, "INVITED", "ana@example.com", "Ana Dueña")));
		when(repository.latestInvitation(userId)).thenReturn(Optional.of(new LatestInvitationRecord(
				UUID.randomUUID(), "PENDING", now.minusMinutes(1), "SENT", now.minusHours(72))));

		var invitation = service.reissueOwnerInvitation(
				new TenantOwnerProvisioning.ReissueOwnerInvitation(organizationId, UUID.randomUUID(), now));

		var order = inOrder(repository);
		order.verify(repository).revokePendingInvitations(userId, now);
		order.verify(repository).createInvitation(
				eq(invitation.invitationId()), eq(userId), any(), eq(invitation.expiresAt()), any(), eq(now));
		assertThat(invitation.invitationUrl()).startsWith("https://gymfort.mx/accept-invitation?token=");
	}

	@Test
	void recentlySentInvitationCannotBeRotatedRepeatedly() {
		UUID organizationId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		when(repository.lockOwnerAccount(organizationId)).thenReturn(Optional.of(
				new OwnerAccountRecord(userId, organizationId, "INVITED", "ana@example.com", "Ana Dueña")));
		when(repository.latestInvitation(userId)).thenReturn(Optional.of(new LatestInvitationRecord(
				UUID.randomUUID(), "PENDING", now.plusHours(72), "SENT", now.minusSeconds(10))));

		assertThatThrownBy(() -> service.reissueOwnerInvitation(
				new TenantOwnerProvisioning.ReissueOwnerInvitation(organizationId, UUID.randomUUID(), now)))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code())
								.isEqualTo("TENANT_OWNER_INVITATION_RESEND_TOO_SOON"));
		verify(repository, never()).revokePendingInvitations(any(), any());
	}

	@Test
	void activeOwnerDoesNotReceiveAnotherInvitation() {
		UUID organizationId = UUID.randomUUID();
		when(repository.lockOwnerAccount(organizationId)).thenReturn(Optional.of(
				new OwnerAccountRecord(
						UUID.randomUUID(), organizationId, "ACTIVE", "ana@example.com", "Ana Dueña")));

		assertThatThrownBy(() -> service.reissueOwnerInvitation(
				new TenantOwnerProvisioning.ReissueOwnerInvitation(
						organizationId, UUID.randomUUID(), OffsetDateTime.now())))
				.isInstanceOfSatisfying(BusinessException.class,
						exception -> assertThat(exception.code())
								.isEqualTo("TENANT_OWNER_INVITATION_ALREADY_ACCEPTED"));
	}
}
