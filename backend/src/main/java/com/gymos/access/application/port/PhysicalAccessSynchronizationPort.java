package com.gymos.access.application.port;

import java.util.List;
import java.util.UUID;

/**
 * Boundary implemented by a local provider connector. The cloud application owns desired rights;
 * an adapter applies them to a physical access system without exposing vendor credentials here.
 */
public interface PhysicalAccessSynchronizationPort {
	SynchronizationResult synchronize(SynchronizationCommand command);

	record SynchronizationCommand(
			UUID organizationId,
			UUID branchId,
			String connectorKey,
			String externalPersonRef,
			List<String> externalAccessLevelRefs,
			String action,
			String idempotencyKey
	) {
		public SynchronizationCommand {
			externalAccessLevelRefs = externalAccessLevelRefs == null ? List.of() : List.copyOf(externalAccessLevelRefs);
		}
	}

	record SynchronizationResult(boolean accepted, String externalOperationRef, String message) {
	}
}
