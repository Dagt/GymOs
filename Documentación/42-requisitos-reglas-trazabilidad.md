# 42. Catálogo de requisitos funcionales (FR), reglas de negocio (BR) y trazabilidad

**Estado:** Inventario inicial PROPUESTO extraído del PRD, use cases, deuda y código existente; los IDs **no reemplazan** contratos ni migraciones. Al aplicar un issue se valida línea por línea contra código, se conserva evidencia y se actualiza su estado. Inspirado en la trazabilidad FR/BR/NFR de TrailerSaas.

## 1. Requisitos funcionales
| FR | Capacidad | Contrato/código de referencia | Issues |
|---|---|---|---|
| FR-001 | Registrar gimnasio, primera sede y propietario mediante invitación única | platformadmin/organizations/identity | #45 |
| FR-002 | Separar identidad PLATFORM y TENANT con soporte temporal | identity/security | #28 #29 |
| FR-003 | Gestionar sedes, usuarios y permisos con alcance real | organizations/security | #29 |
| FR-010 | Alta/búsqueda/importación de socios con ciclo de vida reversible | clients/onboarding | #30 |
| FR-011 | Registro socio en kiosco habilitado por sede | selfservice/clients | #34 |
| FR-020 | Catálogo físico/servicios/variantes y múltiples códigos de barras | catalog | #33 #55 |
| FR-021 | Existencias, movimientos, transferencias, conteos y alertas | inventory | #54 |
| FR-030 | POS ventas, comprobantes y pagos manuales | sales/payments | #33 |
| FR-031 | Stripe Connect, Checkout, Setup y confirmación con webhook firmado | payments/selfservice | #34 |
| FR-032 | Pago Punto Mercado Pago con conciliación | payments | #33 #34 |
| FR-033 | Configurar medios disponibles por sede sin duplicar conexión del tenant | configuration/payments | #34 |
| FR-040 | Alta/renovación/cambio de plan con periodos correctos | memberships | #33 #35 |
| FR-041 | Beneficios económicos/acceso con alcance LOCAL/PLAN_COVERAGE | benefits/eligibility | #37 |
| FR-042 | Paquetes de visitas, saldo único dentro del tenant, snapshot inmutable | visitpackages/access | #35 |
| FR-050 | Acceso lógico y decisión autorizada sobre derechos | access | #29 #35 |
| FR-051 | Sincronizar con HikCentral/torniquete vía conector local seguro | rama Connector / access | #38 #39 |
| FR-052 | Autorizar en línea el consumo físico de visitas sin puerta abierta por error | rama Connector / access | #38 #39 |
| FR-060 | Reservas con disponibilidad/colisiones/reglas | bookings | #36 |
| FR-070 | Rutinas versionadas, ejercicios, asignaciones, métricas y PDF | coaching | #48 #55 |
| FR-080 | Profesionales wellness, agenda y citas | wellness | #44 |
| FR-090 | Reglas de automatización y reintentos durables | automations | #46 |
| FR-091 | Envío transaccional ZeptoMail configurable con marca tenant | notifications | #56 |
| FR-100 | Reportes por organización/sucursal y KPIs definidos | reporting | #53 |
| FR-101 | Auditoría inmutable de operaciones críticas | audit | #31 |
| FR-110 | Administración comercial SaaS manual por cliente/sede y MRR | platformbilling | #52 |
| FR-111 | Marca visual y comunicación por sucursal con fallback | configuration/organizations | #51 |
| FR-120 | Experiencia diferenciada por rol y kiosco táctil accesible | frontend | #47 #48 #50 |

## 2. Reglas de negocio transversales
| BR | Invariante | Origen / prueba |
|---|---|---|
| BR-001 | Ningún dato ni permiso cruza `organization_id` fuera de soporte autorizado | constitución; #29 |
| BR-002 | Sucursal operativa determina alcance de stock, venta y configuración local | modelo; #29 #33 |
| BR-003 | Sólo Recepción puede abrir sesión de kiosco; navegador no otorga permiso | configuración/seguridad; #34 |
| BR-004 | Pago se considera confirmado sólo tras webhook firmado y conciliación proveedor | pagos; #34 |
| BR-005 | Reintento no crea segunda venta/pago/renovación/consumo | ventas/pagos/paquetes; #33 #35 |
| BR-006 | Una membresía vigente concede derechos antes de consumir paquetes | elegibilidad; #35 |
| BR-007 | Un socio no puede tener dos paquetes abiertos `PENDING_PAYMENT`/`ACTIVE` | V98–V101; #35 |
| BR-008 | Cada sede seleccionada de paquete necesita acceso base `CONSUMES_VISIT` | plan paquete; #35 |
| BR-009 | `INCLUDED_WITH_VISIT` requiere consumo base mismo entitlement, sede y fecha operativa | V101; #35 |
| BR-010 | Premium `LOCAL` de Centro no aparece mágicamente en Tecnológica | beneficios/acceso; #37 |
| BR-011 | La transacción crea tenant/sucursal/dueño, y falla SMTP no deshace alta confirmada | platformadmin; #45 |
| BR-012 | Baja automática de socio es reversible; no borrar historia financiera | clients; #30 |
| BR-013 | Stripe readiness y toggle de capacidad operativa son conceptos distintos | kiosco; #34 |
| BR-014 | Conector no almacena biometría ni toma decisión comercial por sí mismo | access/hikcentral; #39 |
| BR-015 | Ante caída de red o estado incierto, no abrir ni repetir apertura a ciegas | Connector; #39 |
| BR-016 | Acceso soporte exige motivo, caducidad, auditable y salida explícita | identity; #29 |
| BR-017 | Identidad/colores de sucursal caen a marca organizacional si no hay override | diseño propuesto #51, pendiente aprobación |
| BR-018 | Datos reales y secretos no se usan como fixtures de QA | NFR; #43 |

## 3. Flujos (WF)
- WF-001: alta gimnasio → primera sede → invitación → activación → onboarding.
- WF-002: registro socio → cotización (inscripción) → pago → membresía → recibo.
- WF-003: recepción: escaneo SKU → venta → pago → movimiento inventario → comprobante.
- WF-004: kiosco: socio/registro → elegir plan → cotizar → Checkout → webhook → resultado.
- WF-005: paquete multi-sucursal → compra → activación → consumo/beneficio incluido.
- WF-006: reserva cupo → pago/elegibilidad → confirmación → cancelación.
- WF-007: rutina → publicar versión → asignar → exportar PDF.
- WF-008: evento de negocio → automatización → notificación → retry/dead letter.
- WF-009: membresía/visita → reconciliación Connector → HikCentral → evento físico.
- WF-010: fallos productivos → alertas → recuperación → auditoría.

**Verificaciones pendientes:** separar cada WF en precondición, evento, resultado, rechazo, roles y pruebas; usar `43-maquinas-estados-y-workflows.md` y `44-flujos-ux-por-rol.md`.

## 4. Convención de estado
**IMPLEMENTADO EN CÓDIGO**, **NO VERIFICADO EN ENTORNO**, **VALIDADO EN TEST**, **ACEPTADO EN PILOTO**, **PLANIFICADO**, **FUERA DE MVP**. No interpretar la presencia en esta lista como aprobación funcional. Cualquier cambio FR/BR requiere actualizar issue, ADR si implica arquitectura, tests y fichas.