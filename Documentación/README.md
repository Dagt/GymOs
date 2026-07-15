# GymOS — Documentación Maestra

Versión: 0.1  
Estado: Borrador consolidado  
Fecha base: 12 de julio de 2026

GymOS es una plataforma web modular para la administración integral de gimnasios y centros de bienestar. Esta carpeta reúne la especificación funcional y técnica definida durante el levantamiento inicial del producto.

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
13. `13-estado-desarrollo.md` — Estado de implementacion por hito, verificaciones y bloqueos locales.

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

## Decisiones fundacionales

- GymOS debe ser un producto vendible desde la primera versión operativa.
- Debe soportar gimnasios independientes y cadenas con varias sucursales desde el modelo base.
- El primer hito debe incluir frontend web y autenticación real.
- Los pagos de V1 se procesan de forma interna y manual.
- Debe poder implementarse un kiosco interno de autoservicio para clientes.
- La estructura recomendada es monorepo con backend, frontend, infraestructura y documentación.
