# Coaches, ejercicios y rutinas

Estado: `ESTABILIZADO`
Rama de cierre: `feature/coaches-workouts`
Código verificado: `84a5fd3`
Corte: 26 de julio de 2026

## Propósito y alcance V1

GymOS permite que cada coach cree, publique, asigne e imprima rutinas, mientras
dueño y gerencia miden el acompañamiento a socios. La plataforma no cobra estas
rutinas, no registra comisiones y no genera ventas, pagos ni movimientos de caja.
La consulta remota por el socio pertenece a una aplicación futura.

## Implementación vigente

### Catálogo de ejercicios

- catálogo SYSTEM inmutable con más de 60 ejercicios;
- búsqueda paginada por nombre, alias, músculo, equipo y tipo;
- ejercicios propios de organización o coach con descripción, instrucciones,
  músculos, patrón, equipo, nivel, lateralidad y advertencias;
- alta y edición por propietario, archivo administrativo y prevención de
  duplicados por nombre o alias normalizado;
- consulta, alta y eliminación de aliases;
- carga, lectura, reemplazo y eliminación de JPG, PNG, WebP, GIF o MP4 de hasta
  8 MB mediante almacenamiento de objetos;
- limpieza del archivo sustituido y compensación si falla la persistencia.

### Constructor y versiones

- biblioteca de rutinas propias o globales según permiso;
- sesiones y bloques múltiples reordenables con controles accesibles;
- ejercicios libres o seleccionados del catálogo;
- series, repeticiones, carga, descanso, tempo, duración, distancia,
  agrupación, opcionalidad, notas y prescripción libre;
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
- detalle paginado y filtros visibles por fechas y coach;
- CSV completo y acotado a 10,000 filas para evitar exportaciones accidentales
  sin filtro;
- alcance propio para coach y global de sucursal para dueño/gerencia.

## Seguridad e invariantes

- `workout.read.own|all` y `workout.manage.own|all` separan propiedad de alcance
  global;
- el perfil de coach se vincula a `user_account_id`; un coach no puede crear,
  leer, modificar, asignar, reemplazar ni imprimir rutinas de otro coach;
- organización y sucursal se filtran en servicio y repositorio;
- los ejercicios SYSTEM son de sólo lectura y un coach sólo modifica los que
  creó;
- editar una rutina nunca cambia el contenido de una asignación histórica;
- `REPLACED` sólo surge del flujo de reemplazo, no de un cambio manual de estado.

## Evidencia de cierre

- backend: `gradlew clean test --no-daemon`, exitoso;
- frontend: 31 pruebas, typecheck, lint y build de producción, exitosos;
- E2E Playwright: 3 recorridos exitosos para constructor/publicación,
  catálogo/aliases/medios y cancelación/reemplazo/métricas;
- pruebas dedicadas de propiedad, permisos HTTP, comparación de periodos,
  PDF y ciclo de medios;
- `git diff --check`, exitoso.

Los antiguos `COA-001` a `COA-007` y `COA-009` quedaron resueltos en
`84a5fd3` y se conservan en la sección de cerradas del documento 28. Los riesgos
transversales de despliegue, migración PostgreSQL real, observabilidad y
almacenamiento se siguen administrando como `DB-001` y `OPS-001`; no son
funcionalidad pendiente específica de Coaches.
