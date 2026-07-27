# 28. Registro vivo de hallazgos y deuda técnica

Estado: VIGENTE / OBLIGATORIO
Corte: 26 de julio de 2026

## 1. Propósito y regla de actualización

Este es el registro único de deuda técnica y hallazgos pendientes de GymOS. Debe actualizarse cada vez que una revisión, desarrollo, prueba o incidente descubra un hallazgo nuevo.

En el mismo cambio que resuelva una deuda se debe cambiar su estado a `RESUELTA`, anotar fecha y commit, retirarla de la tabla activa y conservarla en el historial de cerradas. No deben mantenerse listas paralelas en documentos por módulo.

Campos obligatorios: identificador estable, prioridad, módulo, estado, hallazgo, impacto, evidencia y criterio de cierre. Estados: `ABIERTA`, `EN_CURSO`, `BLOQUEADA`, `RESUELTA`, `ACEPTADA`.

## 2. Deuda activa

| ID | Pri. | Módulo | Estado | Hallazgo e impacto | Criterio de cierre |
|---|---|---|---|---|---|
| GEN-001 | P0 | General/seguridad | ABIERTA | La autorización no aplica uniformemente propiedad, sucursal y organización. | Matriz automatizada positiva/negativa para módulos sensibles. |
| AUTH-001 | P0 | Identidad | ABIERTA | El refresh token requiere revisar almacenamiento y exposición en cliente. | Mecanismo seguro, rotación y pruebas de reutilización. |
| GEN-002 | P1 | General/arquitectura | ABIERTA | Algunos módulos carecen de puertos de aplicación consistentes. | Fronteras documentadas y verificadas por pruebas de arquitectura. |
| GEN-004 | P1 | General/calidad | ABIERTA | No existe suite E2E homogénea de recorridos críticos. | E2E en CI para autenticación, venta, membresía, reserva, inventario y rutina. |
| ORG-001 | P1 | Organizaciones | ABIERTA | Cobertura incompleta de UUID ajeno, sucursales y CRUD de equipo. | Pruebas positivas/negativas por rol, tenant y sucursal. |
| CFG-001 | P2 | Configuración | ABIERTA | Marca/logotipo no tiene ciclo completo de archivo y limpieza. | Carga, reemplazo, fallback, eliminación y pruebas. |
| ONB-001 | P1 | Onboarding | ABIERTA | Falta demostrar idempotencia y recuperación de altas parciales. | Pruebas transaccionales y reintento seguro. |
| CLI-001 | P1 | Socios/CRM | ABIERTA | Falta auditoría completa de PII, duplicados e importaciones fallidas. | Política de exposición y pruebas CRUD/importación/multi-sucursal. |
| CAT-001 | P2 | Catálogo | ABIERTA | Quedan compatibilidad legacy, alto volumen y ciclo de imágenes por validar. | Retirar contratos obsoletos y aprobar pruebas de volumen/archivos. |
| SAL-001 | P0 | Ventas/pagos | ABIERTA | Concurrencia y conciliación financiera no tienen E2E integral. | Pruebas simultáneas e invariantes venta–pago–inventario–refund. |
| MEM-001 | P1 | Membresías | ABIERTA | Semántica de inicio, fin y duración requiere auditoría integral. | Casos de borde y pruebas con zona horaria y renovación. |
| BEN-001 | P1 | Beneficios | ABIERTA | Elegibilidad no tiene una fuente de verdad transversal demostrada. | Política única usada por membresía, reserva y acceso con pruebas. |
| ACC-001 | P0 | Accesos | ABIERTA | Doble consumo, reintentos y privacidad requieren pruebas. | Consumo atómico, respuestas mínimas y matriz dispositivo/tenant. |
| SELF-001 | P0 | Autoservicio | ABIERTA | Token de terminal, rate limiting y privacidad necesitan hardening. | Threat model, límites, expiración y E2E de kiosco. |
| BKG-001 | P1 | Reservas | ABIERTA | Falta E2E de colisiones, pagos, cancelación y otra sucursal. | Suite crítica de calendario y autorización en CI. |
| INV-001 | P1 | Inventarios | ABIERTA | Transferencias y conteos no tienen paginación parametrizada completa. | Contratos paginados, UI y pruebas de volumen. |
| INV-002 | P2 | Inventarios | ABIERTA | Falta indicador inequívoco de guardado por renglón en conteos. | Estado guardando/guardado/error accesible por partida. |
| COA-018 | P2 | Coaches/multimedia | ABIERTA | El contrato heredado permite recibir `mediaUrl` aunque la UI usa carga real; si falla el borrado en MinIO al reemplazar/eliminar, el objeto anterior puede quedar huérfano sin cola de reintento. | Retirar `mediaUrl` de altas/ediciones, usar una referencia interna inequívoca y añadir cleanup durable con prueba de fallo/reintento. |
| WEL-001 | P1 | Wellness | ABIERTA | Avance de citas aislado en rama divergente, no validado con Coaches. | Revisar y fusionar mediante PR independiente. |
| AUT-001 | P1 | Automatizaciones | ABIERTA | Faltan locks, reintentos e idempotencia de ejecución. | Ejecución única demostrada y telemetría por tenant. |
| NOT-001 | P1 | Notificaciones | ABIERTA | Proveedores, preferencias, reintentos y deduplicación no están cerrados. | Entrega trazable e idempotente con privacidad. |
| REP-001 | P1 | Reportes | ABIERTA | Filtros tenant/sucursal y definiciones KPI no están auditados uniformemente. | Contratos KPI y pruebas negativas/volumen para cada reporte. |
| AUD-001 | P1 | Auditoría | ABIERTA | No existe matriz completa de mutaciones sensibles contra `audit_log`. | Cobertura automatizada, retención e inmutabilidad definidas. |
| DB-001 | P0 | Datos/Flyway | ABIERTA | Falta demostrar migración desde cero y upgrade de una base real. | Pipeline de migración limpia y actualización con rollback documentado. |
| OPS-001 | P1 | Producción | ABIERTA | Faltan observabilidad, backups restaurables y runbooks verificados. | Simulacro, alertas y procedimientos de recuperación. |

## 3. Hallazgos cerrados en este corte

| ID | Módulo | Resolución | Evidencia |
|---|---|---|---|
| COA-C01 | Coaches/catálogo | Corregida discrepancia `alias`/`alias_name`. | Repositorio alineado con V45. |
| COA-C02 | Coaches/catálogo | Ejercicios SYSTEM protegidos contra edición y alias. | Servicio y `UPDATE` limitado a organización. |
| COA-C03 | Coaches/rutinas | Corregido evento de publicación. | Auditoría `WORKOUT_PUBLISHED`. |
| COA-C04 | Coaches/versiones | La edición reutiliza el borrador vigente. | Actualización transaccional y reconstrucción de estructura. |
| COA-C05 | Coaches/seguridad | Detalle de versión limitado por sucursal. | Consulta enlazada con `workout.branch_id`. |
| COA-C06 | Coaches/API | Asignación acepta `Idempotency-Key` con compatibilidad temporal del campo en cuerpo. | Controlador y cliente web alineados. |
| COA-001 | Coaches/seguridad | Incorporados permisos y filtros `workout.*.own/all` por usuario, organización y sucursal. | `84a5fd3`; pruebas `CoachingServiceSecurityTests` y `SecurityAuthorizationTests`. |
| COA-002 | Coaches/catálogo | UI permite editar/archivar ejercicios administrables y consultar/agregar/eliminar aliases. | `84a5fd3`; E2E de catálogo y aliases. |
| COA-003 | Coaches/constructor | Sesiones, bloques y ejercicios tienen CRUD, ordenamiento accesible y prescripción completa. | `84a5fd3`; pruebas del constructor y E2E de publicación. |
| COA-004 | Coaches/asignaciones | Cancelación exige motivo; reemplazo conserva vínculo, historial e idempotencia y tiene UX completa. | `84a5fd3`; E2E de cancelación y reemplazo. |
| COA-005 | Coaches/calidad | Añadida cobertura dedicada de servicio, seguridad HTTP, PDF, medios, helpers y tres E2E. | `84a5fd3`; suites backend y frontend exitosas. |
| COA-006 | Coaches/PDF | PDF A4 usa identidad del gimnasio, logotipo seguro/fallback, ficha estructurada y saltos de página. | `84a5fd3`; extracción y aserciones PDFBox. |
| COA-007 | Coaches/métricas | Periodos comparables, filtros coach/socio, detalle paginado, KPIs y CSV acotado. | `84a5fd3`; pruebas de comparación y E2E de filtro. |
| COA-009 | Coaches/catálogo | Ciclo de medios implementado con tipos/tamaño permitidos, reemplazo, eliminación y compensación. | `84a5fd3`; `CoachingExerciseMediaServiceTests`. |
| COA-010 | Coaches/métricas | Añadidas series continuas de 4–52 semanas, tres gráficas históricas y filtro directo por socio aplicado a resumen, detalle, tendencia y CSV. | `8162814`; `CoachingCatalogServiceTests` y E2E de métricas. |
| COA-011 | Coaches/perfiles | Especialidades, biografía, estado y múltiples sucursales se administran como perfil agregado con permiso dedicado y auditoría. | `8162814`; `CoachingServiceSecurityTests`, `SecurityAuthorizationTests` y E2E multi-sucursal. |
| COA-012 | Coaches/roles y UX | Dueño limitado a métricas; carga operativa condicionada por rol; perfil movido a Equipo y perfiles faltantes reparados desde usuarios Coach. | `0066c0b`; V48, `DatabaseMigrationTests` y 5 E2E de Coaches/Equipo. |
| COA-013 | Coaches/PostgreSQL | Corregido `500` por parámetros UUID nulos sin tipo en consultas de rutinas, resumen, detalle y tendencias. | `3933aa5`; `DatabaseMigrationTests.coachingQueriesAcceptNullUuidFiltersOnPostgresql` y smoke real sobre V48. |
| COA-014 | Coaches/roles y métricas | Separado el alcance analítico de la lectura operativa; dueño y gerente abren métricas por defecto, sin pestañas ni herramientas de Coach. | `5dd0e47`; V49, pruebas de servicio/migración y 6 E2E, incluido gerente con token operativo anterior. |
| COA-015 | Identidad/Coaches/UX | Integrados especialidades y biografía en el alta transaccional del usuario Coach; restaurada la alineación uniforme de filas y acciones en Equipo. | `602d37b`; prueba PostgreSQL de provisioning, servicio de identidad y 8 E2E con medición de layout. |
| COA-016 | Identidad/Coaches/UX | Rediseñados Equipo, constructor y catálogo con jerarquía compacta, taxonomía guiada e identidad fija; métricas del Coach reducidas a su contexto personal. | `d1d5124`; 31 pruebas frontend, typecheck, lint, build, revisión visual responsiva y 8 E2E. |
| COA-017 | Coaches/catálogo | Añadidos equipos personalizados reutilizables por organización, endpoint no paginado de opciones, scroll interno, tarjetas uniformes y descripciones acotadas. | `9af72e7`; prueba PostgreSQL de opciones, servicio, 31 pruebas frontend, build y 9 E2E. |
| COA-019 | Coaches/catálogo/UX | Corregida la altura excesiva de tarjetas SYSTEM y propias; la descripción queda en una línea sin expandir la fila y el alta de equipo ya no deforma el selector muscular. | `1f69dfa`; typecheck, lint, 31 pruebas frontend, build y 9 E2E con medición de altura y alineación. |
| GEN-003 | General/documentación | Estado, deuda, ramas y fichas modulares consolidados; se retiraron fuentes transitorias paralelas. | Documentos 13, 28, 29 y `modulos/` vigentes al corte. |
| BR-C01 | Gestión de ramas | Wellness retirado del árbol final de Coaches. | Revert explícito de `4a7a7d1`. |

## 4. Historia

Los registros anteriores fueron consolidados y retirados del árbol para evitar
fuentes paralelas. Git conserva su historia. Este documento es la única lista
operativa.
