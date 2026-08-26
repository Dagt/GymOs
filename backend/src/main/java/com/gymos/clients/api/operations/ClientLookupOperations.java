package com.gymos.clients.api.operations;

import java.util.Optional;
import java.util.UUID;

public interface ClientLookupOperations {
	Optional<ClientVerificationResult> verifyForSelfService(VerifyClientIdentityCommand command);

	ClientVerificationResult registerForSelfService(RegisterSelfServiceClientCommand command);

	Optional<SelfServiceClientProfile> selfServiceProfile(UUID organizationId, UUID clientId);

	SelfServiceClientProfile updateSelfServiceContact(UpdateSelfServiceContactCommand command);
}
