package com.gymos.identity.api.operations;

import com.gymos.common.security.AccountScope;
import com.gymos.identity.application.IdentityProperties;
import com.gymos.identity.application.UserInvitationService;
import com.gymos.identity.infrastructure.IdentityProvisioningRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class TenantOwnerProvisioningAdapter implements TenantOwnerProvisioning {
	private final IdentityProvisioningRepository repository;
	private final PasswordEncoder passwordEncoder;
	private final IdentityProperties properties;
	private final SecureRandom secureRandom = new SecureRandom();

	TenantOwnerProvisioningAdapter(
			IdentityProvisioningRepository repository,
			PasswordEncoder passwordEncoder,
			IdentityProperties properties
	) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
	}

	@Override
	public OwnerInvitation provisionOwner(ProvisionOwner command) {
		String normalizedEmail = command.email().trim().toLowerCase(java.util.Locale.ROOT);
		if (repository.accountScopeForUsername(normalizedEmail).isPresent()) {
			throw new org.springframework.dao.DuplicateKeyException("Login identifier already exists");
		}
		UUID personId = repository.createPerson(
				AccountScope.TENANT, command.organizationId(), command.firstName(), command.lastName(),
				normalizedEmail, command.phone(), command.actorUserId(), command.now());
		String unusablePassword = randomToken();
		UUID userId = repository.createUser(
				AccountScope.TENANT, command.organizationId(), personId, normalizedEmail,
				passwordEncoder.encode(unusablePassword), "INVITED", command.actorUserId(), command.now());
		repository.assignRole(userId, repository.tenantOwnerRoleId(command.organizationId()));
		repository.assignBranch(userId, command.branchId());

		String invitationToken = randomToken();
		var expiresAt = command.now().plusHours(properties.invitationHours());
		repository.createInvitation(
				UUID.randomUUID(), userId, UserInvitationService.sha256(invitationToken), expiresAt,
				command.actorUserId(), command.now());
		return new OwnerInvitation(
				userId, properties.frontendBaseUrl() + "/accept-invitation?token=" + invitationToken, expiresAt);
	}

	private String randomToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
