# 13. Estado actual de desarrollo

Estado: FUENTE CANÓNICA / EN PROGRESO
Corte: 26 de julio de 2026
Código auditado: `feature/coaches-workouts` en `0066c0b`, posterior a la
separación de los cambios de Wellness.

## 1. Cómo interpretar este documento

Este archivo responde únicamente qué existe hoy en el código. El detalle se
mantiene en `modulos/`, los pendientes en `28-registro-vivo-deuda-tecnica.md` y
la situación de ramas en `29-cronologia-ramas.md`.

Estados permitidos:

- `FUNCIONAL`: el flujo principal existe en backend y frontend.
- `PARCIAL`: existe una base utilizable, pero faltan contratos, UX o pruebas.
- `ESTABILIZADO`: funcional, con cobertura y validaciones suficientes para piloto.
- `PLANIFICADO`: documentado, sin implementación completa.
- `NO AUDITADO`: no hay evidencia reciente suficiente para elevar el estado.

Ningún módulo se declara “completo” sólo por el nombre de una rama o commit.

## 2. Resumen ejecutivo por módulo

| Módulo | Estado | Evidencia actual | Pendiente principal |
|---|---|---|---|
| [Plataforma, tenant y sucursales](modulos/01-organizations.md) | FUNCIONAL | Consolas separadas, selección de gimnasio/sucursal y contexto tenant | Matriz negativa multi-tenant y endurecimiento global |
| [Identidad, sesiones y permisos](modulos/02-identity-security.md) | FUNCIONAL | Inicio/renovación de sesión, roles y permisos por endpoint | Refresh token seguro y autorización uniforme |
| [Socios/CRM](modulos/05-clients.md) | FUNCIONAL | Alta, búsqueda, detalle y asociación con membresías | Cobertura CRUD y escenarios multi-sucursal |
| [Catálogo comercial](modulos/06-catalog.md) | ESTABILIZADO | Productos, membresías, categorías y operación por sucursal | Paginación/consistencia transversal |
| [Ventas y pagos manuales](modulos/07-sales.md) | FUNCIONAL | Ventas, partidas, pagos, validación y reembolsos | Pruebas integrales de concurrencia y conciliación |
| [Membresías](modulos/08-memberships.md) y [kiosco](modulos/11-selfservice.md) | FUNCIONAL | Alta, renovación, cambio de plan y autoservicio | Revisar semántica de fechas y seguridad de sesión |
| [Reservas y recursos](modulos/12-bookings.md) | FUNCIONAL | Calendario, disponibilidad, restricciones por plan y recepción | Suite E2E y deuda UX residual |
| [Inventarios](modulos/13-inventory.md) | ESTABILIZADO | Existencias, transferencias, conteos, valuación y movimientos | Paginación parametrizada y feedback por renglón |
| [Coaches y rutinas](modulos/14-coaching.md) | ESTABILIZADO | Dueño limitado a métricas; Coach con catálogo/constructor/asignaciones; perfil multi-sucursal en Equipo; histórico 4–52 semanas, CSV, PDF y E2E | Riesgos transversales `DB-001` y `OPS-001` |
| [Wellness/profesionales](modulos/15-wellness.md) | PARCIAL en `main` | Base histórica de profesionales, servicios y disponibilidad | Agenda aislada en rama propia |
| [Reportes generales](modulos/18-reporting.md) | PARCIAL | Reportes operativos por dominio | Definiciones KPI y cobertura homogénea |
| [Producción/piloto](modulos/20-common-operations.md) | PLANIFICADO | Configuración y runbooks base | Observabilidad, backups, seguridad y piloto |

## 3. Estado verificable de Coaches y rutinas

El módulo permite:

- crear coaches y administrar especialidades, biografía, estado y múltiples
  sucursales desde Gestión del equipo;
- consultar y ampliar un catálogo de ejercicios SYSTEM y propios;
- crear rutinas estructuradas en sesiones, bloques y ejercicios;
- guardar y editar el borrador vigente sin crear una versión en cada guardado;
- publicar versiones inmutables y consultar su historial;
- duplicar rutinas;
- asignar una versión publicada a un socio con idempotencia;
- completar, cancelar o reemplazar asignaciones conservando trazabilidad;
- exigir motivo de cancelación y mostrar el vínculo de reemplazo;
- consultar métricas paginadas por periodo, coach y socio, comparadas contra el
  periodo anterior;
- consultar gráficas históricas de 4 a 52 semanas para asignaciones, socios
  únicos y primeras rutinas, con semanas sin actividad explícitas;
- buscar y aplicar directamente el filtro de socio en la pantalla de métricas,
  incluyendo reporte, histórico y CSV;
- exportar CSV y descargar un PDF A4 con identidad del gimnasio;
- cargar y administrar medios de ejercicios;
- separar alcance propio del coach y alcance global de reportes para
  dueño/gerencia;
- limitar al dueño a métricas, sin cargar ni exponer constructor, ejercicios,
  asignación, PDF o mutaciones del historial.

El alcance funcional V1 está cerrado y estabilizado. El cierre base está en
`84a5fd3`, la ampliación de perfiles e histórico en `8162814` y la corrección de
alcance/UX en `0066c0b`. Los hallazgos específicos `COA-001` a `COA-007` y
`COA-009` a `COA-012` pasaron al historial
resuelto del documento 28.

## 4. Verificación del corte

Validación ejecutada sobre el árbol saneado:

- backend: `gradlew clean test --no-daemon`, exitoso;
- frontend: 31 pruebas, typecheck, lint y build de producción, exitosos;
- E2E específico de Coaches: 5 recorridos Playwright exitosos;
- cobertura dedicada: propiedad `own/all`, permiso de perfiles, métricas,
  histórico semanal, perfil multi-sucursal, PDF y medios;
- comprobación de whitespace Git: exitosa.

Al finalizar cada iteración se debe registrar aquí el commit auditado, suites
ejecutadas, módulos afectados y deuda nueva o cerrada en el documento 28.

## 5. Reglas de mantenimiento

1. Actualizar este documento en el mismo commit que cambie materialmente el estado de un módulo.
2. No duplicar listas de pendientes: registrar el identificador del documento 28.
3. Mantener el detalle en `modulos/` y la cronología Git en el documento 29.
4. No usar porcentajes sin criterios cerrados y evidencia reproducible.
5. Si código, pruebas y documentación discrepan, prevalece el código verificado y la documentación debe corregirse.
