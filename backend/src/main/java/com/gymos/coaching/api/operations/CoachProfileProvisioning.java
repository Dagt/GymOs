package com.gymos.coaching.api.operations;

import java.util.List;
import java.util.UUID;

public interface CoachProfileProvisioning {
	void synchronize(
			UUID organizationId,
			UUID userId,
			String displayName,
			String email,
			String phone,
			List<UUID> branchIds,
			boolean active,
			UUID actorUserId
	);
}
