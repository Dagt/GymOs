DELETE FROM role_permission rp
USING role r, permission p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.name = 'Recepcion'
  AND p.code = 'resource.manage';

ALTER TABLE branch
    DROP COLUMN IF EXISTS brand_name,
    DROP COLUMN IF EXISTS logo_url,
    DROP COLUMN IF EXISTS primary_color,
    DROP COLUMN IF EXISTS accent_color,
    DROP COLUMN IF EXISTS surface_color;
