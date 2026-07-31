CREATE TABLE membership_period (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    membership_id UUID NOT NULL REFERENCES membership(id) ON DELETE CASCADE,
    client_id UUID NOT NULL REFERENCES client(id),
    plan_id UUID NOT NULL REFERENCES membership_plan(id),
    sale_id UUID NOT NULL REFERENCES sale(id),
    operation_type VARCHAR(40) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    source_event_key VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_membership_period_range CHECK (ends_on >= starts_on),
    CONSTRAINT ck_membership_period_operation CHECK (
        operation_type IN ('LEGACY_IMPORT', 'ENROLLMENT', 'RENEWAL', 'PLAN_CHANGE')
    )
);

CREATE UNIQUE INDEX ux_membership_period_event
    ON membership_period (organization_id, source_event_key)
    WHERE source_event_key IS NOT NULL;

CREATE INDEX ix_membership_period_membership_created
    ON membership_period (organization_id, membership_id, created_at DESC);

INSERT INTO membership_period (
    id, organization_id, branch_id, membership_id, client_id, plan_id, sale_id,
    operation_type, starts_on, ends_on, source_event_key, created_at
)
SELECT gen_random_uuid(), m.organization_id, m.branch_id, m.id, m.client_id,
       m.plan_id, m.sale_id, 'LEGACY_IMPORT', m.starts_on, m.ends_on,
       NULL, m.activated_at
FROM membership m
WHERE m.deleted_at IS NULL;

WITH ranked_active AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY organization_id, client_id
               ORDER BY grace_ends_on DESC, ends_on DESC, activated_at DESC, id
           ) AS active_rank
    FROM membership
    WHERE status = 'ACTIVE'
      AND deleted_at IS NULL
)
UPDATE membership m
SET status = 'CANCELLED',
    cancelled_at = COALESCE(m.cancelled_at, NOW()),
    cancellation_reason = COALESCE(
        m.cancellation_reason,
        'Reconciliacion automatica: membresia activa duplicada previa a V54'
    ),
    updated_at = NOW(),
    version = version + 1
FROM ranked_active ranked
WHERE m.id = ranked.id
  AND ranked.active_rank > 1;

CREATE UNIQUE INDEX ux_membership_single_active_client
    ON membership (organization_id, client_id)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;
