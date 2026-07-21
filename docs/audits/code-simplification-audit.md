# Auditoría inicial de simplificación y refactorización

## Alcance

Rama auditada: `feature/code-simplification-rebasion-and-refactor`.

Commit base funcional: `91beb1c58dcc3b54bedbbdbea5c716d40a78b6ec`.

La rama contiene el mismo código funcional que `feature/bookings-experience-and-validation`. `main` sólo añade el commit de merge de ese trabajo, sin diferencias de contenido relevantes para esta auditoría.

## Objetivo

Reducir complejidad, duplicación y acoplamiento sin cambiar contratos, reglas de negocio, permisos, diseño aprobado ni comportamiento observable.

## Política de integración

- La rama se mantendrá como espacio de trabajo hasta completar validación local.
- No se abrirá un pull request hasta que el usuario pruebe localmente que no se rompió diseño, lógica ni funcionalidad.
- El PR borrador #4 fue cerrado deliberadamente el 21 de julio de 2026.
- Los resultados de CI se usarán como diagnóstico, no como autorización de integración.

## Criterios de clasificación

- `BLOCKER`: impide refactorizar con seguridad.
- `HIGH`: deuda frágil o con alto riesgo de regresión.
- `MEDIUM`: complejidad o duplicación importante.
- `LOW`: limpieza localizada.
- `SAFE_REFACTOR`: puede cambiarse con riesgo bajo.
- `REQUIRES_TESTS_FIRST`: requiere cobertura antes de tocarse.
- `DO_NOT_TOUCH_YET`: no debe modificarse hasta resolver dependencias.

## Línea base de validación

Ejecución de GitHub Actions asociada al primer bloque de auditoría:

- Pruebas frontend añadidas: `PASS`.
- Frontend lint: `FAIL`.
- Frontend typecheck: no ejecutado por dependencia del lint.
- Frontend build: no ejecutado por dependencia del lint.
- Backend tests: `FAIL`.
- Docker: no ejecutado porque backend y frontend no quedaron verdes.

Estos fallos se consideran parte de la línea base hasta identificar su causa exacta. No se atribuirán automáticamente al refactor.

Regla: antes de un cambio estructural amplio se debe corregir o clasificar cada fallo como `PREEXISTING` o `INTRODUCED`.

## Hallazgos iniciales

### RF-001 — Lógica de reservas añadida mediante manipulación del DOM

- Severidad: `HIGH`
- Clasificación: `REQUIRES_TESTS_FIRST`
- Archivos:
  - `frontend/src/app/bookings/BookingsNotesEnhancer.tsx`
  - `frontend/src/app/bookings/BookingsRoleLayoutEnhancer.tsx`
  - `frontend/src/app/bookings/layout.tsx`
- Evidencia:
  - Uso de `MutationObserver` sobre `document.body`.
  - Búsqueda de elementos por textos visibles, clases Tailwind y posición.
  - Creación manual de botones y movimiento de nodos entre contenedores.
  - Asociación de reservas con tarjetas mediante nombre y texto de horarios.
- Riesgo:
  - Cambios menores de texto o estructura rompen funcionalidades.
  - Doble fuente de verdad entre React y el DOM mutado.
  - Reejecuciones del observador, condiciones de carrera y comportamiento difícil de probar.
- Solución:
  - Integrar acciones de nota directamente en `BookingCard`.
  - Renderizar el layout por permisos directamente desde JSX.
  - Eliminar ambos enhancers y el montaje indirecto.
- Pruebas necesarias:
  - Render de tarjeta con y sin nota.
  - Apertura y cierre del modal.
  - Layout para `resource.manage` y para perfiles operativos.

### RF-002 — CSS de reservas acoplado a clases utilitarias y estructura incidental

- Severidad: `HIGH`
- Clasificación: `REQUIRES_TESTS_FIRST`
- Archivo: `frontend/src/app/bookings/bookings-layout.module.css`
- Evidencia:
  - Selectores globales como `.rounded-2xl.shadow-inner`, `.mt-5.flex.flex-wrap.gap-2`, `.grid-cols-7 > button`, `.snap-start > div > article > p:nth-child(2)` y `.max-h-[24rem]`.
  - Uso extensivo de `!important`.
  - El significado depende de combinaciones de clases Tailwind y orden de elementos.
- Riesgo:
  - Un cambio visual no relacionado puede alterar reservas.
  - Dificulta localizar qué regla controla cada componente.
- Solución:
  - Introducir clases semánticas explícitas en componentes React.
  - Mantener CSS Modules sólo para estilos propios del módulo.
  - Eliminar selectores posicionales y dependientes de utilidades Tailwind.

### RF-003 — Página de reservas monolítica

- Severidad: `HIGH`
- Clasificación: `REQUIRES_TESTS_FIRST`
- Archivo: `frontend/src/app/bookings/page.tsx`
- Responsabilidades acumuladas:
  - Carga de organización, sucursales, recursos, planes, clientes y reservas.
  - Formulario de creación de reserva.
  - Formulario de creación de recurso.
  - Agenda mensual.
  - Detalle por recurso.
  - Modales y mensajes.
  - Fechas, horarios, colores, parsing de configuración y traducción de errores.
- Solución propuesta:
  - `BookingsPage` como orquestador.
  - `BookingCreateForm`.
  - `ResourceCreateForm`.
  - `MonthlyBookingCalendar`.
  - `SelectedDayDetail`.
  - `BookingCard`.
  - `BookingNoticeModal`.
  - Hooks `useBookingsData`, `useClientSearch` y `useBookingCalendar` sólo si reducen complejidad real.
  - Utilidades de fecha y horario en archivos puros y testeables.

### RF-004 — Campo de fecha del kiosco montado mediante DOM indirecto

- Severidad: `HIGH`
- Clasificación: `SAFE_REFACTOR`
- Archivos:
  - `frontend/src/app/kiosk/[terminalCode]/KioskBirthDateField.tsx`
  - `frontend/src/app/kiosk/[terminalCode]/layout.tsx`
  - `frontend/src/app/kiosk/[terminalCode]/page.tsx`
- Evidencia:
  - El componente oculta tres `select`, crea un `input`, sincroniza valores mediante setters nativos y dispara eventos manualmente.
- Solución:
  - Usar un único estado `birthDateDisplay` con formato `DD/MM/AAAA` dentro de la página.
  - Parsear y validar con una función pura.
  - Enviar `AAAA-MM-DD` al API.
  - Eliminar enhancer y layout auxiliar.

### RF-005 — Compresión excesiva del código Java

- Severidad: `MEDIUM`
- Clasificación: `SAFE_REFACTOR`
- Archivos observados:
  - `backend/src/main/java/com/gymos/selfservice/api/SelfServiceController.java`
  - `backend/src/main/java/com/gymos/selfservice/application/SelfServiceService.java`
- Evidencia:
  - Métodos, records y condicionales completos en una sola línea.
  - Dificulta revisión, trazabilidad y detección de errores.
- Solución:
  - Restaurar formato legible y consistente.
  - No modificar comportamiento ni contratos.
  - Aplicar formatter del proyecto y mantener métodos enfocados.

### RF-006 — Self-service depende de clases internas de bookings

- Severidad: `HIGH`
- Clasificación: `ARCHITECTURAL_DEBT`
- Archivos:
  - `SelfServiceController`
  - `SelfServiceService`
- Evidencia:
  - Importa `BookingService` y records de `BookingRepository` desde `infrastructure`.
- Riesgo:
  - Rompe la separación modular y expone tipos de persistencia entre módulos.
- Solución:
  - Crear una API pública de operaciones de bookings.
  - Exponer DTOs/records propios del contrato público.
  - Hacer que self-service dependa exclusivamente de esa API.
- Pruebas necesarias:
  - Prueba de arquitectura de Spring Modulith.
  - Recursos, disponibilidad y creación desde kiosco.
  - Privacidad: no revelar reservas ni clientes ajenos.

### RF-007 — Configuración horaria almacenada en `notes` y parseada en frontend y backend

- Severidad: `MEDIUM`
- Clasificación: `REQUIRES_TESTS_FIRST`
- Evidencia:
  - El horario por día se serializa dentro de `resource.notes`.
  - Backend y frontend implementan parsers independientes.
  - Backend usa un `ObjectMapper` estático creado manualmente.
- Riesgo:
  - Divergencia de formato, fallbacks silenciosos y datos inválidos tratados como horario global.
- Solución de corto plazo:
  - Crear un contrato compartido por módulo y un parser único en backend.
  - Inyectar `ObjectMapper` configurado por Spring.
  - Mantener compatibilidad con los datos actuales.
- Solución futura:
  - Evaluar modelo relacional de horarios en una migración separada; fuera de este refactor sin cambio funcional.

### RF-008 — Consulta de conflicto de socio separada del repositorio principal

- Severidad: `MEDIUM`
- Clasificación: `SAFE_REFACTOR`
- Archivo: `ClientBookingConflictRepository.java`
- Observación:
  - La consulta está correctamente en infrastructure, pero el nombre describe una única query y aumenta fragmentación.
- Propuesta:
  - Integrarla en un puerto/repositorio de lectura de reservas o en `BookingRepository`, manteniendo una sola abstracción de persistencia del módulo.
  - Antes, cubrir conflicto por traslape y estados no bloqueantes.

### RF-009 — Deuda explícita de `JdbcClient` en application

- Severidad: `HIGH`
- Clasificación: `REQUIRES_TESTS_FIRST`
- Allowlist actual detectada por prueba de arquitectura:
  - `CatalogService`
  - `ClientImportService`
  - `ClientService`
  - `OnboardingService`
  - `OrganizationService`
  - `SalesService`
- Solución:
  - Extraer puertos de persistencia por caso de uso.
  - Migrar un servicio por bloque.
  - Reducir la allowlist después de cada migración.

### RF-010 — Cliente API frontend fragmentado de forma inconsistente

- Severidad: `MEDIUM`
- Clasificación: `REQUIRES_TESTS_FIRST`
- Evidencia:
  - Kiosco de reservas usa un cliente HTTP propio en `frontend/src/lib/kiosk-bookings.ts`.
  - El resto del frontend conserva un cliente general/legacy.
- Riesgo:
  - Duplicación de base URL, headers, parsing y errores.
- Solución:
  - Crear transporte HTTP común.
  - Mantener clientes de dominio pequeños sobre ese transporte.
  - No unificar contratos de autenticación normal y self-service; sólo reutilizar infraestructura HTTP.

### RF-011 — Ausencia de pruebas frontend suficientes

- Severidad: `BLOCKER`
- Clasificación: `REQUIRES_TESTS_FIRST`
- Impacto:
  - Impide demostrar que una refactorización visual/estructural conserva el comportamiento.
- Cobertura mínima previa:
  - Parsing `DD/MM/AAAA`.
  - Fechas y horarios de reservas.
  - Mensajes de error amigables.
  - Render de tarjeta y nota.
  - Layout según permisos.
  - Filtrado de canceladas.
  - Navegación de calendario y botón “Ir a hoy”.

### RF-012 — Línea base de CI roja

- Severidad: `BLOCKER`
- Clasificación: `DO_NOT_TOUCH_YET` para cambios amplios
- Estado confirmado:
  - Pruebas frontend: pasan.
  - Lint frontend: falla.
  - Suite backend: falla.
  - Typecheck, build y Docker quedan bloqueados.
- Acción:
  - Identificar el error exacto de lint.
  - Identificar las pruebas backend fallidas.
  - Clasificar cada fallo como preexistente o introducido.
  - No ejecutar refactors estructurales amplios mientras la línea base siga roja.

## Primer objetivo recomendado

El primer bloque debe ser frontend de reservas y kiosco porque concentra deuda reciente, visible y eliminable sin cambiar contratos:

1. Completar pruebas de utilidades y componentes críticos.
2. Corregir o clasificar los fallos de línea base.
3. Integrar nota y layout directamente en React.
4. Eliminar enhancers y observadores.
5. Reemplazar CSS frágil por clases semánticas.
6. Integrar directamente el campo `DD/MM/AAAA`.
7. Ejecutar regresión visual y funcional.

En paralelo continuará la matriz transversal de todos los módulos de la plataforma.

## Estado

Auditoría inicial: `IN_PROGRESS`.

Se añadieron pruebas y documentación, pero no se ha modificado todavía lógica funcional de producción en esta rama.