package com.gymos.identity.api.operations;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TenantOwnerProvisioning {
	OwnerInvitation provisionOwner(ProvisionOwner command);

	OwnerInvitation reissueOwnerInvitation(ReissueOwnerInvitation command);

	List<OwnerInvitationState> invitationStates(Set<UUID> organizationIds, OffsetDateTime now);

	void recordEmailDelivery(UUID invitationId, String deliveryStatus, OffsetDateTime attemptedAt);

	record ProvisionOwner(
			UUID organizationId,
			UUID branchId,
			UUID actorUserId,
			String firstName,
			String lastName,
			String email,
			String phone,
			OffsetDateTime now
	) {
	}

	record OwnerInvitation(
			UUID invitationId,
			UUID userId,
			String ownerName,
			String ownerEmail,
			String invitationUrl,
			OffsetDateTime expiresAt,
			String emailDeliveryStatus
	) {
	}

	record ReissueOwnerInvitation(
			UUID organizationId,
			UUID actorUserId,
			OffsetDateTime now
	) {
	}

	record OwnerInvitationState(
			UUID organizationId,
			UUID userId,
			String ownerName,
			String ownerEmail,
			String accountStatus,
			UUID invitationId,
			String invitationStatus,
			OffsetDateTime invitationExpiresAt,
			String emailDeliveryStatus,
			OffsetDateTime lastEmailAttemptAt,
			OffsetDateTime lastEmailSentAt
	) {
	}
}
