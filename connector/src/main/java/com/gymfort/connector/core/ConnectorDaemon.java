package com.gymfort.connector.core;

import com.gymfort.connector.cloud.CloudApiException;
import com.gymfort.connector.cloud.CloudGateway;
import com.gymfort.connector.provider.AccessProviderDriver;
import com.gymfort.connector.provider.ProviderHealth;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConnectorDaemon {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorDaemon.class);

    private final CloudGateway cloud;
    private final ConnectorWorker worker;
    private final ProviderRuntimeWorker runtimeWorker;
    private final AccessProviderDriver driver;
    private final String connectorVersion;
    private final String instanceId;
    private final Duration pollInterval;
    private final Duration heartbeatInterval;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public ConnectorDaemon(
            CloudGateway cloud,
            ConnectorWorker worker,
            AccessProviderDriver driver,
            String connectorVersion,
            String instanceId,
            Duration pollInterval,
            Duration heartbeatInterval
    ) {
        this(cloud, worker, null, driver, connectorVersion, instanceId, pollInterval, heartbeatInterval);
    }

    public ConnectorDaemon(
            CloudGateway cloud,
            ConnectorWorker worker,
            ProviderRuntimeWorker runtimeWorker,
            AccessProviderDriver driver,
            String connectorVersion,
            String instanceId,
            Duration pollInterval,
            Duration heartbeatInterval
    ) {
        this.cloud = cloud;
        this.worker = worker;
        this.runtimeWorker = runtimeWorker;
        this.driver = driver;
        this.connectorVersion = connectorVersion;
        this.instanceId = instanceId;
        this.pollInterval = pollInterval;
        this.heartbeatInterval = heartbeatInterval;
    }

    public void run() {
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(this::stop));
        Instant nextHeartbeat = Instant.EPOCH;
        int consecutiveFailures = 0;
        LOGGER.info("GymFort Local Connector started. provider={} instance={}", driver.providerKey(), instanceId);
        while (running.get()) {
            try {
                Instant now = Instant.now();
                if (!now.isBefore(nextHeartbeat)) {
                    sendHeartbeat();
                    nextHeartbeat = now.plus(heartbeatInterval);
                }
                worker.runOnce();
                if (runtimeWorker != null) {
                    runtimeWorker.runOnce();
                }
                consecutiveFailures = 0;
                sleep(pollInterval);
            } catch (CloudApiException exception) {
                consecutiveFailures++;
                LOGGER.warn("GymFort Cloud unavailable. status={} authenticationFailure={}",
                        exception.statusCode(), exception.authenticationFailure());
                if (exception.authenticationFailure()) {
                    sleep(Duration.ofMinutes(1));
                } else {
                    sleep(backoff(consecutiveFailures));
                }
            } catch (RuntimeException exception) {
                consecutiveFailures++;
                LOGGER.error("Connector loop failed safely: {}", safe(exception.getMessage()));
                sleep(backoff(consecutiveFailures));
            }
        }
        LOGGER.info("GymFort Local Connector stopped.");
    }

    public void stop() {
        running.set(false);
    }

    private void sendHeartbeat() {
        ProviderHealth health = driver.health();
        cloud.heartbeat(
                health.healthy() ? "HEALTHY" : "DEGRADED", connectorVersion, instanceId,
                health.detail(), health.capabilities().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()));
    }

    Duration backoff(int failures) {
        long baseSeconds = Math.min(40, 1L << Math.min(Math.max(failures, 1), 6));
        long jitterSeconds = ThreadLocalRandom.current().nextLong(0, (baseSeconds / 2) + 1);
        return Duration.ofSeconds(Math.min(60, baseSeconds + jitterSeconds));
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            running.set(false);
        }
    }

    private static String safe(String value) {
        if (value == null) {
            return "unspecified";
        }
        return value.replaceAll("(?i)(secret|password|token|authorization)\\s*[:=]\\s*\\S+", "$1=<redacted>");
    }
}
