# 27. Implementación — Coaches y rutinas

Estado: IMPLEMENTADO
Fecha: 26 de julio de 2026
Rama: `feature/coaches-workouts`
Documento rector: `25-plan-accion-coaches-rutinas.md`

## Resultado

El módulo dejó de ser una captura manual de JSON y quedó convertido en una sección
operable para coaches, dueño y gerencia. Permite mantener ejercicios, construir
rutinas estructuradas, guardar borradores, publicar versiones, duplicar plantillas,
asignarlas a socios, descargar el PDF y consultar desempeño por periodo.

Las rutinas continúan completamente separadas de catálogo comercial, POS, ventas,
pagos, comisiones e ingresos.

## Migración V45

`V45__complete_coaches_workouts.sql` incorpora:

- catálogo global y por organización en `exercise`;
- aliases en `exercise_alias`;
- sesiones normalizadas en `workout_session`;
- bloques en `workout_session_block`;
- prescripciones e instantáneas en `workout_exercise`;
- campos de idempotencia, notas imprimibles, reemplazo y cierre en
  `workout_assignment`;
- índices para catálogo, asignaciones y métricas;
- permisos de ejercicios, asignación, PDF y métricas;
- catálogo inicial de más de 60 ejercicios de fuerza, movilidad, cardio, core,
  calentamiento y estiramiento.

La migración conserva las tablas y datos de Hito 5. El JSON versionado permanece
como instantánea compatible y cada versión nueva también se proyecta al modelo
normalizado.

## Backend

Contratos implementados o conservados:

- `GET|POST /api/v1/coaches`
- `GET|POST /api/v1/exercises`
- `PATCH /api/v1/exercises/{exerciseId}/status`
- `GET|POST /api/v1/workouts`
- `GET /api/v1/workouts/{workoutId}`
- `POST /api/v1/workouts/{workoutId}/versions`
- `POST /api/v1/workouts/{workoutId}/publish`
- `POST /api/v1/workouts/{workoutId}/duplicate`
- `POST /api/v1/workouts/{workoutId}/assign` por compatibilidad
- `POST /api/v1/workout-assignments`
- `PATCH /api/v1/workout-assignments/{assignmentId}/status`
- `GET /api/v1/workout-assignments`
- `GET /api/v1/workout-assignments/{assignmentId}/pdf`
- `GET /api/v1/reports/coaches/workout-assignments/summary`
- `GET /api/v1/reports/coaches/workout-assignments/export`

Reglas implementadas:

- aislamiento por organización y sucursal;
- detección de ejercicios duplicados por nombre normalizado;
- ejercicios de sistema inmutables y ejercicios propios aislados;
- versiones en borrador y publicación explícita;
- asignación exclusiva de versiones publicadas;
- llave y hash de idempotencia para evitar doble asignación;
- estados `ACTIVE`, `COMPLETED`, `CANCELLED` y `REPLACED`;
- auditoría de catálogo, publicación, asignación y cambios de estado;
- métricas por coach incluyendo coaches activos sin asignaciones;
- detalle por socio y exportación CSV;
- PDF generado siempre desde la versión de la asignación.

## Frontend

`/coaches` se reorganizó en cuatro superficies:

1. **Constructor:** datos generales, días/sesiones, enfoque, notas, ejercicios
   ordenados, prescripción libre, catálogo, borrador, publicación y duplicado.
2. **Ejercicios:** búsqueda y alta inline con músculo, equipo, tipo e instrucciones.
3. **Asignaciones:** búsqueda de socio, selección de versión publicada, vigencia,
   notas privadas/imprimibles, historial, PDF y finalización.
4. **Métricas:** periodo, rutinas asignadas, socios únicos, coaches activos,
   coaches sin actividad, desglose por coach y CSV.

El constructor conserva expresiones flexibles como `3x12, 10, 8 subiendo`,
alternativas `VS`, opcionales y notas narrativas.

## Seguridad

Permisos nuevos:

- `exercise.read`
- `exercise.create`
- `exercise.manage`
- `workout.assign`
- `workout.pdf.download`
- `coach.metrics.read`

Coach obtiene lectura/alta de ejercicios, asignación y PDF. Dueño, gerente y
administración reciben gestión institucional y métricas según su contexto. El
backend sigue siendo la fuente de verdad para organización, sucursal y permisos.

## Verificación

- migraciones Flyway sobre PostgreSQL Testcontainers;
- suite backend completa;
- pruebas unitarias de normalización, duplicidad y coaches sin actividad;
- pruebas frontend;
- lint y typecheck;
- build de producción de Next.js;
- `git diff --check`.

## Decisiones conservadas

- no existe cobro de rutinas en GymOS;
- no se registra el efectivo recibido por el coach;
- asignar no crea venta ni pago;
- el PDF permite entrega física;
- la futura app del socio podrá consumir la versión asignada sin modificar
  históricos, pero no forma parte de esta implementación.
