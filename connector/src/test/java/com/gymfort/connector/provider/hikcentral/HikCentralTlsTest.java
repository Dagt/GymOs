package com.gymfort.connector.provider.hikcentral;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HikCentralTlsTest {
    @TempDir
    Path tempDirectory;

    @Test
    void refusesAMissingExplicitCertificateInsteadOfFallingBackToInsecureTls() {
        Path missing = tempDirectory.resolve("missing-hikcentral.cer");

        assertThatThrownBy(() -> HikCentralTls.httpClient(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No existe el certificado confiable");
    }
}
