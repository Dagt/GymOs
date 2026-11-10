# ADR-002 — Consistencia financiera, idempotencia y devoluciones

El alcance #34 mantiene bloqueada la devolución manual de un cobro Stripe.
La confirmación de un reembolso y su efecto sobre membresías o visitas
requieren un contrato de conciliación y la decisión pendiente en #35.

**Estado:** PROPOSED (29/10/2026). **Issues:** #33 #34 #35. **OQ:** OQ-005.

## Contexto
POS, pagos manuales, Stripe Connect/Checkout, Point, membresías, paquetes de visitas, inventario y recibos ya existen. Las reglas exigen que retorno del navegador no confirme pago. Falta evidencia E2E completa de devolución con consumos/stock y concurrencia bajo proveedor real.

## Alternativas de refunds en paquetes
A. Bloquear refunds cuando hubo consumo y escalar revisión humana; conservador y compatible con la política documentada para total consumido.
B. Refund proporcional al saldo intacto, con ajuste irreversible del entitlement y ledger; requiere contrato fiscal, reconciliación y reglas de tarifas.
C. Crédito administrativo manual con trazabilidad, separado del proveedor; requiere reconciliación y evitar devolución monetaria doble.

## Invariantes no negociables
Evento firmado/conciliado antes de activar; importe, moneda, propietario, sucursal, sale_id y contexto idénticos. Duplicados o eventos fuera de orden no duplican movimiento financiero, stock, inscripción, vigencia ni visitas. Devoluciones no borran ventas ni auditoría. El permiso de reembolso es distinto de venta.

## Decisión pendiente
Aprobar comportamiento refund por tipo de artículo y escenario: producto físico devuelto, membresía vigente, visitas pendientes/consumidas, cuota inscripción, callback fallido, proveedor down. No mezclar cobranza SaaS platformbilling con ventas tenant.

## QA de salida
PostgreSQL concurrente, webhook duplicate/out of order, cancel/refund parciales, límite saldo y roll-forward. Contratos Stripe test y Point sandbox donde habilitados, screenshots UI de estado del pago con respaldo autoritativo. ADR aceptada antes de desarrollar cambios económicos.
## SAL-001

Decisión técnica de SAL-001. Bloqueo de fila de venta y clave transaccional en PostgreSQL. Reserva de inventario antes de Point y conciliación del pago con sus efectos locales. Las opciones de política comercial de devoluciones siguen pendientes.
