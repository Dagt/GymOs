# 29. Cronología y situación de ramas

Estado: AUDITADO
Corte remoto: 26 de julio de 2026
Base comparada: `origin/main` en `f44708d`

## 1. Criterio

La pertenencia a `main` se determinó por ancestros de Git, no por nombres. Una rama detrás sin commits exclusivos es histórica y no debe fusionarse otra vez. Una rama divergente requiere revisión o rebase.

## 2. Línea de tiempo integrada

| Fecha | Hito verificable |
|---|---|
| 13–16 jul | Fundación, identidad, ventas, membresías, operación, servicios avanzados, productización y preparación administrativa |
| 17 jul | Arquitectura frontend y separación de consolas |
| 18–20 jul | Correcciones multi-tenant, CRM, membresías y reservas |
| 20 jul | Integración de experiencia y validaciones de reservas |
| 22 jul | Integración de simplificación/refactor general |
| 24 jul | Cierre integral de catálogo y operación por sucursal |
| 25 jul | Integración de endurecimiento de inventarios por PR #6 |
| 26 jul | Coaches/rutinas en rama; todavía no integrado en main |
| 26 jul | Sanitización documental global: fichas por módulo y retiro de documentos transitorios |
| 26 jul | Cierre funcional de Coaches/rutinas en `84a5fd3`: seguridad `own/all`, constructor, medios, trazabilidad, métricas paginadas, PDF A4 y E2E |

## 3. Inventario de ramas remotas

Ya contenidas en `main`, sin commits exclusivos: `backup/system-updated-2026-07-20`, ramas `feature/hito-*`, `feature/bookings-experience-and-validation`, `feature/frontend-architecture-stabilization`, `feature/platform-tenant-role-ux-correction` y `feature/inventory-operations-hardening`. Son referencia histórica, no trabajo por fusionar.

| Rama activa/divergente | Situación | Acción |
|---|---|---|
| `feature/coaches-workouts` | Alcance funcional V1 cerrado y validado en `84a5fd3`; Wellness permanece excluido. | Publicar la documentación de cierre y abrir PR hacia `main`. |
| `feature/wellness-services` | Un commit exclusivo y uno de main ausente. | Rebase y revisión independiente. |
| `feature/code-simplification-rebasion-and-refactor` | Un commit exclusivo y seis de main ausentes. | Auditar commit y rebase. |
| `feature/catalog-inventory-enhancement-plan` | Historia fuertemente divergente. | No fusionar directamente; extraer cambios útiles. |

## 4. Incidente de alcance del 26 de julio

Coaches incorporó `4a7a7d1` de Wellness mediante `f3cdf74` y después `907c530`. La sanitización conserva el historial, pero revierte el contenido Wellness explícitamente. Los hunks útiles de Coaches se corrigen y prueban. Así se evita reescribir destructivamente la rama.

## 5. Regla futura

1. Una rama representa un módulo u objetivo.
2. Antes de fusionar: `fetch`, comparación con `origin/main`, pruebas y revisión documental.
3. Trabajo ajeno se integra por cherry-pick o PR propio, no por merge lateral.
4. Todo cierre actualiza documentos 13, 28 y 29.
5. Las ramas históricas sólo se eliminan por decisión explícita.

## 6. Sanitización documental global

Se consolidaron los planes, prompts, cierres y auditorías transitorias en:

- `13-estado-desarrollo.md`;
- `modulos/`, con una ficha por módulo;
- `28-registro-vivo-deuda-tecnica.md`;
- este documento para la historia de ramas.

Los archivos residuales fueron eliminados del árbol vigente. Su contenido sigue
disponible en el historial Git anterior a esta sanitización.
