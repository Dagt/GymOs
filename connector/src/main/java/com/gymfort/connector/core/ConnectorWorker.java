package com.gymfort.connector.core;

import com.gymfort.connector.cloud.CloudGateway;
import com.gymfort.connector.cloud.CloudModels.ClaimedJob;
import com.gymfort.connector.provider.AccessProviderDriver;
import com.gymfort.connector.provider.ProviderException;
import com.gymfort.connector.provider.ProviderSyncCommand;
import com.gymfort.connector.provider.ProviderSyncCommand.Action;
import com.gymfort.connector.provider.ProviderSyncResult;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConnectorWorker implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorWorker.class);

    private final CloudGateway cloud;
    private final AccessProviderDriver driver;
    private final ExecutionLedger ledger;
    private final int claimLimit;
    private final int leaseSeconds;
    private final ScheduledExecutorService leaseScheduler;

    public ConnectorWorker(
            CloudGateway cloud,
            AccessProviderDriver driver,
            ExecutionLedger ledger,
            int claimLimit,
            int leaseSeconds
    ) {
        this.cloud = cloud;
        this.driver = driver;
        this.ledger = ledger;
        this.claimLimit = claimLimit;
        this.leaseSeconds = leaseSeconds;
        this.leaseScheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("connector-lease-renewal", 0).factory());
    }

    public int runOnce() {
        List<ClaimedJob> jobs = cloud.claim(claimLimit, leaseSeconds);
        for (ClaimedJob job : jobs) {
            process(job);
        }
        return jobs.size();
    }

    private void process(ClaimedJob job) {
        if (job.claimToken() == null) {
            throw new IllegalArgumentException("GymFort entregó un job sin claimToken: " + job.id());
        }
        ExecutionLedger.Execution execution = ledger.start(job.idempotencyKey(), job.id());
        if (execution.state() == ExecutionLedger.State.SUCCEEDED) {
            cloud.complete(job.id(), job.claimToken(), execution.externalOperationRef());
            return;
        }

        ScheduledFuture<?> renewal = leaseScheduler.scheduleAtFixedRate(
                () -> renewLease(job), Math.max(15, leaseSeconds / 2), Math.max(15, leaseSeconds / 2),
                TimeUnit.SECONDS);
        try {
            ProviderSyncResult result = driver.synchronize(toCommand(job));
            ledger.succeed(job.idempotencyKey(), result.externalOperationRef());
            cloud.complete(job.id(), job.claimToken(), result.externalOperationRef());
            LOGGER.info("Physical access job completed. jobId={} action={}", job.id(), job.action());
        } catch (ProviderException exception) {
            ledger.fail(job.idempotencyKey(), exception.getMessage());
            cloud.fail(job.id(), job.claimToken(), exception.getMessage(), exception.retryable(),
                    exception.retryable() ? 30 : 0);
            LOGGER.warn("Provider rejected physical access job. jobId={} retryable={} error={}",
                    job.id(), exception.retryable(), safe(exception.getMessage()));
        } finally {
            renewal.cancel(false);
        }
    }

    private ProviderSyncCommand toCommand(ClaimedJob job) {
        Action action;
        try {
            action = Action.valueOf(job.action());
        } catch (RuntimeException exception) {
            throw new ProviderException("Acción cloud no soportada: " + job.action(), false, exception);
        }
        return new ProviderSyncCommand(
                job.id(), job.idempotencyKey(), action, job.clientId(),
                job.externalPersonRef(), job.desiredAccessLevelRefs());
    }

    private void renewLease(ClaimedJob job) {
        try {
            cloud.extendLease(job.id(), job.claimToken(), leaseSeconds);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not extend job lease. jobId={} error={}", job.id(), safe(exception.getMessage()));
        }
    }

    private static String safe(String value) {
        if (value == null) {
            return "unspecified";
        }
        return value.replaceAll("(?i)(secret|password|token|authorization)\\s*[:=]\\s*\\S+", "$1=<redacted>");
    }

    @Override
    public void close() {
        leaseScheduler.shutdownNow();
        cloud.close();
        driver.close();
        ledger.close();
    }
}
