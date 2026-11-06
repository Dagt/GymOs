# 36. Épicas, backlog, orden de Codex y dependencias

**Corte:** 29 de octubre de 2026. **Fuente de tareas ejecutables:** [GitHub Issues](https://github.com/Dagt/GymOs/issues). El registro de hallazgos sigue siendo `28-registro-vivo-deuda-tecnica.md`; **un issue no sustituye ni duplica el ID de deuda**. El histórico de PR #1–20 no equivale a issues de roadmap.

## 1. Épicas

| Épica | Issue | Objetivo | Issues hijos |
|---|---|---|---|
| Gobierno y documentación | [#21](https://github.com/Dagt/GymOs/issues/21) | MVP, fuentes canónicas y gates | #57, #58, #59 |
| Seguridad y aislamiento | [#22](https://github.com/Dagt/GymOs/issues/22) | tokens, tenant, PII, auditoría | #28–32 |
| Pagos y comercio | [#23](https://github.com/Dagt/GymOs/issues/23) | atomicidad, pagos, visitas, reservas | #33–37 |
| Acceso físico | [#24](https://github.com/Dagt/GymOs/issues/24) | Connector/HikCentral verificado | #38–41 |
| Piloto e infraestructura | [#25](https://github.com/Dagt/GymOs/issues/25) | CI, backup, staging, recuperación | #42–46 |
| Sistema de diseño y UX | [#26](https://github.com/Dagt/GymOs/issues/26) | identidad real, componentes, QA | #47–50 |
| Evolución comercial | [#27](https://github.com/Dagt/GymOs/issues/27) | marcas, SaaS billing, reporting, media | #51–56 |

Los 32 issues #28–59 fueron creados como trabajo **pendiente de validar/implementar**; no cerrar por mera existencia del código. Las etiquetas de prioridad están en el título `[P0]`, `[P1]` y `[P2]` hasta configurar taxonomía de etiquetas nativas con revisión.

## 2. Secuencia recomendada (una unidad revisable por PR)

| Ola | Issues | Dependencia/gate |
|---|---|---|
| A — Control | #57, #59, #44 | Inventario, reproducir/diagnosticar CI y E2E; ninguna rama debe presentarse verde sin artefactos |
| B — Seguridad | #28, #29, #32, #30, #31 | La matriz negativa precede exponer nuevas rutas y migrar sesiones |
| C — Dinero/datos | #33, #34, #42, #35, #36 | Pago y migraciones antes de piloto con cobros/paquetes |
| D — Piloto | #45, #43, #46, #53 | Onboarding, staging, restauración, métricas |
| E — Acceso físico, si entra en contrato | #38 → #41 → #40 → #39; #37 | Integrar por PRs; no mutar V104 sin revisar; aceptar con hardware real |
| F — UX producto | #49 → #47 → #48 → #50 | Primero reproducibilidad; después patrones, screens y accesibilidad |
| G — Evolución | #51, #52, #54, #55, #56, #58 | Por cliente/valor y decisiones técnicas; marca por sede puede adelantarse |

Enfoque: abrir pocos PRs simultáneos, minimizar interdependencias, proteger Flyway. La prioridad P0 es por riesgo; no significa que el hardware deba retrasar un piloto expresamente cloud-only.

## 3. Definición de issue listo (Definition of Ready)

Debe tener: problema verificable, archivo/ruta o evidencia, reglas de negocio, alcance y exclusiones, actor/sucursal, caso de aceptación reproducible, riesgos de seguridad/finanzas, escenarios negativos, dependencia, pruebas y notas de migración. Cuando la evidencia sea sospecha, escribir «investigar» y no «bug confirmado». Si falta información del proveedor, registrar condición externa que bloquea y no inventar contratos.

## 4. Definición de terminado (Definition of Done)

- PR vinculado al issue, sin merge autónomo y cambios coherentes.
- Contrato API y modelos revisados; cambios incompatibles requieren ADR/versión.
- Roles, tenant, sucursal, propiedad, PII y consentimiento probados donde aplique.
- Idempotencia, transacciones, compensaciones, reintentos y auditoría donde aplique.
- Pruebas unitarias/PG + frontend + E2E + sandbox/hardware cuando aplique; evidencia reproducible con SHA y comando.
- UI responde a carga, vacío, error, éxito, teclado, screen sizes, notificaciones y flujo por rol.
- Documentación viva actualizada, deuda movida a histórico **solamente tras evidencia**, changelog, runbooks y plan de rollback.
- Revisores verifican CI remoto, compatibilidad de migraciones y ausencia de secretos. Fallo o ausencia de CI mantiene **HOLD**, no se sustituye con afirmación.

## 5. Dependencias críticas y decisiones

- `AUTH-001`: seleccionar diseño de cookies/BFF y política CSRF primero, migrar sesiones con rollback, no cambiar tokens a ciegas.
- `DB-001`: no editar V1–V103 ni V104 si ya fue aplicada fuera de la rama; registrar callbacks y pruebas upgrades legacy.
- `PAY-001`: no probar en modo live; para Stripe se necesita cuenta test y webhooks verificados; informes de e2e mock no demuestran captura real.
- `ACC-002`: la rama Connector no integrada contiene cambios de backend, CI, frontend, migración y empaquetado; decidir split por capas con pruebas propias.
- `CFG-003`: modelo y política de fallback antes de rediseñar pantallas o emails por sede.
- `UX-003`: resolver escrituras ocultas en scripts antes de confiar en snapshots y diffs frontend.

## 6. Ciclo operativo semanal

Product Owner prioriza issue; Codex hace **revisión de código actual primero**, plan, implementación, pruebas y PR Draft; revisor compara PR vs requisitos y CI; Product Owner fusiona. Actualizar #21–27 sólo con el progreso real de los hijos. Después de merge actualizar `13-estado-desarrollo`, `28-deuda`, `11-roadmap`, fichas, changelog y matriz QA. No acumular ramas paralelas largas ni fusionar conflictos semánticos por fuerza.

## 7. Prompt de arranque

Consultar `38-directrices-codex-y-plantillas.md` y comenzar por el **issue #59 o #44** para entender/recuperar CI, sin alterar código financiero todavía. La auditoría documental puede revisarse y fusionarse primero. El issue #38 debe tratarse como evaluación de una rama de riesgo, no como orden de merge inmediato.
## SAL-001

SAL-001 (#33) cubre integridad de operaciones locales, reservas y aplicación idempotente. PAY-001 (#34) aporta la evidencia de Stripe y la recuperación del kiosco. VIS-001 (#35) cubre los recorridos de paquetes.
