package com.gymos.clients.api.operations;

import java.util.Optional;
import java.util.UUID;

public interface ClientLookupOperations {
	Optional<ClientVerificationResult> verifyForSelfService(VerifyClientIdentityCommand command);

	Optional<SelfServiceClientProfile> selfServiceProfile(UUID organizationId, UUID clientId);
}
