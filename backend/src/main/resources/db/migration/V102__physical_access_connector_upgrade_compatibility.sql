-- V84 was applied by pilot installations before the claim-token hardening and
-- pending-state conversion were finalized. Keep V84 immutable at its deployed
-- checksum and deliver those compatible additions incrementally.

CREATE UNIQUE INDEX IF NOT EXISTS ux_access_connector_credential_tenant_identity
    ON access_connector_credential (id, organization_id, branch_id, connection_id);

ALTER TABLE access_provider_sync_job
    ADD COLUMN IF NOT EXISTS claim_token UUID;

-- Claims issued by the pre-token protocol cannot be acknowledged safely by the
-- hardened protocol. Requeue them during the planned restart so they can be
-- claimed again with a verifiable token and the same idempotency key.
UPDATE access_provider_sync_job
SET status = 'PENDING',
    claimed_at = NULL,
    lease_until = NULL,
    claimed_by_credential_id = NULL,
    available_at = NOW(),
    updated_at = NOW(),
    version = version + 1
WHERE status = 'CLAIMED'
  AND claim_token IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'access_provider_sync_job'::regclass
          AND conname = 'access_provider_sync_job_claimed_by_credential_id_fkey'
    ) THEN
        ALTER TABLE access_provider_sync_job
            DROP CONSTRAINT access_provider_sync_job_claimed_by_credential_id_fkey;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'access_provider_sync_job'::regclass
          AND conname = 'fk_access_provider_sync_job_claimed_credential'
    ) THEN
        ALTER TABLE access_provider_sync_job
            ADD CONSTRAINT fk_access_provider_sync_job_claimed_credential
            FOREIGN KEY (claimed_by_credential_id, organization_id, branch_id, connection_id)
            REFERENCES access_connector_credential(id, organization_id, branch_id, connection_id)
            ON DELETE SET NULL (claimed_by_credential_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'access_provider_sync_job'::regclass
          AND conname = 'ck_access_provider_sync_job_claim_token'
    ) THEN
        ALTER TABLE access_provider_sync_job
            ADD CONSTRAINT ck_access_provider_sync_job_claim_token
            CHECK ((status = 'CLAIMED') = (claim_token IS NOT NULL));
    END IF;
END $$;

-- Preserve pending V83 desired states: safe/linked states become durable jobs,
-- while states that cannot be delivered are made explicitly BLOCKED.
UPDATE access_provider_sync_state state
SET status = 'BLOCKED',
    blocked_reason_code = CASE
        WHEN NOT EXISTS (
            SELECT 1
            FROM access_provider_client_link link
            WHERE link.organization_id = state.organization_id
              AND link.branch_id = state.branch_id
              AND link.connection_id = state.connection_id
              AND link.client_id = state.client_id
        ) THEN 'EXTERNAL_PERSON_LINK_REQUIRED'
        ELSE 'CONNECTION_DISABLED'
    END,
    last_error = CASE
        WHEN NOT EXISTS (
            SELECT 1
            FROM access_provider_client_link link
            WHERE link.organization_id = state.organization_id
              AND link.branch_id = state.branch_id
              AND link.connection_id = state.connection_id
              AND link.client_id = state.client_id
        ) THEN 'Falta vincular al socio con la persona del proveedor fisico.'
        ELSE 'La conexion fisica esta desactivada.'
    END,
    source_event_key = COALESCE(source_event_key, 'migration-v102:block:' || state.id),
    updated_at = NOW(),
    version = state.version + 1
FROM access_provider_connection connection
WHERE connection.id = state.connection_id
  AND connection.organization_id = state.organization_id
  AND connection.branch_id = state.branch_id
  AND state.latest_job_id IS NULL
  AND state.status IN ('PENDING', 'FAILED')
  AND (
      connection.enabled = FALSE
      OR NOT EXISTS (
          SELECT 1
          FROM access_provider_client_link link
          WHERE link.organization_id = state.organization_id
            AND link.branch_id = state.branch_id
            AND link.connection_id = state.connection_id
            AND link.client_id = state.client_id
      )
  );

INSERT INTO access_provider_sync_job (
    id, organization_id, branch_id, connection_id, sync_state_id, client_id,
    desired_version, action, external_person_ref, desired_access_level_refs,
    idempotency_key, status, attempts, retryable, available_at, request_source,
    created_at, updated_at, version
)
SELECT gen_random_uuid(), state.organization_id, state.branch_id, state.connection_id,
       state.id, state.client_id, state.desired_version, state.action,
       link.external_person_ref, state.desired_access_level_refs,
       'physical-access:migration-v102:' || state.id || ':' || state.desired_version,
       'PENDING', 0, TRUE, NOW(), 'MIGRATION_V102', NOW(), NOW(), 0
FROM access_provider_sync_state state
JOIN access_provider_connection connection
  ON connection.id = state.connection_id
 AND connection.organization_id = state.organization_id
 AND connection.branch_id = state.branch_id
JOIN access_provider_client_link link
  ON link.connection_id = state.connection_id
 AND link.organization_id = state.organization_id
 AND link.branch_id = state.branch_id
 AND link.client_id = state.client_id
WHERE state.status IN ('PENDING', 'FAILED')
  AND state.latest_job_id IS NULL
  AND connection.enabled = TRUE
ON CONFLICT (connection_id, idempotency_key) DO NOTHING;

UPDATE access_provider_sync_state state
SET latest_job_id = job.id,
    status = 'PENDING',
    last_error = NULL,
    blocked_reason_code = NULL,
    source_event_key = COALESCE(state.source_event_key, 'migration-v102:queued:' || state.id),
    updated_at = NOW(),
    version = state.version + 1
FROM access_provider_sync_job job
WHERE job.sync_state_id = state.id
  AND job.request_source = 'MIGRATION_V102';
