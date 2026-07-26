# 27. Prompt maestro de implementación — Coaches y rutinas

Estado: LISTO PARA EJECUCIÓN  
Fecha: 26 de julio de 2026  
Rama obligatoria: `feature/coaches-workouts`

## Rol

Actúa como arquitecto de software senior y desarrollador full stack responsable de completar el módulo de Coaches y rutinas de GymOS sin alterar innecesariamente los contratos estables del resto de la plataforma.

## Fuentes obligatorias

Antes de modificar código, revisar:

- `25-plan-accion-coaches-rutinas.md`;
- `26-ajustes-transversales-coaches-rutinas.md`;
- documentación maestra `01` a `13`;
- migraciones y código existente de coaches/workouts;
- permisos, arquitectura Modulith, convenciones de API y sistema visual vigente.

## Decisión central

Las rutinas no se venden ni se cobran mediante GymOS. La asignación no crea ventas, pagos, comisiones, ingresos ni movimientos de caja. Los cobros privados en efectivo entre coach y socio quedan fuera de la plataforma.

## Resultado esperado

Entregar un módulo completo en el que:

1. el coach disponga de un catálogo amplio y extensible de ejercicios;
2. pueda crear una rutina visualmente, estructurada por sesiones, bloques y ejercicios;
3. pueda publicar versiones inmutables;
4. pueda asignar una versión concreta a un socio;
5. pueda descargar un PDF imprimible;
6. dueño y gerente consulten métricas semanales y el detalle de socios atendidos por coach;
7. todos los históricos permanezcan auditables;
8. la información quede preparada para una futura app del socio.

## Restricciones de arquitectura

- Respetar `API -> application -> infrastructure`.
- No usar JDBC directamente desde application.
- Mantener aislamiento por `organization_id` y alcance por sucursal.
- Las operaciones deben usar el principal autenticado; no confiar en IDs de organización enviados por cliente.
- Versiones publicadas y asignaciones son inmutables en contenido.
- Escrituras críticas usan idempotencia.
- No persistir toda la rutina únicamente como JSON opaco.
- Se permite snapshot JSON versionado para exportación y futura app, acompañado de tablas normalizadas.
- No incorporar dependencia de `sales`, `payments`, `memberships` o POS salvo consultas legítimas de elegibilidad del socio.

## Fases de trabajo

### Fase 0 — Auditoría

- Localizar backend, frontend, migraciones, permisos y pruebas existentes.
- Comparar estado real contra los documentos 25 y 26.
- Registrar qué puede reutilizarse, qué debe migrarse y qué deuda existe.
- Evitar duplicar entidades o endpoints ya funcionales.

### Fase 1 — Dominio y persistencia

Implementar o ajustar:

- `coach_profile` y alcance de sucursales;
- catálogo de ejercicios, aliases, grupos musculares y equipamiento;
- rutinas, versiones, sesiones, bloques y ejercicios;
- asignaciones y eventos de estado;
- índices, restricciones, auditoría e idempotencia.

Crear migraciones compatibles con datos existentes. Ninguna migración debe destruir históricos.

### Fase 2 — Catálogo de ejercicios

- Crear un seed amplio y útil, no una lista mínima de demostración.
- Incluir fuerza, movilidad, calentamiento, core, cardio y estiramiento.
- Incluir variantes por barra, mancuerna, polea, máquina, peso corporal, bandas y accesorios.
- Permitir alta inline de ejercicios faltantes.
- Detectar posibles duplicados sin impedir de forma ciega variantes legítimas.
- Implementar búsqueda por nombre, alias, grupo muscular, equipamiento y tipo.

### Fase 3 — Constructor de rutinas

- Biblioteca de plantillas.
- Crear, editar borrador, duplicar, archivar y publicar.
- Sesiones/días y bloques reordenables.
- Ejercicios reordenables.
- Soportar campos estructurados y prescripción libre.
- Soportar superseries, circuitos, alternativas, notas, opcionales, movilidad y estiramiento.
- Vista previa antes de publicar.

La UI no debe exponer un editor JSON.

### Fase 4 — Versionado y asignación

- Publicar genera versión numerada e inmutable.
- Editar una publicada genera nuevo borrador.
- Asignar solamente versiones publicadas.
- Validar socio, coach, organización y sucursal.
- Permitir reemplazo controlado de asignación activa.
- Mantener historial y estado.
- Evitar duplicados por reintento.

### Fase 5 — PDF

Generar un PDF con:

- identidad del gimnasio;
- socio, coach, fecha, folio y versión;
- sesiones ordenadas;
- ejercicios y prescripciones;
- notas, movilidad, calentamiento y estiramiento;
- formato imprimible A4/carta;
- contenido determinista a partir de la versión asignada.

Probar rutinas largas, saltos de página, caracteres acentuados y textos libres.

### Fase 6 — Métricas

Implementar resumen y detalle:

- asignaciones de esta semana y anterior;
- variación;
- socios únicos;
- primera rutina y reasignaciones;
- ranking informativo;
- coaches sin actividad;
- filtros por rango, sucursal, coach, socio y estado;
- exportación CSV.

La unidad de conteo es una asignación única creada, no una rutina creada, publicación o descarga.

### Fase 7 — Seguridad y UX

- Permisos granulares de coach, ejercicio, rutina, asignación y reportes.
- Coach: administrar propias rutinas/asignaciones dentro de alcance.
- Dueño: alcance organización.
- Gerente: alcance de sucursales.
- Recepción: sin edición por defecto.
- Admin GymOS en soporte: comportamiento consistente con reglas existentes.
- Estados vacíos, carga, errores, confirmaciones y accesibilidad.
- Usar primitivas visuales oficiales de GymOS.

### Fase 8 — Pruebas y cierre

Backend:

- dominio y application;
- repositorios e integración PostgreSQL/Testcontainers;
- aislamiento multi-organización y sucursal;
- permisos por rol;
- idempotencia;
- versionado inmutable;
- PDF;
- métricas y límites temporales por zona horaria.

Frontend:

- componentes y controladores;
- constructor;
- búsqueda/alta inline;
- asignación;
- filtros y métricas;
- permisos;
- E2E del flujo principal.

Verificaciones mínimas:

```powershell
cd backend
.\gradlew.bat clean test --no-daemon

cd ..\frontend
npm run test
npm run lint
npm run typecheck
npm run build
npm run test:e2e

cd ..
docker compose -f infra/docker-compose.yml config
docker compose -f infra/docker-compose.yml up -d --build
```

## Criterios de aceptación obligatorios

- Un coach crea una rutina de cinco días equivalente al ejemplo aportado.
- Puede expresar `3x12, 10, 8 subiendo` sin pérdida de información.
- Puede crear un ejercicio que no existe y usarlo inmediatamente.
- Publicar crea una versión inmutable.
- Asignar registra coach, socio, sucursal, fecha y versión.
- Reintentar la asignación no duplica la métrica.
- El PDF reproduce fielmente el contenido asignado.
- El dueño visualiza cuántas rutinas asignó cada coach durante la semana y a qué socios.
- El gerente solo visualiza sus sucursales.
- No existe venta, pago o ingreso asociado a la asignación.
- Archivar un ejercicio o coach no rompe documentos históricos.
- La suite completa queda verde.

## Entregables de cierre

- código backend y frontend;
- migraciones;
- seed de ejercicios;
- OpenAPI actualizado;
- pruebas;
- documento de implementación real;
- actualización de `13-estado-desarrollo.md`;
- registro de deuda técnica restante;
- commits convencionales y rama limpia.