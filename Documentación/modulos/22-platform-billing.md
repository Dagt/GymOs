# Cobranza comercial de GymFort

Estado: `FUNCIONAL / COBRO MANUAL CONTROLADO`
Última revisión: 23 de agosto de 2026
Código: `backend/.../platformbilling`, `/platform/billing`, migraciones V81 y V82.

## Propósito

Gestionar lo que cada gimnasio cliente paga a GymFort. Este módulo es distinto
de ventas, membresías y pagos del gimnasio hacia sus propios socios.

`Organization` representa el cliente comercial y cada `Branch` contratada es
una línea facturable. Así un cliente puede iniciar con una sede y contratar una
segunda con plan, fecha e importe propios; el panel agrega ambas para mostrar
el total mensual del cliente.

## Implementado

- consola exclusiva de `Administrador GymOS` en `/platform/billing`;
- resumen de MRR configurado, clientes, sucursales, socios activos y usuarios
  internos activos;
- vista por cliente con total mensual e implementación pendiente;
- expediente por sucursal con socios activos, usuarios internos, número de
  planes de membresía activos, plan sugerido y contratado, importes, fecha de
  corte, próxima fecha y estado de cobro;
- escalones estándar GymFort 150/500/1000/2000 y cotización manual sólo para
  Enterprise;
- estados comerciales `ACTIVE`, `PENDING`, `PAUSED` y `CANCELLED`;
- historial inmutable de cobros recibidos por transferencia, efectivo, tarjeta,
  Stripe u otro medio, con referencia y notas;
- la mensualidad avanza automáticamente el corte al siguiente día ancla;
- la implementación queda liquidada cuando el acumulado de sus pagos cubre el
  importe acordado;
- auditoría `PLATFORM_BILLING_UPDATED` y
  `PLATFORM_BILLING_PAYMENT_RECORDED`.

## Límites deliberados V1

- El pago se registra después de que GymFort lo recibió/verificó; no se intenta
  cobrar automáticamente a la tarjeta o cuenta del gimnasio cliente.
- No emite CFDI ni factura fiscal.
- No hay reembolso, anulación, nota de crédito ni edición de una cobranza: esos
  movimientos deberán añadirse como ajustes contables trazables.
- La recomendación de plan no cambia el contrato ni el precio sin una decisión
  explícita del Administrador GymOS.

## Controles

- `/api/v1/platform/billing/**` exige `platform.admin` en la cadena de
  seguridad y en el servicio.
- El selector excluye la organización técnica de GymFort.
- Las cantidades de operación se consultan con contexto de organización y
  sucursal; no exponen información personal de socios.
- El historial limita inicialmente la consulta a los 50 pagos más recientes por
  sucursal; un reporte/exportación paginada es mejora posterior.

## Siguiente evolución

- catálogo administrable de precios, descuento, impuestos y reglas de
  facturación;
- cargos automáticos de suscripción SaaS y conciliación bancaria/Stripe;
- CFDI e integración contable;
- notas de crédito, ajustes, vencimiento y recordatorios de cobranza;
- reporte/exportación por rango y tablero de MRR, churn y cohortes.
