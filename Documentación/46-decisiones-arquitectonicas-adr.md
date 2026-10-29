# 46. Registro de decisiones arquitectónicas (ADR)

**Estado:** transición desde `12-decisiones-fundacionales.md` hacia ADR versionados al estilo TrailerSaas. Las bases históricas no se reescriben silenciosamente. Cada ADR indica **PROPOSED / ACCEPTED / SUPERSEDED / REJECTED**, fecha, responsable, alternativas, consecuencias y pruebas. No presentar un ADR propuesto como autorización para cambiar código.

## 1. Decisiones vigentes evidenciadas
- A-001: producto GymFort; nombres técnicos GymOs por compatibilidad (README + constitución).
- A-002: monolito modular Java25 / Spring Boot4 / Spring Modulith; PostgreSQL/Flyway.
- A-003: Next16/React19/TypeScript, Cloudflare OpenNext y backend separado.
- A-004: identidad PLATFORM vs TENANT, asistencia temporal con auditoría.
- A-005: pagos autoritativos por webhook firmado y reconciliación; pasarela alojada, no PAN/CVV.
- A-006: una organización con múltiples sedes; derechos por cobertura efectiva de beneficio y sucursal.
- A-007: proveedor físico a través de Connector, HikCentral controla biometría local; no aprobación física de piloto.
- A-008: notificaciones durables, email SMTP por ZeptoMail, no prometer entrega por `SENT`.

Los documentos canónicos son `01`, `03`, `09`, `12`, `33` y la implementación; la tabla es índice de decisión, no sustituto.

## 2. Nuevas ADR por resolver
| ADR | Estado | Problema | Issue/OQ |
|---|---|---|---|
| ADR-001 | PROPOSED | Estrategia de sesión browser, refresh HttpOnly/BFF y CSRF | #28 / OQ-002 |
| ADR-002 | PROPOSED | Límite de consistencia financiera, refund y compensación | #33 #35 / OQ-005 |
| ADR-003 | PROPOSED | Modos de acceso físico local vs autorización online, fallos y biometría | #38 #39 / OQ-001 OQ-010 |
| ADR-004 | PROPOSED | Marca por sucursal, fallback y comunicación/recibo PDF | #51 / OQ-006 |
| ADR-005 | PROPOSED | Despliegue, backup, RPO/RTO y observabilidad mínimas | #42 #43 #46 / OQ-003 |
| ADR-006 | PROPOSED | Convención de pruebas FULL/FAST, procedencia y bloqueo de CI | #44 #59 |
| ADR-007 | PROPOSED | Política fiscal y contable de cobros SaaS vs ventas del gimnasio | #52 / OQ-008 OQ-009 |
| ADR-008 | PROPOSED | División técnica del Connector y frontera cloud/on-prem | #38 / OQ-004 |

## 3. Plantilla obligatoria
### ADR-XXX — Título
- **Estado:** PROPOSED.
- **Fecha/responsable:** fecha de revisión, equipo.
- **Contexto y problema:** evidencia de código y uso; prioridad.
- **Restricciones:** invariantes BR/NFR, compatibilidad, permisos y datos.
- **Alternativas:** al menos conservar base, opción A y B; riesgos/costes reales.
- **Decisión propuesta:** explícita y sin ambigüedades.
- **Consecuencias:** positivas, trade-offs, migraciones, rollback y amenazas.
- **Pruebas:** unitarias/PG/E2E/sandbox/sitio; criterios verificables.
- **Vínculos:** issue, PR, doc, schema y ADR reemplazada.
- **Aprobación:** sólo Product Owner/revisor autorizado cambia a ACCEPTED.

## 4. Reglas
Un nuevo requerimiento no autoriza reemplazar stack por gusto. Cambio de schema exige nueva Flyway, compatibilidad histórica y contrato API. Diferencia documental relevante no se resuelve «adivinando»; registrar OQ y evidencia. Los estados de repositorio/CI jamás se infieren del nombre de rama. Para decisiones de privacidad/fiscalidad, la aceptación del dueño no sustituye revisión legal aplicable.

El documento `45` mantiene preguntas abiertas, `42` mantiene requisitos; ambos se actualizan cuando se acepta un ADR.

## ADR individuales propuestas

Consultar [índice ADR](adr/README.md) y las propuestas [sesión/refresh](adr/ADR-001-sesiones-refresh-y-csrf.md), [pagos/refunds](adr/ADR-002-finanzas-reembolso-concurrencia.md), [acceso físico](adr/ADR-003-acceso-fisico-hikcentral-fail-closed.md) y [marca por sede](adr/ADR-004-branding-por-sucursal.md). Ninguna está aceptada sin aprobación explícita.
