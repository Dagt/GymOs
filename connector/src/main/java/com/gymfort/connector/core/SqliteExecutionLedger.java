package com.gymfort.connector.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public final class SqliteExecutionLedger implements ExecutionLedger {
    private final String jdbcUrl;

    public SqliteExecutionLedger(Path stateDirectory) {
        try {
            Files.createDirectories(stateDirectory);
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible crear el directorio de estado local.", exception);
        }
        this.jdbcUrl = "jdbc:sqlite:" + stateDirectory.resolve("connector-state.db").toAbsolutePath();
        initialize();
    }

    @Override
    public synchronized Execution start(String idempotencyKey, UUID cloudJobId) {
        requiredKey(idempotencyKey);
        try (Connection connection = connection()) {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT OR IGNORE INTO job_execution (
                        idempotency_key, cloud_job_id, state, created_at, updated_at
                    ) VALUES (?, ?, 'STARTED', ?, ?)
                    """)) {
                String now = OffsetDateTime.now().toString();
                insert.setString(1, idempotencyKey);
                insert.setString(2, cloudJobId.toString());
                insert.setString(3, now);
                insert.setString(4, now);
                insert.executeUpdate();
            }
            return find(connection, idempotencyKey).orElseThrow();
        } catch (SQLException exception) {
            throw new IllegalStateException("No fue posible registrar el inicio idempotente del job.", exception);
        }
    }

    @Override
    public synchronized Optional<Execution> find(String idempotencyKey) {
        requiredKey(idempotencyKey);
        try (Connection connection = connection()) {
            return find(connection, idempotencyKey);
        } catch (SQLException exception) {
            throw new IllegalStateException("No fue posible consultar el ledger local.", exception);
        }
    }

    @Override
    public synchronized void succeed(String idempotencyKey, String externalOperationRef) {
        update(idempotencyKey, "SUCCEEDED", externalOperationRef, null);
    }

    @Override
    public synchronized void fail(String idempotencyKey, String error) {
        update(idempotencyKey, "FAILED", null, safe(error));
    }

    @Override
    public void close() {
        // Connections are intentionally short-lived to make restart recovery simple.
    }

    private void initialize() {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=FULL");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS job_execution (
                        idempotency_key TEXT PRIMARY KEY,
                        cloud_job_id TEXT NOT NULL,
                        state TEXT NOT NULL CHECK (state IN ('STARTED', 'SUCCEEDED', 'FAILED')),
                        external_operation_ref TEXT,
                        last_error TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("No fue posible inicializar el ledger SQLite.", exception);
        }
    }

    private Connection connection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }

    private Optional<Execution> find(Connection connection, String idempotencyKey) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT idempotency_key, cloud_job_id, state, external_operation_ref, last_error
                FROM job_execution
                WHERE idempotency_key = ?
                """)) {
            query.setString(1, idempotencyKey);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Execution(
                        result.getString("idempotency_key"),
                        UUID.fromString(result.getString("cloud_job_id")),
                        State.valueOf(result.getString("state")),
                        result.getString("external_operation_ref"),
                        result.getString("last_error")));
            }
        }
    }

    private void update(String idempotencyKey, String state, String externalOperationRef, String error) {
        requiredKey(idempotencyKey);
        try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement("""
                UPDATE job_execution
                SET state = ?, external_operation_ref = ?, last_error = ?, updated_at = ?
                WHERE idempotency_key = ?
                """)) {
            update.setString(1, state);
            update.setString(2, externalOperationRef);
            update.setString(3, error);
            update.setString(4, OffsetDateTime.now().toString());
            update.setString(5, idempotencyKey);
            if (update.executeUpdate() != 1) {
                throw new IllegalStateException("El job no fue iniciado antes de finalizarse.");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("No fue posible actualizar el ledger local.", exception);
        }
    }

    private static void requiredKey(String value) {
        if (value == null || value.isBlank() || value.length() > 180) {
            throw new IllegalArgumentException("Idempotency key inválida.");
        }
    }

    private static String safe(String value) {
        if (value == null) {
            return null;
        }
        return value.substring(0, Math.min(value.length(), 900));
    }
}
