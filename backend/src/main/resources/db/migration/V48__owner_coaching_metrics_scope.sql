-- The tenant owner supervises coaching through metrics. Routine authoring,
-- exercise catalog maintenance and assignment operations belong to coaches.
DELETE FROM role_permission rp
USING role r, permission p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.name = 'Dueno del gimnasio'
  AND p.code IN (
      'coach.manage',
      'exercise.read',
      'exercise.create',
      'exercise.manage',
      'workout.read',
      'workout.manage',
      'workout.read.own',
      'workout.read.all',
      'workout.manage.own',
      'workout.manage.all',
      'workout.assign',
      'workout.pdf.download'
  );

-- Repair operational profiles for Coach users created before profile
-- provisioning was introduced. Existing profile details and status are kept.
WITH coach_users AS (
    SELECT DISTINCT
        u.id AS user_id,
        u.organization_id,
        uba.branch_id,
        trim(concat(p.first_name, ' ', p.last_name, ' ', coalesce(p.maternal_last_name, ''))) AS display_name,
        p.email,
        p.phone,
        u.status,
        coalesce(u.created_by, u.id) AS actor_user_id
    FROM user_account u
    JOIN person p ON p.id = u.person_id
    JOIN user_role ur ON ur.user_id = u.id
    JOIN role r ON r.id = ur.role_id
    JOIN user_branch_access uba ON uba.user_id = u.id
    JOIN branch b
      ON b.id = uba.branch_id
     AND b.organization_id = u.organization_id
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
    coach_users.organization_id,
    coach_users.branch_id,
    coach_users.user_id,
    coach_users.display_name,
    coach_users.email,
    coach_users.phone,
    '[]'::jsonb,
    NULL,
    CASE WHEN coach_users.status = 'ACTIVE' THEN 'ACTIVE' ELSE 'INACTIVE' END,
    now(),
    now(),
    coach_users.actor_user_id,
    coach_users.actor_user_id,
    0
FROM coach_users
ON CONFLICT (organization_id, branch_id, user_account_id)
    WHERE user_account_id IS NOT NULL
DO NOTHING;
