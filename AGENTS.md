# Instrucciones para agentes — GymFort

Este repositorio tiene nombre técnico histórico GymOs pero producto visible GymFort. **Antes de modificar código**, leer en este orden:

1. `Documentación/01-constitution.md` y `Documentación/12-decisiones-fundacionales.md`.
2. `Documentación/35-mvp-y-criterios-de-salida.md`, `36-epicas-backlog-codex.md` y el issue concreto.
3. `Documentación/03-sds.md`, `06-api-contracts.md`, `08-frontend-spec.md` y la ficha correspondiente en `Documentación/modulos/`.
4. `Documentación/28-registro-vivo-deuda-tecnica.md` y `37-auditoria-repositorio-2026-10-29.md` para limitaciones reales.
5. `Documentación/38-directrices-codex-y-plantillas.md`, `39-sistema-diseno-y-ux.md` y `40-qa-matriz-de-aceptacion-y-evidencias.md`.

## Reglas innegociables
- No usar ramas antiguas como fuente de verdad. Auditar la rama del issue respecto de `main` y buscar PRs previos. `feature/local-access-connector` no está integrada al corte del 29/10/2026.
- Una tarea/issue por PR de tamaño revisable, salvo dependencias explícitas. Crear PR **Draft**, no realizar merge ni despliegue.
- No cambiar migraciones Flyway publicadas; crear siguiente versión y probar instalación limpia + upgrade histórico.
- No añadir secretos, credenciales reales, PII ni biometría a Git, screenshots, pruebas o logs.
- Toda mutación requiere autorización de plataforma/tenant/sucursal/rol y evaluación de propiedad; el cliente frontend no es límite de seguridad.
- Pago confirmado = webhook validado + conciliación autoritativa, nunca regreso del navegador.
- Mantener idempotencia, transacciones, auditoría y fronteras de Spring Modulith.
- UI por rol, estados carga/vacío/error/éxito, accesibilidad, responsive y estilo unificado. No asumir que un mockup es la UI implementada.
- Conector, Stripe y SMTP deben distinguir pruebas simuladas de pruebas conectadas; no certificar hardware sin aceptación física.
- Registrar versión de herramientas, SHA, comandos ejecutados y evidencia, sin inventar CI verde.
- Documentar cambio en ficha modular, deuda, estado canónico, roadmap y changelog.

## Comandos sugeridos
- Backend: `cd backend && ./gradlew test postgresIntegrationTest --no-daemon --console=plain`.
- Frontend: `cd frontend && npm ci && npm test && npm run lint && npm run typecheck && npm run build && npm run cf:build && npm run test:e2e`.
- Connector (si aplica): `backend/gradlew -p connector test` y empaquetado sólo en Windows para piloto.
- Verificación de migraciones y restores mediante `Documentación/40-qa-matriz-de-aceptacion-y-evidencias.md`.
- En Windows usar `gradlew.bat` y `npm.cmd`.

Este documento agrega reglas de trabajo; `frontend/AGENTS.md` conserva las reglas específicas de la versión Next instalada.