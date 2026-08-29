package com.gymfort.connector.provider.hikcentral;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HikCentralOpenApiSignerTest {
    @Test
    void buildsTheDocumentedCanonicalRequestAndStableSignature() {
        HikCentralOpenApiSigner.SignedHeaders signed = new HikCentralOpenApiSigner().sign(
                "POST",
                "/artemis/api/acs/v1/privilege/group",
                "{\"pageNo\":1,\"pageSize\":100,\"type\":1}".getBytes(StandardCharsets.UTF_8),
                "test-app-key",
                "test-app-secret".toCharArray(),
                1700000000123L,
                "fixed-nonce");

        assertThat(signed.canonicalString()).isEqualTo("""
                POST
                */*
                2QapXhwpQa7UaqrSnW1p/w==
                application/json
                x-ca-key:test-app-key
                x-ca-nonce:fixed-nonce
                x-ca-timestamp:1700000000123
                /artemis/api/acs/v1/privilege/group""");
        assertThat(signed.headers())
                .containsEntry("Content-MD5", "2QapXhwpQa7UaqrSnW1p/w==")
                .containsEntry("X-Ca-Signature-Headers", "x-ca-key,x-ca-nonce,x-ca-timestamp")
                .containsEntry("X-Ca-Signature", "FIIRm5wfH/lUjhn6T/ARZsFlcZFCvkFHYc/Y1xWnXWk=");
    }
}
