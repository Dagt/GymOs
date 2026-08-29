package com.gymfort.connector.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectorConfigTest {
    @TempDir
    Path tempDirectory;

    @Test
    void loadsSimulatorAndResolvesStateRelativeToTheConfigurationFile() throws Exception {
        Path configFile = tempDirectory.resolve("connector.properties");
        Files.writeString(configFile, """
                cloud.baseUrl=https://gymfort.example/api/
                connector.key=centro-01
                connector.provider=simulator
                connector.stateDirectory=data/state
                connector.pollInterval=PT1S
                connector.heartbeatInterval=PT30S
                connector.claimLimit=7
                connector.leaseSeconds=60
                """);

        ConnectorConfig config = ConnectorConfig.load(configFile);

        assertThat(config.cloudBaseUrl().toString()).isEqualTo("https://gymfort.example/api");
        assertThat(config.providerKey()).isEqualTo("SIMULATOR");
        assertThat(config.stateDirectory()).isEqualTo(
                tempDirectory.resolve("data/state").toAbsolutePath().normalize());
        assertThat(config.claimLimit()).isEqualTo(7);
        assertThat(config.hikCentral()).isNull();
    }

    @Test
    void rejectsHikCentralWithoutAnExplicitOwnershipBoundary() throws Exception {
        Path configFile = tempDirectory.resolve("connector.properties");
        Files.writeString(configFile, """
                cloud.baseUrl=https://gymfort.example
                connector.key=centro-01
                connector.provider=HIKCENTRAL
                hikcentral.baseUrl=https://hikcentral.local
                hikcentral.appKey=app-key
                """);

        assertThatThrownBy(() -> ConnectorConfig.load(configFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("managedAccessLevelRefs");
    }

    @Test
    void rejectsCredentialsEmbeddedInUrls() throws Exception {
        Path configFile = tempDirectory.resolve("connector.properties");
        Files.writeString(configFile, """
                cloud.baseUrl=https://admin:password@gymfort.example
                connector.key=centro-01
                connector.provider=SIMULATOR
                """);

        assertThatThrownBy(() -> ConnectorConfig.load(configFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no debe incluir credenciales");
    }

    @Test
    void rejectsUnencryptedHikCentralEndpoint() throws Exception {
        Path configFile = tempDirectory.resolve("connector.properties");
        Files.writeString(configFile, """
                cloud.baseUrl=https://gymfort.example
                connector.key=centro-01
                connector.provider=HIKCENTRAL
                hikcentral.baseUrl=http://hikcentral.local:8443
                hikcentral.appKey=app-key
                hikcentral.managedAccessLevelRefs=CENTRO_GENERAL
                """);

        assertThatThrownBy(() -> ConnectorConfig.load(configFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("debe utilizar https");
    }

    @Test
    void resolvesAnOptionalHikCentralCertificateRelativeToConfig() throws Exception {
        Path configFile = tempDirectory.resolve("connector.properties");
        Files.writeString(configFile, """
                cloud.baseUrl=https://gymfort.example
                connector.key=centro-01
                connector.provider=HIKCENTRAL
                hikcentral.baseUrl=https://hikcentral.local:8443
                hikcentral.appKey=app-key
                hikcentral.managedAccessLevelRefs=CENTRO_GENERAL
                hikcentral.trustedCertificatePath=certificates/hikcentral.cer
                """);

        ConnectorConfig config = ConnectorConfig.load(configFile);

        assertThat(config.hikCentral().trustedCertificatePath()).isEqualTo(
                tempDirectory.resolve("certificates/hikcentral.cer").toAbsolutePath().normalize());
    }
}
