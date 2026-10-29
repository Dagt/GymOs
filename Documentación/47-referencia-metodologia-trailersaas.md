# 47. Cómo se trasladó la metodología de TrailerSaas a GymFort

**Referencia real consultada:** [Dagt/TrailerSaas](https://github.com/Dagt/TrailerSaas), rama main, documentación e issues revisados 29/10/2026. No se copian reglas de transporte: se reutiliza **estructura de conocimiento, trazabilidad, UX y entrega**, adaptada a un SaaS de gimnasios ya desarrollado.

## 1. Correspondencia exacta entre ambos proyectos
| Estructura que hizo controlable TrailerSaas | Equivalente GymFort a partir de esta reorganización | Observación |
|---|---|---|
| `AGENTS.md` | [`AGENTS.md`](../AGENTS.md) + [`38`](38-directrices-codex-y-plantillas.md) | Define qué lee Codex y evita PR sin issue |
| `docs/discovery/*` + design-partner baseline | [`32-retroalimentacion-primer-cliente.md`](32-retroalimentacion-primer-cliente.md) + [`45`](45-preguntas-y-decisiones-pendientes.md) | La experiencia del cliente es evidencia, no obligación de codificar casos únicos |
| `docs/product/product-vision.md` + `mvp-scope.md` | [`01`](01-constitution.md), [`02`](02-prd.md), [`35`](35-mvp-y-criterios-de-salida.md) | MVP de **cierre/piloto**, no reconstrucción de funciones existentes |
| `docs/product/requirements-catalog.md` | [`42`](42-requisitos-reglas-trazabilidad.md) | FR identificados y unidos a issues/contratos |
| `docs/product/business-rules.md` | [`42`](42-requisitos-reglas-trazabilidad.md) + [`33`](33-planes-beneficios-paquetes-visitas.md) | BR e invariantes accesos/pagos |
| `docs/architecture/non-functional-requirements.md` | [`41`](41-requisitos-no-funcionales.md) | NFR por seguridad, confiabilidad, performance, privacidad |
| `docs/domain/domain-model.md` y `docs/data-model/*` | [`04`](04-domain-model.md), [`05`](05-data-model.md) | Ya existían; revisar contra migraciones V1–V104 |
| `docs/state-machines/*` | [`43`](43-maquinas-estados-y-workflows.md) | Pagos, visitas, membresías, kiosco y Connector |
| `docs/workflows/core-workflows.md` | [`07`](07-use-cases.md), [`42`](42-requisitos-reglas-trazabilidad.md) | Flujos de punta a punta por actor |
| `docs/ux/design-system.md` y `docs/ux/*-web.md` | [`08`](08-frontend-spec.md), [`39`](39-sistema-diseno-y-ux.md), [`44`](44-flujos-ux-por-rol.md) | Sistema visual + rutas reales + estados, no solo mocks |
| `docs/api/*` | [`06`](06-api-contracts.md) + OpenAPI backend | Contract-first; cambios API necesitan tests |
| `docs/decisions/ADR-*` | [`12`](12-decisiones-fundacionales.md), [`46`](46-decisiones-arquitectonicas-adr.md), [`adr/`](adr/README.md) | ADR propuestas; jamás marcar ACCEPTED por haber creado archivo |
| `docs/product/open-questions.md` | [`45`](45-preguntas-y-decisiones-pendientes.md) | Evita decisiones inventadas por Codex |
| `docs/backlog/{README,implementation-order,codex-workflow}.md` | [`36`](36-epicas-backlog-codex.md), [`38`](38-directrices-codex-y-plantillas.md) + GitHub #21–59 | Épicas y tareas acotadas, olas con dependencias |
| `docs/development/ci*.md` | [`40`](40-qa-matriz-de-aceptacion-y-evidencias.md), [`RELEASE.md`](../RELEASE.md), CI, runbooks | FAST vs FULL del SHA, pendiente automatización de E2E |
| `docs/ux/evidence/*` | objetivo #48 | Captura real antes/después con provenance, **no** se fabrica evidencia |
| `docs/security/*` | [`docs/security`](../docs/security/permissions-matrix.md), [`41`](41-requisitos-no-funcionales.md) | Matriz negativa pendiente #29 |

## 2. Diferencias que no se deben trasladar literalmente
- TrailerSaas usa React/Vite Web y Flutter offline; GymFort usa Next/OpenNext y **kiosco web**, no requiere app Flutter de socio en MVP.
- TrailerSaas gestiona transporte/QR/geofence; GymFort gestiona Stripe, inventario, membresías/beneficios, SaaS billing y HikCentral con riesgo financiero/físico específico.
- GymFort ya tiene **22 fichas modulares** y 103 migraciones main; no regenerar un nuevo esquema cero ni duplicar el PRD.
- GymFort tiene historial de 20 PRs y varias ramas largas; normalizar próximos cambios a un issue/PR, no retroconstruir issues falsos para PRs históricos.

## 3. Controles que deben imitar exactamente la disciplina de TrailerSaas
1. **Alcance:** issue hijo definido, linked FR/BR/NFR + DoD; nunca trabajar una épica completa en un PR.
2. **Fuentes:** AGENTS + constitución + requisitos + OQ/ADR + módulo + schema/API + QA.
3. **Cambios:** rama `issue-N-descripcion`, commits coherentes, datos sintéticos, historial Flyway intacto.
4. **Revisión:** PR Draft, pruebas FAST focalizadas, FULL del último SHA ya estabilizado, screenshots con provenance y casos negativos.
5. **Gates:** CI remoto completo confirmado o HOLD explícito, sin mezclar simulación y hardware/provider real, nunca merge por Codex.
6. **Seguimiento:** cerrar issue tras PR merge + criterios cumplidos, registrar evidencia, mover deuda al historial, actualizar roadmap y release.
7. **Desviaciones:** decisiones nuevas como OQ/ADR, no introducir funcionalidades por intuición.

## 4. Riesgos que hacen diferente el piloto GymFort
Seguridad del refresh JS (#28), ACL tenant/branch (#29), conciliación Stripe/Mercado Pago (#33–34), migraciones/restore (#42), CI (#44/#59), Connector sin PR ni aceptación de sitio (#38–40), branding de dos marcas (#51), experiencia UI aún sin baseline de pantalla real (#48). Se separan P0 de bloqueantes condicionales.

## 5. Flujo de primera iteración recomendado
Fusionar el **PR documental tras revisión** y después pedir a Codex: (1) #59 para evidenciar estado real CI y recuperar logs; (2) #44 E2E, en paralelo a (3) #28/#29 seguridad; (4) #33/#34 consistencia financiera; (5) #42 datos; (6) #43 aceptación controlada. Trabajo del Connector #38 se revisa **como rama aparte**, sin mezclar con docs ni con un despliegue.
