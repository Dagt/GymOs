package com.gymfort.connector.provider;

/**
 * Boundary between the provider-neutral Connector worker and a physical access
 * platform. Implementations must treat desiredAccessLevelRefs as GymFort's exact
 * owned set and must be safe to invoke again with the same idempotency key.
 */
public interface AccessProviderDriver extends AutoCloseable {
    String providerKey();

    ProviderHealth health();

    ProviderSyncResult synchronize(ProviderSyncCommand command);

    @Override
    default void close() {
    }
}
