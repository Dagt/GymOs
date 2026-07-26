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
