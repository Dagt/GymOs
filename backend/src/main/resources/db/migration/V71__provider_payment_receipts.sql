ALTER TABLE payment_provider_operation
    ADD COLUMN receipt_url VARCHAR(2048);

CREATE INDEX ix_provider_operation_client_updated
    ON payment_provider_operation (organization_id, client_id, updated_at DESC, id DESC)
    WHERE client_id IS NOT NULL;
