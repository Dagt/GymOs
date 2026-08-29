ALTER TABLE branch
    ADD COLUMN registration_fee_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN registration_fee_cents BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN registration_fee_label VARCHAR(120) NOT NULL DEFAULT 'Inscripción',
    ADD COLUMN auto_inactivation_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN inactivity_months INTEGER NOT NULL DEFAULT 6,
    ADD COLUMN reception_manual_terminal_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN reception_mercado_pago_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN mercado_pago_terminal_id VARCHAR(160),
    ADD COLUMN stripe_self_checkout_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN stripe_recurring_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_branch_registration_fee CHECK (registration_fee_cents >= 0),
    ADD CONSTRAINT ck_branch_inactivity_months CHECK (inactivity_months BETWEEN 1 AND 120),
    ADD CONSTRAINT ck_branch_reception_card_method CHECK (
        NOT (reception_manual_terminal_enabled AND reception_mercado_pago_enabled)
    ),
    ADD CONSTRAINT ck_branch_stripe_recurring_requires_checkout CHECK (
        NOT stripe_recurring_enabled OR stripe_self_checkout_enabled
    );

UPDATE branch target
SET registration_fee_enabled = lifecycle.registration_fee_enabled,
    registration_fee_cents = lifecycle.registration_fee_cents,
    registration_fee_label = lifecycle.registration_fee_label,
    auto_inactivation_enabled = lifecycle.auto_inactivation_enabled,
    inactivity_months = lifecycle.inactivity_months
FROM client_lifecycle_configuration lifecycle
WHERE lifecycle.organization_id = target.organization_id;

UPDATE branch target
SET reception_manual_terminal_enabled = payments.reception_manual_terminal_enabled,
    reception_mercado_pago_enabled = payments.reception_mercado_pago_enabled,
    mercado_pago_terminal_id = payments.mercado_pago_terminal_id,
    stripe_self_checkout_enabled = payments.stripe_self_checkout_enabled,
    stripe_recurring_enabled = payments.stripe_recurring_enabled
FROM organization_payment_configuration payments
WHERE payments.organization_id = target.organization_id;

COMMENT ON COLUMN branch.registration_fee_enabled IS 'Configuración operativa independiente de inscripción de esta sucursal.';
COMMENT ON COLUMN branch.auto_inactivation_enabled IS 'Aplica a socios cuya sucursal base es esta sucursal.';
COMMENT ON COLUMN branch.stripe_self_checkout_enabled IS 'Habilitación operativa por sucursal; la cuenta Stripe conectada pertenece a la organización.';
COMMENT ON COLUMN branch.stripe_recurring_enabled IS 'Habilitación de domiciliación por sucursal; la cuenta Stripe conectada pertenece a la organización.';
