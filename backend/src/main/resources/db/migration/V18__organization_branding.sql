ALTER TABLE organization
    ADD COLUMN brand_name VARCHAR(120),
    ADD COLUMN logo_url VARCHAR(500),
    ADD COLUMN primary_color VARCHAR(16),
    ADD COLUMN accent_color VARCHAR(16),
    ADD COLUMN surface_color VARCHAR(16);

UPDATE organization
SET brand_name = trade_name,
    primary_color = COALESCE(primary_color, '#164E63'),
    accent_color = COALESCE(accent_color, '#F59E0B'),
    surface_color = COALESCE(surface_color, '#F4F7FB')
WHERE brand_name IS NULL;

INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES ('8a000000-0000-0000-0000-000000000104', 'platform.admin', 'Permiso platform.admin', 'platform', now(), now(), 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code = 'platform.admin'
WHERE r.name = 'Administrador GymOS'
ON CONFLICT DO NOTHING;
