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
socio muestra los periodos con su venta, y el carrito minorista ya no acepta
planes. El diseño del catálogo permanece sin cambios.

La duración usa extremos inclusivos (`durationDays - 1`), la renovación activa
continúa al día siguiente y la vencida reinicia en la fecha operativa. Un worker
y una reconciliación previa a lectura/mutación cambian a `EXPIRED` después de la
gracia. La prueba PostgreSQL simultánea validó el bloqueo y descubrió/corrigió
que el lock no debía unir la venta mutable antes de releer el agregado.
