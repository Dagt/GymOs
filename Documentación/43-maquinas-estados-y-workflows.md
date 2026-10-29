# 43. Máquinas de estados y workflows críticos

**Estado:** propuestas de verificación documental. Los nombres exactos de enum/tabla se deben contrastar en código y Flyway por issue; no crear otra máquina paralela. Referencia a contratos `06-api-contracts.md`, `07-use-cases.md`, `33-planes-beneficios-paquetes-visitas.md` y matriz `34`.

## SM-01 — venta y pago
`Cotización → Venta/Pago PENDING → proveedor inicia transacción → webhook firmado → conciliación autoritativa → CONFIRMED o REJECTED/EXPIRED → efectos negocio`.
- URL de retorno del navegador **no** confirma; webhook duplicado o evento tardío nunca duplica efectos.
- Venta, inscripción, stock, membresía, recibo y reembolso forman transacciones/compensaciones auditadas.
- Timeout incierto no equivale a pago denegado ni a pago confirmado.
- Tests: #33, #34, #42, QA-PAY-01..04.

## SM-02 — membresía
`Sin membresía → pendiente de cobro → activa → renovada/cambiada → vencida` con fechas inclusivas, tolerancia y reglas locales. No asumir que «suspendida» existe como transición: #37 investiga comandos y emisión de derechos antes de agregarlos.
- Renovación desde sede secundaria no transfiere silenciosamente origen de membresía.
- Pago rechazado nunca activa plan.
- Acceso físico revoca al vencer o desactivar socio según dominio actual.

## SM-03 — entitlement de paquete de visitas
Estados documentados `PENDING_PAYMENT → ACTIVE → CONSUMED/EXPIRED/CANCELLED`. Un socio/tenant tiene a lo sumo un paquete pendiente o activo.
- El saldo representa toda la organización; el evento de consumo pertenece a una sede y fecha de negocio.
- Cada acceso conserva snapshot real beneficio–sucursal–modo; no recombinar filas de otros contratos.
- `CONSUMES_VISIT` descuenta exactamente 1 con llave idempotente; `INCLUDED_WITH_VISIT` exige consumo base del **mismo entitlement, fecha y sede**, sin nuevo descuento.
- Una membresía que ya concede ese acceso tiene prioridad. No completar permisos locales con derechos multi-sucursal inventados.
- Cuando faltan derechos físicos o fecha, fallar seguro y no consumir saldo.
- Refund posterior a consumos requiere política explícita y auditoría; #35.

## SM-04 — sesión de kiosco
`Recepción autorizada abre → sesión temporal activa → socio identificado o nuevo registro → operaciones limitadas → salida/caducidad/cancelación → limpieza de identidad`.
- Cambiar entre «Ya soy socio» y «Quiero registrarme» reinicia identidad/sesión según contrato actual.
- No ocultar catálogo de planes porque Stripe no esté listo; bloquear sólo paso de pago, con motivo claro.
- Fallo de consulta opcional tras alta exitosa no debe mostrar «falló el registro».
- En caducidad no mostrar datos del socio anterior; nunca consultar recursos de otro tenant/sucursal.

## SM-05 — automatización/email
`Evento → inbox durable → regla activa con contexto → trabajo pendiente/lease → entrega aceptada o retry → dead letter/intervención`.
- SMTP `SENT` significa aceptación del proveedor, no entrega final; ZeptoMail bounce/complaint queda como #56.
- Duplicado del mismo evento no envía repetición involuntaria ni cambia estado de venta.
- Nunca exponer emails/tokens en logs de seguimiento.

## SM-06 — conector/puerta física
**Solo en rama no integrada**: `desired rights/evento del dispositivo → validación de identidad, terminal, tenant, sede, derecho/saldo → decisión → apertura remota una sola vez → ACK/auditoría`.
- Modalidades distintas `PROVIDER_MANAGED` (HikCentral aplica niveles locales) y `GYMFORT_ONLINE` (requiere round-trip para paquetes medidos).
- Cloud/hardware caído, ref no vinculada, token revocado o resultado incierto ⇒ **no nueva apertura automática**; recuperación idempotente.
- No confundir sincronización de derechos estáticos con descuento transaccional de visitas.
- La verdad del hardware depende de matriz de sitio. #38 #39 #40.

## SM-07 — reserva/cupo
`Disponible → solicitud → elegibilidad/cobro si aplica → confirmación → completada/cancelada`.
- Dos solicitudes del último cupo no pueden confirmar simultáneamente.
- Cambios y cancelaciones conservan historial y efecto económico según contrato; #36.

## Flujo de revisión de una transición
Cada PR identifica estado anterior, evento, precondición de rol/tenant/sede, datos actualizados, evento/auditoría, falla de red, evento repetido, concurrencia, rollback/compensación, UI y prueba. Cambiar máquina existente requiere ADR o decisión aprobada, no sólo renombrar strings.