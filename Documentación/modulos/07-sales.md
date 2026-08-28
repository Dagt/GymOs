# Ventas, pagos y devoluciones

Estado: `FUNCIONAL`
Última revisión: 26 de agosto de 2026

## Implementado

- ventas con partidas e instantáneas comerciales;
- pagos manuales en efectivo, transferencia y tarjeta;
- pagos administrados por Stripe/Mercado Pago, recibos, operaciones pendientes
  e idempotencia ante reintentos/webhooks;
- validación y rechazo de pagos;
- idempotencia y vínculo con membresías;
- reembolsos totales o parciales por producto/cantidad;
- devolución con o sin reposición al inventario;
- reportes y detalle frontend.

## Pendiente

- concurrencia y conciliación financiera de extremo a extremo;
- cobertura de todos los permisos y UUID ajenos (`GEN-001`);
- E2E crítico de venta–pago–inventario–devolución (`GEN-004`).
## Estado vigente del comercio de membresías

Las renovaciones y cambios de plan siguen registrados en Sales/Payments y, por
lo tanto, alimentan movimientos de dinero, métricas y cortes de caja. El
cotizador de membresías entrega precio de lista, descuento y total; las líneas
de venta y pagos conservan esos valores en lugar de reemplazar el precio de
lista por un total ya descontado. El alta genérica rechaza líneas
`MEMBERSHIP_PLAN` con `SALE_MEMBERSHIP_OPERATION_REQUIRED`; las operaciones
específicas siguen persistiendo en las mismas tablas financieras.

V55 agrega `membership_sale_benefit`: el detalle financiero conserva los
beneficios aplicados aunque cambie su definición. Los listados y el reporte
exponen `businessContext` y un desglose separado para venta minorista, alta,
renovación y cambio de plan. La creación específica usa inserción idempotente
atómica, probada con dos solicitudes PostgreSQL concurrentes.

V93 incorpora el cargo de inscripción a la cotización y al total conciliado de
la primera alta. Se persiste como una partida `REGISTRATION_FEE` separada de
`MEMBERSHIP_PLAN`; los beneficios descuentan el plan, no la inscripción. Importe
y concepto participan en el hash de idempotencia. V94 unifica la decisión
temporal: la reinscripción usa exclusivamente `inactivityMonths`, incluso si la
baja automática está desactivada; no cambia el modelo financiero ni el desglose.

V95 no cambia el modelo financiero. Una variante continúa siendo el mismo tipo
de `catalog_product` que las ventas ya referencian; familia y códigos alias sólo
mejoran la captura y resolución del artículo. `sale_item` conserva `product_id`
y snapshot comercial, por lo que sabores, precios y devoluciones no se mezclan.
Los defaults del alta masiva se copian a cada variante antes de persistirla; las
ventas nunca resuelven precio o costo desde la familia.
