-- Migration V44: Inventory Operations Hardening, Negative Stock Policy, Physical Counts and Branch Transfers

-- 1. Configuración de Sucursal para Inventario (Política de Stock Negativo)
ALTER TABLE branch
    ADD COLUMN IF NOT EXISTS allow_negative_stock BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Permisos adicionales de Inventario
INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES
    ('88888888-8888-8888-8888-888888888923', 'inventory.transfer', 'Gestionar transferencias entre sucursales', 'inventory', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888924', 'inventory.count', 'Realizar conteos fisicos de inventario', 'inventory', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888925', 'inventory.settings.manage', 'Configurar parametros de inventario', 'inventory', now(), now(), 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT legacy.role_id, expanded.id
FROM role_permission legacy
JOIN permission legacy_permission
    ON legacy_permission.id = legacy.permission_id
   AND legacy_permission.code = 'inventory.manage'
CROSS JOIN permission expanded
WHERE expanded.code IN (
    'inventory.transfer',
    'inventory.count',
    'inventory.settings.manage'
)
ON CONFLICT DO NOTHING;

-- 3. Conteos Físicos de Inventario (Auditoría de Stock)
CREATE TABLE inventory_physical_count (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    status VARCHAR(32) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    approved_at TIMESTAMPTZ,
    approved_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_inventory_physical_count_org_branch_status
    ON inventory_physical_count (organization_id, branch_id, status);

CREATE TABLE inventory_physical_count_item (
    id UUID PRIMARY KEY,
    physical_count_id UUID NOT NULL REFERENCES inventory_physical_count(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES catalog_product(id),
    system_quantity INTEGER NOT NULL,
    counted_quantity INTEGER,
    variance_quantity INTEGER,
    unit_cost_cents BIGINT,
    notes TEXT,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_inventory_physical_count_item_count_prod
    ON inventory_physical_count_item (physical_count_id, product_id);

-- 4. Transferencias entre Sucursales (Inter-Branch Transfers)
CREATE TABLE inventory_transfer (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    origin_branch_id UUID NOT NULL REFERENCES branch(id),
    destination_branch_id UUID NOT NULL REFERENCES branch(id),
    status VARCHAR(32) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    shipped_at TIMESTAMPTZ,
    shipped_by UUID,
    received_at TIMESTAMPTZ,
    received_by UUID,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_inventory_transfer_org_origin
    ON inventory_transfer (organization_id, origin_branch_id, status);
CREATE INDEX ix_inventory_transfer_org_dest
    ON inventory_transfer (organization_id, destination_branch_id, status);

CREATE TABLE inventory_transfer_item (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL REFERENCES inventory_transfer(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES catalog_product(id),
    requested_quantity INTEGER NOT NULL,
    shipped_quantity INTEGER,
    received_quantity INTEGER,
    unit_cost_cents BIGINT
);

CREATE UNIQUE INDEX ux_inventory_transfer_item_transfer_prod
    ON inventory_transfer_item (transfer_id, product_id);
