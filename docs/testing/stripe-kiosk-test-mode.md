# Pruebas de Stripe para kiosco y domiciliación

Esta guía separa dos verificaciones diferentes: el contrato automatizado contra
la API sandbox de Stripe y el recorrido manual de GymOS de extremo a extremo.
Nunca uses llaves ni tarjetas reales.

## 1. Suite automatizada contra Stripe test

La suite usa variables exclusivas de prueba; no inicia la aplicación ni depende
de la configuración guardada de un gimnasio.

```powershell
$env:GYMOS_STRIPE_TEST_SECRET_KEY = "sk_test_..."
$env:GYMOS_STRIPE_TEST_ACCOUNT_ID = "acct_..." # cuenta Connect test; opcional para pruebas de plataforma
cd backend
.\gradlew.bat stripeTest
```

La suite se omite si falta `GYMOS_STRIPE_TEST_SECRET_KEY` y rechaza por diseño
una llave live. Verifica:

- creación idempotente y recuperación después de una respuesta incierta;
- confirmación off-session exitosa;
- estado `requires_action`;
- rechazo por fondos insuficientes.

## 2. Preparar la aplicación local

Para el recorrido completo, el backend usa variables distintas a las de la
suite:

```powershell
$env:GYMOS_STRIPE_SECRET_KEY = "sk_test_..."
$env:GYMOS_STRIPE_WEBHOOK_SECRET = "whsec_..." # se obtiene al iniciar Stripe CLI
$env:GYMOS_STRIPE_RETURN_URL = "http://localhost:3000/settings/organization?payment=stripe-return"
$env:GYMOS_STRIPE_REFRESH_URL = "http://localhost:3000/settings/organization?payment=stripe-refresh"
```

Inicia PostgreSQL, Redis, backend y frontend con el procedimiento habitual.
Después abre otra terminal, autentica Stripe CLI y reenvía los eventos de las
cuentas Connect al backend:

```powershell
stripe login
stripe listen --events checkout.session.completed,payment_intent.succeeded,payment_intent.payment_failed,charge.succeeded,charge.failed --forward-connect-to http://localhost:8080/api/v1/payment-webhooks/stripe
```

Copia el `whsec_...` mostrado por ese comando a
`GYMOS_STRIPE_WEBHOOK_SECRET` **antes de iniciar o reiniciar el backend**. La
aplicación verifica la firma; un secreto de otro listener debe ser rechazado.

## 3. Configurar el gimnasio

1. Entra como dueño, gerente autorizado o administrador de soporte y selecciona
   la sucursal que vas a probar.
2. Ve a `Configuración` → `Medios de cobro`.
3. Selecciona `Vincular Stripe`, completa el onboarding de la cuenta Express en
   modo test y usa `Verificar conexión de Stripe`.
4. Activa `Stripe para self checkout` y, para renovaciones automáticas,
   `Domiciliación con Stripe`. Guarda.
5. Ve a `Kiosco web`, activa en la sucursal actual las operaciones que deseas
   probar y guarda.
6. Crea un plan activo asociado a esa sucursal y un socio con fecha de
   nacimiento. Asigna la membresía o deja al socio sin ella según el caso.
7. Desde `Inicio recepción`, selecciona `Abrir kiosco seguro` y verifica al socio
   con contacto y fecha de nacimiento.

## 4. Tarjetas y escenarios manuales

En Checkout usa cualquier fecha futura y cualquier CVC de tres dígitos:

| Escenario | Tarjeta test | Resultado esperado |
| --- | --- | --- |
| Pago exitoso | `4242 4242 4242 4242` | Stripe confirma el pago; GymOS activa o extiende una sola vez. |
| Autenticación 3DS interactiva | `4000 0025 0000 3155` | Checkout o Setup solicita autenticación cuando corresponde. Si se autoriza correctamente para uso futuro, Stripe puede permitir cargos posteriores. |
| Fondos insuficientes en Checkout | `4000 0000 0000 9995` | El pago inmediato se rechaza y no extiende la membresía. Esta tarjeta no debe usarse para alta de domiciliación porque Stripe no permite adjuntarla. |
| Falla después de guardar la tarjeta | `4000 0000 0000 0341` | La tarjeta se adjunta durante Setup, pero el cargo posterior se rechaza; permite probar recuperación y sustitución del método. |

Recorridos mínimos:

1. Contrata, renueva y cambia un plan desde `Membresías`.
2. Comprueba en `Pagos` que el estado y el recibo corresponden a Stripe.
3. Reenvía un webhook desde Stripe Workbench/CLI y confirma que no duplica la
   venta, el pago ni la extensión de membresía.
4. En `Domiciliación`, acepta el consentimiento, tokeniza la tarjeta y comprueba
   marca, últimos cuatro, siguiente fecha y estado del mandato.
5. Ejecuta el ciclo recurrente con la tarjeta exitosa y después con
   `4000 0000 0000 0341`. Comprueba reintentos, estado `PAST_DUE`, recuperación
   y sustitución. El caso off-session `requires_action` se reproduce de forma
   determinista en `stripeTest` mediante el PaymentMethod sandbox
   `pm_card_authenticationRequired`.
6. Sustituye la tarjeta y revoca el mandato desde el kiosco.
7. Revisa `Configuración` → `Historial técnico`: cargos, mandatos y webhooks
   deben estar paginados, aislados al gimnasio y sin datos sensibles.

## 5. Recuperación e idempotencia

Para verificar recuperación real:

1. deja que el webhook llegue al backend y detén el proceso antes de que el
   worker termine la conciliación;
2. reinicia el backend y espera al worker de recuperación;
3. comprueba que la operación termina una vez y conserva la misma venta, pago,
   cargo y periodo;
4. repite el mismo evento y confirma que el historial registra la recepción sin
   crear otro efecto financiero.

Para concurrencia y leasing con PostgreSQL real:

```powershell
cd backend
.\gradlew.bat postgresIntegrationTest
```

Los errores operativos deben aparecer en `Historial técnico` y en las métricas
tenant-scoped; los detalles de recuperación están en
[`../operations/stripe-automation-recovery.md`](../operations/stripe-automation-recovery.md).
