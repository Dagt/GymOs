# Plan de simplificación y refactorización

## Rama

`feature/code-simplification-rebasion-and-refactor`

## Principio rector

Cada cambio debe conservar exactamente el comportamiento observable, contratos HTTP, permisos, reglas de negocio, multi-tenancy y diseño aprobado.

No se mezclará refactorización con funcionalidad nueva.

## Puertas de calidad

Antes y después de cada bloque:

```bash
cd backend
./gradlew clean test --no-daemon

cd ../frontend
npm ci
npm run lint
npm run typecheck
npm run build

cd ..
docker compose -f infra/docker-compose.yml config
docker compose -f infra/docker-compose.yml build
```

Cuando una validación no pueda ejecutarse, el bloque no se declarará terminado y deberá quedar documentado.

## Fase 0 — Línea base

Estado: `IN_PROGRESS`

Objetivos:

- Confirmar SHA base.
- Comparar con `main`.
- Ejecutar CI o validación local.
- Registrar fallos preexistentes.
- Identificar flujos críticos de regresión.

Criterio de salida:

- Estado conocido de backend, frontend y Docker.
- Ningún fallo nuevo atribuido al refactor.

## Fase 1 — Pruebas de caracterización frontend

Estado: `PLANNED`

Alcance inicial:

- Utilidades de fecha y hora.
- Formato y parsing `DD/MM/AAAA`.
- Filtrado de reservas canceladas.
- Error de conflicto de recurso.
- Error por membresía no permitida.
- Error por horario operativo.
- Error por reserva en el pasado.
- Tarjeta con y sin nota.
- Layout de agenda según permiso `resource.manage`.

Objetivo:

Capturar el comportamiento actual antes de reorganizar componentes.

## Fase 2 — Refactor de reservas frontend

Estado: `PLANNED`

### Bloque 2.1 — Componentes puros

Extraer sin modificar diseño:

- `BookingNoticeModal`
- `BookingCard`
- `BookingNoteModal`
- `ResourceBookingColumn`
- `MonthlyCalendarToolbar`
- `MonthlyCalendarGrid`
- `SelectedDayDetail`

### Bloque 2.2 — Formularios

Extraer:

- `BookingCreateForm`
- `ResourceCreateForm`

Mantener el estado en el nivel más bajo posible y evitar hooks genéricos innecesarios.

### Bloque 2.3 — Layout por permisos

- Construir el JSX correcto con `canManageResources`.
- No mover nodos después del render.
- Eliminar `BookingsRoleLayoutEnhancer.tsx`.

### Bloque 2.4 — Notas

- Renderizar `Cancelar` y `Ver nota` directamente desde `BookingCard`.
- Eliminar `BookingsNotesEnhancer.tsx`.
- Evitar una segunda consulta de reservas para completar el DOM.

### Bloque 2.5 — Estilos semánticos

- Sustituir selectores globales frágiles.
- Mantener medidas, colores y distribución actuales.
- Reducir `!important`.
- Dejar clases con nombres de intención.

Criterio de salida:

- Sin `MutationObserver` en reservas.
- Sin creación manual de nodos.
- Misma interfaz y comportamiento.
- Pruebas y build verdes.

## Fase 3 — Refactor del kiosco frontend

Estado: `PLANNED`

### Bloque 3.1 — Fecha mexicana

- Reemplazar `birthDay`, `birthMonth`, `birthYear` por un único estado de entrada.
- Usar un campo visible `DD/MM/AAAA`.
- Parsear a `AAAA-MM-DD` mediante función pura.
- Eliminar `KioskBirthDateField.tsx` y el layout auxiliar.

### Bloque 3.2 — División de página

Extraer:

- `KioskIdentityForm`
- `KioskBookingPanel`
- `KioskMembershipPanel`
- `KioskPendingPayments`
- `KioskHeader`

### Bloque 3.3 — Cliente HTTP

- Reutilizar transporte común.
- Mantener headers de self-service específicos.
- Evitar duplicar base URL y tratamiento de errores.

Criterio de salida:

- Sin manipulación directa del DOM.
- Misma privacidad y validaciones.
- Kiosco conserva branding y flujo actual.

## Fase 4 — API pública del módulo bookings

Estado: `PLANNED`

Objetivos:

- Crear API pública para:
  - listar recursos reservables;
  - consultar disponibilidad;
  - crear reserva;
- Crear DTOs públicos propios del módulo.
- Evitar que self-service importe `BookingService` o tipos de `infrastructure`.
- Añadir prueba de arquitectura que impida regresión.

Criterio de salida:

- Self-service depende sólo de `bookings.api`.
- Ningún tipo de repositorio cruza el límite modular.

## Fase 5 — Simplificación backend del módulo bookings

Estado: `PLANNED`

Objetivos:

- Centralizar zona horaria configurable.
- Inyectar `ObjectMapper`.
- Extraer parsing de horarios de recurso.
- Consolidar repositorios de bookings.
- Añadir pruebas de:
  - horario por día;
  - día cerrado;
  - límites de apertura y cierre;
  - conflicto de recurso;
  - conflicto del socio;
  - reservas en el pasado;
  - zona horaria.

No se cambiará el formato persistido de horarios durante este bloque.

## Fase 6 — Deuda `JdbcClient` en application

Estado: `PLANNED`

Orden propuesto por riesgo:

1. `OrganizationService`
2. `CatalogService`
3. `ClientService`
4. `ClientImportService`
5. `OnboardingService`
6. `SalesService`

Por cada servicio:

- Crear puerto.
- Implementarlo en infrastructure.
- Mover SQL.
- Ejecutar pruebas.
- Retirar el servicio de la allowlist de arquitectura.

`SalesService` será el último por su superficie funcional y riesgo.

## Fase 7 — Cliente API frontend por dominios

Estado: `PLANNED`

Objetivos:

- Transporte HTTP común.
- Clientes de dominio pequeños.
- Tipos junto al dominio correspondiente.
- Fachada temporal para compatibilidad.
- Migración gradual de imports.
- Eliminar `legacy` sólo cuando no existan consumidores.

## Fase 8 — Limpieza transversal

Estado: `PLANNED`

- Código muerto.
- Imports y tipos duplicados.
- Formato Java y TypeScript.
- Errores y mensajes inconsistentes.
- Utilidades duplicadas de fecha, moneda y UUID.
- Documentación desactualizada.

## Fase 9 — Verificación final

Estado: `PLANNED`

Entregable:

`docs/audits/refactor-final-verification.md`

Debe contener:

- Comparación antes/después.
- Archivos eliminados y creados.
- Complejidad reducida.
- Allowlist `JdbcClient` antes/después.
- Pruebas añadidas.
- Resultado de backend, frontend y Docker.
- Verificación de contratos.
- Riesgos residuales.
- Recomendación de merge.

## Primer bloque de implementación autorizado

El primer cambio de código será:

1. Añadir funciones puras y pruebas para fecha mexicana y fechas de reservas.
2. Integrar el botón y modal de notas directamente en React.
3. Integrar el layout por permisos directamente en React.
4. Retirar los enhancers sólo después de verificar equivalencia.

No se iniciará un segundo módulo mientras este bloque no compile y conserve el comportamiento.
