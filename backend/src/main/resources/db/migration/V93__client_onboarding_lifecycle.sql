ALTER TABLE client
    ADD COLUMN inactivated_at TIMESTAMPTZ,
    ADD COLUMN inactivation_reason VARCHAR(40);

CREATE TABLE client_lifecycle_configuration (
    organization_id UUID PRIMARY KEY REFERENCES organization(id),
    registration_fee_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    registration_fee_cents BIGINT NOT NULL DEFAULT 0,
    registration_fee_label VARCHAR(120) NOT NULL DEFAULT 'Inscripción',
    registration_recharge_months INTEGER NOT NULL DEFAULT 6,
    auto_inactivation_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    inactivity_months INTEGER NOT NULL DEFAULT 6,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID REFERENCES user_account(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_client_lifecycle_registration_fee CHECK (registration_fee_cents >= 0),
    CONSTRAINT ck_client_lifecycle_registration_months CHECK (registration_recharge_months BETWEEN 1 AND 120),
    CONSTRAINT ck_client_lifecycle_inactivity_months CHECK (inactivity_months BETWEEN 1 AND 120)
);

CREATE INDEX ix_client_automatic_inactivation
    ON client (organization_id, status, joined_at)
    WHERE deleted_at IS NULL AND status = 'ACTIVE';

UPDATE self_service_terminal
SET allowed_actions_json = jsonb_set(
        COALESCE(allowed_actions_json, '{}'::jsonb),
        '{clientRegistration}',
        'false'::jsonb,
        true
    ),
    updated_at = now(),
    version = version + 1
WHERE deleted_at IS NULL
  AND NOT COALESCE(allowed_actions_json, '{}'::jsonb) ? 'clientRegistration';
