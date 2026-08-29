package com.gymfort.connector.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteExecutionLedgerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void preservesSuccessfulExecutionAcrossRestartAndDoesNotResetIt() {
        UUID originalJobId = UUID.randomUUID();
        try (SqliteExecutionLedger ledger = new SqliteExecutionLedger(tempDirectory)) {
            assertThat(ledger.start("membership:123:revision:4", originalJobId).state())
                    .isEqualTo(ExecutionLedger.State.STARTED);
            ledger.succeed("membership:123:revision:4", "hikcentral:operation-1");
        }

        try (SqliteExecutionLedger restarted = new SqliteExecutionLedger(tempDirectory)) {
            ExecutionLedger.Execution execution = restarted.start(
                    "membership:123:revision:4", UUID.randomUUID());

            assertThat(execution.state()).isEqualTo(ExecutionLedger.State.SUCCEEDED);
            assertThat(execution.cloudJobId()).isEqualTo(originalJobId);
            assertThat(execution.externalOperationRef()).isEqualTo("hikcentral:operation-1");
        }
    }

    @Test
    void failedExecutionCanBeRetriedWithoutLosingItsIdentity() {
        UUID jobId = UUID.randomUUID();
        try (SqliteExecutionLedger ledger = new SqliteExecutionLedger(tempDirectory)) {
            ledger.start("job-retry", jobId);
            ledger.fail("job-retry", "temporary provider outage");

            ExecutionLedger.Execution execution = ledger.start("job-retry", UUID.randomUUID());
            assertThat(execution.state()).isEqualTo(ExecutionLedger.State.FAILED);
            assertThat(execution.cloudJobId()).isEqualTo(jobId);
            assertThat(execution.lastError()).isEqualTo("temporary provider outage");

            ledger.succeed("job-retry", "provider:done");
            assertThat(ledger.find("job-retry").orElseThrow().state())
                    .isEqualTo(ExecutionLedger.State.SUCCEEDED);
        }
    }
}
