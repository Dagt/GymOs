# 40. QA de GymFort: matriz de aceptación, entornos y evidencia

**Estado: directriz PROPUESTA para piloto. Corte: 2026-10-29.** Ninguna fila marcada como objetivo equivale a ejecución aprobada. Se aprovechan pruebas existentes y `34-matriz-pruebas-centro-tecnologica.md`; no duplicar suites por nombre.

## 1. Jerarquía y límites de pruebas
- **Estática:** Kotlin Gradle, linters, TypeScript, build Cloudflare, arquitectura Spring Modulith; no garantizan ejecución real.
- **Unitaria:** JUnit y Node tests de reglas comerciales, seguridad, fechas México, montos e interfaces; no prueban concurrencia real.
- **Integración:** PostgreSQL/Testcontainers y Flyway desde limpio + upgrades históricos; asegurar locks, FK, ownership, idempotencia.
- **E2E UI:** Playwright en roles PLATFORM, dueño, gerente, recepción, coach y kiosco; distinguir APIs simuladas de backend real.
- **Proveedor real controlado:** Stripe Connect test, Mercado Pago test cuando corresponda, ZeptoMail; reportar cuenta, evento, firma y resultado sin secretos.
- **Sitio físico:** Connector Windows + HikCentral y torniquete; el simulador no satisface la matriz PENDING-SITE.
- **Operación:** staging, backing object storage, restore ensayado, alertas y recuperación; anotar RPO/RTO medidos.

## 2. Matriz de regresión mínima

Las regresiones de #29 se agregan a `TenantIsolationPostgresIntegrationTests` y `operational-hardening.spec.ts`. La [matriz de aislamiento](../docs/security/multi-tenant-verification.md) indica qué flujos se prueban por HTTP/PostgreSQL, cuáles usan API interceptada en Playwright y cuáles se verificaron por inspección.
| ID | Recorrido o fallo inducido | Evidencia requerida | Issue |
|---|---|---|---|
| QA-SEC-01 | Tenant A solicita por UUID recurso de B | Denegación y cero lectura/escritura cross-tenant HTTP/PG | #29 |
| QA-SEC-02 | Gerente de sucursal A cambia recurso, tarifa, inventario o usuario solo B | 403/404, sin mutación y auditable | #29 |
| QA-SEC-03 | PLATFORM fuera de soporte toca TENANT y TENANT toca PLATFORM | No acceso, soporte temporal/caducado probado | #29 |
| QA-SEC-04 | Refresh concurrente, token reutilizado/robado, expiración, logout | Sin sesión persistente indebida; threat model | #28 |
| QA-SEC-05 | Login incorrecto, 429 o backend indisponible | Mensajes públicos sin URI, HTTP ni correlation ID | #32 |
| QA-CRM-01 | Alta/socio duplicado e import CSV con filas inválidas | No PII ajena, feedback por fila, transacción definida | #30 |
| QA-ONB-01 | Alta cliente confirmada, SMTP falla después | Tenant permanece; invitación se puede reemitir | #45 |
| QA-PAY-01 | Dos cobros simultáneos idénticos | Exactamente un efecto financiero y un recibo lógico | #33 |
| QA-PAY-02 | Webhook Stripe duplicado, fuera de orden o con monto diferente | Rechazo/reconciliación, cero doble activación | #34 |
| QA-PAY-03 | Regreso navegador sin webhook validado | Venta/membresía quedan pendientes, no activadas | #34 |
| QA-PAY-04 | Cancelación/refund parcial después de stock/beneficio | Política explícita, transacción auditable | #33 |
| QA-MEM-01 | Renovar plan multisucursal desde segunda sede | Vigencia correcta, ownership/venta atribuidos según contrato | #35 |
| QA-PKG-01 | Última visita consumida en paralelo en dos puntos | Una concesión, saldo nunca negativo | #35 |
| QA-PKG-02 | Sauna INCLUDED_WITH_VISIT antes de General de la misma sede/fecha | Denegado, sin descontar saldo | #35 |
| QA-PKG-03 | General en Tecnológica y Sauna Centro mismo día | Sauna denegada hasta consumo base Centro | #35 |
| QA-BKG-01 | Dos reservas concurrentes del último cupo | No overbooking; cancelación/rollback consistentes | #36 |
| QA-INV-01 | Conteos por fila offline/fallo de guardado | No mostrar falso «Guardado»; reintento por fila | #54 |
| QA-CFG-01 | Dos sucursales con nombre, logo y reply-to propios | Cambio de sede resuelve marca; fallback organización | #51 |
| QA-UX-01 | Pantallas 390/768/1440 + kiosco | Capturas reales, focus, empty/loading/error/denied | #48 |
| QA-CON-01 | Conector con token revocado, HCP o Cloud caídos | Fail closed/no apertura; recuperación sin doble apertura | #38 #39 |
| QA-CON-02 | Evento HikCentral real y reconocimiento en torniquete | Matriz SITE, número de eventos, evidencia sin biometría | #39 |
| QA-DB-01 | Flyway limpio y upgrade V83/V84/V85/V99/V103 | Migración consistente, sin reparar a ciegas | #42 |
| QA-OPS-01 | Backup restaurado, data reconciliada, alerta detectada | Procedimiento repetible y RPO/RTO medidos | #43 |

Para paquetes ver además `34-matriz-pruebas-centro-tecnologica.md` (casos M/P/A/U); para HikCentral usar `connector/docs/TEST-MATRIX.md` **en la rama Connector**, no asumir que existe en main.

## 3. Gates del mismo SHA
- **FAST local:** inspección del diff + pruebas enfocadas y `git diff --check`; una suite fallida no se oculta.
- **FULL:** JUnit unitarios, PostgreSQL integración, arquitectura, npm test/lint/typecheck/build/Cloudflare, Playwright críticos, Compose y seguridad dependencias. Jobs del **mismo SHA final**, sin skipped no autorizados.
- **EXTERNAL:** se etiqueta aparte si depende de Stripe test, Mercado Pago, email o hardware; su falta no se debe disfrazar de PASS.
- **RELEASE:** restauración, smoke por rol, reconciliación y aprobación del alcance habilitado; sin secretos de prueba en producción.

En el corte auditado, main y la rama del Connector muestran fallos CI; no hay evidencia verde actual y los logs remotos históricos no estaban disponibles.

## 4. Formato de evidencia por PR
| Campo | Qué registrar |
|---|---|
| Base y HEAD | SHA completo, rama y diff contra main |
| Comando | Copia exacta, desde qué directorio y sistema/versión |
| Datos | Solo fixtures sintéticos; esquema/migración evaluados |
| Resultado | Pasa / falla / no ejecutado / bloqueado, contadores verificables |
| Reporte | Link GitHub Action, archivo de resultados, captura o traza saneada |
| Limitaciones | API simulada, provider test vs live, hardware no presente |
| Decisión | HOLD, Ready for Review o Go/No-go; responsable humano |

## 5. Definition of Done de QA
Todo issue refleja cobertura positiva, negativa, concurrencia cuando aplique, tenant/sucursal/rol, error/timeout, mutaciones auditables y rollback. Mantener evidencia reproducible en PR. Después del merge, actualizar documentación y deuda con fecha y commit real.
## ACC-006 — Matriz de registro presencial

| Escenario | Evidencia reproducible |
| --- | --- |
| Membresía y gracia, vencida, socio inactivo o suspendido, prioridad sobre el paquete | `VisitPackageEntitlementRepositoryIntegrationTests`, contra Eligibility y PostgreSQL reales |
| Cinco consumos, sexto rechazo, paquete vencido o fuera de sede, beneficio incluido, saldo compartido | Misma suite, fixtures de snapshots, ledger y auditoría |
| Dos recepcionistas, reintento idéntico, clave con otro cuerpo, dos ingresos legítimos y última visita en dos sedes | Misma suite, transacciones concurrentes y barrera |
| Reversión, reintento simultáneo, aislamiento tenant/sucursal e historial separado de ventas | Misma suite y `SecurityAuthorizationTests` |
| PostgreSQL limpio y upgrade V103 con bitácora existente | `DatabaseMigrationTests` y arranque Flyway de las suites PostgreSQL |
| Error de red después de confirmar, recarga, misma clave, saldo y corrección por supervisor | `frontend/e2e/manual-check-in.spec.ts`, Chromium con API interceptada. La integración de datos se verifica en PostgreSQL. |
| Paquete vencido con saldo, agotado con membresía vigente y derecho que cambia tras la consulta | `manual-check-in.spec.ts` comprueba que sólo se ofrecen derechos de la respuesta vigente. La suite PostgreSQL valida que el rechazo de una reversión conserve el ledger y que el crédito posterior mantenga vencido el paquete original. |

El job frontend de CI ejecuta la suite Playwright completa en Chromium.
Comandos locales en `AGENTS.md`. Para ejecutar Playwright con el puerto
predeterminado ocupado, configura `PLAYWRIGHT_PORT=3100`. Registra el SHA y
los resultados del run en el PR de [#61](https://github.com/Dagt/GymOs/issues/61).
