package com.gymos.memberships.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class MembershipOperationIdempotency {
	String resolve(String operation, UUID membershipId, String provided) {
		if (provided != null && !provided.isBlank()) {
			return provided.trim();
		}
		return operation + ":" + membershipId + ":" + UUID.randomUUID();
	}
}
