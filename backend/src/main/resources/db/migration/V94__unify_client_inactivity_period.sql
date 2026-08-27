ALTER TABLE client_lifecycle_configuration
    DROP CONSTRAINT IF EXISTS ck_client_lifecycle_registration_months;

ALTER TABLE client_lifecycle_configuration
    DROP COLUMN IF EXISTS registration_recharge_months;

COMMENT ON COLUMN client_lifecycle_configuration.inactivity_months IS
    'Canonical number of months without a current membership for automatic inactivation and registration fee recharge.';
