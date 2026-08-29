package com.gymfort.connector.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ConnectorDaemonTest {
    @Test
    void cloudBackoffIsBoundedAndJitteredWithinTheExpectedWindow() {
        ConnectorDaemon daemon = new ConnectorDaemon(
                null, null, null, "test", "test", Duration.ofSeconds(1), Duration.ofMinutes(1));

        for (int attempt = 0; attempt < 100; attempt++) {
            assertThat(daemon.backoff(1)).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(3));
            assertThat(daemon.backoff(20)).isBetween(Duration.ofSeconds(40), Duration.ofSeconds(60));
        }
    }
}
