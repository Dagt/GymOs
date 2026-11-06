ALTER TABLE sale ADD COLUMN cancellation_reason VARCHAR(500);
ALTER TABLE payment_provider_webhook_event ADD COLUMN processing_attempt_id UUID;
ALTER TABLE payment_provider_webhook_event ADD COLUMN processing_started_at TIMESTAMPTZ;
ALTER TABLE sale ADD COLUMN initial_payment_id UUID REFERENCES payment(id);
CREATE TABLE inventory_sale_reservation (
    stock_id UUID NOT NULL REFERENCES inventory_stock(id),
    sale_id UUID NOT NULL REFERENCES sale(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (stock_id, sale_id)
);
CREATE INDEX ix_inventory_sale_reservation_sale ON inventory_sale_reservation(sale_id);
ALTER TABLE payment ADD COLUMN provider_submission_prepared_at TIMESTAMPTZ;
