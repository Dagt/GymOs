CREATE TABLE automation_domain_event (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    branch_id UUID,
    event_type VARCHAR(80) NOT NULL,
    event_id VARCHAR(240) NOT NULL,
    event_version INTEGER NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload_json JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    processed_at TIMESTAMPTZ,
    last_error VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_automation_domain_event_status
        CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    CONSTRAINT uk_automation_domain_event_identity
        UNIQUE (organization_id, event_type, event_id)
);

CREATE INDEX ix_automation_domain_event_pending
    ON automation_domain_event (status, occurred_at, created_at)
    WHERE status = 'PENDING';
