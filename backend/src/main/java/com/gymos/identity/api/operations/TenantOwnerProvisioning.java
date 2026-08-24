package com.gymos.identity.api.operations;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface TenantOwnerProvisioning {
	OwnerInvitation provisionOwner(ProvisionOwner command);

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
			UUID userId,
			String invitationUrl,
			OffsetDateTime expiresAt
	) {
	}
}
