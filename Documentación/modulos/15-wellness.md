# Wellness y profesionales

Estado: `ESTABILIZADO`
Última revisión en el árbol integrado: 21 de agosto de 2026

## Alcance vigente

La implementación cerrada originalmente en `feature/wellness-services` fue
reaplicada sobre la rama actual mediante `922c0ce` y estabilizada en `4eac4d5`.
No se usa la pantalla básica que quedó después del revert `08ec1a8`.

### Directorio y servicios

- profesionales internos (`STAFF`) y externos (`EXTERNAL`) por organización y
  sucursal;
- la modalidad se persiste en `professional.engagement_type`; no se infiere a
  partir de la ubicación del consultorio;
- nombre visible, especialidad, correo, teléfono, biografía, ubicación de
  consultorio y estado;
- alta, edición y baja lógica de profesionales;
- catálogo de servicios por profesional con nombre, tipo, duración y precio;
- alta y edición de servicios;
- disponibilidad semanal por día y rango horario.

### Agenda y citas

- vistas mensual, semanal y diaria;
- filtros por fecha, profesional y estado;
- búsqueda y selección de socio;
- selección de profesional, servicio y horario disponible;
- los internos se reservan únicamente sobre su disponibilidad semanal;
- los externos aceptan una fecha y hora solicitadas sin requerir disponibilidad
  interna, conservan su lugar externo de atención y nacen como `REQUESTED`;
- citas `REQUESTED`, `CONFIRMED`, `COMPLETED`, `CANCELLED` o `REJECTED`;
- confirmación directa de citas internas;
- coordinación de fecha, hora y notas antes de confirmar una atención externa;
- cancelación y finalización conservando la trazabilidad;
- acceso directo desde Recepción.

### Roles

- dueño: consulta y administración con `professional.read|manage`;
- gerente: lectura y administración del módulo;
- recepción: lectura, agenda y operación de citas según sus permisos de
  reservas;
- el menú lateral muestra Wellness para dueño, gerente y recepción cuando el
  token contiene `professional.read` o `booking.read`.

## Persistencia y API

- `V50__wellness_appointments_and_office_location.sql` agrega ubicación y
  `wellness_appointment` de forma idempotente;
- `V51__wellness_role_permissions.sql` repara permisos existentes de gerente y
  recepción;
- `V59__professional_engagement_type.sql` agrega la modalidad explícita y
  clasifica los registros históricos con disponibilidad activa como `STAFF` y
  el resto como `EXTERNAL`;
- `/professionals/**` administra directorio, servicios y disponibilidad;
- `/wellness/**` administra disponibilidad calculada y ciclo de citas;
- organización y sucursal se toman del principal autenticado, no del payload.

Reglas de dominio:

- `engagementType` sólo admite `STAFF` o `EXTERNAL`;
- un profesional `EXTERNAL` puede tener `officeLocation`, pero no puede crear
  disponibilidad interna;
- la API decide el estado inicial de la cita por `engagementType`, nunca por la
  presencia o ausencia de `officeLocation`.

## Evidencia

- `gradlew clean test --no-daemon`, exitoso con PostgreSQL y migraciones;
- `WellnessServiceTests`, exitosas;
- frontend: 32 pruebas, typecheck, lint y build de producción, exitosos;
- pruebas de navegación verifican Wellness para gerente y recepción.

## Deuda relacionada

El aislamiento de la rama quedó resuelto como `WEL-001`. Continúan aplicando
`GEN-001`, `GEN-004`, `DB-001` y `OPS-001`: falta una matriz negativa
multi-tenant homogénea, E2E dedicado de Wellness en CI, prueba de actualización
desde una base histórica real y observabilidad de producción.
