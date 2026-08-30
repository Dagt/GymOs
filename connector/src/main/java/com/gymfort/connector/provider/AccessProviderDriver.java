package com.gymfort.connector.provider;

/**
 * Boundary between the provider-neutral Connector worker and a physical access
 * platform. Implementations must treat desiredAccessLevelRefs as GymFort's exact
 * owned set and must be safe to invoke again with the same idempotency key.
 */
public interface AccessProviderDriver extends AutoCloseable {
    String providerKey();

    ProviderHealth health();

    default java.util.Set<ProviderCapability> capabilities() {
        return health().capabilities();
    }

    ProviderSyncResult synchronize(ProviderSyncCommand command);

    default ProviderPage<ProviderPerson> persons(String cursor, int limit) {
        return ProviderPage.empty(cursor);
    }

    default ProviderPage<ProviderDevice> devices(String cursor, int limit) {
        return ProviderPage.empty(cursor);
    }

    default ProviderPage<ProviderAccessEvent> events(String cursor, int limit) {
        return ProviderPage.empty(cursor);
    }

    default ProviderOpenResult remoteOpen(ProviderOpenCommand command) {
        throw new ProviderException("El proveedor no admite apertura remota en este perfil.", false);
    }

    @Override
    default void close() {
    }
}
