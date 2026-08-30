package com.gymfort.connector.provider.simulator;

import com.gymfort.connector.provider.AccessProviderDriver;
import com.gymfort.connector.provider.ProviderCapability;
import com.gymfort.connector.provider.ProviderException;
import com.gymfort.connector.provider.ProviderHealth;
import com.gymfort.connector.provider.ProviderPage;
import com.gymfort.connector.provider.ProviderPerson;
import com.gymfort.connector.provider.ProviderDevice;
import com.gymfort.connector.provider.ProviderAccessEvent;
import com.gymfort.connector.provider.ProviderOpenCommand;
import com.gymfort.connector.provider.ProviderOpenResult;
import com.gymfort.connector.provider.ProviderSyncCommand;
import com.gymfort.connector.provider.ProviderSyncResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class SimulatorDriver implements AccessProviderDriver {
    private final Map<String, List<String>> rightsByPerson = new ConcurrentHashMap<>();
    private final List<ProviderPerson> persons = new CopyOnWriteArrayList<>();
    private final List<ProviderDevice> devices = new CopyOnWriteArrayList<>();
    private final List<ProviderAccessEvent> events = new CopyOnWriteArrayList<>();
    private final List<ProviderOpenCommand> openings = new CopyOnWriteArrayList<>();
    private final AtomicInteger operationSequence = new AtomicInteger();

    @Override
    public String providerKey() {
        return "SIMULATOR";
    }

    @Override
    public ProviderHealth health() {
        return new ProviderHealth(true, "simulator-1", capabilities(), "Simulador local disponible.");
    }

    @Override
    public Set<ProviderCapability> capabilities() {
        return Set.of(
                ProviderCapability.VERSION_DISCOVERY,
                ProviderCapability.PERSON_LOOKUP,
                ProviderCapability.DEVICE_DISCOVERY,
                ProviderCapability.ACCESS_LEVEL_ASSIGNMENT,
                ProviderCapability.EVENT_POLLING,
                ProviderCapability.REMOTE_OPEN);
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

    @Override
    public ProviderPage<ProviderPerson> persons(String cursor, int limit) {
        return page(persons, cursor, limit);
    }

    @Override
    public ProviderPage<ProviderDevice> devices(String cursor, int limit) {
        return page(devices, cursor, limit);
    }

    @Override
    public ProviderPage<ProviderAccessEvent> events(String cursor, int limit) {
        return page(events, cursor, limit);
    }

    @Override
    public ProviderOpenResult remoteOpen(ProviderOpenCommand command) {
        openings.add(command);
        return new ProviderOpenResult("simulator:open:" + operationSequence.incrementAndGet());
    }

    public void addPerson(ProviderPerson person) {
        persons.add(person);
    }

    public void addDevice(ProviderDevice device) {
        devices.add(device);
    }

    public void emit(ProviderAccessEvent event) {
        events.add(event);
    }

    public List<ProviderOpenCommand> openings() {
        return List.copyOf(openings);
    }

    private static <T> ProviderPage<T> page(List<T> values, String cursor, int limit) {
        int start = cursor == null || cursor.isBlank() ? 0 : Integer.parseInt(cursor);
        int end = Math.min(values.size(), start + Math.max(1, limit));
        List<T> items = List.copyOf(values.subList(start, end));
        return new ProviderPage<>(items, Integer.toString(end), end < values.size());
    }
}
