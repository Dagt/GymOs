package com.gymos.identity.api.operations;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.AccountScope;
import com.gymos.identity.application.IdentityProperties;
import com.gymos.identity.application.UserInvitationService;
import com.gymos.identity.infrastructure.IdentityProvisioningRepository;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class TenantOwnerProvisioningAdapter implements TenantOwnerProvisioning {
	private static final Set<String> EMAIL_DELIVERY_STATUSES = Set.of(
			"PENDING", "SENT", "FAILED", "NOT_CONFIGURED");
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
	@Transactional
	public OwnerInvitation provisionOwner(ProvisionOwner command) {
		String normalizedEmail = command.email().trim().toLowerCase(java.util.Locale.ROOT);
		if (repository.accountScopeForUsername(normalizedEmail).isPresent()) {
			throw new BusinessException(
					"TENANT_OWNER_EMAIL_ALREADY_EXISTS",
					"Ese correo ya tiene una cuenta de acceso. Usa otro correo o gestiona la cuenta existente.",
					Map.of("field", "ownerEmail"));
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

		return createInvitation(
				userId,
				(command.firstName().trim() + " " + command.lastName().trim()).trim(),
				normalizedEmail,
				command.actorUserId(),
				command.now());
	}

	@Override
	@Transactional
	public OwnerInvitation reissueOwnerInvitation(ReissueOwnerInvitation command) {
		var owner = repository.lockOwnerAccount(command.organizationId())
				.orElseThrow(() -> new BusinessException(
						"TENANT_OWNER_NOT_FOUND",
						"El gimnasio no tiene una cuenta de dueño disponible para invitar."));
		if (!"INVITED".equals(owner.accountStatus())) {
			throw new BusinessException(
					"TENANT_OWNER_INVITATION_ALREADY_ACCEPTED",
					"La cuenta del dueño ya fue activada; no necesita una nueva invitación.");
		}

		repository.latestInvitation(owner.userId()).ifPresent(latest -> {
			long cooldown = properties.invitationResendCooldownSeconds();
			boolean deliveryCouldStillBeInFlight = Set.of("PENDING", "SENT")
					.contains(latest.emailDeliveryStatus());
			if (cooldown > 0
					&& "PENDING".equals(latest.status())
					&& deliveryCouldStillBeInFlight
					&& command.now().isBefore(latest.createdAt().plusSeconds(cooldown))) {
				throw new BusinessException(
						"TENANT_OWNER_INVITATION_RESEND_TOO_SOON",
						"La invitación acaba de enviarse. Espera un momento antes de generar otra.",
						Map.of("retryAt", latest.createdAt().plusSeconds(cooldown).toString()));
			}
		});

		repository.revokePendingInvitations(owner.userId(), command.now());
		return createInvitation(
				owner.userId(), owner.ownerName(), owner.email(), command.actorUserId(), command.now());
	}

	@Override
	@Transactional(readOnly = true)
	public List<OwnerInvitationState> invitationStates(Set<UUID> organizationIds, OffsetDateTime now) {
		return repository.ownerInvitationStates(organizationIds, now).stream()
				.map(state -> new OwnerInvitationState(
						state.organizationId(), state.userId(), state.ownerName(), state.email(),
						state.accountStatus(), state.invitationId(), state.invitationStatus(),
						state.invitationExpiresAt(), state.emailDeliveryStatus(),
						state.lastEmailAttemptAt(), state.lastEmailSentAt()))
				.toList();
	}

	@Override
	@Transactional
	public void recordEmailDelivery(UUID invitationId, String deliveryStatus, OffsetDateTime attemptedAt) {
		if (!EMAIL_DELIVERY_STATUSES.contains(deliveryStatus)) {
			throw new IllegalArgumentException("Unsupported invitation email delivery status");
		}
		repository.recordInvitationEmailDelivery(invitationId, deliveryStatus, attemptedAt);
	}

	private OwnerInvitation createInvitation(
			UUID userId,
			String ownerName,
			String ownerEmail,
			UUID actorUserId,
			OffsetDateTime now
	) {
		String invitationToken = randomToken();
		UUID invitationId = UUID.randomUUID();
		var expiresAt = now.plusHours(properties.invitationHours());
		repository.createInvitation(
				invitationId, userId, UserInvitationService.sha256(invitationToken), expiresAt,
				actorUserId, now);
		return new OwnerInvitation(
				invitationId, userId, ownerName, ownerEmail,
				properties.frontendBaseUrl() + "/accept-invitation?token=" + invitationToken,
				expiresAt, "PENDING");
	}

	private String randomToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
