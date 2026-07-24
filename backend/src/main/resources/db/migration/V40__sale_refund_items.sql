CREATE TABLE sale_refund_item (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    sale_id UUID NOT NULL,
    refund_id UUID NOT NULL,
    sale_item_id UUID NOT NULL,
    product_id UUID,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    restock BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    CONSTRAINT fk_sale_refund_item_refund
        FOREIGN KEY (refund_id) REFERENCES sale_refund (id),
    CONSTRAINT fk_sale_refund_item_sale_item
        FOREIGN KEY (sale_item_id) REFERENCES sale_item (id)
);

CREATE UNIQUE INDEX uq_sale_refund_item_refund_sale_item
    ON sale_refund_item (refund_id, sale_item_id);

CREATE INDEX idx_sale_refund_item_sale_item
    ON sale_refund_item (organization_id, sale_id, sale_item_id);

CREATE INDEX idx_sale_refund_item_refund
    ON sale_refund_item (organization_id, refund_id);
