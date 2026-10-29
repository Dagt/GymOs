# 41. Requisitos no funcionales (NFR) de GymFort

**Estado:** Propuesta de baseline para validación; no afirmar SLAs medidos. Inspirado en el catálogo NFR de TrailerSaas y adaptado a gimnasios, Stripe, multi-sucursal y control físico. En conflictos prevalecen constitución, acuerdos explícitos y ADR aceptadas.

| ID | Obligación verificable | Evidencia y dependencia |
|---|---|---|
| NFR-001 | Aislamiento tenant en todas las consultas y mutaciones | Matriz HTTP/PG de #29 |
| NFR-002 | Scoping por sucursal y ownership con controles backend; roles PLATFORM vs TENANT | #29, pruebas negativas |
| NFR-003 | Mínimo privilegio, acceso soporte temporal y auditable | #29 #31 |
| NFR-004 | Tokens/sesiones seguros, revocables y sin exposición innecesaria a JS | #28, ADR propuesto |
| NFR-005 | No guardar secretos, PAN, CVV ni biometría de HikCentral en repositorio | #28 #38 #39 |
| NFR-006 | HTTPS, webhook autenticado y validación DNS/TLS para integraciones | #34 #43 |
| NFR-010 | Todos los cambios financieros usan idempotencia de negocio y conciliación | #33 #34 |
| NFR-011 | Visitas consumidas exactamente una vez, sin saldo negativo ante carreras | #35 |
| NFR-012 | Reservas/cupos consistentes ante altas simultáneas | #36 |
| NFR-013 | Invariantes de conteo/venta/reembolso no dejan stock inconsistente | #33 #54 |
| NFR-014 | Eventos y workers durables con leases/retries/poison, sin duplicar efectos | #34 #46 |
| NFR-020 | Compilación reproducible sin mutación implícita del código fuente | #49 |
| NFR-021 | Pruebas FULL por SHA final y regresión E2E con artifacts | #44 #59 |
| NFR-022 | Schema Flyway inmutable, upgrades probados y plan forward-only | #42 |
| NFR-030 | Navegación usable por rol en móvil, tablet, desktop y kiosco | #47 #48 #50 |
| NFR-031 | Contraste, focus visible, etiquetas, teclado y mensajes de estado (WCAG 2.2 AA objetivo) | #50; mediciones en PR |
| NFR-032 | Las acciones críticas muestran estado pendiente/confirmado y errores no técnicos | #32 #47 |
| NFR-040 | Logs y tracing con correlationId de uso interno, sin PII ni tokens | #31 #46 |
| NFR-041 | Alertas verificadas para fallos de pagos, colas, integraciones y backups | #46 |
| NFR-042 | Backup de DB y objeto, restauración ensayada; RPO/RTO acordados y medidos | #42 #43 |
| NFR-043 | Soporte temporal, rotación de secretos y procedimientos de incidente | #43 |
| NFR-050 | Modo físico no debe abrir una puerta por default ante caída Cloud/HikCentral | #38 #39 |
| NFR-051 | Identidad biométrica y rostro se quedan en HikCentral, nunca en GymFort | #39 |
| NFR-052 | Conector con secretos DPAPI, TLS válido, ledger durable y distribución íntegra | #40 |
| NFR-060 | PII con minimización, consentimiento/revisión de retención y exportación segura | #30 #31 |
| NFR-061 | Auditoría de mutaciones sensibles con atribución y sin modificación silenciosa | #31 |
| NFR-062 | Contratos públicos versionados y dependencias entre módulos restringidas | #58 |
| NFR-063 | El sistema puede operar en un tenant multibranch sin promover derechos entre organizaciones | #29 #35 #51 |

## Performance, capacidad y SLO: sin inventar objetivos
Registrar antes del piloto volumen esperado de socios, sedes, ventas/hora, dispositivos, terminales y reservas. Medir p50/p95/p99 de login, búsqueda, escaneo/venta, cotización, reserva, actualización de stock, webhook y latencia del camino de autorización online. Los umbrales serán **propuestos y aprobados** a partir de carga real; hasta entonces no anotar cumplimiento. La autorización física requiere un SLO distinto por riesgo de denegación segura.

## Retención y cumplimiento
Diferenciar facturas/comprobantes de venta, logs operativos, auditoría, fotos, datos personales, OTP/invitaciones y biometría externa. No introducir políticas de eliminación automática sin revisión jurídica/fiscal aplicable. La documentación no equivale a dictamen legal.

## Guía de trazabilidad
Cada NFR tiene: issue responsable, pruebas negativas/regresión, ambientes, artefactos y fecha de evidencia; completar en PR y no inventar PASS. Referencias: `35` MVP, `40` QA y `42` catálogo FR/BR.