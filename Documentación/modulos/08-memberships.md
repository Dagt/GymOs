# Membresías

Estado: `FUNCIONAL`

## Implementado

- alta, activación, renovación y cambio de plan;
- vínculo con ventas y pagos;
- vigencia, estado, visitas y operación en kiosco;
- planes multi-sucursal y restricciones de reservas.

## Pendiente

- E2E HTTP/navegador con pagos rechazados y validación posterior;
- parametrizar zona operativa por tenant si GymOS sale del alcance México.
## Evolución en `feature/reception-pos-init`

El catálogo visual sigue incluyendo planes y sus operaciones generan ventas,
pagos, métricas y movimientos de caja. V54 incorpora `membership_period`,
backfill, reconciliación determinista de activas duplicadas, índice único por
socio y bloqueo pesimista para renovación/cambio. El cotizador backend ya
calcula beneficios y Sales conserva subtotal, descuento y total. La ficha del
socio muestra sólo la membresía operativa actual; los periodos y sus ventas
viven en el Timeline de actividad para no presentar una membresía cancelada
como si fuera otro plan actual. El carrito minorista ya no acepta planes. El
diseño del catálogo permanece sin cambios.

La duración usa extremos inclusivos (`durationDays - 1`), la renovación activa
continúa al día siguiente y la vencida reinicia en la fecha operativa. Un worker
y una reconciliación previa a lectura/mutación cambian a `EXPIRED` después de la
gracia. La prueba PostgreSQL simultánea validó el bloqueo y descubrió/corrigió
que el lock no debía unir la venta mutable antes de releer el agregado.

La ficha usa los permisos reales `membership.renew`,
`membership.change-plan` y `sale.create`; se retiraron comprobaciones frontend
contra los códigos inexistentes `membership.update` y `membership.create`.
Renovar y cambiar plan abren un drawer en la propia ficha con cotización y
pago. El cambio sólo admite una membresía activa: cancela el periodo actual,
crea una membresía del nuevo plan desde la fecha operativa, no prorratea ni
transfiere días pendientes y actualiza inmediatamente los accesos heredados.

La renovación rápida vive dentro de `/reception`. La entrada lateral y la
tarjeta que enviaban a `/reception/renewals` fueron retiradas; esa URL sólo
redirige al inicio para conservar marcadores antiguos. El bloque embebido
permite buscar socio, renovar el mismo plan, cambiar a otro plan activo o
contratar el inicial, siempre con cotización y registro de pago.
