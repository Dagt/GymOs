# Ventas, pagos y devoluciones

Estado: `FUNCIONAL`

## Implementado

- ventas con partidas e instantáneas comerciales;
- pagos manuales en efectivo, transferencia y tarjeta;
- validación y rechazo de pagos;
- idempotencia y vínculo con membresías;
- reembolsos totales o parciales por producto/cantidad;
- devolución con o sin reposición al inventario;
- reportes y detalle frontend.

## Pendiente

- concurrencia y conciliación financiera de extremo a extremo;
- cobertura de todos los permisos y UUID ajenos (`GEN-001`);
- E2E crítico de venta–pago–inventario–devolución (`GEN-004`).
## Evolución en `feature/reception-pos-init`

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
