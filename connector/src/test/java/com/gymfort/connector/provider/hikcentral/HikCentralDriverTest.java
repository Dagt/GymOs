package com.gymfort.connector.provider.hikcentral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymfort.connector.provider.ProviderException;
import com.gymfort.connector.provider.ProviderSyncCommand;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HikCentralDriverTest {
    @Test
    void reconcilesOnlyAccessLevelsOwnedByGymFort() {
        FakeApi api = new FakeApi(Set.of("CENTRO_GENERAL", "CENTRO_SAUNA", "MANUAL_SECURITY"));
        api.assignments.get("CENTRO_SAUNA").add("person-1");
        api.assignments.get("MANUAL_SECURITY").add("person-1");
        HikCentralDriver driver = new HikCentralDriver(
                api, Set.of("CENTRO_GENERAL", "CENTRO_SAUNA"));

        driver.synchronize(command(ProviderSyncCommand.Action.UPSERT, List.of("CENTRO_GENERAL")));

        assertThat(api.assignments.get("CENTRO_GENERAL")).containsExactly("person-1");
        assertThat(api.assignments.get("CENTRO_SAUNA")).isEmpty();
        assertThat(api.assignments.get("MANUAL_SECURITY")).containsExactly("person-1");
        assertThat(api.operations).containsExactly(
                "add:CENTRO_GENERAL:person-1", "remove:CENTRO_SAUNA:person-1");
    }

    @Test
    void refusesCloudRightsOutsideTheConfiguredOwnershipBoundary() {
        FakeApi api = new FakeApi(Set.of("CENTRO_GENERAL", "MANUAL_SECURITY"));
        HikCentralDriver driver = new HikCentralDriver(api, Set.of("CENTRO_GENERAL"));

        assertThatThrownBy(() -> driver.synchronize(command(
                ProviderSyncCommand.Action.UPSERT, List.of("MANUAL_SECURITY"))))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("fuera del conjunto administrado");
        assertThat(api.operations).isEmpty();
    }

    @Test
    void doctorReportsMissingConfiguredAccessLevels() {
        FakeApi api = new FakeApi(Set.of("CENTRO_GENERAL"));
        HikCentralDriver driver = new HikCentralDriver(
                api, Set.of("CENTRO_GENERAL", "CENTRO_SAUNA"));

        assertThat(driver.health().healthy()).isFalse();
        assertThat(driver.health().detail()).contains("CENTRO_SAUNA");
    }

    private static ProviderSyncCommand command(
            ProviderSyncCommand.Action action,
            List<String> desired
    ) {
        UUID jobId = UUID.randomUUID();
        return new ProviderSyncCommand(
                jobId, "job-" + jobId, action, UUID.randomUUID(), "person-1", desired);
    }

    private static final class FakeApi implements HikCentralApi {
        private final Map<String, Set<String>> assignments = new HashMap<>();
        private final List<String> operations = new ArrayList<>();

        private FakeApi(Set<String> accessLevelIds) {
            accessLevelIds.forEach(id -> assignments.put(id, new HashSet<>()));
        }

        @Override
        public String version() {
            return "2.6.1";
        }

        @Override
        public Set<String> accessLevelIds() {
            return Set.copyOf(assignments.keySet());
        }

        @Override
        public boolean accessLevelContains(String accessLevelRef, String personRef) {
            return assignments.get(accessLevelRef).contains(personRef);
        }

        @Override
        public void addPerson(String accessLevelRef, String personRef) {
            assignments.get(accessLevelRef).add(personRef);
            operations.add("add:" + accessLevelRef + ":" + personRef);
        }

        @Override
        public void removePerson(String accessLevelRef, String personRef) {
            assignments.get(accessLevelRef).remove(personRef);
            operations.add("remove:" + accessLevelRef + ":" + personRef);
        }
    }
}
