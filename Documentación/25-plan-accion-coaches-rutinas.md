# 25. Plan de acción — Coaches y rutinas

Estado: APROBADO PARA IMPLEMENTACIÓN  
Fecha: 25 de julio de 2026  
Rama: `feature/coaches-workouts`

## 1. Objetivo

Completar el módulo de Coaches y rutinas para que cada coach pueda crear rutinas estructuradas, mantener un catálogo amplio y extensible de ejercicios, asignar una versión concreta de la rutina a un socio y generar un PDF imprimible. El dueño y los gerentes deben poder medir semanalmente cuántas rutinas asigna cada coach y a qué socios.

La finalidad de estas métricas es evaluar participación, seguimiento e involucramiento del coach con los socios; no calcular ventas, comisiones ni ingresos.

## 2. Decisiones de producto no negociables

1. **No se cobran rutinas en GymOS.** Los acuerdos y cobros en efectivo entre coach y socio ocurren fuera de la plataforma.
2. **No se crea venta, pago, cuenta por cobrar, comisión ni ingreso** al crear o asignar una rutina.
3. El alcance actual es administrativo: crear, versionar, asignar, consultar, auditar, medir y exportar.
4. La app móvil o portal remoto del socio queda fuera de esta etapa.
5. La información debe quedar preparada para que una futura app del socio consuma la rutina asignada sin rediseñar el dominio.
6. Una asignación conserva una instantánea/version específica; las ediciones posteriores no alteran retroactivamente lo entregado al socio.
7. Los datos se aíslan por organización y, cuando corresponda, por sucursal.

## 3. Referencia funcional entregada

El PDF de ejemplo contiene dos páginas verticales con una rutina de cinco días organizada por grupo muscular, ejercicios y prescripción de series/repeticiones. También incluye indicaciones libres como movilidad, calentamiento, estiramiento, preentreno, ejercicios opcionales, progresiones de carga y notas para sábado y domingo. Usa alternativas marcadas como `VS`, imágenes demostrativas de movilidad/estiramiento, nombre del socio, firma/nombre del coach y marca del gimnasio.

La plataforma debe poder representar como mínimo:

- nombre del socio y coach;
- título, objetivo, nivel y vigencia de la rutina;
- días o sesiones ordenadas;
- nombre o enfoque de cada sesión, por ejemplo `Pecho y hombro`;
- ejercicios ordenados por sesión;
- series, repeticiones y texto de prescripción flexible;
- carga, intensidad o progresión opcional;
- descanso, tempo, duración o distancia opcionales;
- superseries, circuitos o agrupaciones;
- alternativas excluyentes o equivalentes, por ejemplo `VS`;
- instrucciones generales y notas por día o ejercicio;
- calentamiento, movilidad, core, cardio, estiramiento y actividades opcionales;
- marca del gimnasio y fecha de generación en el PDF.
- nombre visible o firma del coach y recursos visuales opcionales por ejercicio/bloque.

El modelo no debe limitar la captura a una única notación. Expresiones como `3x12`, `3x12, 10, 8 subiendo`, `3x24 pasos`, `3x8 a 10 pesadas`, `3 series` o indicaciones narrativas deben conservarse fielmente.

## 4. Actores y permisos

### Coach

- consultar su perfil y sucursales habilitadas;
- consultar el catálogo de ejercicios de su organización;
- proponer o crear ejercicios personalizados según permiso;
- crear, editar, duplicar y publicar sus rutinas;
- asignar rutinas a socios elegibles dentro de su alcance;
- consultar sus asignaciones;
- descargar PDF de sus rutinas asignadas.

### Dueño

- administrar coaches de toda la organización;
- consultar, revisar y descargar rutinas;
- consultar métricas por coach, sucursal, periodo y socio;
- administrar el catálogo institucional de ejercicios;
- corregir, archivar o fusionar duplicados sin romper históricos.

### Gerente

- mismas consultas y métricas, limitadas a sus sucursales;
- administrar coaches y ejercicios únicamente cuando sus permisos lo permitan.

### Recepción

- sin edición de rutinas por defecto;
- consulta o descarga solamente si se concede permiso explícito.

## 5. Alcance funcional

### 5.1 Perfil de coach

El coach debe vincularse con una persona/usuario autenticable y no duplicar datos personales. Se registran:

- `organizationId`;
- persona/usuario relacionado;
- sucursales autorizadas;
- especialidades;
- biografía corta opcional;
- estado `ACTIVE`, `INACTIVE` o `ARCHIVED`;
- fechas de alta y actualización.

Un coach inactivo no puede crear nuevas asignaciones, pero sus históricos permanecen visibles.

### 5.2 Catálogo de ejercicios

El sistema debe incluir un catálogo inicial amplio y permitir ampliarlo sin despliegues de código.

Cada ejercicio debe contemplar:

- nombre canónico;
- aliases o nombres alternativos;
- descripción e instrucciones;
- grupo muscular principal y secundarios;
- patrón de movimiento;
- tipo: fuerza, movilidad, cardio, core, calentamiento, estiramiento u otro;
- equipamiento requerido;
- nivel sugerido;
- unilateral/bilateral;
- medios opcionales: imagen, miniatura o video/URL;
- contraindicaciones o advertencias opcionales;
- origen: `SYSTEM`, `ORGANIZATION` o `COACH`;
- creador y estado de revisión;
- estado `ACTIVE` o `ARCHIVED`.

Reglas:

- los ejercicios `SYSTEM` son reutilizables y no se eliminan físicamente;
- los ejercicios creados por una organización no son visibles para otras;
- un coach puede crear una opción faltante durante la edición de una rutina;
- deben detectarse duplicados por nombre normalizado y aliases;
- archivar un ejercicio no rompe rutinas o asignaciones históricas;
- las rutinas conservan una instantánea mínima del nombre e indicaciones usadas.

### 5.3 Constructor de rutinas

Una rutina es una plantilla reutilizable del coach o de la organización. Debe soportar:

- título, objetivo, nivel, descripción y duración sugerida;
- estado `DRAFT`, `PUBLISHED` o `ARCHIVED`;
- duplicado de una rutina existente;
- días/sesiones reordenables;
- ejercicios reordenables por arrastrar y soltar;
- bloques de calentamiento, trabajo principal, accesorios, cardio y vuelta a la calma;
- superseries, triseries, circuitos y ejercicios alternativos;
- notas generales y por sesión;
- campos estructurados y un campo de prescripción libre.

Campos de una prescripción de ejercicio:

- `exerciseId` opcional si se usa un ejercicio ad hoc;
- nombre instantáneo;
- número de series opcional;
- repeticiones mínimas/máximas o texto libre;
- carga/intensidad: peso, porcentaje, RPE, RIR o texto libre;
- descanso en segundos opcional;
- tempo opcional;
- duración o distancia opcional;
- lados/pasos opcionales;
- notas técnicas;
- indicador opcional;
- posición dentro de la sesión y bloque.

### 5.4 Versionado

- editar una rutina en borrador modifica el borrador actual;
- publicar genera una versión inmutable numerada;
- modificar una rutina publicada crea un nuevo borrador basado en la última versión;
- toda asignación apunta a `workoutVersionId`;
- el PDF se genera desde la versión asignada;
- no existe actualización masiva silenciosa de asignaciones previas.

### 5.5 Asignación a socios

Una asignación debe registrar:

- organización y sucursal;
- coach responsable;
- socio;
- rutina y versión;
- fecha de asignación;
- inicio y fin sugeridos opcionales;
- estado `ACTIVE`, `COMPLETED`, `CANCELLED` o `REPLACED`;
- notas privadas del coach;
- observaciones imprimibles;
- usuario que ejecutó la asignación;
- sello de auditoría.

Reglas:

- el socio debe pertenecer a la misma organización;
- debe respetarse el alcance de sucursal del coach;
- solo versiones publicadas pueden asignarse;
- una nueva asignación puede reemplazar otra activa, conservando ambas;
- asignar es el hecho que alimenta la métrica, no crear o imprimir;
- reintentos con la misma llave de idempotencia no duplican métricas.

### 5.6 PDF imprimible

El PDF debe ser legible en impresión A4/carta y contener:

- logotipo y nombre comercial del gimnasio;
- datos de socio y coach;
- título, objetivo, nivel, periodo y versión;
- sesiones/días en orden;
- tabla o bloques de ejercicios con prescripción e indicaciones;
- notas generales, movilidad, calentamiento y estiramiento;
- fecha y folio de asignación;
- pie de página indicando que la rutina no sustituye valoración médica cuando aplique.

La generación debe ser determinista: descargar de nuevo la misma asignación reproduce el mismo contenido funcional aunque cambie la plantilla visual futura. Puede regenerarse visualmente, pero siempre desde la instantánea/version asignada.

## 6. Métricas para dueño y gerencia

### 6.1 Métrica principal

`rutinas_asignadas` = número de asignaciones únicas creadas durante el periodo, agrupadas por coach y semana de negocio en la zona horaria de la organización/sucursal.

No se contabilizan:

- borradores;
- versiones publicadas sin asignación;
- descargas de PDF;
- asignaciones canceladas por error técnico cuando exista reversión auditada;
- reintentos idempotentes.

### 6.2 Indicadores

- rutinas asignadas esta semana y semana anterior;
- variación absoluta y porcentual;
- socios únicos atendidos;
- socios con primera rutina;
- socios con reasignación o actualización;
- promedio de asignaciones por coach activo;
- distribución por sucursal;
- ranking informativo por coach;
- detalle coach → fecha → socio → rutina → versión;
- coaches activos sin asignaciones en el periodo.

Estas métricas son de desempeño operativo y acompañamiento. No deben presentarse como ventas ni ligar automáticamente compensación económica.

### 6.3 Filtros y exportación

- semana, rango de fechas y comparación;
- sucursal;
- coach;
- socio;
- estado de asignación;
- exportación CSV del detalle;
- acceso al PDF desde el detalle, sujeto a permisos.

## 7. Modelo de datos propuesto

Entidades/tablas mínimas:

- `coach_profile`;
- `coach_branch`;
- `exercise`;
- `exercise_alias`;
- `exercise_muscle_group`;
- `exercise_equipment`;
- `workout`;
- `workout_version`;
- `workout_session`;
- `workout_session_block`;
- `workout_exercise`;
- `workout_assignment`;
- `workout_assignment_event` o auditoría equivalente.

Todas las tablas de negocio incluyen `organization_id`; las dependientes de operación física incluyen `branch_id` cuando aplique. Se deben agregar índices para periodo/coach, socio, versión, estado y búsqueda normalizada de ejercicios.

El contenido no debe persistirse únicamente como un JSON opaco. Se permite una instantánea JSON para exportación y compatibilidad futura, pero los datos que requieran búsqueda, métricas, validación y edición deben estar normalizados.

## 8. API propuesta

Prefijo sugerido: `/api/v1`.

### Coaches

- `GET /coaches`
- `GET /coaches/{coachId}`
- `POST /coaches`
- `PUT /coaches/{coachId}`
- `PATCH /coaches/{coachId}/status`

### Ejercicios

- `GET /exercises?query=&muscleGroup=&equipment=&type=&page=&size=`
- `GET /exercises/{exerciseId}`
- `POST /exercises`
- `PUT /exercises/{exerciseId}`
- `PATCH /exercises/{exerciseId}/status`
- `POST /exercises/{exerciseId}/aliases`

### Rutinas

- `GET /workouts`
- `GET /workouts/{workoutId}`
- `POST /workouts`
- `PUT /workouts/{workoutId}`
- `POST /workouts/{workoutId}/duplicate`
- `POST /workouts/{workoutId}/publish`
- `GET /workouts/{workoutId}/versions`
- `GET /workout-versions/{versionId}`

### Asignaciones y PDF

- `GET /workout-assignments`
- `GET /workout-assignments/{assignmentId}`
- `POST /workout-assignments`
- `PATCH /workout-assignments/{assignmentId}/status`
- `GET /workout-assignments/{assignmentId}/pdf`

### Métricas

- `GET /reports/coaches/workout-assignments/summary`
- `GET /reports/coaches/workout-assignments/detail`
- `GET /reports/coaches/workout-assignments/export`

Todas las escrituras deben aceptar auditoría y las operaciones sensibles deben soportar idempotencia.

## 9. Frontend propuesto

### `/coaches`

Listado, filtros, estado, sucursales, especialidades y acceso a métricas/rutinas por coach.

### `/exercises`

Buscador paginado, filtros, ficha, creación rápida, edición, aliases, archivado y advertencias de duplicidad.

### `/workouts`

Biblioteca de plantillas con estado, coach, objetivo, nivel, última versión, duplicado y publicación.

### `/workouts/new` y `/workouts/{id}/edit`

Constructor por sesiones y bloques, búsqueda rápida de ejercicio, alta inline de ejercicio faltante, ordenamiento y vista previa.

### `/workout-assignments`

Asignación a socio, historial, estado, versión, descarga PDF y reemplazo controlado.

### Dashboard de dueño/gerente

Tarjetas semanales, tendencia, ranking, coaches sin actividad y tabla detallada con filtros.

## 10. Seguridad y permisos sugeridos

- `coach.read`
- `coach.manage`
- `exercise.read`
- `exercise.create`
- `exercise.manage`
- `workout.read`
- `workout.create`
- `workout.manage.own`
- `workout.manage.all`
- `workout.assign`
- `workout.assignment.read.own`
- `workout.assignment.read.all`
- `workout.pdf.download`
- `coach.metrics.read`

La autorización del backend es la fuente de verdad. La UI solo oculta o deshabilita acciones como apoyo visual.

## 11. Auditoría

Registrar como mínimo:

- alta y cambios de coach;
- creación, edición, publicación y archivado de ejercicios;
- creación, edición, publicación y duplicado de rutinas;
- asignación, reemplazo, cancelación y finalización;
- descarga de PDF cuando se requiera trazabilidad;
- actor, fecha, organización, sucursal, entidad, acción y correlación.

## 12. Fuera de alcance

- cobro de rutinas por POS o pasarela;
- registro de efectivo recibido por el coach;
- comisiones y nómina;
- chat coach-socio;
- seguimiento diario realizado por el socio;
- videos propios alojados en la primera iteración;
- recomendaciones automáticas por IA;
- prescripción clínica o expediente médico;
- aplicación móvil del socio.

## 13. Fases de implementación

### Fase 0 — Auditoría del estado actual

- inventariar backend, frontend, migraciones, permisos y pruebas existentes;
- comparar lo existente con este documento;
- identificar contratos incompatibles y datos demo;
- registrar deuda técnica antes de modificar.

### Fase 1 — Dominio y migraciones

- consolidar perfiles de coach;
- implementar catálogo de ejercicios normalizado y seed amplio;
- reforzar rutina, versión, sesiones, bloques y ejercicios;
- implementar asignación inmutable e idempotente.

### Fase 2 — API y seguridad

- casos de uso y puertos de aplicación;
- contratos REST y validaciones;
- permisos por rol y sucursal;
- auditoría y pruebas de aislamiento multi-organización.

### Fase 3 — Experiencia de coach

- catálogo de ejercicios;
- constructor de rutinas;
- creación inline de ejercicios;
- publicación, duplicado y asignación.

### Fase 4 — PDF

- plantilla institucional;
- instantánea estable;
- pruebas de contenido, paginación y caracteres especiales.

### Fase 5 — Métricas

- agregados semanales;
- dashboard y detalle;
- filtros y CSV;
- pruebas de zona horaria, idempotencia y permisos.

### Fase 6 — Cierre

- pruebas backend, frontend, arquitectura y E2E;
- OpenAPI;
- migración sobre datos representativos;
- actualización de estado y deuda técnica.

## 14. Criterios de aceptación del módulo

- Un coach crea una rutina de cinco o más sesiones usando ejercicios existentes y nuevos.
- La prescripción reproduce formatos variables como los del PDF de referencia sin pérdida de información.
- El coach publica una versión y la asigna a un socio elegible.
- La asignación aparece una sola vez en la métrica semanal aunque la solicitud se reintente.
- El dueño consulta cuántas rutinas asignó cada coach y el detalle de socios.
- Un gerente solo observa sucursales autorizadas.
- El PDF muestra la rutina completa y puede imprimirse sin depender de una app del socio.
- Editar y publicar una nueva versión no cambia asignaciones anteriores.
- Un ejercicio archivado sigue visible en históricos.
- Ninguna operación genera ventas, pagos, comisiones o movimientos de caja.
- La suite valida aislamiento por organización, permisos y auditoría.

## 15. Línea base auditada y brechas

Estado encontrado antes de implementar:

- migración `V13` con `coach`, `workout`, `workout_version` en JSON y
  `workout_assignment`;
- API base para crear/listar coaches y rutinas, publicar otra versión, asignar y
  consultar asignaciones por socio;
- `/coaches` permite capturar JSON manual, buscar socio y asignar;
- `GET /reports/coaches` solo entrega totales generales y últimos 30 días;
- los perfiles de coach se sincronizan por coincidencia de identidad, pero el modelo
  conserva datos personales duplicados;
- permisos actuales son amplios: `coach.read/manage`, `workout.read/manage` y
  `report.coaches.read`.

Brechas que esta rama debe cerrar:

- no existe catálogo de ejercicios ni seed amplio;
- no existen sesiones, bloques y prescripciones normalizadas;
- crear una rutina publica inmediatamente y no hay flujo real de borrador/publicación;
- no hay constructor visual, alta inline, duplicado ni filtros paginados;
- la asignación no tiene llave de idempotencia ni estados/reemplazo completos;
- no hay PDF por asignación;
- no hay métrica semanal por coach y socio, comparación, coaches sin actividad ni CSV;
- faltan pruebas específicas del módulo, aislamiento y autorización granular;
- el `content_json` y el textarea manual no son experiencia ni persistencia finales;
- cualquier referencia previa a `WorkoutService` comercial queda anulada: rutinas no
  pertenecen al catálogo/POS.
