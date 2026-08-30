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

/** Durable cursors and fail-safe state for provider event processing. */
public final class ConnectorStateStore implements AutoCloseable {
    private final String jdbcUrl;

    public ConnectorStateStore(Path stateDirectory) {
        try {
            Files.createDirectories(stateDirectory);
        } catch (Exception exception) {
            throw new IllegalStateException("No fue posible crear el directorio de estado local.", exception);
        }
        this.jdbcUrl = "jdbc:sqlite:" + stateDirectory.resolve("connector-state.db").toAbsolutePath();
        initialize();
    }

    public synchronized String cursor(String stream) {
        try (Connection connection = connection(); PreparedStatement query = connection.prepareStatement(
                "SELECT cursor_value FROM provider_cursor WHERE stream_name = ?")) {
            query.setString(1, required(stream, 80));
            try (ResultSet result = query.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("No fue posible consultar el cursor local.", exception);
        }
    }

    public synchronized void saveCursor(String stream, String cursor) {
        try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement("""
                INSERT INTO provider_cursor (stream_name, cursor_value, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(stream_name) DO UPDATE SET
                    cursor_value = excluded.cursor_value,
                    updated_at = excluded.updated_at
                """)) {
            update.setString(1, required(stream, 80));
            update.setString(2, cursor);
            update.setString(3, OffsetDateTime.now().toString());
            update.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("No fue posible guardar el cursor local.", exception);
        }
    }

    public synchronized EventState begin(String externalEventId) {
        String eventId = required(externalEventId, 180);
        try (Connection connection = connection()) {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT OR IGNORE INTO provider_event_execution (
                        external_event_id, state, created_at, updated_at
                    ) VALUES (?, 'RECEIVED', ?, ?)
                    """)) {
                String now = OffsetDateTime.now().toString();
                insert.setString(1, eventId);
                insert.setString(2, now);
                insert.setString(3, now);
                insert.executeUpdate();
            }
            return find(connection, eventId).orElseThrow();
        } catch (SQLException exception) {
            throw new IllegalStateException("No fue posible registrar el evento físico local.", exception);
        }
    }

    public synchronized Optional<EventState> find(String externalEventId) {
        try (Connection connection = connection()) {
            return find(connection, required(externalEventId, 180));
        } catch (SQLException exception) {
            throw new IllegalStateException("No fue posible consultar el evento físico local.", exception);
        }
    }

    public void authorized(String eventId, UUID authorizationId, boolean shouldOpen) {
        update(eventId, shouldOpen ? State.AUTHORIZED : State.COMPLETED, authorizationId, null, null);
    }

    public void actuating(String eventId, UUID authorizationId) {
        update(eventId, State.ACTUATING, authorizationId, null, null);
    }

    public void succeeded(String eventId, UUID authorizationId, String operationRef) {
        update(eventId, State.COMPLETED, authorizationId, operationRef, null);
    }

    public void failed(String eventId, UUID authorizationId, String error) {
        update(eventId, State.FAILED, authorizationId, null, safe(error));
    }

    public void uncertain(String eventId, UUID authorizationId, String error) {
        update(eventId, State.UNCERTAIN, authorizationId, null, safe(error));
    }

    private synchronized void update(
            String eventId, State state, UUID authorizationId, String operationRef, String error
    ) {
        try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement("""
                UPDATE provider_event_execution
                SET state = ?, authorization_id = COALESCE(?, authorization_id),
                    external_operation_ref = ?, last_error = ?, updated_at = ?
                WHERE external_event_id = ?
                """)) {
            update.setString(1, state.name());
            update.setString(2, authorizationId == null ? null : authorizationId.toString());
            update.setString(3, operationRef);
            update.setString(4, error);
            update.setString(5, OffsetDateTime.now().toString());
            update.setString(6, required(eventId, 180));
            if (update.executeUpdate() != 1) {
                throw new IllegalStateException("El evento debe iniciarse antes de actualizarse.");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("No fue posible actualizar el evento físico local.", exception);
        }
    }

    private void initialize() {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=FULL");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS provider_cursor (
                        stream_name TEXT PRIMARY KEY,
                        cursor_value TEXT,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS provider_event_execution (
                        external_event_id TEXT PRIMARY KEY,
                        state TEXT NOT NULL CHECK (state IN (
                            'RECEIVED', 'AUTHORIZED', 'ACTUATING', 'COMPLETED', 'FAILED', 'UNCERTAIN'
                        )),
                        authorization_id TEXT,
                        external_operation_ref TEXT,
                        last_error TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("No fue posible inicializar el estado del proveedor.", exception);
        }
    }

    private Connection connection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }

    private Optional<EventState> find(Connection connection, String eventId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT external_event_id, state, authorization_id, external_operation_ref, last_error
                FROM provider_event_execution WHERE external_event_id = ?
                """)) {
            query.setString(1, eventId);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) return Optional.empty();
                String authorization = result.getString("authorization_id");
                return Optional.of(new EventState(
                        result.getString("external_event_id"), State.valueOf(result.getString("state")),
                        authorization == null ? null : UUID.fromString(authorization),
                        result.getString("external_operation_ref"), result.getString("last_error")));
            }
        }
    }

    private static String required(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException("Identificador local inválido.");
        }
        return value;
    }

    private static String safe(String value) {
        if (value == null) return null;
        String sanitized = value.replaceAll(
                "(?i)(secret|password|token|authorization)\\s*[:=]\\s*\\S+", "$1=<redacted>");
        return sanitized.substring(0, Math.min(sanitized.length(), 900));
    }

    @Override
    public void close() {
    }

    public record EventState(
            String externalEventId, State state, UUID authorizationId,
            String externalOperationRef, String lastError
    ) {
    }

    public enum State { RECEIVED, AUTHORIZED, ACTUATING, COMPLETED, FAILED, UNCERTAIN }
}
