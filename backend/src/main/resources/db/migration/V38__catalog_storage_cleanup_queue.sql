CREATE TABLE IF NOT EXISTS catalog_storage_cleanup (
    id UUID PRIMARY KEY,
    object_key VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_catalog_storage_cleanup_object UNIQUE (object_key),
    CONSTRAINT ck_catalog_storage_cleanup_status CHECK (status IN ('PENDING', 'COMPLETED')),
    CONSTRAINT ck_catalog_storage_cleanup_attempts CHECK (attempts >= 0)
);

CREATE INDEX IF NOT EXISTS ix_catalog_storage_cleanup_pending
    ON catalog_storage_cleanup (next_attempt_at, created_at)
    WHERE status = 'PENDING';
