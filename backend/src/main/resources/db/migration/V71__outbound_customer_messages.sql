CREATE TABLE outbound_message (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    branch_id UUID,
    rule_id UUID,
    work_item_id UUID,
    client_id UUID NOT NULL,
    channel VARCHAR(32) NOT NULL,
    recipient VARCHAR(320),
    subject VARCHAR(240),
    body TEXT NOT NULL,
    template_code VARCHAR(120),
    template_version INTEGER NOT NULL DEFAULT 1,
    communication_type VARCHAR(32) NOT NULL DEFAULT 'OPERATIONAL',
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(500) NOT NULL,
    provider VARCHAR(80),
    provider_message_id VARCHAR(240),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT outbound_message_channel_ck CHECK (channel IN ('EMAIL', 'WHATSAPP', 'PUSH')),
    CONSTRAINT outbound_message_status_ck CHECK (status IN (
        'PENDING', 'SENDING', 'ACCEPTED', 'DELIVERED', 'FAILED',
        'UNDELIVERABLE', 'CANCELLED'
    )),
    CONSTRAINT outbound_message_communication_type_ck CHECK (
        communication_type IN ('OPERATIONAL', 'MARKETING')
    ),
    CONSTRAINT outbound_message_idempotency_uq UNIQUE (organization_id, idempotency_key)
);

CREATE INDEX outbound_message_dispatch_idx
    ON outbound_message (channel, status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX outbound_message_batch_idx ON outbound_message (batch_id, created_at);
CREATE INDEX outbound_message_client_idx ON outbound_message (organization_id, client_id, created_at DESC);
CREATE INDEX outbound_message_rule_idx ON outbound_message (organization_id, rule_id, created_at DESC);
