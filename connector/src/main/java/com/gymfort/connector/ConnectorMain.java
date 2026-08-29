package com.gymfort.connector;

import com.gymfort.connector.cloud.CloudModels.ActivationResponse;
import com.gymfort.connector.cloud.GymFortCloudClient;
import com.gymfort.connector.config.ConnectorConfig;
import com.gymfort.connector.core.ConnectorDaemon;
import com.gymfort.connector.core.ConnectorWorker;
import com.gymfort.connector.core.SqliteExecutionLedger;
import com.gymfort.connector.provider.AccessProviderDriver;
import com.gymfort.connector.provider.ProviderHealth;
import com.gymfort.connector.provider.hikcentral.HikCentralDriver;
import com.gymfort.connector.provider.hikcentral.HikCentralOpenApiClient;
import com.gymfort.connector.provider.simulator.SimulatorDriver;
import com.gymfort.connector.secrets.SecretStore;
import com.gymfort.connector.secrets.SecretStores;
import java.io.Console;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

public final class ConnectorMain {
    private static final String CLOUD_SECRET = "cloud.connectorSecret";
    private static final String HIKCENTRAL_SECRET = "hikcentral.appSecret";

    private ConnectorMain() {
    }

    public static void main(String[] args) {
        try {
            Arguments parsed = Arguments.parse(args);
            ConnectorConfig config = ConnectorConfig.load(parsed.configPath());
            SecretStore secrets = SecretStores.systemDefault(config.stateDirectory());
            switch (parsed.command()) {
                case "activate" -> activate(config, secrets, parsed.commandArgument());
                case "set-secret" -> setSecret(secrets, parsed.commandArgument());
                case "doctor" -> doctor(config, secrets);
                case "run" -> run(config, secrets);
                default -> throw new IllegalArgumentException("Comando no soportado: " + parsed.command());
            }
        } catch (RuntimeException exception) {
            System.err.println("Connector error: " + safe(exception.getMessage()));
            System.exit(1);
        }
    }

    private static void activate(ConnectorConfig config, SecretStore secrets, String activationCode) {
        if (activationCode == null || activationCode.isBlank()) {
            throw new IllegalArgumentException("Indica el código de activación de un solo uso.");
        }
        GymFortCloudClient cloud = new GymFortCloudClient(config.cloudBaseUrl(), config.connectorKey(), null);
        ActivationResponse response = cloud.activate(activationCode.trim());
        char[] secret = response.connectorSecret().toCharArray();
        try {
            secrets.write(CLOUD_SECRET, secret);
        } finally {
            Arrays.fill(secret, '\0');
        }
        System.out.println("Connector activado para el proveedor " + response.providerKey()
                + ". El secreto quedó protegido localmente.");
    }

    private static void setSecret(SecretStore secrets, String name) {
        if (!HIKCENTRAL_SECRET.equals(name)) {
            throw new IllegalArgumentException("Sólo se permite configurar " + HIKCENTRAL_SECRET + ".");
        }
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Se requiere una consola interactiva para capturar el secreto.");
        }
        char[] value = console.readPassword("App Secret de HikCentral: ");
        try {
            secrets.write(name, value);
        } finally {
            if (value != null) {
                Arrays.fill(value, '\0');
            }
        }
        System.out.println("Secreto HikCentral protegido localmente.");
    }

    private static void doctor(ConnectorConfig config, SecretStore secrets) {
        boolean cloudReady = hasSecret(secrets, CLOUD_SECRET);
        try (AccessProviderDriver driver = driver(config, secrets)) {
            ProviderHealth health = driver.health();
            System.out.println("GymFort credential: " + (cloudReady ? "READY" : "MISSING"));
            System.out.println("Provider: " + driver.providerKey());
            System.out.println("Provider health: " + (health.healthy() ? "HEALTHY" : "DEGRADED"));
            System.out.println("Provider version: " + (health.providerVersion() == null ? "unknown" : health.providerVersion()));
            System.out.println("Capabilities: " + health.capabilities());
            System.out.println("Detail: " + (health.detail() == null ? "none" : health.detail()));
            if (!cloudReady || !health.healthy()) {
                throw new IllegalStateException("El diagnóstico encontró requisitos pendientes.");
            }
        }
    }

    private static void run(ConnectorConfig config, SecretStore secrets) {
        char[] cloudSecret = requiredSecret(secrets, CLOUD_SECRET,
                "Activa primero el Connector o configura GYMFORT_CONNECTOR_SECRET.");
        AccessProviderDriver driver = driver(config, secrets);
        SqliteExecutionLedger ledger = new SqliteExecutionLedger(config.stateDirectory());
        GymFortCloudClient cloud = new GymFortCloudClient(
                config.cloudBaseUrl(), config.connectorKey(), cloudSecret);
        Arrays.fill(cloudSecret, '\0');
        String version = ConnectorMain.class.getPackage().getImplementationVersion();
        if (version == null) {
            version = "development";
        }
        try (ConnectorWorker worker = new ConnectorWorker(
                cloud, driver, ledger, config.claimLimit(), config.leaseSeconds())) {
            new ConnectorDaemon(
                    cloud, worker, driver, version, config.instanceId(),
                    config.pollInterval(), config.heartbeatInterval()).run();
        }
    }

    private static AccessProviderDriver driver(ConnectorConfig config, SecretStore secrets) {
        if ("SIMULATOR".equals(config.providerKey())) {
            return new SimulatorDriver();
        }
        char[] appSecret = requiredSecret(secrets, HIKCENTRAL_SECRET,
                "Configura GYMFORT_HIKCENTRAL_APP_SECRET o ejecuta set-secret hikcentral.appSecret.");
        try {
            HikCentralOpenApiClient api = new HikCentralOpenApiClient(
                    config.hikCentral().baseUrl(), config.hikCentral().appKey(), appSecret);
            return new HikCentralDriver(api, config.hikCentral().managedAccessLevelRefs());
        } finally {
            Arrays.fill(appSecret, '\0');
        }
    }

    private static char[] requiredSecret(SecretStore secrets, String name, String error) {
        return secrets.read(name).orElseThrow(() -> new IllegalStateException(error));
    }

    private static boolean hasSecret(SecretStore secrets, String name) {
        Optional<char[]> value = secrets.read(name);
        value.ifPresent(secret -> Arrays.fill(secret, '\0'));
        return value.isPresent();
    }

    private static String safe(String value) {
        if (value == null) {
            return "Fallo no especificado.";
        }
        String sanitized = value.replaceAll(
                "(?i)(secret|password|token|authorization)\\s*[:=]\\s*\\S+", "$1=<redacted>");
        return sanitized.substring(0, Math.min(sanitized.length(), 900));
    }

    private record Arguments(Path configPath, String command, String commandArgument) {
        static Arguments parse(String[] args) {
            if (args.length < 3 || !"--config".equals(args[0])) {
                throw new IllegalArgumentException(
                        "Uso: --config <archivo.properties> <activate|set-secret|doctor|run> [argumento]");
            }
            return new Arguments(
                    Path.of(args[1]), args[2].trim().toLowerCase(), args.length > 3 ? args[3] : null);
        }
    }
}
