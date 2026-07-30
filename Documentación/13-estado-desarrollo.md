# 13. Estado actual de desarrollo

Estado: FUENTE CANÓNICA / EN PROGRESO
Corte: 29 de julio de 2026
Código auditado: árbol de trabajo de `feature/reception-pos-init`.

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
| [Ventas y pagos manuales](modulos/07-sales.md) | FUNCIONAL | Venta minorista y membresías con contexto, partidas, pagos, snapshots de beneficios, reportes, validación, reembolsos e idempotencia concurrente | E2E integral venta–pago–inventario–refund (`SAL-001`) |
| [Membresías](modulos/08-memberships.md) | ESTABILIZADO | Periodos y fechas inclusivas, expiración, activa única, cotización backend, alta/renovación/cambio separados y concurrencia PostgreSQL | E2E HTTP/navegador de pagos pendientes/rechazados |
| [Beneficios](modulos/09-benefits.md) | ESTABILIZADO | Política transversal única para cotización de membresía, elegibilidad de reservas y acceso; descuentos con snapshot y ACCESS heredado/revocado dinámicamente por plan | Permisos negativos y concurrencia de asignaciones |
| [Kiosco](modulos/11-selfservice.md) | FUNCIONAL | Consulta operativa y pagos dentro del gimnasio | Hardening de sesión y privacidad (`SELF-001`) |
| [Reservas y recursos](modulos/12-bookings.md) | FUNCIONAL | Calendario, disponibilidad, restricciones por plan y recepción | Suite E2E y deuda UX residual |
| [Inventarios](modulos/13-inventory.md) | ESTABILIZADO | Existencias, transferencias, conteos, valuación y movimientos | Paginación parametrizada y feedback por renglón |
| [Coaches y rutinas](modulos/14-coaching.md) | ESTABILIZADO | Dueño y gerente limitados a métricas; Coach con constructor guiado, catálogo compacto, asignación reactiva, métricas personales y PDF moderno cuyos lookups respetan puertos de aplicación; histórico 4–52 semanas, CSV y 12 E2E | `COA-018`, `DB-001` y `OPS-001` |
| [Wellness/profesionales](modulos/15-wellness.md) | ESTABILIZADO en esta rama | Directorio, servicios, disponibilidad, agenda, citas internas/externas, coordinación y permisos para dueño, gerente y recepción | Cobertura E2E y hardening multi-tenant transversal |
| [Reportes generales](modulos/18-reporting.md) | PARCIAL | Reportes operativos por dominio | Definiciones KPI y cobertura homogénea |
| [Producción/piloto](modulos/20-common-operations.md) | PLANIFICADO | Configuración y runbooks base | Observabilidad, backups, seguridad y piloto |

## 3. Estado verificable de Coaches y rutinas

El módulo permite:

- crear coaches y administrar especialidades, biografía, estado y múltiples
  sucursales desde Gestión del equipo;
- consultar y ampliar un catálogo de ejercicios SYSTEM y propios;
- crear y editar ejercicios en un panel guiado con taxonomía controlada,
  detalles técnicos progresivos, medios y nombres alternativos;
- crear equipos durante el alta de un ejercicio y reutilizarlos desde una lista
  reconstruida para toda la organización;
- recorrer el catálogo dentro de una superficie con scroll y tarjetas compactas
  de 88 px; la descripción se consulta bajo demanda y no ocupa espacio en la fila;
- crear un equipo desde un bloque independiente que no desplaza ni deforma los
  selectores de grupo muscular y equipo;
- crear rutinas estructuradas en sesiones, bloques y ejercicios;
- usar un constructor guiado en tres pasos —datos generales, días/ejercicios y
  revisión/guardado— con cada ejercicio normal reducido a nombre, series,
  repeticiones, carga y descanso en una sola fila;
- prescribir series iguales directamente o abrir una hoja lateral compacta para
  personalizar repeticiones/carga por serie y tramos sin descanso; al cerrarla,
  una síntesis sustituye todas las tarjetas y campos detallados;
- consultar tempo, duración, distancia, agrupación, opcionalidad y notas sólo
  bajo `Más opciones`, sin perder los datos avanzados existentes;
- regresar desde la revisión usando los pasos superiores, sin una segunda acción
  redundante junto a guardar y publicar;
- guardar y editar el borrador vigente sin crear una versión en cada guardado;
- publicar versiones inmutables y consultar su historial;
- duplicar rutinas;
- buscar socios activos automáticamente a partir de dos caracteres y asignar
  una versión publicada con idempotencia;
- completar, cancelar o reemplazar asignaciones conservando trazabilidad;
- exigir motivo de cancelación y mostrar el vínculo de reemplazo;
- consultar métricas paginadas por periodo, coach y socio, comparadas contra el
  periodo anterior;
- consultar gráficas históricas de 4 a 52 semanas para asignaciones, socios
  únicos y primeras rutinas, con semanas sin actividad explícitas;
- buscar y aplicar directamente el filtro de socio en la pantalla de métricas,
  incluyendo reporte, histórico y CSV;
- exportar CSV y descargar de forma controlada un PDF A4 compacto y deportivo con elección de variante visual (`COMPACT_LIGHT` para impresión en papel o `COMPACT_DARK` para consulta visual en celular), respaldado por un motor documental desacoplado (`WorkoutPdfDocumentMapper`, `PrescriptionFormatter`, `CompactWorkoutPdfRenderer`) que condensa rutinas de 4-5 días en 2-3 páginas con logo del gimnasio, cabecera ligera, notaciones sintéticas (`3 × 12 / 10 / 8 · subir carga`, `3 × (8 + 15)`), folio y numeración `Página X de Y`;
- cargar y administrar medios de ejercicios;
- separar alcance propio del coach y alcance global de reportes para
  dueño/gerencia;
- adaptar la vista del coach a métricas personales, sin selector global,
  conteos del equipo ni comparación redundante consigo mismo;
- limitar a dueño y gerente al tablero de métricas como vista predeterminada,
  sin pestaña redundante ni carga/exposición de constructor, ejercicios,
  asignación, PDF o mutaciones del historial;
- autorizar los reportes con `coach.metrics.read` sin exigir lectura operativa
  de rutinas;
- crear usuarios Coach desde el mismo alta de Equipo, incluyendo especialidades,
  biografía y sucursales en una sola transacción y sin romper el layout común;
- administrar Equipo en una tabla visual plana y responsiva, con roles/contexto
  compactos y sin tarjetas anidadas por usuario.

El alcance funcional V1 está cerrado y estabilizado. El cierre base está en
`84a5fd3`, la ampliación de perfiles e histórico en `8162814`, la corrección de
alcance/UX en `0066c0b`, la compatibilidad PostgreSQL real en `3933aa5` y el
alcance analítico de supervisores en `5dd0e47`, la integración del alta Coach
en Equipo en `602d37b`, el rediseño UX en `d1d5124` y el catálogo reutilizable
en `9af72e7`, con compactación y alineación corregidas en `1f69dfa`. Los
flujos de asignación y construcción se corrigieron en `6bb92c2`; la
prescripción estructurada y el PDF se cerraron en `fbdfa65`, y su compactación,
composición moderna y carga segura del logo interno en `e718fdf`. Los hallazgos
específicos `COA-001` a `COA-007`, `COA-009` a `COA-017` y `COA-019` a `COA-024`
pasaron al
historial
resuelto del documento 28.

## 4. Verificación del corte

Validación ejecutada sobre el árbol saneado:

- backend: `gradlew clean test --no-daemon`, exitoso;
- frontend: 33 pruebas, typecheck, lint y build de producción, exitosos;
- E2E específico de Coaches/Equipo: 12 recorridos Playwright exitosos;
- cobertura dedicada: propiedad `own/all`, permiso de perfiles, métricas,
  histórico semanal, perfil multi-sucursal, prescripción detallada, descarga,
  paginación e incrustación real del logo del PDF y medios;
- prueba PostgreSQL de filtros UUID nulos para biblioteca, resumen, detalle,
  desempeño y tendencia;
- comprobación de whitespace Git: exitosa.

Al finalizar cada iteración se debe registrar aquí el commit auditado, suites
ejecutadas, módulos afectados y deuda nueva o cerrada en el documento 28.

## 5. Reglas de mantenimiento

1. Actualizar este documento en el mismo commit que cambie materialmente el estado de un módulo.
2. No duplicar listas de pendientes: registrar el identificador del documento 28.
3. Mantener el detalle en `modulos/` y la cronología Git en el documento 29.
4. No usar porcentajes sin criterios cerrados y evidencia reproducible.
5. Si código, pruebas y documentación discrepan, prevalece el código verificado y la documentación debe corregirse.
