package com.gymfort.connector.core;

import com.gymfort.connector.cloud.CloudGateway;
import com.gymfort.connector.cloud.CloudModels.AuthorizationRequest;
import com.gymfort.connector.cloud.CloudModels.AuthorizationResponse;
import com.gymfort.connector.cloud.CloudModels.DeviceCandidate;
import com.gymfort.connector.cloud.CloudModels.PersonCandidate;
import com.gymfort.connector.provider.AccessProviderDriver;
import com.gymfort.connector.provider.ProviderAccessEvent;
import com.gymfort.connector.provider.ProviderActuationException;
import com.gymfort.connector.provider.ProviderCapability;
import com.gymfort.connector.provider.ProviderDevice;
import com.gymfort.connector.provider.ProviderOpenCommand;
import com.gymfort.connector.provider.ProviderOpenResult;
import com.gymfort.connector.provider.ProviderPage;
import com.gymfort.connector.provider.ProviderPerson;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProviderRuntimeWorker implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderRuntimeWorker.class);
    private static final int PAGE_SIZE = 100;

    private final CloudGateway cloud;
    private final AccessProviderDriver driver;
    private final ConnectorStateStore state;
    private final Duration discoveryInterval;
    private Instant nextDiscovery = Instant.EPOCH;

    public ProviderRuntimeWorker(
            CloudGateway cloud, AccessProviderDriver driver, ConnectorStateStore state,
            Duration discoveryInterval
    ) {
        this.cloud = cloud;
        this.driver = driver;
        this.state = state;
        this.discoveryInterval = discoveryInterval;
    }

    public int runOnce() {
        Set<ProviderCapability> capabilities = driver.capabilities();
        if (!Instant.now().isBefore(nextDiscovery)) {
            if (capabilities.contains(ProviderCapability.PERSON_LOOKUP)) publishPersons();
            if (capabilities.contains(ProviderCapability.DEVICE_DISCOVERY)) publishDevices();
            nextDiscovery = Instant.now().plus(discoveryInterval);
        }
        return capabilities.contains(ProviderCapability.EVENT_POLLING) ? processEvents() : 0;
    }

    private void publishPersons() {
        String cursor = null;
        do {
            ProviderPage<ProviderPerson> page = driver.persons(cursor, PAGE_SIZE);
            for (ProviderPerson person : page.items()) {
                cloud.publishPerson(new PersonCandidate(
                        person.externalPersonRef(), person.displayName(), person.email(),
                        person.phone(), person.employeeNumber()));
            }
            cursor = page.nextCursor();
            if (!page.hasMore()) break;
        } while (true);
    }

    private void publishDevices() {
        String cursor = null;
        do {
            ProviderPage<ProviderDevice> page = driver.devices(cursor, PAGE_SIZE);
            for (ProviderDevice device : page.items()) {
                cloud.publishDevice(new DeviceCandidate(
                        device.externalDeviceRef(), device.name(), device.deviceType(), device.metadata()));
            }
            cursor = page.nextCursor();
            if (!page.hasMore()) break;
        } while (true);
    }

    private int processEvents() {
        String cursor = state.cursor("access-events");
        ProviderPage<ProviderAccessEvent> page = driver.events(cursor, PAGE_SIZE);
        int processed = 0;
        for (ProviderAccessEvent event : page.items()) {
            processEvent(event);
            processed++;
        }
        if (!page.items().isEmpty() || page.nextCursor() != null) {
            state.saveCursor("access-events", page.nextCursor());
        }
        return processed;
    }

    private void processEvent(ProviderAccessEvent event) {
        ConnectorStateStore.EventState local = state.begin(event.externalEventId());
        if (local.state() == ConnectorStateStore.State.COMPLETED
                || local.state() == ConnectorStateStore.State.UNCERTAIN) {
            return;
        }
        if (local.state() == ConnectorStateStore.State.ACTUATING) {
            String error = "El proceso reinició durante una apertura; no se repite automáticamente para evitar doble actuación.";
            cloud.reportActuation(local.authorizationId(), "UNCERTAIN", null, error);
            state.uncertain(event.externalEventId(), local.authorizationId(), error);
            return;
        }

        AuthorizationResponse authorization = cloud.authorize(new AuthorizationRequest(
                driver.providerKey(), event.externalEventId(), event.externalPersonRef(),
                event.externalDeviceRef(), event.occurredAt(), event.providerDecision(),
                event.eventType(), event.metadata()));
        state.authorized(event.externalEventId(), authorization.authorizationId(), authorization.shouldOpen());
        if (!authorization.shouldOpen()) return;

        state.actuating(event.externalEventId(), authorization.authorizationId());
        try {
            ProviderOpenResult result = driver.remoteOpen(new ProviderOpenCommand(
                    authorization.authorizationId(), event.externalEventId(), event.externalDeviceRef(),
                    "physical-open:" + authorization.authorizationId()));
            cloud.reportActuation(authorization.authorizationId(), "SUCCEEDED",
                    result.externalOperationRef(), null);
            state.succeeded(event.externalEventId(), authorization.authorizationId(), result.externalOperationRef());
        } catch (ProviderActuationException exception) {
            String status = exception.outcomeUncertain() ? "UNCERTAIN" : "FAILED";
            cloud.reportActuation(authorization.authorizationId(), status, null, safe(exception.getMessage()));
            if (exception.outcomeUncertain()) {
                state.uncertain(event.externalEventId(), authorization.authorizationId(), exception.getMessage());
            } else {
                state.failed(event.externalEventId(), authorization.authorizationId(), exception.getMessage());
            }
            LOGGER.warn("Physical actuation failed. authorizationId={} uncertain={} error={}",
                    authorization.authorizationId(), exception.outcomeUncertain(), safe(exception.getMessage()));
        } catch (RuntimeException exception) {
            cloud.reportActuation(authorization.authorizationId(), "UNCERTAIN", null, safe(exception.getMessage()));
            state.uncertain(event.externalEventId(), authorization.authorizationId(), exception.getMessage());
            throw exception;
        }
    }

    private static String safe(String value) {
        if (value == null) return "unspecified";
        return value.replaceAll("(?i)(secret|password|token|authorization)\\s*[:=]\\s*\\S+", "$1=<redacted>");
    }

    @Override
    public void close() {
        state.close();
    }
}
