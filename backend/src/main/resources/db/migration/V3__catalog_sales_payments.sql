CREATE TABLE catalog_product (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    sku VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    product_type VARCHAR(40) NOT NULL,
    price_cents BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    taxable BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_catalog_product_org_sku ON catalog_product (organization_id, sku) WHERE deleted_at IS NULL;
CREATE INDEX ix_catalog_product_org_status ON catalog_product (organization_id, status);

CREATE TABLE membership_plan (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    product_id UUID NOT NULL REFERENCES catalog_product(id),
    duration_days INTEGER NOT NULL,
    grace_days INTEGER NOT NULL DEFAULT 0,
    benefits_json JSONB,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX ix_membership_plan_org_status ON membership_plan (organization_id, status);
CREATE UNIQUE INDEX ux_membership_plan_product ON membership_plan (product_id) WHERE deleted_at IS NULL;

CREATE TABLE sale (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    client_id UUID REFERENCES client(id),
    sale_number VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL,
    subtotal_cents BIGINT NOT NULL,
    discount_cents BIGINT NOT NULL DEFAULT 0,
    total_cents BIGINT NOT NULL,
    paid_cents BIGINT NOT NULL DEFAULT 0,
    balance_cents BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    notes TEXT,
    sold_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_sale_org_number ON sale (organization_id, sale_number);
CREATE INDEX ix_sale_org_branch_sold ON sale (organization_id, branch_id, sold_at);
CREATE INDEX ix_sale_org_client ON sale (organization_id, client_id);

CREATE TABLE sale_item (
    id UUID PRIMARY KEY,
    sale_id UUID NOT NULL REFERENCES sale(id),
    organization_id UUID NOT NULL REFERENCES organization(id),
    product_id UUID REFERENCES catalog_product(id),
    item_type VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price_cents BIGINT NOT NULL,
    discount_cents BIGINT NOT NULL DEFAULT 0,
    total_cents BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_sale_item_sale ON sale_item (sale_id);

CREATE TABLE payment (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    sale_id UUID NOT NULL REFERENCES sale(id),
    client_id UUID REFERENCES client(id),
    method VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount_cents BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    reference VARCHAR(160),
    receipt_object_key VARCHAR(512),
    received_at TIMESTAMPTZ NOT NULL,
    validated_at TIMESTAMPTZ,
    validated_by UUID,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX ix_payment_sale ON payment (sale_id);
CREATE INDEX ix_payment_org_status ON payment (organization_id, status);

INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES
    ('88888888-8888-8888-8888-888888888801', 'catalog.manage', 'Permiso catalog.manage', 'catalog', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888802', 'catalog.read', 'Permiso catalog.read', 'catalog', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888803', 'sale.create', 'Permiso sale.create', 'sales', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888804', 'sale.read', 'Permiso sale.read', 'sales', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888805', 'payment.manual.create', 'Permiso payment.manual.create', 'payments', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888806', 'payment.manual.validate', 'Permiso payment.manual.validate', 'payments', now(), now(), 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'catalog.manage',
    'catalog.read',
    'sale.create',
    'sale.read',
    'payment.manual.create',
    'payment.manual.validate'
)
WHERE r.name = 'Administrador'
ON CONFLICT DO NOTHING;

INSERT INTO catalog_product (
    id, organization_id, sku, name, description, product_type, price_cents,
    currency, taxable, status, created_at, updated_at, version
)
VALUES
    (
        '66666666-6666-6666-6666-666666666601',
        '11111111-1111-1111-1111-111111111111',
        'PLAN-MENSUAL',
        'Membresia mensual',
        'Acceso general por 30 dias.',
        'MEMBERSHIP_PLAN',
        69900,
        'MXN',
        TRUE,
        'ACTIVE',
        now(),
        now(),
        0
    ),
    (
        '66666666-6666-6666-6666-666666666602',
        '11111111-1111-1111-1111-111111111111',
        'BOTELLA-AGUA',
        'Botella de agua',
        'Producto de recepcion.',
        'PHYSICAL',
        2500,
        'MXN',
        TRUE,
        'ACTIVE',
        now(),
        now(),
        0
    )
ON CONFLICT DO NOTHING;

INSERT INTO membership_plan (
    id, organization_id, product_id, duration_days, grace_days, benefits_json,
    status, created_at, updated_at, version
)
VALUES (
    '77777777-7777-7777-7777-777777777701',
    '11111111-1111-1111-1111-111111111111',
    '66666666-6666-6666-6666-666666666601',
    30,
    3,
    '{"access":"general","sessions":"unlimited"}'::jsonb,
    'ACTIVE',
    now(),
    now(),
    0
)
ON CONFLICT DO NOTHING;
