# Base común, datos y operación

Estado: `FUNCIONAL` para desarrollo / `PLANIFICADO` para producción

## Implementado

- Spring Boot, PostgreSQL, Flyway y frontend Next.js;
- manejo común de errores, correlación, tiempo y seguridad;
- Docker Compose y health checks;
- runbooks de despliegue, rollback, backup, restore e incidentes;
- pruebas backend y validaciones frontend reproducibles.

## Pendiente

- suite E2E transversal (`GEN-004`);
- observabilidad, alertas y protección de PII;
- simulacro de backup/restore y runbooks verificados (`OPS-001`);
- CI/CD y configuración de producción endurecida;
- revisar todas las migraciones desde cero y upgrade real.

Runbooks vigentes: `../../docs/operations/`.
