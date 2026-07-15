ALTER TABLE sale
    ADD COLUMN idempotency_request_hash VARCHAR(128);

ALTER TABLE payment
    ADD COLUMN idempotency_request_hash VARCHAR(128);
