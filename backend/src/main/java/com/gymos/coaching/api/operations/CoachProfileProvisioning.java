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
			List<String> specialties,
			String bio,
			boolean active,
			UUID actorUserId
	);
}
