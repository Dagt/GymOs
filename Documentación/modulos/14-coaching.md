# Coaches, ejercicios y rutinas

Estado: `ESTABILIZADO`
Base histórica de cierre: `feature/coaches-workouts` / `e718fdf`
Última revisión en el árbol integrado: 25 de agosto de 2026

## Propósito y alcance V1

GymFort permite que cada coach cree, publique, asigne e imprima rutinas, mientras
dueño y gerencia miden el acompañamiento a socios. La plataforma no cobra estas
rutinas, no registra comisiones y no genera ventas, pagos ni movimientos de caja.
La consulta remota por el socio pertenece a una aplicación futura.

## Implementación vigente

### Administración del equipo de coaches

- perfil agregado por usuario, aun cuando opere en más de una sucursal;
- especialidades normalizadas y biografía en la misma ficha del usuario;
- el alta de un usuario con rol Coach solicita esos datos adicionales y crea
  cuenta, rol, sucursales y perfil dentro de la misma transacción;
- el estado se administra con el control existente de suspender/reactivar;
- selección múltiple de sucursales dentro del alcance del administrador;
- creación o reactivación automática del perfil operativo en cada sucursal
  seleccionada y desactivación controlada en las retiradas;
- sincronización del acceso `user_branch_access` sin modificar sucursales fuera
  del alcance del actor;
- permiso dedicado `coach.profile.manage`, otorgado a dueño, gerencia y perfiles
  administrativos;
- alta, credenciales, cambio de rol, estado, perfil y sucursales se concentran
  en `/users`; `/coaches` ya no duplica la gestión de identidad.
- Equipo presenta a todos los usuarios en una misma tabla visual compacta y
  responsiva, con identidad, sucursales, rol, estado y acciones alineadas;
- los roles disponibles quedan en un resumen desplegable y los indicadores de
  contexto usan una cuadrícula compacta para priorizar la lista operativa;
- `Datos de Coach` sigue siendo una acción complementaria del mismo usuario:
  no crea una entidad visual separada ni desplaza el control de estado.

### Catálogo de ejercicios

- catálogo SYSTEM inmutable con más de 60 ejercicios;
- búsqueda paginada por nombre, alias, músculo, equipo y tipo;
- ejercicios propios de organización o coach con descripción, instrucciones,
  músculos, patrón, equipo, nivel, lateralidad y advertencias;
- alta y edición por propietario, archivo administrativo y prevención de
  duplicados por nombre o alias normalizado;
- consulta, alta y eliminación de aliases;
- alta y edición en panel lateral, separadas de la superficie de consulta;
- taxonomía guiada para músculo principal, patrón de movimiento, equipo, nivel
  y lateralidad, conservando valores anteriores sin multiplicar variantes;
- el selector permite crear un equipo durante el alta; se guarda en
  `exercise.equipment` y el endpoint `/exercises/equipment-options` reconstruye
  las opciones activas SYSTEM y de la organización sin depender de paginación;
- el nombre de un equipo nuevo se captura debajo de la cuadrícula de
  identificación para no desplazar el selector de grupo muscular;
- identificación y ejecución visibles primero; detalles técnicos, medios y
  nombres alternativos se administran de forma progresiva;
- catálogo con paginación visible de 25/50/100 ejercicios, total del servidor,
  scroll interno de altura acotada con barra oculta, cuadrícula responsiva y
  tarjetas de 88 px; el teclado también permite recorrer el listado;
- el constructor consulta sus propias sugerencias en servidor: ni la página
  ni los filtros del catálogo visible restringen la selección del ejercicio;
- la descripción e instrucciones no se imprimen dentro de la tarjeta: `Ver
  descripción` abre un panel de consulta que no altera la cuadrícula;
- carga, lectura, reemplazo y eliminación de JPG, PNG, WebP, GIF o MP4 de hasta
  8 MB mediante almacenamiento de objetos;
- intento de limpieza del archivo sustituido y compensación si falla la
  persistencia de la nueva referencia.

### Persistencia de multimedia y nombres alternativos

- el ejercicio se crea primero para obtener el UUID que define propiedad,
  organización y ruta del objeto;
- el navegador envía el archivo como `multipart/form-data` a
  `POST /exercises/{exerciseId}/media`;
- se aceptan JPG, PNG, WebP, GIF y MP4 de 1 byte a 8 MB;
- el binario se almacena en MinIO, no en PostgreSQL; la clave sigue
  `coaching/{organizationId}/{exerciseId}/{uuid}-{nombreSeguro}`;
- PostgreSQL conserva únicamente esa clave en `exercise.media_url`; pese al
  nombre histórico de la columna, no es una URL pública;
- la lectura usa `GET /exercises/{exerciseId}/media`, valida el tenant y entrega
  los bytes con su `Content-Type`;
- al reemplazar se sube el nuevo objeto, se actualiza la referencia y se intenta
  eliminar el anterior; si falla la actualización SQL, el objeto nuevo se
  elimina como compensación;
- los nombres alternativos viven en `exercise_alias` con texto visible y una
  versión normalizada para búsqueda y unicidad; apuntan al mismo ejercicio, no
  crean una copia;
- `COA-018` registra el reintento pendiente de limpieza de objetos huérfanos y
  la retirada del campo heredado `mediaUrl` del contrato de alta/edición.

### Constructor y versiones

- biblioteca de rutinas propias o globales según permiso;
- sesiones y bloques múltiples reordenables con controles accesibles;
- ejercicios libres o seleccionados del catálogo;
- los ejercicios recién agregados conservan su estado abierto durante la
  captura; escribir la primera letra ya no contrae la tarjeta;
- cada ejercicio normal ocupa una sola fila esencial: nombre, series,
  repeticiones, carga y descanso;
- `Personalizar series` abre una hoja lateral compacta con repeticiones y carga
  por serie; conserva tramos adicionales sin descanso para pirámides,
  progresiones y dropsets sin llenar la página de tarjetas;
- al cerrar el panel, la tarjeta muestra una síntesis como
  `20 @ 20 kg → 15 @ 25 kg → 10 @ 30 kg`;
- tempo, duración, distancia, agrupación, opcionalidad, notas e indicación
  complementaria permanecen disponibles, pero bajo `Más opciones` y un segundo
  nivel desplegable;
- el rediseño no cambia `content_json`: las rutinas uniformes y los `setPlan`
  detallados anteriores siguen siendo editables e imprimibles;
- flujo en tres pasos: datos generales, días/ejercicios y revisión/guardado;
- notas de sesión y bloque colapsables, resumen de días/bloques/ejercicios,
  biblioteca de rutinas colapsada y barra contextual de navegación/guardado;
- la revisión no repite un botón para volver a editar días: los tres pasos
  superiores son la navegación única;
- identidad fija del coach conectado en lugar de un selector deshabilitado;
  sólo quien tiene alcance `workout.manage.all` conserva selección de coach;
- guardado del borrador vigente sin producir versiones artificiales;
- publicación inmutable, duplicado e historial de versiones;
- persistencia estructurada en sesiones, bloques y ejercicios, conservando
  `content_json` como instantánea compatible.

### Asignaciones e impresión

- sólo se asignan versiones publicadas a socios activos;
- la búsqueda del socio se ejecuta automáticamente 250 ms después de escribir
  dos caracteres; no requiere presionar un botón;
- `PUBLISHED` es el estado canónico producido por publicar y aceptado al
  asignar; `ACTIVE` se conserva sólo para plantillas heredadas;
- para rutinas con un borrador posterior, el listado selecciona la versión
  publicada más reciente y nunca propone el borrador para una asignación;
- `Idempotency-Key` evita duplicados y detecta reutilización con otro payload;
- inicio y fin obligatorios, fin inclusivo e inicio no anterior al día de
  operación en `America/Mexico_City`; socio y coach deben estar activos;
- una sola asignación por socio/sucursal para periodos que se solapen, incluso
  entre rutinas o coaches distintos; lock transaccional del socio evita dos
  altas simultáneas. La siguiente rutina puede programarse desde el día
  siguiente al fin de la anterior;
- estados activa, programada, completada, cancelada y reemplazada. Programada
  es la proyección de una asignación cuyo inicio aún no llega; no cuenta como
  vigente. Al pasar el fin se muestra completada aunque el worker no haya
  corrido; éste persiste el cierre por lotes con motivo `PERIOD_ENDED` y auditoría;
- completar por vencimiento indica **fin del periodo**, no demuestra que el
  socio realizó los ejercicios; no se reabren estados finales ni se completa
  manualmente una asignación que todavía no inicia;
- cancelación con motivo obligatorio y auditoría;
- reemplazo idempotente que conserva y muestra el vínculo histórico; una
  sustitución de la rutina vigente empieza hoy y exige nueva fecha de fin.
  Programar la siguiente sin solape usa una nueva asignación, no reemplazo;
- historial independiente de los filtros de métricas, paginado en servidor y
  con scroll interno oculto; el socio seleccionado permite acotar su historial;
- duplicados legacy no se eliminan ni se elige arbitrariamente cuál conservar:
  el coach debe cancelar los sobrantes con motivo. Registros antiguos sin fin
  requieren cancelación/reemplazo explícitos;
- notas privadas separadas de indicaciones imprimibles;
- descarga PDF mediante un enlace temporal insertado en el documento, con
  validación de respuesta vacía, estado visible y mensaje de éxito/error;
- soporta selección de variante visual mediante parámetro `?template=COMPACT_LIGHT` (Impresión clara) o `COMPACT_DARK` (Visual oscuro), conservando `DETAILED` para retrocompatibilidad;
- modelo documental desacoplado (`WorkoutPdfDocument`, `WorkoutPdfSession`, `WorkoutPdfBlock`, `WorkoutPdfExercise`, `WorkoutPdfBranding`) mapeado desde la versión publicada de la asignación;
- formateador de prescripción deportiva (`PrescriptionFormatter`) que condensa series/reps/carga en notación limpia de una sola línea (`3 × 12 / 10 / 8 · subir carga`, `3 × (8 + 15)`, `3 × 12 · RPE 8`);
- PDF A4 reproducible con composición compacta y deportiva, encabezado liviano (menos del 15% de la primera página), ficha única de socio/coach/objetivo/vigencia, días continuos sin desperdiciar hojas, pie de página con folio, versión y numeración `Página X de Y`;
- los logotipos nuevos usan la ruta relativa versionada
  `/api/tenant-logo/{organizationId}?v=...`, que el frontend resuelve desde
  almacenamiento persistente; los enlaces heredados `/tenants/...` siguen
  siendo compatibles. Ambos se resuelven únicamente contra el origen del
  frontend autorizado en CORS; las URL externas siguen limitadas a HTTPS
  público y se conserva la inicial como fallback;
- el logotipo mantiene su proporción dentro del encabezado y cuenta con prueba
  automatizada de incrustación de una imagen real.
- los lookups de marca y equipo del PDF son puertos de aplicación inyectados en
  `WorkoutPdfDocumentMapper`; sus consultas JDBC están confinadas a adaptadores
  de infraestructura y ya no existe el service locator global `static volatile`
  que hacía depender el resultado del orden de arranque. Los lookups reciben los
  IDs de organización y sucursal, evitando resolver marca o equipo por nombres
  ambiguos de otro tenant;
- la paleta documental conserva por separado color principal, acento y fondo
  de la organización. El adaptador ya no consulta las columnas de marca por
  sucursal retiradas en V27, por lo que una excepción opcional no sustituye el
  acento configurado por una paleta gris.

### Métricas

- asignaciones y socios únicos por periodo, coach y socio;
- coaches activos, coaches sin actividad y primeras rutinas;
- comparación automática contra un periodo anterior de igual duración;
- detalle paginado y filtros visibles por fechas, coach y socio;
- búsqueda directa y selección explícita de socio en la pantalla de métricas;
- serie histórica continua por rango (hasta un año), semanas de lunes a domingo
  y días sin actividad incluidos; se mantiene el parámetro legacy de 4–52 semanas;
- gráficas separadas de rutinas asignadas, socios únicos y primeras rutinas;
- CSV completo y acotado a 10,000 filas para evitar exportaciones accidentales
  sin filtro, respetando también el socio seleccionado;
- alcance propio para coach y global de sucursal para dueño/gerencia;
- la vista personal del coach oculta el selector global, `Coaches activos`,
  `Sin asignaciones` y `Desempeño por coach`; muestra asignaciones, socios
  atendidos y primeras rutinas de su propio alcance;
- dueño y gerente entran directamente al tablero de métricas, sin pestaña
  redundante y sin cargar rutinas, catálogo de ejercicios ni acciones sobre
  asignaciones; la detección por rol también protege la interfaz mientras se
  renueva un token anterior.

En **Indicadores → Coaches y Rutinas**, las gráficas semanal y primerizas vs.
renovaciones comparten el periodo seleccionado. La primera asignación del socio
en la organización se cuenta una sola vez, incluso con varias altas el mismo
día; las posteriores son renovaciones. Las canceladas se excluyen y las
reemplazadas permanecen en el historial. `assigned_on` usa la fecha de México,
no el día UTC del servidor; V91 repara únicamente el defecto histórico que
coincide con el día UTC de creación, sin modificar los periodos elegidos.

El desempeño individual tiene eje de fechas y una línea por coach (hasta cinco
visibles), vista acumulada **dentro del periodo** o por día, colores/trazos
distintos, semanas/tablas consultables y errores diferenciados de cero actividad.
No suma socios únicos semanales como si fueran personas distintas del periodo.
Los indicadores actuales excluyen asignaciones futuras/finalizadas y no
multiplican registros por joins con la biblioteca de rutinas.

## Seguridad e invariantes

- `workout.read.own|all` y `workout.manage.own|all` separan propiedad de alcance
  global;
- `coach.profile.manage` protege tanto la consulta como la mutación de perfiles
  avanzados desde Gestión del equipo;
- los roles `Dueno del gimnasio` y `Gerente` conservan `coach.read`,
  `coach.metrics.read` y `coach.profile.manage`, pero no reciben permisos de
  lectura/gestión de rutinas, catálogo, asignación ni PDF;
- los reportes usan `coach.metrics.read` y un alcance analítico propio; no
  dependen de `workout.read.*`, reservado para consultar rutinas operativas;
- el perfil de coach se vincula a `user_account_id`; un coach no puede crear,
  leer, modificar, asignar, reemplazar ni imprimir rutinas de otro coach;
- organización y sucursal se filtran en servicio y repositorio;
- los filtros UUID opcionales se tipan explícitamente en PostgreSQL para evitar
  `500` cuando coach, socio o propietario no están seleccionados;
- los ejercicios SYSTEM son de sólo lectura y un coach sólo modifica los que
  creó;
- editar una rutina nunca cambia el contenido de una asignación histórica;
- `REPLACED` sólo surge del flujo de reemplazo, no de un cambio manual de estado.

## Evidencia de cierre

La regresión de fechas, solapes concurrentes, estados, métricas y cierre
automático se valida en `OperationalHardeningPostgresIntegrationTests`; el
dashboard y los flujos visibles en `operational-hardening.spec.ts` y
`coaches-workouts.spec.ts`. Configuración, migraciones y resultados del corte
actual: [cierre operativo](../../docs/operations/operational-hardening.md).
Los resultados siguientes se conservan como evidencia histórica de cortes previos.

- paginación: `ExercisePaginationIntegrationTests` y `e2e/pagination.spec.ts`
  comprueban navegación y búsqueda con 127 ejercicios y selección independiente
  desde el constructor; contrato y límites en
  [paginación de listados](../../docs/operations/list-pagination.md);
- backend: `gradlew clean test --no-daemon`, exitoso;
- frontend: 33 pruebas, typecheck, lint y build de producción, exitosos;
- E2E Playwright: 13 recorridos exitosos para constructor/publicación,
  catálogo/aliases/medios, cancelación/reemplazo/métricas históricas y
  administración multi-sucursal del coach, incluyendo acceso directo a
  métricas para dueño y gerente sin superficies operativas, alta integral de
  Coach, identidad fija en el constructor, métricas personales y alineación
  visual de la tabla de Equipo, búsqueda reactiva de socio, asignación de una
  rutina `PUBLISHED`, prescripción progresiva/dropset en panel lateral y
  descarga real del PDF, incluido el tecleo carácter por carácter en un
  ejercicio recién agregado sin que se contraiga;
- pruebas dedicadas de propiedad, permisos HTTP, comparación de periodos,
  series semanales, perfiles agregados, sincronización de sucursales, PDF corto
  y multipágina y ciclo de medios;
- verificación visual del PDF moderno renderizado con Poppler, incluido un
  documento con imagen real y otro con acento amarillo de organización, sin
  recortes, superposiciones, pérdida de color, encabezado o pie;
- `git diff --check`, exitoso.

Los antiguos `COA-001` a `COA-007` y `COA-009` quedaron resueltos en
`84a5fd3`; `COA-010` y `COA-011` se cerraron en `8162814`, `COA-012` en
`0066c0b`, `COA-013` en `3933aa5`, `COA-014` en `5dd0e47`, `COA-015` en
`602d37b`, `COA-016` en `d1d5124`, `COA-017` en `9af72e7` y `COA-019` a
`COA-024` en los cortes posteriores. Todos se conservan en la sección de
cerradas del documento 28. `COA-018` permanece como deuda específica de
limpieza durable de medios; los riesgos transversales de despliegue, migración
PostgreSQL real y observabilidad siguen como `DB-001` y `OPS-001`.
