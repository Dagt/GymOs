package com.gymfort.connector.provider.hikcentral;

import java.util.Set;
import com.gymfort.connector.provider.ProviderPage;
import com.gymfort.connector.provider.ProviderPerson;
import com.gymfort.connector.provider.ProviderDevice;
import com.gymfort.connector.provider.ProviderAccessEvent;
import com.gymfort.connector.provider.ProviderOpenCommand;
import com.gymfort.connector.provider.ProviderOpenResult;

interface HikCentralApi extends AutoCloseable {
    String version();

    Set<String> accessLevelIds();

    boolean accessLevelContains(String accessLevelRef, String personRef);

    void addPerson(String accessLevelRef, String personRef);

    void removePerson(String accessLevelRef, String personRef);

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
        throw new HikCentralApiException("El perfil OpenAPI no configura apertura remota.", false);
    }

    default boolean supportsPersonSearch() { return false; }
    default boolean supportsDeviceSearch() { return false; }
    default boolean supportsEventSearch() { return false; }
    default boolean supportsRemoteOpen() { return false; }

    @Override
    default void close() {
    }
}
