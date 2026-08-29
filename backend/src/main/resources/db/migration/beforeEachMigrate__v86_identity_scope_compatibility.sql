-- Pilot databases created before V86 kept tenant identity columns NOT NULL.
-- V86 intentionally detaches platform identities, but its historical SQL drops
-- those constraints after updating the rows. Prepare only the V85 -> V86 step
-- without changing the checksum of an already published versioned migration.
DO $$
BEGIN
    IF to_regclass('person') IS NOT NULL
       AND to_regclass('user_account') IS NOT NULL
       AND EXISTS (
           SELECT 1
           FROM flyway_schema_history
           WHERE version = '85' AND success = TRUE
       )
       AND NOT EXISTS (
           SELECT 1
           FROM flyway_schema_history
           WHERE version = '86' AND success = TRUE
       ) THEN
        ALTER TABLE person ALTER COLUMN organization_id DROP NOT NULL;
        ALTER TABLE user_account ALTER COLUMN organization_id DROP NOT NULL;
    END IF;
END $$;
