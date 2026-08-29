package com.gymfort.connector.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymfort.connector.cloud.CloudGateway;
import com.gymfort.connector.cloud.CloudModels.ClaimedJob;
import com.gymfort.connector.provider.AccessProviderDriver;
import com.gymfort.connector.provider.ProviderCapability;
import com.gymfort.connector.provider.ProviderException;
import com.gymfort.connector.provider.ProviderHealth;
import com.gymfort.connector.provider.ProviderSyncCommand;
import com.gymfort.connector.provider.ProviderSyncResult;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectorWorkerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void doesNotApplyTheProviderMutationTwiceWhenCloudCompletionMustBeRetried() {
        ClaimedJob firstClaim = job("membership:42:revision:7");
        FakeCloud cloud = new FakeCloud(firstClaim);
        cloud.failFirstCompletion = true;
        RecordingDriver driver = new RecordingDriver();
        SqliteExecutionLedger ledger = new SqliteExecutionLedger(tempDirectory);

        try (ConnectorWorker worker = new ConnectorWorker(cloud, driver, ledger, 10, 90)) {
            assertThatThrownBy(worker::runOnce).hasMessageContaining("simulated cloud outage");
            assertThat(driver.commands).hasSize(1);
            assertThat(ledger.find(firstClaim.idempotencyKey()).orElseThrow().state())
                    .isEqualTo(ExecutionLedger.State.SUCCEEDED);

            cloud.claims = List.of(new ClaimedJob(
                    firstClaim.id(), firstClaim.idempotencyKey(), firstClaim.action(),
                    firstClaim.clientId(), firstClaim.externalPersonRef(),
                    firstClaim.desiredAccessLevelRefs(), firstClaim.status(),
                    firstClaim.attempts() + 1, firstClaim.retryable(),
                    firstClaim.leaseUntil(), UUID.randomUUID(), firstClaim.lastError()));

            assertThat(worker.runOnce()).isEqualTo(1);
            assertThat(driver.commands).hasSize(1);
            assertThat(cloud.completed).hasSize(1);
            assertThat(cloud.completed.getFirst().externalOperationRef)
                    .isEqualTo("provider:" + firstClaim.id());
        }
    }

    @Test
    void reportsRetryableProviderFailureWithoutCompletingTheJob() {
        ClaimedJob claim = job("membership:77:revision:1");
        FakeCloud cloud = new FakeCloud(claim);
        RecordingDriver driver = new RecordingDriver();
        driver.failure = new ProviderException("HikCentral temporarily unavailable", true);

        try (ConnectorWorker worker = new ConnectorWorker(
                cloud, driver, new SqliteExecutionLedger(tempDirectory), 10, 90)) {
            assertThat(worker.runOnce()).isEqualTo(1);
        }

        assertThat(cloud.completed).isEmpty();
        assertThat(cloud.failed).containsExactly(
                "HikCentral temporarily unavailable|true|30");
    }

    private static ClaimedJob job(String key) {
        return new ClaimedJob(
                UUID.randomUUID(), key, "UPSERT", UUID.randomUUID(), "person-42",
                List.of("CENTRO_GENERAL", "CENTRO_SAUNA"), "PROCESSING", 1, true,
                OffsetDateTime.now().plusMinutes(1), UUID.randomUUID(), null);
    }

    private static final class FakeCloud implements CloudGateway {
        private List<ClaimedJob> claims;
        private boolean failFirstCompletion;
        private final List<Completion> completed = new ArrayList<>();
        private final List<String> failed = new ArrayList<>();

        private FakeCloud(ClaimedJob job) {
            this.claims = List.of(job);
        }

        @Override
        public List<ClaimedJob> claim(int limit, int leaseSeconds) {
            List<ClaimedJob> result = claims;
            claims = List.of();
            return result;
        }

        @Override
        public void complete(UUID jobId, UUID claimToken, String externalOperationRef) {
            if (failFirstCompletion) {
                failFirstCompletion = false;
                throw new IllegalStateException("simulated cloud outage");
            }
            completed.add(new Completion(jobId, externalOperationRef));
        }

        @Override
        public void fail(
                UUID jobId,
                UUID claimToken,
                String error,
                boolean retryable,
                int retryAfterSeconds
        ) {
            failed.add(error + "|" + retryable + "|" + retryAfterSeconds);
        }

        @Override
        public void extendLease(UUID jobId, UUID claimToken, int leaseSeconds) {
        }

        @Override
        public void heartbeat(
                String status,
                String connectorVersion,
                String instanceId,
                String lastError
        ) {
        }

        private record Completion(UUID jobId, String externalOperationRef) {
        }
    }

    private static final class RecordingDriver implements AccessProviderDriver {
        private final List<ProviderSyncCommand> commands = new ArrayList<>();
        private ProviderException failure;

        @Override
        public String providerKey() {
            return "TEST";
        }

        @Override
        public ProviderHealth health() {
            return new ProviderHealth(true, "test", Set.of(
                    ProviderCapability.ACCESS_LEVEL_ASSIGNMENT), "ok");
        }

        @Override
        public ProviderSyncResult synchronize(ProviderSyncCommand command) {
            commands.add(command);
            if (failure != null) {
                throw failure;
            }
            return new ProviderSyncResult("provider:" + command.jobId());
        }
    }
}
