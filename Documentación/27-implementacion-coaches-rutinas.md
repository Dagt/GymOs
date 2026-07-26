# 27. Avance de implementación — Coaches y rutinas

Estado: IMPLEMENTACIÓN PARCIAL / NÚCLEO FUNCIONAL
Fecha de corte: 26 de julio de 2026
Rama: `feature/coaches-workouts`
Documento rector: `25-plan-accion-coaches-rutinas.md`

## 1. Propósito y criterio de lectura

Este documento registra el estado real del código después del primer corte de
implementación. No sustituye el alcance objetivo del documento `25` ni debe
interpretarse como cierre del módulo.

Estados utilizados:

- `IMPLEMENTADO`: existe en backend y frontend cuando ambos son necesarios y cuenta
  con validación proporcional.
- `PARCIAL`: existe una base técnica, pero falta comportamiento, interfaz, seguridad
  o pruebas para cumplir el criterio completo.
- `PENDIENTE`: todavía no existe un flujo utilizable.

Estado global: `PARCIAL`. El núcleo permite crear, publicar y asignar una rutina,
descargar un PDF básico y consultar métricas elementales, pero todavía no satisface
todos los criterios de aceptación del plan rector.

## 2. Alcance implementado

### 2.1 Datos y migración

`V45__complete_coaches_workouts.sql` incorpora:

- catálogo global y por organización en `exercise`;
- estructura para aliases en `exercise_alias`;
- sesiones en `workout_session`;
- bloques en `workout_session_block`;
- prescripciones e instantáneas en `workout_exercise`;
- idempotencia, notas imprimibles y estados en `workout_assignment`;
- índices para búsquedas y métricas;
- permisos de ejercicios, asignación, PDF y métricas;
- más de 60 ejercicios iniciales.

El JSON versionado se conserva como instantánea compatible y las versiones nuevas
se proyectan al modelo normalizado.

### 2.2 Backend

Implementado:

- listado y alta de ejercicios;
- consulta y edición individual de ejercicios propios;
- alta de aliases normalizados con protección de duplicados;
- búsqueda paginada básica por nombre, músculo, equipo y tipo;
- advertencia de nombre duplicado;
- borrador, publicación y duplicado de rutina;
- edición que reutiliza el borrador vigente y crea una versión nueva después de publicar;
- consulta de detalle de rutina, versiones y asignaciones;
- reemplazo transaccional de asignaciones en backend;
- asignación exclusiva de versiones publicadas;
- llave y hash de idempotencia;
- estados de asignación;
- consulta agregada por rango;
- detalle incluido en el reporte;
- exportación CSV;
- PDF básico desde la versión asignada;
- auditoría de las operaciones principales.

### 2.3 Frontend

`/coaches` contiene:

1. constructor por sesiones y ejercicios;
2. catálogo y alta rápida;
3. asignación a socios e historial;
4. métricas por periodo y coach;
5. descarga de PDF y CSV;
6. guardado de borrador, publicación y duplicado.
7. carga de una rutina existente en el constructor y edición de su borrador.

### 2.4 Verificación ejecutada

- migraciones Flyway sobre PostgreSQL Testcontainers;
- suite backend completa;
- pruebas unitarias de catálogo y métricas;
- 28 pruebas frontend existentes;
- typecheck;
- lint;
- build de producción de Next.js;
- validación de Docker Compose;
- `git diff --check`.

No se ejecutó ni agregó todavía una suite E2E específica del flujo completo de
Coaches y rutinas.

## 3. Brechas de prioridad alta

Estas brechas impiden declarar el módulo terminado.

### P1-01 Edición real de rutinas

Estado: `PARCIAL`.

Implementado:

- `PUT /workouts/{workoutId}`;
- carga de una rutina existente en el constructor;
- actualización del borrador vigente;
- creación de borrador nuevo cuando la última versión ya fue publicada.

Falta:

- control de concurrencia o versión optimista;
- ruta dedicada `/workouts/{id}/edit` si se decide separar la experiencia.

Criterio de terminado: un coach abre una rutina existente, modifica el borrador,
publica una versión nueva y las asignaciones previas permanecen inmutables.

### P1-02 Administración completa de ejercicios y aliases

Estado: `PARCIAL`.

Existen alta, consulta, edición individual, creación de aliases desde la interfaz,
normalización, búsqueda por alias y protección de ejercicios SYSTEM. Falta:

- interfaz para consultar y eliminar aliases;
- interfaz para editar instrucciones, músculo, equipo, nivel, medios y advertencias;
- fusión administrativa de duplicados.

Criterio de terminado: dueño/gerencia administran la ficha completa y un coach
encuentra el mismo ejercicio por cualquiera de sus nombres aceptados.

### P1-03 Constructor completo y reordenamiento

Estado: `PARCIAL`.

Falta:

- múltiples bloques editables por sesión;
- agregar, renombrar, eliminar y reordenar bloques;
- reordenar sesiones y ejercicios mediante drag-and-drop;
- alternativa accesible con botones o teclado;
- controles estructurados para calentamiento, trabajo principal, accesorios, cardio
  y vuelta a la calma.

Criterio de terminado: se reproduce la rutina de referencia sin depender del orden
de captura ni de un único bloque principal.

### P1-04 Prescripción estructurada completa

Estado: `PARCIAL`.

La prescripción libre funciona, pero faltan controles propios para:

- series y repeticiones mínimas/máximas;
- carga, porcentaje, RPE y RIR;
- descanso;
- tempo;
- duración y distancia;
- lados y pasos;
- superseries, triseries y circuitos;
- alternativas `VS`;
- ejercicios opcionales;
- notas técnicas separadas.

Criterio de terminado: el coach puede usar campos estructurados o texto libre sin
perder expresiones como `3x12, 10, 8 subiendo`.

### P1-05 Reemplazo y cancelación de asignaciones

Estado: `PARCIAL`.

Existe reemplazo transaccional en backend, vínculo
`replaced_assignment_id`, estado `REPLACED` y auditoría. Falta:

- acción de cancelación desde frontend;
- acción de reemplazo desde frontend;
- confirmaciones y motivos obligatorios;
- actualización local de la pantalla sin recarga completa.

Criterio de terminado: una rutina activa se reemplaza o cancela sin borrar el
histórico ni duplicar la métrica incorrectamente.

### P1-06 Contratos de detalle

Estado: `PARCIAL`.

Implementado:

- `GET /workout-assignments/{assignmentId}`;
- `GET /workout-versions/{versionId}`;

Falta:

- contrato detallado normalizado de sesión, bloque y prescripción;
- uso de esos contratos en edición, detalle y futura app del socio.

Criterio de terminado: cualquier asignación puede reconstruirse mediante API sin
leer directamente JSON interno.

### P1-07 Autorización por propiedad y alcance

Estado: `PARCIAL`.

Los permisos nuevos coexisten con `workout.manage`, que sigue siendo amplio. Falta:

- distinguir gestión propia y global;
- `workout.manage.own` y `workout.manage.all` o una política equivalente;
- limitar a Coach a sus rutinas y asignaciones;
- limitar dueño/gerente por organización y sucursales autorizadas;
- pruebas negativas de UUID, organización, sucursal y propiedad;
- retirar permisos heredados que amplíen acceso por accidente.

Criterio de terminado: modificar URLs o UUID no permite leer ni cambiar recursos
fuera del alcance efectivo del usuario.

### P1-08 Pruebas E2E y OpenAPI

Estado: `PENDIENTE`.

Falta:

- E2E Coach: crear borrador, publicar, asignar y descargar PDF;
- E2E Dueño: consultar métrica y socio;
- E2E Gerente: rechazo de otra sucursal;
- E2E de inmutabilidad histórica;
- E2E que confirme ausencia en POS/caja;
- OpenAPI actualizado con payloads, respuestas, filtros, errores y archivos;
- trazabilidad automatizada de criterios de aceptación.

Criterio de terminado: contratos documentados y flujos críticos reproducibles en CI.

## 4. Brechas de prioridad media

### P2-01 Comparación semanal

Estado: `PARCIAL`.

Existe consulta por rango, pero falta calcular y mostrar:

- semana actual;
- semana anterior;
- variación absoluta;
- variación porcentual;
- tratamiento explícito cuando la semana anterior es cero.

### P2-02 Filtros y navegación de reportes

Estado: `PARCIAL`.

El backend acepta parte de los filtros, pero la interfaz todavía necesita:

- filtro por coach;
- socio;
- sucursal;
- estado;
- semana predefinida y rango personalizado;
- filtros persistidos en URL;
- paginación del detalle;
- descarga CSV con los mismos filtros visibles.

### P2-03 Indicadores de acompañamiento

Estado: `PARCIAL`.

Falta presentar y validar claramente:

- primera rutina;
- reasignación o actualización;
- promedio por coach activo;
- distribución por sucursal;
- ranking informativo;
- coaches sin actividad;
- detalle coach → fecha → socio → rutina → versión.

### P2-04 Perfil de coach multi-sucursal

Estado: `PARCIAL`.

El modelo heredado mantiene perfiles por sucursal. Falta:

- un perfil único vinculado a `user_account/person`;
- relación explícita `coach_branch`;
- especialidades y estado compartidos sin duplicar datos personales;
- cambio de sucursal sin duplicar coach;
- consulta y administración multi-sucursal.

### P2-05 Ciclo de vida del catálogo

Estado: `PARCIAL`.

El backend puede cambiar estado, pero falta en frontend:

- archivar y reactivar;
- confirmar la acción;
- mostrar dónde se usa el ejercicio;
- impedir selección nueva de archivados;
- conservar visualización histórica;
- revisión/aprobación de ejercicios creados por coaches.

### P2-06 Paginación y búsqueda completa

Estado: `PARCIAL`.

Falta:

- controles de página y tamaño;
- debounce;
- búsqueda por aliases;
- filtros combinables;
- estado en URL;
- estados de carga, vacío y error por consulta;
- evitar cargar un conjunto fijo como sustituto de paginación.

### P2-07 Medios e indicaciones visuales

Estado: `PARCIAL`.

El modelo permite `mediaUrl`, pero falta:

- captura y edición desde frontend;
- validación y política de URLs;
- miniatura;
- imagen o video de referencia;
- tratamiento de medios archivados o inaccesibles.

## 5. Brechas específicas del PDF

Estado general del PDF: `PARCIAL`. El archivo básico es descargable y usa la versión
asignada, pero aún no alcanza la calidad definida en el plan.

### PDF-01 Identidad visual

Falta:

- logotipo real del gimnasio;
- colores institucionales;
- nombre comercial y sucursal;
- nombre o firma visible del coach;
- plantilla visual consistente con GymOS.

### PDF-02 Contenido y estructura

Falta:

- tablas o bloques visuales por sesión;
- separación clara entre ejercicio y prescripción;
- representación visual de superseries, circuitos, `VS` y opcionales;
- imágenes de movilidad y estiramiento;
- advertencias y observaciones por ejercicio;
- folio legible de asignación;
- pie médico configurable.

### PDF-03 Reproducibilidad histórica

Falta:

- versión explícita de plantilla;
- snapshot de datos visuales necesarios;
- política para cambios de logo o imágenes;
- prueba de que una descarga futura conserva el mismo contenido funcional;
- definición de si el binario se almacena o se regenera determinísticamente.

### PDF-04 Calidad y pruebas

Falta:

- pruebas de múltiples páginas;
- caracteres españoles y símbolos de entrenamiento;
- rutinas largas;
- tablas que no se corten;
- encabezados y pies repetidos;
- validación en tamaño carta y A4;
- renderizado a imagen y revisión visual automatizada o documentada;
- prueba de descarga desde frontend.

Criterio de terminado del PDF: la rutina de referencia puede reproducirse completa,
imprimirse sin recortes y descargarse nuevamente desde la misma asignación sin
depender del estado actual de la plantilla.

## 6. Contratos del plan todavía no implementados

- `GET /exercises/{exerciseId}`
- `PUT /exercises/{exerciseId}`
- `POST /exercises/{exerciseId}/aliases`
- `PUT /workouts/{workoutId}`
- `GET /workout-versions/{versionId}`
- `GET /workout-assignments/{assignmentId}`
- operación explícita de reemplazo de asignación
- endpoint de detalle de métricas separado cuando el volumen requiera paginación

## 7. Condición para cambiar el estado a IMPLEMENTADO

El estado global solo puede cambiar a `IMPLEMENTADO` cuando:

1. todas las brechas P1 estén cerradas;
2. las brechas P2 acordadas para V1 estén cerradas o explícitamente reubicadas fuera
   de alcance mediante decisión de producto;
3. el PDF cumpla su criterio de terminado;
4. OpenAPI refleje los contratos reales;
5. las pruebas E2E críticas pasen;
6. seguridad por propiedad, organización y sucursal esté probada;
7. el documento `25` tenga trazabilidad criterio por criterio;
8. no exista vínculo de rutinas con POS, ventas, pagos, comisiones o caja.

## 8. Decisiones que permanecen cerradas

- GymOS no cobra rutinas.
- No se registra el efectivo recibido directamente por el coach.
- Asignar una rutina no crea venta, pago, comisión, ingreso ni movimiento de caja.
- El PDF es el medio de entrega física de V1.
- La app remota del socio continúa fuera de alcance, aunque los contratos deben quedar
  preparados para consumir una versión asignada.
