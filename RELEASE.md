# Proceso de release

GymFort usa versionado semántico. El nombre técnico `GymOS` puede permanecer en
artefactos internos hasta completar una migración compatible.

- `MAJOR`: cambio incompatible de producto, datos o contrato.
- `MINOR`: funcionalidad nueva compatible.
- `PATCH`: corrección o mejora operacional segura.

## Checklist

1. Confirmar CI verde y `git diff --check`.
2. Ejecutar pruebas backend y migraciones PostgreSQL.
3. Ejecutar pruebas frontend, lint, typecheck, build y E2E críticos.
4. Validar y construir Docker Compose.
5. Si afecta pagos, verificar Stripe CLI fijado y recorrer sandbox con webhook.
6. Si afecta correo, verificar cola, reintento, remitente y entregabilidad del proveedor.
7. Revisar migración desde cero y upgrade de una base de referencia.
8. Actualizar `CHANGELOG.md`, estado, módulos, deuda y runbooks.
9. Ejecutar smoke test, registrar evidencia y etiquetar el commit.

Las versiones piloto usan `v0.x.y-pilot.n`.
