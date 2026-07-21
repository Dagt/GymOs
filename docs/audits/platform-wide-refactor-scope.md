# Alcance transversal de refactorización de GymOs

## Objetivo

La refactorización se evaluará sobre la plataforma completa y no sólo sobre los cambios recientes. Cada bloque deberá considerar el impacto en arquitectura, seguridad, multi-tenancy, contratos, operaciones, experiencia de usuario y mantenibilidad.

## Frentes permanentes de revisión

### 1. Arquitectura modular backend

- Dependencias entre módulos y uso exclusivo de APIs públicas.
- Ausencia de tipos de `infrastructure` cruzando límites modulares.
- Servicios de aplicación sin SQL ni dependencias directas a `JdbcClient`.
- Reglas de negocio fuera de controladores.
- Eventos, idempotencia y transacciones consistentes.
- Pruebas de arquitectura ejecutables en CI.

### 2. Seguridad y multi-tenancy

- Toda consulta y mutación debe estar acotada por `organization_id` y, cuando corresponda, `branch_id`.
- Permisos verificados en backend; el frontend sólo adapta la experiencia.
- Sesiones, kiosco, rate limiting, secretos, CORS y manejo de tokens.
- Auditoría de cambios críticos y trazabilidad por correlation ID.
- Pruebas negativas de aislamiento entre organizaciones y sucursales.

### 3. Dominios funcionales

La revisión abarca, como mínimo:

- identity y security;
- organizations y configuration;
- clients y onboarding;
- catalog, sales y payments;
- memberships y benefits;
- inventory;
- access y visits;
- bookings;
- coaching y wellness;
- notifications y automations;
- reporting y audit;
- self-service y kiosco.

Para cada dominio se registrarán: contratos, reglas, dependencias, deuda, cobertura, riesgos y estado de documentación.

### 4. Frontend completo

- División por dominio y componentes con responsabilidad acotada.
- Transporte HTTP común y clientes por dominio.
- Fachada legacy de `frontend/src/lib/api` eliminada; revisar clientes especiales fuera del paquete API antes de nuevas unificaciones.
- Estado, errores y permisos consistentes.
- Sin manipulación directa del DOM para implementar lógica de producto.
- Accesibilidad, navegación por teclado, i18n y formato regional.
- Pruebas de utilidades, componentes críticos y flujos operativos.

### 5. Datos y migraciones

- Flyway como única fuente de evolución de esquema.
- Migraciones reproducibles desde cero y sobre una base existente.
- Restricciones, índices, integridad referencial y soft delete.
- Estrategia para datos de horarios, configuración y branding actualmente almacenados en campos genéricos.
- Pruebas de migración y compatibilidad hacia atrás cuando aplique.

### 6. Integraciones y hardware

- Adaptadores desacoplados para pagos, correo, mensajería, almacenamiento y control de acceso.
- Contratos claros, timeouts, reintentos e idempotencia.
- Ninguna regla del dominio dependiente directamente de un proveedor o dispositivo.

### 7. Operación e infraestructura

- Docker Compose reproducible.
- Configuración y secretos por ambiente.
- HTTPS, proxy, health checks, logs y métricas.
- Backup y restore probados.
- Staging formal antes del piloto.
- Runbooks de instalación, actualización, recuperación y soporte.

### 8. Calidad y entrega

- Backend tests, frontend tests, lint, typecheck, build y Docker en CI.
- Protección de `main` y revisión obligatoria.
- PRs pequeños por concepto cuando sea posible.
- Registro explícito de fallos preexistentes y riesgos residuales.
- No declarar un bloque terminado si no fue validado.

## Matriz que se mantendrá durante el trabajo

Cada módulo será clasificado con los siguientes campos:

| Campo | Valores esperados |
| --- | --- |
| Funcionalidad | `PLANNED`, `PARTIAL`, `FUNCTIONAL`, `STABLE` |
| Arquitectura | `NON_COMPLIANT`, `PARTIAL`, `COMPLIANT` |
| Seguridad | `UNKNOWN`, `PARTIAL`, `VERIFIED` |
| Multi-tenant | `UNKNOWN`, `PARTIAL`, `VERIFIED` |
| Pruebas | `NONE`, `PARTIAL`, `CRITICAL_FLOWS`, `COMPREHENSIVE` |
| Documentación | `NONE`, `PARTIAL`, `CURRENT` |
| Producción | `NOT_READY`, `PILOT_READY`, `PRODUCTION_READY` |

## Regla de priorización

El trabajo se ordenará por riesgo sistémico:

1. CI y capacidad de detectar regresiones.
2. Seguridad, aislamiento multi-tenant e integridad de pagos/membresías.
3. Límites modulares y contratos públicos.
4. Flujos críticos de operación.
5. Simplificación frontend y experiencia de usuario.
6. Operación, staging, backup y piloto.
7. Limpieza cosmética o de bajo riesgo.

## Entregables

- Inventario transversal actualizado.
- Pruebas de caracterización y arquitectura.
- Refactors por bloques verificables.
- Matriz de módulos y riesgos.
- Informe final antes/después con recomendación de merge y de piloto.
