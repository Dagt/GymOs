# Diagnóstico del kiosco

## No abre la sesión

1. Confirma que el usuario es Recepción y posee `selfservice.session.launch`.
   Dueño/gerente configuran el kiosco, pero no deben abrirlo.
2. Selecciona organización y sucursal activa.
3. Abre desde Recepción y permite la nueva pestaña; una URL directa a
   `/kiosk/reception` se rechaza sin el handoff de un solo uso.
4. Verifica la capacidad habilitada en Configuración para esa sucursal.
5. Revisa reloj local/backend, rate limit y estado `blocked-until`.

## Verificación o sesión rechazada

- Enviar `X-Self-Service-Session-Token` después de verificar.
- La identidad expira tras tres minutos de inactividad; la ejecución del
  navegador tiene máximo de doce horas.
- No revelar si un correo/teléfono existe. Mantener respuestas genéricas.
- Revisar `GYMOS_TRUSTED_PROXIES`; no confiar en cabeceras de clientes públicos.

## Stripe se queda esperando

1. Confirma backend y red antes de culpar al listener: Stripe CLI sólo recibe
   webhooks después de que Checkout crea/termina una operación.
2. Comprueba `sk_test_...`, cuenta conectada, modalidad habilitada y URLs de retorno.
3. Confirma que el listener usa `--forward-connect-to` y que el backend arrancó
   con el mismo `whsec_...`.
4. Revisa historial técnico de operaciones/webhooks y el `correlationId`.
5. No repitas manualmente efectos de membresía por una pantalla de retorno.

## Plan multi-sucursal

La membresía conserva sucursal de origen. Con `multiBranchAccess=true`, el socio
debe ser reconocido y reservar recursos elegibles en otra sucursal activa del
mismo tenant. Si no ocurre, revisar asociación del plan, estado/vigencia,
tenant, capacidad de reservas y restricciones del recurso.

## Pagos y domiciliación

Usa primero los historiales tenant-scoped de Configuración: operaciones,
mandatos, cargos recurrentes y webhooks. No inspecciones ni copies payloads con
datos sensibles.

Métricas principales:

- `gymos.payments.recurring.worker`
- `gymos.payments.recurring.charges`
- `gymos.payments.webhooks`
- `gymos.payments.reconciliation`
- `gymos.payment.operations`
- `gymos.recurring.expired_leases`

## Pruebas

```powershell
cd backend
.\gradlew.bat postgresIntegrationTest
```

```powershell
$env:GYMOS_STRIPE_TEST_SECRET_KEY = "sk_test_..."
$env:GYMOS_STRIPE_TEST_ACCOUNT_ID = "acct_..."
cd backend
.\gradlew.bat stripeTest
```

La suite Stripe rechaza llaves live y se omite sin secreto. Guía completa:
[`../testing/stripe-kiosk-test-mode.md`](../testing/stripe-kiosk-test-mode.md).

## Recuperación de un pago Stripe (#34)

Si Checkout se interrumpe, solicita al socio que se verifique de nuevo en la
sucursal donde se originó el pago. Desde **Mis pagos → Continuar pago** puede
reabrir el pago original si su estado lo permite. Si ya se está conciliando,
espera el estado del proveedor antes de repetir la compra. El mensaje de
cancelación en el navegador indica que se salió de Checkout.

Si no se pudo cerrar la sesión por falta de red, abre un kiosco nuevo desde
Recepción. Si se cerró el navegador o expiró el modo kiosco, se requiere
igualmente un nuevo lanzamiento. Para investigar una operación, usa el
identificador interno de venta/pago y el historial técnico. No copies
credenciales ni datos personales en el ticket.
