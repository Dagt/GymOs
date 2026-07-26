# GymOS — Documentación Maestra

Versión: 0.2  
Estado: ESTRUCTURA CANÓNICA
Fecha base: 26 de julio de 2026

GymOS es una plataforma web modular para la administración integral de gimnasios
y centros de bienestar. Esta carpeta separa especificación, estado, deuda y
documentación por módulo.

## Documentos

1. `01-constitution.md` — Constitución del proyecto y principios no negociables.
2. `02-prd.md` — Product Requirements Document.
3. `03-sds.md` — Software Design Specification.
4. `04-domain-model.md` — Modelo de dominio, agregados, estados, invariantes y eventos.
5. `05-data-model.md` — Modelo físico de datos, tablas, relaciones e índices.
6. `06-api-contracts.md` — Contratos API propuestos.
7. `07-use-cases.md` — Casos de uso, flujos, errores y criterios de aceptación.
8. `08-frontend-spec.md` — Especificación funcional de frontend web.
9. `09-integrations-infrastructure.md` — Integraciones, despliegue, observabilidad y seguridad operacional.
10. `10-ai-development.md` — Estrategia de desarrollo asistido por IA y prompts maestros.
11. `11-roadmap.md` — Orden de implementación, épicas y entregables.
12. `12-decisiones-fundacionales.md` — Decisiones confirmadas sobre producto vendible, multi-organización, sucursales, primer hito, seguridad, pagos e infraestructura local.
13. `13-estado-desarrollo.md` — resumen canónico vigente de la plataforma.
14. `modulos/` — un documento de estado por cada módulo backend.
15. `28-registro-vivo-deuda-tecnica.md` — lista única de hallazgos y deuda.
16. `29-cronologia-ramas.md` — cronología Git y ramas activas/divergentes.

Documentación operativa y de seguridad vigente:

- `../docs/operations/` — despliegue, rollback, backup, restore, incidentes y soporte.
- `../docs/security/` — permisos y verificación multi-tenant.

### Regla de mantenimiento documental

Todo cambio material debe actualizar en el mismo commit la ficha correspondiente
en `modulos/`, el resumen 13 y la deuda 28. Si cambia la integración de ramas,
también debe actualizar el documento 29. No deben crearse prompts, planes de
acción, cierres ni bitácoras nuevas como fuentes paralelas: Git conserva el
historial.

## Alcance de la versión 1

La versión 1 incluye toda la operación interna del gimnasio:

- Organizaciones y sucursales.
- Personas y clientes.
- Usuarios, roles y permisos.
- Planes, membresías, renovaciones y visitas.
- Pagos en efectivo, transferencia y tarjeta.
- Kiosco de autoservicio en gimnasio para consultas, pagos y membresías.
- Punto de venta.
- Inventario.
- Reservas de recursos y servicios.
- Control de acceso e integración con torniquetes.
- Entrenadores y rutinas.
- Profesionales de bienestar.
- Notificaciones y automatizaciones.
- Reportes y dashboards.
- Auditoría y configuración.

La aplicación móvil o portal remoto de clientes queda fuera de la versión 1, pero la arquitectura debe dejarla preparada. El kiosco de autoservicio dentro del gimnasio sí queda contemplado para V1.

### Precisión de alcance para Coaches y rutinas

En V1, GymOS permite que el coach consulte y amplíe un catálogo de ejercicios, cree y versione rutinas estructuradas, asigne una versión específica a un socio, conserve el historial, genere un PDF imprimible y permita que dueño/gerencia consulten métricas semanales de asignaciones por coach y socio.

Las rutinas **no se cobran por medio de GymOS**. Los cobros en efectivo acordados entre coach y socio son externos a la plataforma y no generan venta, pago, comisión, ingreso ni movimiento de caja. La consulta remota de la rutina por el socio se reserva para una aplicación futura.

## Decisiones fundacionales

- GymOS debe ser un producto vendible desde la primera versión operativa.
- Debe soportar gimnasios independientes y cadenas con varias sucursales desde el modelo base.
- El primer hito debe incluir frontend web y autenticación real.
- Los pagos de V1 se procesan de forma interna y manual, excepto los cobros externos entre coaches y socios, que no forman parte del sistema.
- Debe poder implementarse un kiosco interno de autoservicio para clientes.
- La estructura recomendada es monorepo con backend, frontend, infraestructura y documentación.
