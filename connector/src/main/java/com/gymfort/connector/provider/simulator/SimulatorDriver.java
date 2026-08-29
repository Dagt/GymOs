package com.gymfort.connector.provider.simulator;

import com.gymfort.connector.provider.AccessProviderDriver;
import com.gymfort.connector.provider.ProviderCapability;
import com.gymfort.connector.provider.ProviderException;
import com.gymfort.connector.provider.ProviderHealth;
import com.gymfort.connector.provider.ProviderSyncCommand;
import com.gymfort.connector.provider.ProviderSyncResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class SimulatorDriver implements AccessProviderDriver {
    private final Map<String, List<String>> rightsByPerson = new ConcurrentHashMap<>();

    @Override
    public String providerKey() {
        return "SIMULATOR";
    }

    @Override
    public ProviderHealth health() {
        return new ProviderHealth(true, "simulator-1", Set.of(
                ProviderCapability.VERSION_DISCOVERY,
                ProviderCapability.PERSON_LOOKUP,
                ProviderCapability.ACCESS_LEVEL_ASSIGNMENT), "Simulador local disponible.");
    }

    @Override
    public ProviderSyncResult synchronize(ProviderSyncCommand command) {
        if (command.externalPersonRef() == null || command.externalPersonRef().isBlank()) {
            throw new ProviderException("El job no contiene una referencia de persona externa.", false);
        }
        List<String> desired = command.action() == ProviderSyncCommand.Action.REVOKE
                ? List.of()
                : command.desiredAccessLevelRefs();
        rightsByPerson.put(command.externalPersonRef(), List.copyOf(desired));
        return new ProviderSyncResult("simulator:" + command.jobId());
    }

    public List<String> accessLevels(String externalPersonRef) {
        return rightsByPerson.getOrDefault(externalPersonRef, List.of());
    }
}
