# Cobranza comercial de GymFort

Estado: `ESTABILIZADO / ADMINISTRACIÓN COMERCIAL MANUAL`
Última revisión: 24 de agosto de 2026
Código: `backend/.../platformadmin`, `backend/.../platformbilling`, `/platform`,
`/platform/gymnasiums`, `/platform/billing`, migraciones V81, V82 y V85.

## Propósito

Gestionar lo que cada gimnasio cliente paga a GymFort. Este módulo es distinto
de ventas, membresías y pagos del gimnasio hacia sus propios socios.

`Organization` representa el cliente comercial y cada `Branch` contratada es
una línea facturable. Así un cliente puede iniciar con una sede y contratar una
segunda con plan, fecha e importe propios; el panel agrega ambas para mostrar
el total mensual del cliente.

## Implementado

- inicio ejecutivo exclusivo de `Administrador GymOS` en `/platform`, accesible
  desde el menú lateral y sin depender de banners dentro de Configuración;
- cartera por gimnasio con salud derivada, etapa comercial, responsable GymFort,
  contacto de cobranza, contrato/renovación, términos, seguimiento y notas;
- centro de acción que prioriza vencidos, clientes en riesgo, altas incompletas y
  seguimientos; exportación CSV de la cartera visible;
- navegación explícita `Resumen de cartera` / `Gimnasios cliente` / `Cobros y contratos`;
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
- clave idempotente única por sucursal para que el reintento de un mismo registro
  devuelva el cobro original sin duplicar historial ni avanzar dos veces el corte;
- control de versión optimista en expediente y configuración comercial para
  evitar que dos pestañas sobrescriban cambios silenciosamente;
- `last_payment_date` y `last_payment_amount` sólo se derivan del historial de
  cobros; la edición de configuración ya no puede alterarlos;
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
- `/api/v1/platform/customers/**` usa la misma doble barrera y registra
  `PLATFORM_CUSTOMER_ACCOUNT_UPDATED`.
- El selector excluye la organización técnica de GymFort.
- Las cantidades de operación se consultan con contexto de organización y
  sucursal; no exponen información personal de socios.
- El historial limita inicialmente la consulta a los 50 pagos más recientes por
  sucursal; el CSV actual es operativo por cliente y no sustituye un reporte
  financiero paginado por periodo.

## Siguiente evolución

- catálogo administrable de precios, descuento, impuestos y reglas de
  facturación;
- cargos automáticos de suscripción SaaS y conciliación bancaria/Stripe;
- CFDI e integración contable;
- notas de crédito, ajustes, vencimiento y recordatorios de cobranza;
- reporte financiero por rango y tableros históricos de churn y cohortes.
