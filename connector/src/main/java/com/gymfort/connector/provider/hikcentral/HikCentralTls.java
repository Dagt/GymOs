package com.gymfort.connector.provider.hikcentral;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

final class HikCentralTls {
    private HikCentralTls() {
    }

    static HttpClient httpClient(Path trustedCertificatePath) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
        if (trustedCertificatePath != null) {
            builder.sslContext(sslContext(trustedCertificatePath));
        }
        return builder.build();
    }

    private static SSLContext sslContext(Path certificatePath) {
        if (!Files.isRegularFile(certificatePath)) {
            throw new IllegalArgumentException(
                    "No existe el certificado confiable de HikCentral: " + certificatePath);
        }
        try {
            X509TrustManager systemTrust = trustManager(null);
            KeyStore localStore = KeyStore.getInstance(KeyStore.getDefaultType());
            localStore.load(null, null);
            try (InputStream input = Files.newInputStream(certificatePath)) {
                X509Certificate certificate = (X509Certificate) CertificateFactory
                        .getInstance("X.509").generateCertificate(input);
                localStore.setCertificateEntry("hikcentral-local", certificate);
            }
            X509TrustManager localTrust = trustManager(localStore);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new CompositeTrustManager(systemTrust, localTrust)}, null);
            return context;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "No fue posible cargar el certificado confiable de HikCentral.", exception);
        }
    }

    private static X509TrustManager trustManager(KeyStore keyStore) throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore);
        return Arrays.stream(factory.getTrustManagers())
                .filter(X509TrustManager.class::isInstance)
                .map(X509TrustManager.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No existe X509TrustManager."));
    }

    private static final class CompositeTrustManager implements X509TrustManager {
        private final X509TrustManager system;
        private final X509TrustManager local;

        private CompositeTrustManager(X509TrustManager system, X509TrustManager local) {
            this.system = system;
            this.local = local;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws java.security.cert.CertificateException {
            system.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws java.security.cert.CertificateException {
            try {
                system.checkServerTrusted(chain, authType);
            } catch (java.security.cert.CertificateException systemFailure) {
                local.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] systemIssuers = system.getAcceptedIssuers();
            X509Certificate[] localIssuers = local.getAcceptedIssuers();
            X509Certificate[] combined = Arrays.copyOf(
                    systemIssuers, systemIssuers.length + localIssuers.length);
            System.arraycopy(localIssuers, 0, combined, systemIssuers.length, localIssuers.length);
            return combined;
        }
    }
}
