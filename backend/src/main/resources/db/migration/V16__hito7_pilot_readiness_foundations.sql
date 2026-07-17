CREATE TABLE benefit (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID REFERENCES branch(id),
    code VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    benefit_type VARCHAR(40) NOT NULL,
    discount_type VARCHAR(30) NOT NULL,
    discount_value_cents INTEGER,
    discount_percent NUMERIC(5,2),
    requires_evidence BOOLEAN NOT NULL DEFAULT false,
    combinable BOOLEAN NOT NULL DEFAULT false,
    priority INTEGER NOT NULL DEFAULT 100,
    valid_from DATE,
    valid_until DATE,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_benefit_org_code ON benefit (organization_id, code) WHERE deleted_at IS NULL;
CREATE INDEX ix_benefit_org_branch_status ON benefit (organization_id, branch_id, status);

CREATE TABLE client_benefit (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID REFERENCES branch(id),
    client_id UUID NOT NULL REFERENCES client(id),
    benefit_id UUID NOT NULL REFERENCES benefit(id),
    status VARCHAR(30) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    valid_from DATE,
    valid_until DATE,
    verified_at TIMESTAMPTZ,
    verified_by UUID REFERENCES user_account(id),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_client_benefit_active ON client_benefit (organization_id, client_id, benefit_id)
WHERE deleted_at IS NULL AND status IN ('ASSIGNED', 'VERIFIED');
CREATE INDEX ix_client_benefit_org_client_status ON client_benefit (organization_id, client_id, status);

CREATE TABLE benefit_evidence (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    client_benefit_id UUID NOT NULL REFERENCES client_benefit(id),
    object_key TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL,
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES user_account(id),
    rejection_reason TEXT
);

CREATE INDEX ix_benefit_evidence_client_benefit ON benefit_evidence (client_benefit_id, status);

CREATE TABLE visit_pass (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    client_id UUID REFERENCES client(id),
    sale_id UUID REFERENCES sale(id),
    pass_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_visits INTEGER NOT NULL,
    remaining_visits INTEGER NOT NULL,
    valid_from DATE NOT NULL,
    valid_until DATE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_visit_pass_org_branch_client_status ON visit_pass (organization_id, branch_id, client_id, status);

CREATE TABLE sale_refund (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    sale_id UUID NOT NULL REFERENCES sale(id),
    payment_id UUID REFERENCES payment(id),
    status VARCHAR(30) NOT NULL,
    amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
    currency CHAR(3) NOT NULL,
    reason TEXT NOT NULL,
    idempotency_key VARCHAR(120),
    requested_by UUID REFERENCES user_account(id),
    requested_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_sale_refund_org_idempotency ON sale_refund (organization_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_sale_refund_org_sale_status ON sale_refund (organization_id, sale_id, status);

CREATE TABLE booking_payment (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    booking_id UUID NOT NULL REFERENCES booking(id),
    sale_id UUID NOT NULL REFERENCES sale(id),
    status VARCHAR(30) NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_booking_payment_booking ON booking_payment (organization_id, booking_id);
CREATE INDEX ix_booking_payment_status_expires ON booking_payment (organization_id, status, expires_at);

CREATE TABLE booking_policy (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID REFERENCES branch(id),
    resource_id UUID REFERENCES resource(id),
    min_notice_minutes INTEGER NOT NULL DEFAULT 0,
    max_advance_days INTEGER NOT NULL DEFAULT 30,
    cancellation_cutoff_minutes INTEGER NOT NULL DEFAULT 0,
    no_show_penalty_cents INTEGER NOT NULL DEFAULT 0,
    buffer_before_minutes INTEGER NOT NULL DEFAULT 0,
    buffer_after_minutes INTEGER NOT NULL DEFAULT 0,
    require_membership BOOLEAN NOT NULL DEFAULT false,
    require_payment BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_booking_policy_org_branch_resource ON booking_policy (organization_id, branch_id, resource_id);

CREATE TABLE auto_renewal_attempt (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    membership_id UUID NOT NULL REFERENCES membership(id),
    sale_id UUID REFERENCES sale(id),
    status VARCHAR(30) NOT NULL,
    attempt_number INTEGER NOT NULL,
    scheduled_for TIMESTAMPTZ NOT NULL,
    attempted_at TIMESTAMPTZ,
    failure_code VARCHAR(80),
    failure_message TEXT,
    idempotency_key VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_auto_renewal_attempt_idempotency ON auto_renewal_attempt (organization_id, idempotency_key);
CREATE INDEX ix_auto_renewal_attempt_status_schedule ON auto_renewal_attempt (organization_id, status, scheduled_for);

CREATE TABLE notification_template (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    code VARCHAR(80) NOT NULL,
    channel VARCHAR(30) NOT NULL,
    subject VARCHAR(200),
    body TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_notification_template_org_code_channel ON notification_template (organization_id, code, channel);

ALTER TABLE membership
    ADD COLUMN IF NOT EXISTS auto_renew BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancellation_reason TEXT;

ALTER TABLE automation_rule
    ADD COLUMN IF NOT EXISTS trigger_type VARCHAR(40) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS schedule_cron VARCHAR(120),
    ADD COLUMN IF NOT EXISTS next_run_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;

INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES
    (gen_random_uuid(), 'benefit.read', 'Leer beneficios', 'benefit', now(), now(), 0),
    (gen_random_uuid(), 'benefit.manage', 'Gestionar beneficios', 'benefit', now(), now(), 0),
    (gen_random_uuid(), 'client.benefit.assign', 'Asignar beneficios a clientes', 'client', now(), now(), 0),
    (gen_random_uuid(), 'client.benefit.verify', 'Verificar evidencia de beneficios', 'client', now(), now(), 0),
    (gen_random_uuid(), 'membership.expire', 'Expirar membresias', 'membership', now(), now(), 0),
    (gen_random_uuid(), 'membership.auto-renew.manage', 'Gestionar renovacion automatica', 'membership', now(), now(), 0),
    (gen_random_uuid(), 'visit.create', 'Crear visitas', 'visit', now(), now(), 0),
    (gen_random_uuid(), 'visit.consume', 'Consumir visitas', 'visit', now(), now(), 0),
    (gen_random_uuid(), 'visit.read', 'Leer visitas', 'visit', now(), now(), 0),
    (gen_random_uuid(), 'booking.payment.manage', 'Gestionar pagos de reservas', 'booking', now(), now(), 0),
    (gen_random_uuid(), 'booking.policy.manage', 'Gestionar politicas de reserva', 'booking', now(), now(), 0),
    (gen_random_uuid(), 'notification.template.manage', 'Gestionar plantillas de notificacion', 'notification', now(), now(), 0),
    (gen_random_uuid(), 'automation.execute', 'Ejecutar automatizaciones', 'automation', now(), now(), 0),
    (gen_random_uuid(), 'automation.scheduler.manage', 'Gestionar automatizaciones programadas', 'automation', now(), now(), 0),
    (gen_random_uuid(), 'backup.execute', 'Ejecutar backups', 'operations', now(), now(), 0),
    (gen_random_uuid(), 'backup.restore', 'Restaurar backups', 'operations', now(), now(), 0),
    (gen_random_uuid(), 'operations.read', 'Leer estado operativo', 'operations', now(), now(), 0)
ON CONFLICT (code) DO NOTHING;
