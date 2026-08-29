package com.gymfort.connector.provider.hikcentral;

import com.gymfort.connector.provider.AccessProviderDriver;
import com.gymfort.connector.provider.ProviderCapability;
import com.gymfort.connector.provider.ProviderException;
import com.gymfort.connector.provider.ProviderHealth;
import com.gymfort.connector.provider.ProviderSyncCommand;
import com.gymfort.connector.provider.ProviderSyncResult;
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
            Set<String> available = api.accessLevelIds();
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

    private Set<ProviderCapability> capabilities() {
        return Set.of(
                ProviderCapability.VERSION_DISCOVERY,
                ProviderCapability.PERSON_LOOKUP,
                ProviderCapability.ACCESS_LEVEL_ASSIGNMENT);
    }

    @Override
    public void close() {
        api.close();
    }
}
