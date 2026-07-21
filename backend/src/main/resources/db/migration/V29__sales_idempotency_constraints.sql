CREATE UNIQUE INDEX ux_sale_org_idempotency_key
    ON sale (organization_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX ux_payment_org_idempotency_key
    ON payment (organization_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX ux_sale_refund_org_idempotency_key
    ON sale_refund (organization_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
