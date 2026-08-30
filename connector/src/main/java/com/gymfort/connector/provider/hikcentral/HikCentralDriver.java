package com.gymfort.connector.provider.hikcentral;

import com.gymfort.connector.provider.AccessProviderDriver;
import com.gymfort.connector.provider.ProviderCapability;
import com.gymfort.connector.provider.ProviderException;
import com.gymfort.connector.provider.ProviderHealth;
import com.gymfort.connector.provider.ProviderSyncCommand;
import com.gymfort.connector.provider.ProviderSyncResult;
import com.gymfort.connector.provider.ProviderPage;
import com.gymfort.connector.provider.ProviderPerson;
import com.gymfort.connector.provider.ProviderDevice;
import com.gymfort.connector.provider.ProviderAccessEvent;
import com.gymfort.connector.provider.ProviderOpenCommand;
import com.gymfort.connector.provider.ProviderOpenResult;
import com.gymfort.connector.provider.ProviderActuationException;
import java.util.HashSet;
import java.util.Set;

public final class HikCentralDriver implements AccessProviderDriver {
    private final HikCentralApi api;
    private final Set<String> managedAccessLevelRefs;

    public HikCentralDriver(HikCentralApi api, Set<String> managedAccessLevelRefs) {
        this.api = api;
        this.managedAccessLevelRefs = Set.copyOf(managedAccessLevelRefs);
    }

    @Override
    public String providerKey() {
        return "HIKCENTRAL";
    }

    @Override
    public ProviderHealth health() {
        try {
            String version = api.version();
            Set<String> available = managedAccessLevelRefs.isEmpty() ? Set.of() : api.accessLevelIds();
            Set<String> missing = new HashSet<>(managedAccessLevelRefs);
            missing.removeAll(available);
            if (!missing.isEmpty()) {
                return new ProviderHealth(false, version, capabilities(),
                        "Faltan Access Levels configurados en HikCentral: " + String.join(", ", missing));
            }
            return new ProviderHealth(true, version, capabilities(), "HikCentral OpenAPI disponible.");
        } catch (HikCentralApiException exception) {
            return new ProviderHealth(false, null, capabilities(), exception.getMessage());
        }
    }

    @Override
    public ProviderSyncResult synchronize(ProviderSyncCommand command) {
        if (command.externalPersonRef() == null || command.externalPersonRef().isBlank()) {
            throw new ProviderException("El socio aún no está vinculado con una persona de HikCentral.", false);
        }
        Set<String> desired = command.action() == ProviderSyncCommand.Action.REVOKE
                ? Set.of()
                : Set.copyOf(command.desiredAccessLevelRefs());
        if (!managedAccessLevelRefs.containsAll(desired)) {
            Set<String> outsideOwnership = new HashSet<>(desired);
            outsideOwnership.removeAll(managedAccessLevelRefs);
            throw new ProviderException(
                    "GymFort solicitó Access Levels fuera del conjunto administrado: "
                            + String.join(", ", outsideOwnership), false);
        }

        try {
            for (String accessLevelRef : managedAccessLevelRefs.stream().sorted().toList()) {
                boolean assigned = api.accessLevelContains(accessLevelRef, command.externalPersonRef());
                if (desired.contains(accessLevelRef) && !assigned) {
                    api.addPerson(accessLevelRef, command.externalPersonRef());
                } else if (!desired.contains(accessLevelRef) && assigned) {
                    api.removePerson(accessLevelRef, command.externalPersonRef());
                }
            }
            return new ProviderSyncResult("hikcentral:" + command.jobId());
        } catch (HikCentralApiException exception) {
            throw new ProviderException(exception.getMessage(), exception.retryable(), exception);
        }
    }

    @Override public ProviderPage<ProviderPerson> persons(String cursor, int limit) {
        return api.persons(cursor, limit);
    }

    @Override public ProviderPage<ProviderDevice> devices(String cursor, int limit) {
        return api.devices(cursor, limit);
    }

    @Override public ProviderPage<ProviderAccessEvent> events(String cursor, int limit) {
        return api.events(cursor, limit);
    }

    @Override
    public ProviderOpenResult remoteOpen(ProviderOpenCommand command) {
        try {
            return api.remoteOpen(command);
        } catch (HikCentralApiException exception) {
            throw new ProviderActuationException(
                    exception.getMessage(), exception.retryable(), exception.retryable(), exception);
        }
    }

    @Override
    public Set<ProviderCapability> capabilities() {
        Set<ProviderCapability> result = new HashSet<>(Set.of(ProviderCapability.VERSION_DISCOVERY));
        if (!managedAccessLevelRefs.isEmpty()) result.add(ProviderCapability.ACCESS_LEVEL_ASSIGNMENT);
        if (api.supportsPersonSearch()) result.add(ProviderCapability.PERSON_LOOKUP);
        if (api.supportsDeviceSearch()) result.add(ProviderCapability.DEVICE_DISCOVERY);
        if (api.supportsEventSearch()) result.add(ProviderCapability.EVENT_POLLING);
        if (api.supportsRemoteOpen()) result.add(ProviderCapability.REMOTE_OPEN);
        return Set.copyOf(result);
    }

    @Override
    public void close() {
        api.close();
    }
}
