# Coaches, ejercicios y rutinas

Estado: `PARCIAL`
Rama activa: `feature/coaches-workouts`

## Propósito

Permitir que coaches creen y asignen rutinas; el gimnasio mide acompañamiento y
desempeño. No se cobran rutinas dentro de GymOS. La futura app del socio queda
fuera del alcance actual.

## Implementado

- coaches por sucursal;
- catálogo SYSTEM y propio con más de 60 ejercicios;
- búsqueda por nombre, alias, músculo, equipo y tipo;
- alta, edición y alias de ejercicios propios; SYSTEM es inmutable;
- rutinas con sesiones, bloques, ejercicios y prescripción estructurada;
- borrador editable, publicación, duplicado e historial de versiones;
- asignación idempotente de versiones publicadas;
- completar, cancelar y reemplazar asignaciones en backend;
- métricas por periodo/coach/socio y exportación CSV;
- PDF básico reproducible desde la versión asignada;
- constructor, edición, catálogo, asignación y métricas en `/coaches`.

## Invariantes

- una asignación apunta a una versión publicada e inmutable;
- editar una rutina no modifica asignaciones históricas;
- los ejercicios SYSTEM son de sólo lectura;
- no existe integración con POS, caja, ventas o comisiones;
- toda lectura/escritura respeta organización y sucursal.

## Pendiente prioritario

- permisos propios/globales (`COA-001`);
- edición/archivo y consulta/eliminación de alias en UI (`COA-002`);
- CRUD y reordenamiento accesible de bloques (`COA-003`);
- UX de reemplazo y motivos (`COA-004`);
- pruebas dedicadas y E2E (`COA-005`);
- PDF final con identidad y QA (`COA-006`);
- comparación semanal y filtros avanzados (`COA-007`);
- carga de medios (`COA-009`).

## Criterio de cierre

Coach crea/edita/publica/asigna/imprime; dueño compara semanas y socios; gerente
queda limitado a sucursales; las asignaciones históricas son inmutables; E2E y
PDF quedan validados.

Última validación: backend completo; frontend 28 pruebas, typecheck, lint y build,
26-jul-2026.
