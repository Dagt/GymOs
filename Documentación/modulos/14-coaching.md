# Coaches, ejercicios y rutinas

Estado: `ESTABILIZADO`
Rama de cierre: `feature/coaches-workouts`
Código verificado: `9af72e7`
Corte: 26 de julio de 2026

## Propósito y alcance V1

GymOS permite que cada coach cree, publique, asigne e imprima rutinas, mientras
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
- identificación y ejecución visibles primero; detalles técnicos, medios y
  nombres alternativos se administran de forma progresiva;
- catálogo con scroll interno, cuadrícula responsiva, tarjetas de altura uniforme
  y descripción limitada a dos líneas;
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
- series, repeticiones, carga, descanso, tempo, duración, distancia,
  agrupación, opcionalidad, notas y prescripción libre;
- datos esenciales visibles por ejercicio y campos avanzados desplegables;
- notas de sesión y bloque colapsables y barra persistente de guardado, para
  reducir la longitud de la página sin eliminar información;
- identidad fija del coach conectado en lugar de un selector deshabilitado;
  sólo quien tiene alcance `workout.manage.all` conserva selección de coach;
- guardado del borrador vigente sin producir versiones artificiales;
- publicación inmutable, duplicado e historial de versiones;
- persistencia estructurada en sesiones, bloques y ejercicios, conservando
  `content_json` como instantánea compatible.

### Asignaciones e impresión

- sólo se asignan versiones publicadas a socios activos;
- `Idempotency-Key` evita duplicados y detecta reutilización con otro payload;
- estados activa, completada, cancelada y reemplazada;
- cancelación con motivo obligatorio y auditoría;
- reemplazo idempotente que conserva y muestra el vínculo histórico;
- notas privadas separadas de indicaciones imprimibles;
- PDF A4 reproducible desde la versión asignada, con color, logotipo seguro o
  inicial, gimnasio, sucursal, teléfono, socio, coach, vigencia, sesiones,
  bloques, prescripción y saltos de página.

### Métricas

- asignaciones y socios únicos por periodo, coach y socio;
- coaches activos, coaches sin actividad y primeras rutinas;
- comparación automática contra un periodo anterior de igual duración;
- detalle paginado y filtros visibles por fechas, coach y socio;
- búsqueda directa y selección explícita de socio en la pantalla de métricas;
- serie histórica continua de 4 a 52 semanas, incluyendo semanas sin actividad;
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

- backend: `gradlew clean test --no-daemon`, exitoso;
- frontend: 31 pruebas, typecheck, lint y build de producción, exitosos;
- E2E Playwright: 9 recorridos exitosos para constructor/publicación,
  catálogo/aliases/medios, cancelación/reemplazo/métricas históricas y
  administración multi-sucursal del coach, incluyendo acceso directo a
  métricas para dueño y gerente sin superficies operativas, alta integral de
  Coach, identidad fija en el constructor, métricas personales y alineación
  visual de la tabla de Equipo;
- pruebas dedicadas de propiedad, permisos HTTP, comparación de periodos,
  series semanales, perfiles agregados, sincronización de sucursales, PDF y
  ciclo de medios;
- `git diff --check`, exitoso.

Los antiguos `COA-001` a `COA-007` y `COA-009` quedaron resueltos en
`84a5fd3`; `COA-010` y `COA-011` se cerraron en `8162814`, `COA-012` en
`0066c0b`, `COA-013` en `3933aa5`, `COA-014` en `5dd0e47`, `COA-015` en
`602d37b`, `COA-016` en `d1d5124` y `COA-017` en `9af72e7`. Todos se conservan
en la sección de cerradas del documento 28. Los riesgos
transversales de despliegue, migración PostgreSQL real, observabilidad y
almacenamiento se siguen administrando como `DB-001` y `OPS-001`; no son
funcionalidad pendiente específica de Coaches.
