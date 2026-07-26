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
| GEN-003 | P1 | General/documentación | EN_CURSO | La documentación mezclaba estado, plan y bitácora. | Documentos 13, 28, 29 y 30 adoptados y mantenidos en cada cierre. |
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
| COA-001 | P1 | Coaches/rutinas | ABIERTA | Autorización amplia `workout.manage`; falta alcance propio/global. | Permisos `own/all`, filtros y pruebas negativas. |
| COA-002 | P1 | Coaches/rutinas | ABIERTA | Administración de ejercicios y alias incompleta en frontend. | Editar, archivar y gestionar alias con duplicidad visible. |
| COA-003 | P1 | Coaches/rutinas | ABIERTA | Constructor sin reordenamiento accesible ni CRUD completo de bloques. | CRUD/reordenamiento con teclado y puntero. |
| COA-004 | P1 | Coaches/rutinas | ABIERTA | Reemplazo existe en backend, pero falta UX y cobertura integral. | Acción UI, confirmación, vínculo visible y pruebas transaccionales. |
| COA-005 | P1 | Coaches/rutinas | ABIERTA | Contratos nuevos carecen de cobertura dedicada suficiente. | Pruebas de servicio/controlador/repositorio y aislamiento tenant/sucursal. |
| COA-006 | P1 | Coaches/rutinas/PDF | ABIERTA | PDF base sin identidad visual, tablas, imágenes ni QA de impresión. | Plantilla A4 aprobada, datos del gimnasio, saltos y pruebas de render. |
| COA-007 | P2 | Coaches/rutinas/métricas | ABIERTA | Falta comparación semanal, filtros, paginación e indicadores de acompañamiento. | KPIs definidos, periodos comparables y exportación consistente. |
| COA-009 | P2 | Coaches/rutinas/catálogo | ABIERTA | Medios limitados a URL, sin carga ni ciclo de archivo. | Servicio, límites, fallback y limpieza documentados. |
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
| BR-C01 | Gestión de ramas | Wellness retirado del árbol final de Coaches. | Revert explícito de `4a7a7d1`. |

## 4. Historia

Los registros anteriores fueron consolidados y retirados del árbol para evitar
fuentes paralelas. Git conserva su historia. Este documento es la única lista
operativa.
