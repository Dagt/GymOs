# 26. Prompt maestro — Implementación de Coaches y rutinas

Fecha: 25 de julio de 2026  
Rama obligatoria: `feature/coaches-workouts`  
Documento rector: `25-plan-accion-coaches-rutinas.md`

## Rol

Actúa como arquitecto y desarrollador senior full stack del repositorio GymOS. Implementa el cierre del módulo de Coaches y rutinas respetando la arquitectura modular existente, el aislamiento multi-organización, el contexto de sucursal, los permisos granulares, la auditoría y el sistema visual del frontend.

## Regla principal

Antes de escribir código:

1. revisa todo el módulo existente de coaches/workouts en backend, frontend, migraciones, pruebas y documentación;
2. compara el estado real contra `25-plan-accion-coaches-rutinas.md`;
3. registra qué existe, qué está incompleto, qué contradice el alcance y qué deuda técnica debe corregirse;
4. conserva contratos válidos y migra de forma segura los que ya no representen el negocio.

No supongas que una clase, tabla o pantalla existente está completa solo porque su nombre coincide con el módulo.

## Decisiones obligatorias

- No integrar rutinas al POS.
- No crear productos `WorkoutService` para cobrar una rutina.
- No registrar pagos en efectivo del coach.
- No calcular comisiones ni ingresos por rutina.
- La métrica se origina en una asignación única e idempotente.
- Solo versiones publicadas pueden asignarse.
- Las asignaciones históricas son inmutables respecto a versiones posteriores.
- La futura app del socio debe poder consumir la estructura sin rediseñar el dominio.
- El catálogo de ejercicios debe ser amplio, buscable, paginado y extensible por organización/coach.
- No almacenar toda la rutina únicamente en JSON opaco; normalizar lo necesario para edición, validación, búsqueda y métricas.

## Secuencia de trabajo

### 1. Auditoría

- localizar paquetes, componentes, migraciones y permisos actuales;
- revisar dependencias indebidas entre API, application e infrastructure;
- revisar si el perfil de coach duplica `Person` o `User`;
- revisar el contenido JSON existente de `WorkoutVersion`;
- revisar si las asignaciones apuntan realmente a una versión;
- revisar endpoints y pantallas existentes;
- revisar si existen vínculos erróneos con catálogo, ventas o pagos;
- documentar hallazgos con prioridad P0, P1 y P2.

### 2. Diseño y migraciones

- definir o ajustar `coach_profile` y alcance por sucursal;
- crear catálogo normalizado de ejercicios, aliases, grupos musculares y equipamiento;
- crear seed amplio y mantenible;
- modelar rutina, versión, sesión, bloque y prescripción de ejercicio;
- conservar instantánea suficiente para históricos y PDF;
- reforzar asignación con versión, coach, socio, sucursal, fechas, estado e idempotencia;
- agregar índices para búsqueda y métricas semanales;
- escribir migraciones Flyway compatibles con datos existentes.

### 3. Backend

- mantener API -> application -> infrastructure;
- definir puertos de consulta y comando;
- aplicar validaciones de organización, sucursal, rol, estado y versión;
- implementar creación inline de ejercicios con detección de duplicados;
- publicar versiones inmutables;
- implementar asignación transaccional e idempotente;
- generar PDF desde la versión asignada;
- implementar consultas agregadas semanales y detalle;
- exportar CSV sin cargar conjuntos ilimitados en memoria;
- auditar acciones sensibles.

### 4. Frontend

- reutilizar primitivas visuales de GymOS;
- separar controlador/orquestación de componentes presentacionales;
- implementar catálogo de ejercicios con debounce, filtros URL y paginación;
- implementar constructor por sesiones/bloques con reordenamiento accesible;
- permitir alta rápida de un ejercicio faltante sin perder el borrador;
- mostrar estado de guardado y errores por campo;
- implementar publicación, duplicado, asignación y descarga PDF;
- implementar dashboard de métricas con filtros y detalle;
- respetar permisos y alcance de sucursal.

### 5. Pruebas

Backend:

- dominio y aplicación;
- persistencia PostgreSQL/Testcontainers;
- aislamiento multi-organización;
- autorización por rol y sucursal;
- versionado inmutable;
- idempotencia de asignaciones;
- ejercicio archivado conservado en históricos;
- zonas horarias en corte semanal;
- PDF con caracteres especiales y múltiples páginas;
- ausencia de efectos en ventas/pagos.

Frontend:

- controladores y validaciones;
- constructor y creación inline;
- permisos;
- filtros y métricas;
- descarga de PDF;
- estados vacío, carga y error.

E2E:

- coach crea/publica/asigna rutina;
- dueño ve la métrica y el detalle;
- gerente no accede a otra sucursal;
- edición posterior no cambia el PDF histórico;
- ninguna asignación aparece en POS o caja.

## Definición de terminado

La tarea no está terminada hasta que:

- el módulo cumple todos los criterios de aceptación de `25-plan-accion-coaches-rutinas.md`;
- OpenAPI refleja contratos reales;
- migraciones y seeds funcionan desde base vacía y sobre datos existentes representativos;
- backend y frontend compilan;
- pruebas unitarias, integración, arquitectura y E2E pasan;
- Docker levanta el sistema;
- se actualizan `13-estado-desarrollo.md` y un documento de cierre con deuda restante;
- se registra expresamente que rutinas no generan ventas, pagos ni caja.

## Comandos mínimos de verificación

```powershell
cd backend
.\gradlew.bat clean test --no-daemon

cd ..\frontend
npm ci
npm run test
npm run lint
npm run typecheck
npm run build
npm run test:e2e

cd ..
docker compose -f infra/docker-compose.yml config
docker compose -f infra/docker-compose.yml up -d --build
```

## Entrega

Realiza commits convencionales y pequeños. No mezcles refactors no relacionados. Al cierre, documenta:

- estado inicial encontrado;
- decisiones y migraciones;
- endpoints y pantallas;
- pruebas ejecutadas;
- deuda técnica restante;
- riesgos de despliegue;
- trazabilidad entre cada criterio de aceptación y su implementación.
