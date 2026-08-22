DELETE FROM role_permission rp
USING role r, permission p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND p.code = 'selfservice.session.launch'
  AND r.name NOT IN ('Recepcion', 'Recepcion / Caja');
