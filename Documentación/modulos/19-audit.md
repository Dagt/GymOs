# Auditoría

Estado: `FUNCIONAL`
Última revisión: 21 de agosto de 2026

## Implementado

- `audit_log` con actor, acción, recurso, canal y correlation ID;
- registro en operaciones críticas de múltiples módulos;
- servicio transversal reutilizable.

## Pendiente

- matriz que demuestre cobertura de cada mutación sensible;
- controles de inmutabilidad/retención;
- búsqueda y exportación administrativa;
- evitar datos personales o secretos en detalles.
