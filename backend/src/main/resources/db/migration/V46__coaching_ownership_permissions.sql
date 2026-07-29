INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES
    (gen_random_uuid(), 'workout.read.own', 'Consultar rutinas propias', 'coaching', now(), now(), 0),
    (gen_random_uuid(), 'workout.read.all', 'Consultar todas las rutinas de la sucursal', 'coaching', now(), now(), 0),
    (gen_random_uuid(), 'workout.manage.own', 'Administrar rutinas propias', 'coaching', now(), now(), 0),
    (gen_random_uuid(), 'workout.manage.all', 'Administrar todas las rutinas de la sucursal', 'coaching', now(), now(), 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'workout.read.own', 'workout.read.all', 'workout.manage.own', 'workout.manage.all'
)
WHERE r.name IN ('Administrador', 'Administrador GymOS', 'Dueno del gimnasio', 'Gerente')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('workout.read.own', 'workout.manage.own', 'coach.metrics.read')
WHERE r.name = 'Coach'
ON CONFLICT DO NOTHING;

DELETE FROM role_permission rp
USING role r, permission p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.name = 'Coach'
  AND p.code IN ('workout.read', 'workout.manage');

ALTER TABLE workout_assignment
    ADD COLUMN IF NOT EXISTS status_reason TEXT;
