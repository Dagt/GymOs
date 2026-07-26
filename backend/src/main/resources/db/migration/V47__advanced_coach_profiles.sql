INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES (
    gen_random_uuid(),
    'coach.profile.manage',
    'Administrar perfil, estado y sucursales de coaches',
    'coaching',
    now(),
    now(),
    0
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code = 'coach.profile.manage'
WHERE r.name IN (
    'Administrador',
    'Administrador GymOS',
    'Dueno del gimnasio',
    'Gerente'
)
ON CONFLICT DO NOTHING;
