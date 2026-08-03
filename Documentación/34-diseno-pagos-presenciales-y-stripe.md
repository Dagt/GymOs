# Diseño de pagos presenciales y Stripe

## Decisión funcional

GymOS separa los cobros presenciales de recepción/POS de los cobros autónomos del kiosco.

### Recepción y punto de venta

Recepción y POS admiten dos modalidades de tarjeta:

1. `MERCADO_PAGO_POINT`: GymOS envía el importe y la referencia de la venta a una terminal Mercado Pago Point configurada en modo PDV. La venta sólo se confirma con el resultado consultado o notificado por Mercado Pago.
2. `EXTERNAL_TERMINAL_MANUAL`: el cajero captura el importe directamente en cualquier terminal bancaria externa y después registra en GymOS la referencia, autorización y confirmación. Nunca se capturan PAN, CVV, NIP ni fecha de vencimiento.

Ambas modalidades son configurables por organización y sucursal. Sólo cuentas con rol de dueño o administración pueden habilitarlas, deshabilitarlas o cambiar sus parámetros. Los cajeros únicamente pueden utilizar los modos que estén habilitados.

Mercado Pago Point se integra mediante API Orders. Cada terminal integrada debe estar asociada a una sucursal/caja de Mercado Pago y operar en modo `PDV`. GymOS envía una orden idempotente a una terminal concreta, consulta su estado, permite cancelar órdenes pendientes y concilia el resultado.

### Kiosco

El kiosco utiliza Stripe para:

- alta inicial de membresía;
- renovación;
- cambio de plan;
- pagos de membresía habilitados para autoservicio;
- configuración y operación de domiciliación.

La domiciliación sólo puede iniciarse, autorizarse, modificarse o revocarse desde una sesión verificada de kiosco. No se expone ninguna operación equivalente en recepción o POS.

El administrador o dueño decide por sucursal si el pago con Stripe y la domiciliación están habilitados para kiosco. Recepción no puede alterar esta decisión durante el cobro.

### Reglas de seguridad

- GymOS nunca almacena PAN, CVV o NIP.
- Los secretos se resuelven del gestor de secretos o variables del entorno mediante referencias; no se almacenan en texto plano en configuración funcional.
- Toda creación de cobro usa llave de idempotencia.
- Una venta o membresía sólo se aplica después de confirmación del proveedor o, en terminal externa manual, después de confirmación explícita y auditada del cajero.
- Los webhooks se validan antes de modificar ventas, membresías o mandatos.
- La configuración y sus cambios quedan auditados.

## Configuración por sucursal

La configuración define:

- Mercado Pago Point habilitado;
- referencia segura del Access Token;
- identificador de terminal Point;
- terminal externa manual habilitada;
- Stripe habilitado para kiosco;
- referencia segura de Stripe Secret Key;
- referencia segura de Stripe Webhook Secret;
- domiciliación Stripe habilitada para kiosco;
- modo sandbox/producción.

Una organización con varias sucursales puede tener parámetros y terminales diferentes en cada una.

## Flujo Mercado Pago Point

1. POS crea o recupera la venta.
2. GymOS valida que Point esté habilitado en la sucursal.
3. GymOS crea una orden `point` con terminal, importe, descripción, referencia externa e idempotencia.
4. La terminal recibe automáticamente el cobro.
5. GymOS conserva el identificador externo y estado pendiente.
6. Al aprobarse, GymOS registra el pago y aplica la venta.
7. Rechazo, cancelación o expiración no aplican la venta.

## Flujo terminal externa manual

1. POS muestra el total calculado por backend.
2. El cajero captura exactamente ese total en la terminal física.
3. Tras aprobación, registra referencia y autorización en GymOS.
4. GymOS exige confirmación expresa, usuario responsable y coincidencia del importe.
5. Se registra el pago y se aplica la venta.

## Flujo Stripe del kiosco

1. El kiosco genera una cotización persistida.
2. GymOS crea una sesión de checkout Stripe usando sólo el importe de la cotización.
3. El socio completa el pago en componentes alojados por Stripe.
4. El retorno del navegador no confirma la operación.
5. El webhook Stripe validado confirma importe, moneda y estado.
6. GymOS consume la cotización una sola vez y aplica la operación.

## Domiciliación Stripe

1. El socio inicia el flujo desde kiosco verificado.
2. GymOS crea o reutiliza un Customer de Stripe y una sesión de Setup/Checkout.
3. Stripe tokeniza el método y devuelve referencias externas.
4. El webhook validado activa el mandato.
5. Los cobros futuros usan el método tokenizado; GymOS no almacena datos de tarjeta.
6. La revocación desde kiosco desactiva el mandato y evita nuevos cobros.

La recepción puede consultar el estado de una domiciliación cuando sus permisos lo permitan, pero no crearla ni modificarla.
