# 38. Directrices operativas de Codex: issue → PR → revisión

## 1. Reglas de actuación

Product Owner decide producto y merge. Codex debe comportarse como equipo multidisciplinario (arquitectura, Spring/Modulith, Next/UX, seguridad, QA, SRE) y dejar cambios inspeccionables. La fuente de verdad de comportamiento es **código + migraciones + tests de la rama base**, interpretada bajo la constitución; documentación histórica puede necesitar actualización. No reescribir un módulo sin entender contratos ya integrados.

### Prompt maestro reutilizable

> Trabaja en el repositorio `Dagt/GymOs` (producto GymFort), **exclusivamente en el issue #N** sobre la rama estable `main` actual; compara el último SHA remoto antes de escribir. Lee `AGENTS.md`, `Documentación/01-constitution.md`, `35-mvp-y-criterios-de-salida.md`, `36-epicas-backlog-codex.md`, `38-directrices-codex-y-plantillas.md`, `39-sistema-diseno-y-ux.md`, `40-qa-matriz-de-aceptacion-y-evidencias.md`, la ficha del módulo y deuda asociada. **Primero audita el código actual**: qué ya existe, qué falta, contratos, migraciones, permisos, dependencias, pruebas y casos de fallo. Expón plan corto con archivos e invariantes. Implementa sólo el alcance del issue sin alterar migraciones históricas, sin secretos ni datos reales. Ejecuta pruebas unitarias/PostgreSQL/Playwright/CI pertinentes; distingue mock, sandbox y hardware. Actualiza documentación viva y changelog. Abre PR **Draft**, enlaza `Closes #N` cuando proceda, incluye SHA, resultados y riesgos. **No hagas merge ni deploy.** Si CI remoto está restringido o falla, declara HOLD con causa sólo cuando esté demostrada, no inventes un PASS.

Sustituir `#N` por el número real. Para #38, trabajar **a partir de la rama Connector** y tratar main como base de integración; no descartar sus cuatro commits ni rebasar destructivamente.

## 2. Plantilla de análisis previo

1. Estado `main`, HEAD del issue y PRs previos; archivos afectados.
2. Caso de uso, actores, criterios, permisos, organización, sucursal y ownership.
3. Invariantes transaccionales y de seguridad; datos en repositorios/tablas.
4. APIs existentes, errores, compatibilidad, eventos/idempotencia y migración.
5. UX por rol, experiencia actual y esperada, contraste y screenshots necesarios.
6. Matriz de pruebas positiva, negativa, concurrente, idempotente, temporal y rollback.
7. Riesgos, partes externas sin evidencia y alcance explícitamente fuera del PR.

## 3. Reglas backend

Java25/Spring Boot4/Modulith2/PostgreSQL18/Flyway, arquitectura modular/hexagonal. Servicio de dominio no depende de controlador ni infraestructura de otro módulo. Intercambio por APIs publicadas/eventos; no consultar tablas ajenas de forma accidental. Transacción por agregado; outbox/inbox/eventos idempotentes para efectos externos. Hash de secretos/tokens y cifrado de credenciales; no secretos en logs. Antes de cambios de schema probar base limpia y upgrade; **NUNCA editar V1–V103 ya aplicadas**, ni reparar checksums sin aprobación y evidencia. Para V104 Connector revisar si se aplicó en algún entorno antes de cambiarlo.

Pagos: registrar pendiente; verificar webhook firma + ownership + monto + moneda + orden + idempotencia; consultar estado autoritativo. No activar membresías por URL de retorno. Acceso físico: comportamiento fail-closed, nunca apertura por timeout; ni importar biometría a GymFort.

## 4. Reglas frontend y UX

Next 16/React19/TypeScript y framework existente. Reusar patrones sin añadir librerías porque sean novedosas. Componentes con contrato, tokens, semántica, foco visible, label, teclado y estados. Aislamiento visual no sustituye RBAC del backend. Formularios con validación inline, toast de resultado donde corresponda, persistencia explícita y errores sanitizados. No inventar diseño basándose sólo en landing; **capturar UI real antes y después**, 390/768/1440 px; kiosco con hit area adecuada. Autenticación y red: no mostrar endpoints ni correlation ID al socio/recepción, sí registrar diagnóstico saneado para soporte. Nunca mutar archivos fuente durante build/test (investigar #49).

## 5. Pruebas y entrega

Comandos desde raíz en Windows:
- `backend\gradlew.bat -p backend test postgresIntegrationTest --no-daemon --console=plain` o entrar a `backend` y ejecutar `.\gradlew.bat test postgresIntegrationTest`.
- `cd frontend`; `npm ci`; `npm test`; `npm run lint`; `npm run typecheck`; `npm run build`; `npm run cf:build`; `npm run test:e2e`.
- Para Connector: `backend\gradlew.bat -p connector test` y `pilotChecksum` sólo en Windows con JDK/JPackage.
- Docker: `docker compose -f infra/docker-compose.yml --profile stripe config` y build de contenedores pertinentes.
Las variantes POSIX usan `./gradlew`. Los comandos orientan, no reemplazan verificación de prerequisitos, contenedores ni proveedor.

**Evidence table obligatoria del PR**: job, comando, SHA, entorno, total tests, resultado, reporte, límites, screenshots, datos sintéticos. Si no hay permiso para ejecutar CI remoto, marcar «NO VERIFICADO» y dejar PR Draft.

## 6. Plantilla PR

Título: `[issue #N] tipo(modulo): resultado`. Cuerpo:
- **Closes #N / Épica #X / deuda ID**
- Problema anterior, decisión y contrato.
- Archivos/migraciones y matriz tenant/sucursal/roles.
- Pruebas (SHA y salidas) separando unitarias, PG, E2E, Stripe y hardware.
- Capturas antes/después y comportamiento en loading/empty/error/success si UI.
- Compatibilidad, rollback, riesgos, cobertura faltante y documentación.
- **Go/no-go**: recomendar «HOLD» o «Ready for Review» con fundamento; no merge por parte de Codex.

## 7. Anti-patrones específicos

No fusionar ramas sólo por nombre «hito», no generar funcionalidad ya existente, no llamar «implementado» a prototipo/futuro, no mezclar cobranza de SaaS con venta del gimnasio, no duplicar lógica de elegibilidad entre clientes/paquetes/membresías, no construir pantallas de complejidad sin flujo operativo, no inventar permisos, no añadir datos demo persistentes en producción, no editar documentos canónicos sin referenciar decisiones/PR, no cambiar unilateralmente políticas fiscales/biométricas.