-- Reconcile historical identity/coaching drift without overriding an intentionally
-- inactive coach profile that is already linked to a user.
--
-- Some databases executed an older V30 before the coach-user backfill was added.
-- In that situation user_role + user_branch_access can be correct while the
-- operational coach row is missing. This migration safely replays the linkage.

WITH coach_users AS (
    SELECT DISTINCT
        u.id AS user_id,
        u.organization_id,
        uba.branch_id,
        TRIM(CONCAT(p.first_name, ' ', p.last_name, ' ', COALESCE(p.maternal_last_name, ''))) AS display_name,
        p.email,
        p.phone,
        u.status AS user_status,
        COALESCE(u.updated_by, u.created_by, u.id) AS actor_user_id
    FROM user_account u
    JOIN person p ON p.id = u.person_id
    JOIN user_role ur ON ur.user_id = u.id
    JOIN role r ON r.id = ur.role_id
    JOIN user_branch_access uba ON uba.user_id = u.id
    JOIN branch b ON b.id = uba.branch_id
        AND b.organization_id = u.organization_id
        AND b.status = 'ACTIVE'
        AND b.deleted_at IS NULL
    WHERE u.deleted_at IS NULL
      AND r.deleted_at IS NULL
      AND lower(r.name) = 'coach'
)
UPDATE coach c
SET user_account_id = cu.user_id,
    display_name = cu.display_name,
    phone = cu.phone,
    status = CASE WHEN cu.user_status = 'ACTIVE' THEN 'ACTIVE' ELSE 'INACTIVE' END,
    updated_at = NOW(),
    updated_by = cu.actor_user_id,
    version = c.version + 1
FROM coach_users cu
WHERE c.organization_id = cu.organization_id
  AND c.branch_id = cu.branch_id
  AND c.user_account_id IS NULL
  AND cu.email IS NOT NULL
  AND c.email IS NOT NULL
  AND lower(c.email) = lower(cu.email)
  AND c.deleted_at IS NULL;

WITH coach_users AS (
    SELECT DISTINCT
        u.id AS user_id,
        u.organization_id,
        uba.branch_id,
        TRIM(CONCAT(p.first_name, ' ', p.last_name, ' ', COALESCE(p.maternal_last_name, ''))) AS display_name,
        p.email,
        p.phone,
        u.status AS user_status,
        COALESCE(u.updated_by, u.created_by, u.id) AS actor_user_id
    FROM user_account u
    JOIN person p ON p.id = u.person_id
    JOIN user_role ur ON ur.user_id = u.id
    JOIN role r ON r.id = ur.role_id
    JOIN user_branch_access uba ON uba.user_id = u.id
    JOIN branch b ON b.id = uba.branch_id
        AND b.organization_id = u.organization_id
        AND b.status = 'ACTIVE'
        AND b.deleted_at IS NULL
    WHERE u.deleted_at IS NULL
      AND r.deleted_at IS NULL
      AND lower(r.name) = 'coach'
)
INSERT INTO coach (
    id, organization_id, branch_id, user_account_id, display_name, email, phone,
    specialties_json, bio, status, created_at, updated_at, created_by, updated_by, version
)
SELECT
    gen_random_uuid(),
    cu.organization_id,
    cu.branch_id,
    cu.user_id,
    cu.display_name,
    cu.email,
    cu.phone,
    '[]'::jsonb,
    NULL,
    CASE WHEN cu.user_status = 'ACTIVE' THEN 'ACTIVE' ELSE 'INACTIVE' END,
    NOW(),
    NOW(),
    cu.actor_user_id,
    cu.actor_user_id,
    0
FROM coach_users cu
WHERE NOT EXISTS (
    SELECT 1
    FROM coach c
    WHERE c.organization_id = cu.organization_id
      AND c.branch_id = cu.branch_id
      AND c.user_account_id = cu.user_id
      AND c.deleted_at IS NULL
);
