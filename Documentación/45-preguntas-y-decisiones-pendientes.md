# 45. Decisiones y preguntas abiertas (OQ) para producto y piloto

**Estado:** OPEN; ninguna recomendación constituye decisión aprobada. Inspirado en el registro OQ de TrailerSaas. Cada respuesta debe indicar responsable, fecha, evidencia y ADR/issue que cambia.

| OQ | Decisión pendiente | Bloquea | Recomendación preliminar |
|---|---|---|---|
| OQ-001 | ¿Primer piloto se vende con control de acceso físico o sólo SaaS? | #38–40 si el equipo es parte del contrato | Separar SKU/alcance, no anunciar hardware hasta aceptación real |
| OQ-002 | ¿Se mantiene refresh en browser o se aprueba BFF cookie HttpOnly con CSRF? | #28 | Threat model y migración segura antes de elegir |
| OQ-003 | ¿Cuál es el volumen esperado por sede (socios, cobros, check-ins, reservas)? | #43 #46 | Medir baseline y aprobar p95/RPO/RTO |
| OQ-004 | ¿Qué licencias, API versión y topología HikCentral del cliente están disponibles? | #39 | Exigir evidencia del sitio, SDK oficial y operador |
| OQ-005 | ¿Política de refunds parciales de paquete con consumos existentes? | #33 #35 | No descontar dinero/visitas retroactivamente sin regla firmada |
| OQ-006 | ¿Qué funciones mínimas de marcas distintas deben operar por sede? | #51 | Definir orden: nombre/logo, remitente y documentos; fallback global |
| OQ-007 | ¿Quién es responsable de aceptación productiva y evidencia de cash/payment? | #34 #43 | Nombrar owner; sandbox primero |
| OQ-008 | ¿Cobro comercial SaaS manual es suficiente para primera venta? | #52 | Mantener cartera manual hasta acuerdo fiscal y motor recurrente |
| OQ-009 | ¿CFDI y retención fiscal/documental corresponden a GymFort o al cliente? | #52 #30 | Revisión fiscal/contractual de México antes de implementar |
| OQ-010 | ¿Acceso online físico debe operar sin internet? | #39 | Fallar cerrado; decidir explícitamente modo/provider y expectativa de cliente |
| OQ-011 | ¿Estándar de diseño UX y criterios visuales finales están aprobados? | #47 | Prototipos vs screenshots reales; requerir aprobación del sistema antes de migración global |
| OQ-012 | ¿Entorno y pruebas de carga/pagos automatizadas sin acceso live? | #34 #44 | Separar test/CI protegido y manual evidencia controlada |
| OQ-013 | ¿Política de comunicaciones y rebotes/quejas en ZeptoMail? | #56 | Consultar plan/API real antes de diseñar webhook |
| OQ-014 | ¿Alcance de suspensión/reactivación de membresía? | #37 | No crear comandos sin evento de cambio de derechos y pruebas |
| OQ-015 | ¿Retención, acceso, exportación y rectificación de PII? | #30 #31 | Definir política legal de MX, no inferir duración |

## Resolución
1. Investigar código/contrato/cliente; distinguir **confirmado vs suposición**.
2. Registrar 2–3 alternativas, pros/contras, alcance y riesgos; pedir aprobación al Product Owner para cambios materiales.
3. Crear o aceptar ADR si modifica seguridad, arquitectura, integración o negocio.
4. Actualizar requisitos FR/BR/NFR, issue, pruebas y UX; marcar OQ RESUELTA con decisión/fecha.
5. No detener issues no dependientes. Bloquear sólo capacidad afectada, con comportamiento de fallback explícito.

## Decisiones ya derivadas de código/documentación
- Stack base Java25/Spring Boot4/Modulith/PostgreSQL/Flyway/Next16/Cloudflare.
- Producto comercial GymFort; nombre técnico GymOs preservado temporalmente.
- Una organización puede incluir varias sucursales; no comparten información entre tenants.
- Kiosco abierto por Recepción; pago por webhook/conciliación; autopay sólo con consentimiento.
- Biométricos se quedan en HikCentral.
Estas son **bases de diseño existentes**, no aprobación del hardware ni de facturación fiscal.