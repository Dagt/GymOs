# 29. Cronología y situación de ramas

Estado: AUDITADO
Corte remoto: 27 de julio de 2026
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
| 26 jul | Ampliación en `8162814`: perfiles avanzados multi-sucursal, filtro directo por socio y métricas históricas de 4–52 semanas |
| 26 jul | Corrección `0066c0b`: dueño limitado a métricas, perfiles consolidados en Equipo y reparación de usuarios Coach existentes |
| 26 jul | Corrección `3933aa5`: filtros UUID tipados y eliminación del `500` reproducido sobre PostgreSQL persistente |
| 26 jul | Corrección `5dd0e47`: métricas independientes de lectura de rutinas y alcance exclusivo de tablero para dueño/gerente |
| 26 jul | Corrección `602d37b`: alta transaccional de datos Coach dentro de Equipo y restauración del layout uniforme de usuarios |
| 26 jul | Rediseño `d1d5124`: Equipo compacto, constructor progresivo, catálogo guiado y métricas personales del Coach |
| 26 jul | Catálogo `9af72e7`: equipos reutilizables por organización, scroll interno y tarjetas uniformes |
| 26 jul | Corrección `1f69dfa`: tarjetas compactas con descripción elidida y alta de equipo desacoplada de la cuadrícula |
| 26 jul | Restauración `922c0ce`: reaplicación de la versión cerrada de Wellness sobre Coaches con migraciones V50/V51 |
| 26 jul | Estabilización `4eac4d5`: confirmación interna/externa, hooks, migración idempotente y navegación probada de Wellness |
| 26 jul | Corrección `6bb92c2`: asignación `PUBLISHED`, búsqueda reactiva, constructor en tres pasos y tarjetas de ejercicios de 88 px |
| 27 jul | Mejora `fbdfa65`: series uniformes/detalladas con dropsets, descarga PDF reparada y documento A4 rediseñado/verificado |

## 3. Inventario de ramas remotas

Ya contenidas en `main`, sin commits exclusivos: `backup/system-updated-2026-07-20`, ramas `feature/hito-*`, `feature/bookings-experience-and-validation`, `feature/frontend-architecture-stabilization`, `feature/platform-tenant-role-ux-correction` y `feature/inventory-operations-hardening`. Son referencia histórica, no trabajo por fusionar.

| Rama activa/divergente | Situación | Acción |
|---|---|---|
| `feature/coaches-workouts` | Coaches V1 estabilizado hasta `fbdfa65`; la versión cerrada de Wellness fue reaplicada y validada en `922c0ce`/`4eac4d5`. | Publicar la documentación actualizada y abrir PR hacia `main`. |
| `feature/wellness-services` | Su commit funcional `4a7a7d1` ya fue auditado y reaplicado sobre el árbol actual; la rama remota conserva historia divergente. | Mantener como referencia; no fusionar de nuevo. |
| `feature/code-simplification-rebasion-and-refactor` | Un commit exclusivo y seis de main ausentes. | Auditar commit y rebase. |
| `feature/catalog-inventory-enhancement-plan` | Historia fuertemente divergente. | No fusionar directamente; extraer cambios útiles. |

## 4. Incidente de alcance del 26 de julio

Coaches incorporó `4a7a7d1` de Wellness mediante `f3cdf74` y después `907c530`.
La sanitización lo retiró explícitamente con `08ec1a8`, aunque Coaches había
nacido después de ese cierre. La revisión posterior determinó que el retiro
también eliminó agenda, citas, permisos y navegación ya terminados.

La versión funcional se reaplicó en `922c0ce`, sin recuperar los documentos
residuales retirados durante la sanitización. Sus migraciones se renumeraron a
V50/V51 para coexistir con V45–V49 de Coaches y se estabilizó contra el árbol
actual en `4eac4d5`. La rama remota de Wellness queda como referencia histórica,
no como una fusión pendiente.

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
