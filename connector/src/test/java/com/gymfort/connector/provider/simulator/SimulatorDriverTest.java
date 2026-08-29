package com.gymfort.connector.provider.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymfort.connector.provider.ProviderException;
import com.gymfort.connector.provider.ProviderSyncCommand;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimulatorDriverTest {
    private final SimulatorDriver driver = new SimulatorDriver();

    @Test
    void appliesTheExactDesiredAccessLevelSetAndRevokesIt() {
        UUID jobId = UUID.randomUUID();
        driver.synchronize(command(jobId, ProviderSyncCommand.Action.UPSERT,
                List.of("CENTRO_GENERAL", "CENTRO_SAUNA")));
        assertThat(driver.accessLevels("person-42"))
                .containsExactly("CENTRO_GENERAL", "CENTRO_SAUNA");

        driver.synchronize(command(UUID.randomUUID(), ProviderSyncCommand.Action.RECONCILE,
                List.of("CENTRO_GENERAL")));
        assertThat(driver.accessLevels("person-42")).containsExactly("CENTRO_GENERAL");

        driver.synchronize(command(UUID.randomUUID(), ProviderSyncCommand.Action.REVOKE,
                List.of("IGNORED")));
        assertThat(driver.accessLevels("person-42")).isEmpty();
    }

    @Test
    void refusesAJobWithoutAnExternalIdentity() {
        ProviderSyncCommand command = new ProviderSyncCommand(
                UUID.randomUUID(), "key", ProviderSyncCommand.Action.UPSERT,
                UUID.randomUUID(), null, List.of("CENTRO_GENERAL"));

        assertThatThrownBy(() -> driver.synchronize(command))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("referencia de persona externa");
    }

    private ProviderSyncCommand command(
            UUID jobId,
            ProviderSyncCommand.Action action,
            List<String> desired
    ) {
        return new ProviderSyncCommand(
                jobId, "key-" + jobId, action, UUID.randomUUID(), "person-42", desired);
    }
}
