package com.gymfort.connector.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymfort.connector.cloud.CloudGateway;
import com.gymfort.connector.cloud.CloudModels.AuthorizationRequest;
import com.gymfort.connector.cloud.CloudModels.AuthorizationResponse;
import com.gymfort.connector.cloud.CloudModels.DeviceCandidate;
import com.gymfort.connector.cloud.CloudModels.PersonCandidate;
import com.gymfort.connector.cloud.CloudModels.ClaimedJob;
import com.gymfort.connector.provider.ProviderAccessEvent;
import com.gymfort.connector.provider.ProviderDevice;
import com.gymfort.connector.provider.ProviderPerson;
import com.gymfort.connector.provider.simulator.SimulatorDriver;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProviderRuntimeWorkerTest {
    @TempDir Path tempDirectory;

    @Test
    void discoversResourcesAuthorizesAndOpensExactlyOnce() {
        SimulatorDriver driver = new SimulatorDriver();
        driver.addPerson(new ProviderPerson("person-1", "Ana Demo", "ana@example.test", null, "42"));
        driver.addDevice(new ProviderDevice("door-1", "Torniquete", "TURNSTILE", Map.of("site", "Centro")));
        driver.emit(new ProviderAccessEvent(
                "event-1", "person-1", "door-1", OffsetDateTime.now(),
                "GRANTED", "FACE_MATCH", Map.of()));
        FakeCloud cloud = new FakeCloud(true);

        try (ProviderRuntimeWorker worker = new ProviderRuntimeWorker(
                cloud, driver, new ConnectorStateStore(tempDirectory), Duration.ofHours(1))) {
            assertThat(worker.runOnce()).isEqualTo(1);
            assertThat(worker.runOnce()).isZero();
        }

        assertThat(cloud.persons).extracting(PersonCandidate::externalPersonRef).containsExactly("person-1");
        assertThat(cloud.devices).extracting(DeviceCandidate::externalDeviceRef).containsExactly("door-1");
        assertThat(cloud.authorizations).hasSize(1);
        assertThat(driver.openings()).hasSize(1);
        assertThat(cloud.actuations).containsExactly("SUCCEEDED");
    }

    @Test
    void marksInterruptedActuationUncertainWithoutOpeningAgain() {
        SimulatorDriver driver = new SimulatorDriver();
        driver.emit(new ProviderAccessEvent(
                "event-crash", "person-1", "door-1", OffsetDateTime.now(),
                null, "FACE_MATCH", Map.of()));
        UUID authorizationId = UUID.randomUUID();
        ConnectorStateStore store = new ConnectorStateStore(tempDirectory);
        store.begin("event-crash");
        store.authorized("event-crash", authorizationId, true);
        store.actuating("event-crash", authorizationId);
        FakeCloud cloud = new FakeCloud(true);

        try (ProviderRuntimeWorker worker = new ProviderRuntimeWorker(
                cloud, driver, store, Duration.ofHours(1))) {
            assertThat(worker.runOnce()).isEqualTo(1);
        }

        assertThat(driver.openings()).isEmpty();
        assertThat(cloud.authorizations).isEmpty();
        assertThat(cloud.actuations).containsExactly("UNCERTAIN");
        assertThat(new ConnectorStateStore(tempDirectory).find("event-crash").orElseThrow().state())
                .isEqualTo(ConnectorStateStore.State.UNCERTAIN);
    }

    @Test
    void providerManagedDecisionNeverTriggersRemoteOpen() {
        SimulatorDriver driver = new SimulatorDriver();
        driver.emit(new ProviderAccessEvent(
                "event-static", "person-1", "door-1", OffsetDateTime.now(),
                "GRANTED", "FACE_MATCH", Map.of()));
        FakeCloud cloud = new FakeCloud(false);

        try (ProviderRuntimeWorker worker = new ProviderRuntimeWorker(
                cloud, driver, new ConnectorStateStore(tempDirectory), Duration.ofHours(1))) {
            worker.runOnce();
        }

        assertThat(driver.openings()).isEmpty();
        assertThat(cloud.actuations).isEmpty();
    }

    private static final class FakeCloud implements CloudGateway {
        private final boolean shouldOpen;
        private final List<PersonCandidate> persons = new ArrayList<>();
        private final List<DeviceCandidate> devices = new ArrayList<>();
        private final List<AuthorizationRequest> authorizations = new ArrayList<>();
        private final List<String> actuations = new ArrayList<>();

        private FakeCloud(boolean shouldOpen) {
            this.shouldOpen = shouldOpen;
        }

        @Override public List<ClaimedJob> claim(int limit, int leaseSeconds) { return List.of(); }
        @Override public void complete(UUID jobId, UUID claimToken, String ref) { }
        @Override public void fail(UUID jobId, UUID token, String error, boolean retryable, int delay) { }
        @Override public void extendLease(UUID jobId, UUID claimToken, int leaseSeconds) { }
        @Override public void heartbeat(String status, String version, String instance, String error) { }
        @Override public void publishPerson(PersonCandidate candidate) { persons.add(candidate); }
        @Override public void publishDevice(DeviceCandidate candidate) { devices.add(candidate); }

        @Override
        public AuthorizationResponse authorize(AuthorizationRequest request) {
            authorizations.add(request);
            UUID id = UUID.randomUUID();
            return new AuthorizationResponse(
                    id, UUID.randomUUID(), request.externalEventId(), "GRANTED",
                    shouldOpen ? "GYMFORT_ONLINE" : "PROVIDER_MANAGED",
                    shouldOpen ? "PENDING" : "NOT_REQUIRED", shouldOpen, "OK", OffsetDateTime.now());
        }

        @Override
        public void reportActuation(UUID authorizationId, String status, String ref, String error) {
            actuations.add(status);
        }
    }
}
