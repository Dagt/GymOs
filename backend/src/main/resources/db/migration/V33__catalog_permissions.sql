INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES
    ('88888888-8888-8888-8888-888888888831', 'catalog.create', 'Crear productos y planes', 'catalog', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888832', 'catalog.update', 'Editar productos y planes', 'catalog', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888833', 'catalog.status.manage', 'Activar y desactivar productos', 'catalog', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888834', 'catalog.archive', 'Archivar productos', 'catalog', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888835', 'catalog.category.manage', 'Administrar categorias', 'catalog', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888836', 'catalog.image.manage', 'Administrar imagenes de productos', 'catalog', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888837', 'catalog.cost.read', 'Consultar costos comerciales', 'catalog', now(), now(), 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT legacy.role_id, expanded.id
FROM role_permission legacy
JOIN permission legacy_permission
    ON legacy_permission.id = legacy.permission_id
   AND legacy_permission.code = 'catalog.manage'
CROSS JOIN permission expanded
WHERE expanded.code IN (
    'catalog.create',
    'catalog.update',
    'catalog.status.manage',
    'catalog.archive',
    'catalog.category.manage',
    'catalog.image.manage',
    'catalog.cost.read'
)
ON CONFLICT DO NOTHING;
