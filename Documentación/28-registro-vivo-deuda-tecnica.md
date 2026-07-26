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
| MEM-001 | P1 | Membresías | ABIERTA | Semántica de inicio, fin y duración requiere auditoría integral. | Casos de borde y pruebas con zona horaria y renovación. |
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

## 4. Registros anteriores

`24-registro-hallazgos-deuda-tecnica-cierre-catalogo.md` queda como evidencia histórica. Sus pendientes vigentes fueron consolidados aquí. Desde este corte, el documento 28 es la única lista operativa.
