# Matriz de revisión transversal por módulo

## Objetivo

Esta matriz evita que la refactorización se concentre únicamente en reservas, kiosco o cambios recientes. Todos los módulos de GymOs deben evaluarse con los mismos criterios antes de declarar terminada la simplificación de la plataforma.

## Escala

- Funcionalidad: `PLANNED`, `PARTIAL`, `FUNCTIONAL`, `STABLE`
- Arquitectura: `NON_COMPLIANT`, `PARTIAL`, `COMPLIANT`
- Seguridad: `UNKNOWN`, `PARTIAL`, `VERIFIED`
- Multi-tenant: `UNKNOWN`, `PARTIAL`, `VERIFIED`
- Pruebas: `NONE`, `PARTIAL`, `CRITICAL_FLOWS`, `COMPREHENSIVE`
- Documentación: `NONE`, `PARTIAL`, `CURRENT`
- Producción: `NOT_READY`, `PILOT_READY`, `PRODUCTION_READY`

Los estados iniciales son conservadores: `UNKNOWN` significa que todavía no existe evidencia suficiente, no necesariamente que el módulo sea inseguro.

## Matriz inicial

| Módulo / frente | Funcionalidad | Arquitectura | Seguridad | Multi-tenant | Pruebas | Documentación | Producción | Prioridad de auditoría | Observaciones iniciales |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Identity / authentication | FUNCTIONAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | NOT_READY | P0 | Revisar emisión/renovación de tokens, sesiones, roles, permisos, revocación y exposición de datos. |
| Security transversal | FUNCTIONAL | PARTIAL | PARTIAL | UNKNOWN | PARTIAL | PARTIAL | NOT_READY | P0 | Revisar filtros, CORS, rate limiting, secretos, headers, errores y autorización exclusivamente backend. |
| Organizations / branches | FUNCTIONAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | NOT_READY | P0 | `OrganizationService` mantiene deuda de SQL en application. Validar aislamiento organización/sucursal. |
| Configuration / branding | FUNCTIONAL | PARTIAL | UNKNOWN | PARTIAL | PARTIAL | PARTIAL | NOT_READY | P2 | Revisar campos genéricos, defaults, permisos y consistencia entre consola y kiosco. |
| Clients | FUNCTIONAL | NON_COMPLIANT | PARTIAL | PARTIAL | PARTIAL | PARTIAL | NOT_READY | P0 | `ClientService` usa `JdbcClient` en application. Revisar PII, búsquedas y soft delete. |
| Client import | FUNCTIONAL | NON_COMPLIANT | PARTIAL | PARTIAL | PARTIAL | PARTIAL | NOT_READY | P1 | `ClientImportService` usa SQL directo. Revisar validación, duplicados, archivos y transacciones. |
| Onboarding | FUNCTIONAL | NON_COMPLIANT | PARTIAL | PARTIAL | PARTIAL | PARTIAL | NOT_READY | P1 | `OnboardingService` usa SQL directo. Revisar idempotencia y consistencia de altas. |
| Catalog / plans / products | FUNCTIONAL | NON_COMPLIANT | PARTIAL | PARTIAL | PARTIAL | PARTIAL | NOT_READY | P1 | `CatalogService` usa SQL directo. Revisar precios, vigencia, moneda y referencias cruzadas. |
| Sales | FUNCTIONAL | NON_COMPLIANT | PARTIAL | PARTIAL | PARTIAL | PARTIAL | NOT_READY | P0 | `SalesService` es de alta superficie y usa SQL directo. Requiere pruebas antes de separar puertos. |
| Payments | FUNCTIONAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | NOT_READY | P0 | Revisar idempotencia, conciliación, estados, redondeos, referencias y adaptadores externos. |
| Memberships | FUNCTIONAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | NOT_READY | P0 | Revisar activación, renovación, cambio de plan, gracia, ventas asociadas y transacciones. |
| Benefits / entitlements | PARTIAL | PARTIAL | UNKNOWN | UNKNOWN | PARTIAL | PARTIAL | NOT_READY | P1 | Confirmar una única fuente de verdad para elegibilidad en acceso y reservas. |
| Inventory | FUNCTIONAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | NOT_READY | P1 | Revisar movimientos, stock negativo, concurrencia, ventas y auditoría. |
| Access control / visits | FUNCTIONAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | NOT_READY | P0 | Revisar decisiones de acceso, membresía, dispositivos, reintentos y registro de visitas. |
| Bookings | FUNCTIONAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | NOT_READY | P1 | Deuda reciente: página monolítica, enhancers DOM, CSS frágil y API modular interna expuesta. |
| Self-service / kiosk | FUNCTIONAL | NON_COMPLIANT | PARTIAL | PARTIAL | PARTIAL | PARTIAL | NOT_READY | P0 | Importa application/infrastructure de bookings. Revisar privacidad, token de sesión y rate limiting. |
| Coaching | PARTIAL | PARTIAL | UNKNOWN | UNKNOWN | PARTIAL | PARTIAL | NOT_READY | P2 | Inventariar contratos, permisos y relación con clientes/membresías. |
| Wellness | PARTIAL | PARTIAL | UNKNOWN | UNKNOWN | PARTIAL | PARTIAL | NOT_READY | P2 | Revisar datos sensibles, acceso por rol y límites con coaching/clientes. |
| Notifications | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | NOT_READY | P1 | Revisar plantillas, datos personales, reintentos, proveedores e idempotencia. |
| Automations / scheduled work | PARTIAL | PARTIAL | UNKNOWN | UNKNOWN | PARTIAL | PARTIAL | NOT_READY | P1 | Revisar ejecución duplicada, locks, reintentos y aislamiento por tenant. |
| Reporting | FUNCTIONAL | PARTIAL | PARTIAL | UNKNOWN | PARTIAL | PARTIAL | NOT_READY | P1 | Alto riesgo de consultas sin filtros tenant. Revisar exportaciones y datos agregados. |
| Audit | FUNCTIONAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | NOT_READY | P0 | Validar cobertura de eventos críticos, actor, correlation ID e inmutabilidad. |
| Frontend shell / navigation | FUNCTIONAL | PARTIAL | PARTIAL | N/A | PARTIAL | PARTIAL | NOT_READY | P1 | Revisar permisos visuales, accesibilidad, rutas, sesión y duplicación de layouts. |
| Frontend API layer | FUNCTIONAL | PARTIAL | PARTIAL | N/A | PARTIAL | PARTIAL | PARTIAL | P1 | Fachada legacy eliminada; quedan clientes especiales y cobertura frontend por revisar. |
| Frontend forms / validation | FUNCTIONAL | PARTIAL | PARTIAL | N/A | PARTIAL | PARTIAL | NOT_READY | P1 | Revisar validación consistente, formatos regionales y mensajes de error. |
| Database / Flyway | FUNCTIONAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | NOT_READY | P0 | Verificar migración desde cero, upgrades, índices, constraints y compatibilidad. |
| Docker / local environment | FUNCTIONAL | PARTIAL | PARTIAL | N/A | PARTIAL | PARTIAL | NOT_READY | P0 | Debe ser reproducible para validación local antes de cualquier PR. |
| CI/CD | PARTIAL | PARTIAL | PARTIAL | N/A | PARTIAL | PARTIAL | NOT_READY | P0 | Línea base actual roja: backend test y frontend lint. Typecheck/build/docker bloqueados. |
| Backup / restore | PLANNED | PARTIAL | UNKNOWN | N/A | NONE | PARTIAL | NOT_READY | P0 | No considerar piloto sin restauración probada y runbook. |
| Observability | PARTIAL | PARTIAL | PARTIAL | N/A | PARTIAL | PARTIAL | NOT_READY | P1 | Revisar logs estructurados, métricas, health checks y ausencia de secretos/PII. |
| Documentation / runbooks | PARTIAL | PARTIAL | UNKNOWN | N/A | N/A | PARTIAL | NOT_READY | P1 | Consolidar instalación, operación, recuperación, soporte y arquitectura real. |

## Orden de revisión

### P0 — Riesgo sistémico

1. CI y línea base reproducible.
2. Seguridad transversal e identidad.
3. Aislamiento multi-tenant.
4. Ventas, pagos y membresías.
5. Acceso, self-service y privacidad.
6. Base de datos, migraciones, backup y restore.

### P1 — Riesgo funcional/arquitectónico alto

1. Clientes, onboarding e importación.
2. Catálogo e inventario.
3. Reservas y cliente API frontend.
4. Reporting, auditoría, notificaciones y automatizaciones.
5. Observabilidad y documentación operativa.

### P2 — Simplificación y módulos de menor criticidad inmediata

1. Coaching y wellness.
2. Configuración y branding.
3. Limpieza visual y código muerto.

## Regla de actualización

Cada cambio debe actualizar esta matriz únicamente cuando exista evidencia verificable: prueba añadida, revisión de código completa, ejecución local, CI verde o validación manual documentada. No se mejorará un estado por inferencia.
