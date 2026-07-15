CREATE TABLE inventory_stock (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    product_id UUID NOT NULL REFERENCES catalog_product(id),
    quantity_on_hand INTEGER NOT NULL DEFAULT 0,
    reorder_point INTEGER NOT NULL DEFAULT 0,
    average_cost_cents BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_inventory_stock_org_branch_product
    ON inventory_stock (organization_id, branch_id, product_id)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_inventory_stock_org_branch_low
    ON inventory_stock (organization_id, branch_id, quantity_on_hand, reorder_point);

CREATE TABLE inventory_movement (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    stock_id UUID NOT NULL REFERENCES inventory_stock(id),
    product_id UUID NOT NULL REFERENCES catalog_product(id),
    movement_type VARCHAR(40) NOT NULL,
    quantity_delta INTEGER NOT NULL,
    quantity_before INTEGER NOT NULL,
    quantity_after INTEGER NOT NULL,
    unit_cost_cents BIGINT,
    reference_type VARCHAR(80),
    reference_id UUID,
    reason TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    correlation_id UUID NOT NULL
);

CREATE INDEX ix_inventory_movement_org_branch_occurred
    ON inventory_movement (organization_id, branch_id, occurred_at);
CREATE INDEX ix_inventory_movement_product_occurred
    ON inventory_movement (product_id, occurred_at);

INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES
    ('88888888-8888-8888-8888-888888888921', 'inventory.read', 'Permiso inventory.read', 'inventory', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888922', 'inventory.manage', 'Permiso inventory.manage', 'inventory', now(), now(), 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('inventory.read', 'inventory.manage')
WHERE r.name = 'Administrador'
ON CONFLICT DO NOTHING;

INSERT INTO inventory_stock (
    id, organization_id, branch_id, product_id, quantity_on_hand, reorder_point,
    average_cost_cents, created_at, updated_at, version
)
VALUES (
    '99999999-9999-9999-9999-999999999921',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '66666666-6666-6666-6666-666666666602',
    24,
    8,
    1200,
    now(),
    now(),
    0
)
ON CONFLICT DO NOTHING;

INSERT INTO inventory_movement (
    id, organization_id, branch_id, stock_id, product_id, movement_type,
    quantity_delta, quantity_before, quantity_after, unit_cost_cents,
    reference_type, reference_id, reason, occurred_at, created_at, correlation_id
)
VALUES (
    '99999999-9999-9999-9999-999999999922',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '99999999-9999-9999-9999-999999999921',
    '66666666-6666-6666-6666-666666666602',
    'INITIAL',
    24,
    0,
    24,
    1200,
    'SEED',
    NULL,
    'Inventario inicial de producto demo.',
    now(),
    now(),
    '00000000-0000-0000-0000-000000000001'
)
ON CONFLICT DO NOTHING;
