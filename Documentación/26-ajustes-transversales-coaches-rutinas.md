# 26. Ajustes transversales de documentación — Coaches y rutinas

Estado: VIGENTE / DECISIÓN DE PRODUCTO  
Fecha: 26 de julio de 2026  
Rama: `feature/coaches-workouts`

## 1. Propósito

Este documento actualiza y aclara transversalmente la documentación maestra de GymOS para el módulo de Coaches y rutinas. Ante cualquier contradicción con los documentos `02-prd.md`, `04-domain-model.md`, `05-data-model.md`, `06-api-contracts.md`, `07-use-cases.md`, `08-frontend-spec.md` o `11-roadmap.md`, prevalecen las decisiones establecidas aquí y en `25-plan-accion-coaches-rutinas.md` hasta que el contenido histórico sea consolidado.

## 2. Corrección del alcance de producto

La mención histórica de `Venta de rutinas`, `Rutinas` como concepto de punto de venta o `WorkoutService` como producto cobrable no forma parte del alcance vigente.

Decisión definitiva para esta etapa:

- GymOS no cobra rutinas.
- GymOS no registra el efectivo que el coach recibe directamente del socio.
- Una asignación de rutina no genera `Sale`, `SaleItem`, `Payment`, ingreso, comisión, cuenta por cobrar ni movimiento de caja.
- Las métricas de Coaches son operativas y de involucramiento con socios, no financieras.
- La monetización futura, si alguna vez se autoriza, deberá diseñarse como una iniciativa independiente y no se infiere del modelo actual.

Por tanto, deben interpretarse como obsoletas para esta etapa:

- la línea `Venta de rutinas` del PRD;
- la inclusión de rutinas dentro del punto de venta;
- el tipo de producto `WorkoutService` cuando implique cobro;
- cualquier dependencia entre asignación de rutina y ventas/pagos.

## 3. Alcance funcional confirmado

El módulo debe permitir:

1. administrar perfiles de coach vinculados a usuarios y personas existentes;
2. consultar un catálogo amplio de ejercicios;
3. crear ejercicios faltantes sin despliegues de código;
4. crear plantillas de rutina estructuradas por días, sesiones, bloques y ejercicios;
5. publicar versiones inmutables;
6. asignar una versión específica a un socio;
7. conservar el historial de asignaciones;
8. descargar una representación PDF imprimible;
9. medir asignaciones por coach, semana, sucursal y socio;
10. exportar el detalle operativo en CSV.

Fuera de alcance inmediato:

- aplicación móvil o portal remoto del socio;
- seguimiento de ejecución diaria por el socio;
- pesos levantados, progreso, fotos o mediciones corporales;
- mensajería coach-socio;
- cobros, comisiones y conciliación de efectivo;
- recomendaciones automáticas clínicas o de entrenamiento.

## 4. Interpretación del ejemplo entregado

La rutina de referencia organiza cinco días por enfoque muscular e incluye movilidad, calentamiento, ejercicios principales y accesorios, estiramiento y notas libres. Sus prescripciones mezclan formatos estructurados y texto flexible, por ejemplo:

- `3x20`;
- `3x12, 10, 8 subiendo`;
- `3x24 pasos`;
- `3x8 a 10 pesadas`;
- `3 series`;
- `core y antebrazo opcional`;
- notas como `Sábado: funcional` y `Domingo: vemos`.

Por esta razón, el constructor no puede imponer un único esquema rígido de series y repeticiones. Debe combinar campos normalizados con una prescripción libre que se conserve literalmente en la versión publicada y en el PDF.

## 5. Ajustes al PRD

La sección `Entrenadores y rutinas` debe entenderse así:

- perfil de coach;
- catálogo extensible de ejercicios;
- rutinas estructuradas;
- versionado inmutable;
- asignación a socios;
- PDF imprimible;
- historial y auditoría;
- métricas semanales por coach y socio.

Se elimina del alcance vigente:

- venta de rutinas;
- cobro dentro de GymOS;
- integración con POS;
- métricas de ingresos por rutina.

El actor Coach puede gestionar rutinas propias, asignaciones y ejercicios según permisos. El dueño puede consultar todo el gimnasio; el gerente se limita a sus sucursales.

## 6. Ajustes al modelo de dominio

### 6.1 CoachProfile

El coach debe referenciar a `Person` y, cuando tenga acceso, a `User`. No debe duplicar nombre, correo o teléfono como fuente primaria.

Responsabilidad:

- representar la función operativa de entrenador dentro de una organización;
- definir sucursales autorizadas, especialidades y estado;
- servir como propietario o responsable de rutinas y asignaciones.

Invariantes:

- pertenece a una organización;
- no puede vincular una persona de otra organización;
- un coach inactivo conserva históricos pero no crea asignaciones nuevas;
- el alcance de sucursal debe validarse en cada asignación.

### 6.2 Exercise

Responsabilidad:

- representar un ejercicio reutilizable, buscable y extensible.

Orígenes:

- `SYSTEM`: catálogo base global;
- `ORGANIZATION`: creado y compartido dentro de un gimnasio;
- `COACH`: creado por un coach y visible según política de la organización.

Invariantes:

- los ejercicios no se eliminan físicamente si están referenciados;
- los nombres se normalizan para advertir duplicados;
- una rutina histórica conserva el nombre instantáneo aunque el ejercicio se archive o renombre;
- un ejercicio de organización nunca se expone a otra organización.

### 6.3 Workout

Responsabilidad:

- plantilla reutilizable de una rutina.

Estados:

- `DRAFT`;
- `PUBLISHED`;
- `ARCHIVED`.

Una publicación produce `WorkoutVersion`. Las asignaciones nunca apuntan solamente a `Workout`; deben apuntar a una versión.

### 6.4 WorkoutVersion

Responsabilidad:

- instantánea inmutable del contenido publicado.

Debe contener sesiones, bloques, ejercicios, prescripciones, notas y datos suficientes para generar el PDF y para una futura aplicación del socio.

### 6.5 WorkoutAssignment

Responsabilidad:

- registrar que un coach asignó una versión de rutina a un socio en una fecha y sucursal determinadas.

Estados:

- `ACTIVE`;
- `COMPLETED`;
- `CANCELLED`;
- `REPLACED`.

Invariantes:

- socio, coach, rutina y sucursal pertenecen a la misma organización;
- solo se asignan versiones publicadas;
- una edición posterior no modifica la asignación existente;
- la creación idempotente no duplica métricas;
- una cancelación conserva auditoría;
- no existe efecto financiero.

## 7. Ajustes al modelo físico de datos

El diseño mínimo debe reemplazar el esquema histórico simplificado por las siguientes tablas o equivalentes:

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
- `workout_assignment_event`.

Reglas físicas:

- todas las entidades incluyen `organization_id`;
- las operaciones locales incluyen `branch_id`;
- las asignaciones incluyen `assigned_at` como timestamp, no solo fecha;
- `workout_assignment` tiene llave de idempotencia única por organización;
- el contenido editable y consultable se normaliza;
- puede existir `snapshot_json` versionado como contrato de exportación, pero no como única fuente de datos;
- ningún campo de rutina referencia obligatoriamente ventas o pagos;
- índices mínimos: coach + assigned_at, branch + assigned_at, client + assigned_at, workout_version, estado, nombre normalizado de ejercicio y aliases.

## 8. Ajustes a contratos API

Los contratos canónicos son:

### Coaches

- `GET /api/v1/coaches`
- `GET /api/v1/coaches/{coachId}`
- `POST /api/v1/coaches`
- `PUT /api/v1/coaches/{coachId}`
- `PATCH /api/v1/coaches/{coachId}/status`

### Ejercicios

- `GET /api/v1/exercises`
- `GET /api/v1/exercises/{exerciseId}`
- `POST /api/v1/exercises`
- `PUT /api/v1/exercises/{exerciseId}`
- `PATCH /api/v1/exercises/{exerciseId}/status`
- `POST /api/v1/exercises/{exerciseId}/aliases`

### Rutinas y versiones

- `GET /api/v1/workouts`
- `GET /api/v1/workouts/{workoutId}`
- `POST /api/v1/workouts`
- `PUT /api/v1/workouts/{workoutId}`
- `POST /api/v1/workouts/{workoutId}/duplicate`
- `POST /api/v1/workouts/{workoutId}/publish`
- `GET /api/v1/workouts/{workoutId}/versions`
- `GET /api/v1/workout-versions/{versionId}`

### Asignaciones y documentos

- `GET /api/v1/workout-assignments`
- `GET /api/v1/workout-assignments/{assignmentId}`
- `POST /api/v1/workout-assignments`
- `PATCH /api/v1/workout-assignments/{assignmentId}/status`
- `GET /api/v1/workout-assignments/{assignmentId}/pdf`

### Reportes

- `GET /api/v1/reports/coaches/workout-assignments/summary`
- `GET /api/v1/reports/coaches/workout-assignments/detail`
- `GET /api/v1/reports/coaches/workout-assignments/export`

Los endpoints no deben invocar ventas, pagos ni POS.

## 9. Ajustes a casos de uso

Casos obligatorios:

- crear ejercicio durante la construcción de una rutina;
- crear rutina borrador;
- editar y reordenar sesiones/bloques/ejercicios;
- publicar nueva versión;
- duplicar rutina;
- asignar versión publicada a socio;
- reemplazar una asignación activa conservando historial;
- descargar PDF;
- consultar métricas semanales y detalle por socio;
- archivar ejercicio, rutina o coach sin romper históricos.

Errores de negocio mínimos:

- `COACH_INACTIVE`;
- `COACH_OUT_OF_BRANCH_SCOPE`;
- `CLIENT_NOT_ELIGIBLE`;
- `EXERCISE_DUPLICATE_SUSPECTED`;
- `WORKOUT_NOT_DRAFT`;
- `WORKOUT_VERSION_NOT_PUBLISHED`;
- `WORKOUT_ASSIGNMENT_DUPLICATE`;
- `WORKOUT_ASSIGNMENT_NOT_FOUND`;
- `WORKOUT_ASSIGNMENT_INVALID_STATUS`;
- `PDF_GENERATION_FAILED`.

## 10. Ajustes al frontend

La experiencia no debe ser un formulario JSON genérico. Debe incluir:

- biblioteca de rutinas;
- constructor visual por días/sesiones;
- bloques reordenables;
- buscador de ejercicios con filtros y alta inline;
- campos rápidos de series, repeticiones, intensidad, descanso y notas;
- prescripción libre para formatos no estándar;
- vista previa antes de publicar;
- selector de socio y versión al asignar;
- historial de asignaciones;
- descarga de PDF;
- dashboard semanal para dueño y gerente.

El catálogo de ejercicios debe ser rápido, paginado y tolerante a aliases, acentos, abreviaturas y nombres coloquiales.

## 11. Ajustes al roadmap

El cierre del módulo se divide en las siguientes fases:

1. auditoría del módulo existente;
2. migración y modelo de dominio;
3. catálogo de ejercicios y seed amplio;
4. constructor y versionado;
5. asignaciones e idempotencia;
6. PDF;
7. métricas y exportación;
8. permisos, pruebas, accesibilidad y documentación final.

No se considera terminado mientras falte cualquiera de estos entregables críticos:

- alta de ejercicio faltante;
- versión inmutable;
- asignación a socio;
- PDF;
- métrica semanal por coach con detalle de socios;
- aislamiento por organización/sucursal;
- pruebas de autorización e idempotencia.

## 12. Criterio de precedencia documental

Orden de autoridad para este módulo:

1. `25-plan-accion-coaches-rutinas.md`;
2. este documento;
3. implementación y migraciones verificadas de la rama;
4. documentos maestros históricos.

Al concluir la implementación, el estado real debe registrarse en un documento de cierre y reconciliarse nuevamente con los documentos maestros.