package com.gymfort.connector.core;

import java.util.Optional;
import java.util.UUID;

public interface ExecutionLedger extends AutoCloseable {
    Execution start(String idempotencyKey, UUID cloudJobId);

    Optional<Execution> find(String idempotencyKey);

    void succeed(String idempotencyKey, String externalOperationRef);

    void fail(String idempotencyKey, String error);

    @Override
    void close();

    record Execution(
            String idempotencyKey,
            UUID cloudJobId,
            State state,
            String externalOperationRef,
            String lastError
    ) {
    }

    enum State {
        STARTED,
        SUCCEEDED,
        FAILED
    }
}
