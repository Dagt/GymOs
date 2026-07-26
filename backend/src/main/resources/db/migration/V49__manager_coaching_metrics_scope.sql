-- Tenant owners and managers supervise coaching through metrics. Routine
-- authoring, exercise catalog maintenance and assignment operations belong to
-- users with the Coach role.
DELETE FROM role_permission rp
USING role r, permission p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.name IN ('Dueno del gimnasio', 'Gerente')
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
