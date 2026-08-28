CREATE UNIQUE INDEX ux_catalog_product_id_organization
    ON catalog_product (id, organization_id);

CREATE UNIQUE INDEX ux_sale_id_organization
    ON sale (id, organization_id);

CREATE TABLE visit_package_plan (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    product_id UUID NOT NULL,
    total_visits INTEGER NOT NULL CHECK (total_visits > 0),
    validity_days INTEGER NOT NULL CHECK (validity_days > 0),
    branch_scope VARCHAR(24) NOT NULL,
    self_service_acquisition_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    registration_fee_policy VARCHAR(20) NOT NULL DEFAULT 'DEFAULT',
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID REFERENCES user_account(id),
    updated_by UUID REFERENCES user_account(id),
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_visit_package_plan_product
        FOREIGN KEY (product_id, organization_id)
        REFERENCES catalog_product(id, organization_id),
    CONSTRAINT ck_visit_package_plan_branch_scope
        CHECK (branch_scope IN ('ALL_BRANCHES', 'SELECTED_BRANCHES')),
    CONSTRAINT ck_visit_package_plan_registration_fee_policy
        CHECK (registration_fee_policy IN ('DEFAULT', 'WAIVED')),
    CONSTRAINT ck_visit_package_plan_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX ux_visit_package_plan_product
    ON visit_package_plan (product_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_visit_package_plan_id_organization
    ON visit_package_plan (id, organization_id);

CREATE INDEX ix_visit_package_plan_org_status
    ON visit_package_plan (organization_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE visit_package_plan_branch (
    visit_package_plan_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    organization_id UUID NOT NULL REFERENCES organization(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES user_account(id),
    PRIMARY KEY (visit_package_plan_id, branch_id),
    CONSTRAINT fk_visit_package_plan_branch_plan
        FOREIGN KEY (visit_package_plan_id, organization_id)
        REFERENCES visit_package_plan(id, organization_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_visit_package_plan_branch_branch
        FOREIGN KEY (branch_id, organization_id)
        REFERENCES branch(id, organization_id)
);

CREATE INDEX ix_visit_package_plan_branch_org_branch
    ON visit_package_plan_branch (organization_id, branch_id);

CREATE TABLE visit_package_plan_benefit (
    visit_package_plan_id UUID NOT NULL,
    benefit_id UUID NOT NULL,
    organization_id UUID NOT NULL REFERENCES organization(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES user_account(id),
    PRIMARY KEY (visit_package_plan_id, benefit_id),
    CONSTRAINT fk_visit_package_plan_benefit_plan
        FOREIGN KEY (visit_package_plan_id, organization_id)
        REFERENCES visit_package_plan(id, organization_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_visit_package_plan_benefit_benefit
        FOREIGN KEY (benefit_id, organization_id)
        REFERENCES benefit(id, organization_id)
);

CREATE INDEX ix_visit_package_plan_benefit_org_benefit
    ON visit_package_plan_benefit (organization_id, benefit_id);

CREATE TABLE visit_package_entitlement (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    home_branch_id UUID NOT NULL,
    client_id UUID NOT NULL,
    visit_package_plan_id UUID NOT NULL,
    sale_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_visits INTEGER NOT NULL CHECK (total_visits > 0),
    remaining_visits INTEGER NOT NULL CHECK (remaining_visits >= 0),
    valid_from DATE,
    valid_until DATE,
    activated_at TIMESTAMPTZ,
    pending_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_visit_package_entitlement_branch
        FOREIGN KEY (home_branch_id, organization_id)
        REFERENCES branch(id, organization_id),
    CONSTRAINT fk_visit_package_entitlement_client
        FOREIGN KEY (client_id, organization_id)
        REFERENCES client(id, organization_id),
    CONSTRAINT fk_visit_package_entitlement_plan
        FOREIGN KEY (visit_package_plan_id, organization_id)
        REFERENCES visit_package_plan(id, organization_id),
    CONSTRAINT fk_visit_package_entitlement_sale
        FOREIGN KEY (sale_id, organization_id)
        REFERENCES sale(id, organization_id),
    CONSTRAINT ck_visit_package_entitlement_status
        CHECK (status IN ('PENDING_PAYMENT', 'ACTIVE', 'CONSUMED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_visit_package_entitlement_balance
        CHECK (remaining_visits <= total_visits),
    CONSTRAINT ck_visit_package_entitlement_dates
        CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from)
);

CREATE UNIQUE INDEX ux_visit_package_entitlement_sale
    ON visit_package_entitlement (organization_id, sale_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_visit_package_entitlement_open_client
    ON visit_package_entitlement (organization_id, client_id)
    WHERE deleted_at IS NULL AND status IN ('PENDING_PAYMENT', 'ACTIVE');

CREATE INDEX ix_visit_package_entitlement_lookup
    ON visit_package_entitlement (organization_id, client_id, status, valid_until)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_visit_package_entitlement_id_organization
    ON visit_package_entitlement (id, organization_id);

CREATE TABLE visit_package_entitlement_branch (
    entitlement_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    organization_id UUID NOT NULL REFERENCES organization(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (entitlement_id, branch_id),
    CONSTRAINT fk_visit_package_entitlement_branch_entitlement
        FOREIGN KEY (entitlement_id, organization_id)
        REFERENCES visit_package_entitlement(id, organization_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_visit_package_entitlement_branch_branch
        FOREIGN KEY (branch_id, organization_id)
        REFERENCES branch(id, organization_id)
);

CREATE INDEX ix_visit_package_entitlement_branch_org_branch
    ON visit_package_entitlement_branch (organization_id, branch_id);

CREATE TABLE visit_package_entitlement_benefit (
    entitlement_id UUID NOT NULL,
    benefit_id UUID NOT NULL,
    organization_id UUID NOT NULL REFERENCES organization(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (entitlement_id, benefit_id),
    CONSTRAINT fk_visit_package_entitlement_benefit_entitlement
        FOREIGN KEY (entitlement_id, organization_id)
        REFERENCES visit_package_entitlement(id, organization_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_visit_package_entitlement_benefit_benefit
        FOREIGN KEY (benefit_id, organization_id)
        REFERENCES benefit(id, organization_id)
);

CREATE INDEX ix_visit_package_entitlement_benefit_org_benefit
    ON visit_package_entitlement_benefit (organization_id, benefit_id);

CREATE TABLE visit_package_consumption (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    entitlement_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    access_device_id UUID,
    access_log_id UUID REFERENCES access_log(id),
    idempotency_key VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'CONSUMED',
    remaining_after INTEGER NOT NULL CHECK (remaining_after >= 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    reversed_at TIMESTAMPTZ,
    reversal_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_visit_package_consumption_entitlement
        FOREIGN KEY (entitlement_id, organization_id)
        REFERENCES visit_package_entitlement(id, organization_id),
    CONSTRAINT fk_visit_package_consumption_branch
        FOREIGN KEY (branch_id, organization_id)
        REFERENCES branch(id, organization_id),
    CONSTRAINT fk_visit_package_consumption_device
        FOREIGN KEY (access_device_id, organization_id)
        REFERENCES access_device(id, organization_id),
    CONSTRAINT ck_visit_package_consumption_status
        CHECK (status IN ('CONSUMED', 'REVERSED'))
);

CREATE UNIQUE INDEX ux_visit_package_consumption_idempotency
    ON visit_package_consumption (organization_id, idempotency_key);

CREATE INDEX ix_visit_package_consumption_entitlement_occurred
    ON visit_package_consumption (organization_id, entitlement_id, occurred_at DESC);

COMMENT ON TABLE visit_package_plan IS
    'Configuración vendible de un SERVICE/VISIT_PASS; no es una membresía ilimitada.';

COMMENT ON TABLE visit_package_entitlement IS
    'Saldo individual reservado o activo; sólo puede existir uno abierto por socio y organización.';

COMMENT ON TABLE visit_package_consumption IS
    'Ledger idempotente de visitas consumidas y su sucursal; nunca representa sabores, membresías ni inventario.';
