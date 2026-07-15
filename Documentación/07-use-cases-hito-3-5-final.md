# 7.x Casos de Uso - Correccion Hito 3.5

Este anexo precisa los cambios finales de estabilizacion aplicados sobre los casos de uso de membresias, pagos y kiosco.

## UC-017A Verificacion segura de kiosco

Actor: Cliente en kiosco interno.

Flujo vigente:
1. El kiosco activo crea una sesion temporal.
2. Backend devuelve `sessionId` y `sessionToken`.
3. Cada operacion posterior envia `X-Self-Service-Session-Token`.
4. El cliente captura email o telefono junto con fecha de nacimiento.
5. `selfservice` delega la verificacion a `ClientLookupOperations`.
6. Las respuestas de fallo son genericas y no confirman si el cliente existe.
7. Verificaciones fallidas incrementan intentos; al superar el limite se bloquea temporalmente la sesion.
8. Rate limiting protege por IP, terminal y sesion mediante puerto `RateLimiter`; Redis es el adaptador productivo.
9. Al cerrar sesion, el token ya no puede reutilizarse.

## UC-018A Renovacion idempotente por pago completado

Flujo vigente:
1. `memberships` solicita a `sales` una venta de renovacion mediante `SalesOperations`.
2. `sales` crea venta, partida y pago manual con `idempotencyKey`.
3. `sales` almacena hash de solicitud asociado a la llave de idempotencia.
4. Si la misma llave reaparece con payload distinto, responde `IDEMPOTENCY_KEY_REUSED`.
5. Si el pago queda pendiente, no se activa membresia.
6. Al validar el pago, `sales` publica `PaymentCompletedV1` desde `sales.api.events`.
7. `memberships` consume el evento, registra `processed_domain_event` y extiende desde la fecha final vigente.
8. Si el evento se reprocesa, no se crea otra membresia.
9. `memberships` publica `MembershipRenewedV1` desde `memberships.api.events`.

## UC-019A Cambio de plan idempotente

Flujo vigente:
1. `memberships` valida membresia activa y plan destino distinto.
2. `memberships` solicita a `sales` venta de cambio de plan.
3. La operacion usa idempotency key y hash de solicitud.
4. Al completarse el pago, `memberships` cancela la membresia anterior y activa la nueva.
5. El evento de pago es idempotente por `processed_domain_event`.

Criterios:
- No se permite cambiar al mismo plan.
- La membresia anterior queda cancelada con auditoria.
- La nueva membresia se activa solo con pago completado.
