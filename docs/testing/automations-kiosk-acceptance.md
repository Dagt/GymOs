# Aceptación de automatizaciones y kiosco

Corte: 21 de agosto de 2026

## 1. Roles y entradas

| Perfil | Entrada | Alcance |
|---|---|---|
| Dueño | Configuración y Automatizaciones | Cuenta Stripe, modalidades, kiosco, correo y reglas |
| Gerente | Configuración y Automatizaciones | Modalidades ya conectadas, capacidades, correo y operación permitida; no administra la cuenta Stripe ni abre kiosco |
| Recepción | `Inicio recepción` → `Abrir kiosco seguro` | Abre la pestaña aislada y conserva su consola |
| Soporte | Modo soporte auditado | Mismo alcance técnico mediante `platform.admin` |
| Socio | Pestaña `/kiosk/reception` | Sesión temporal sin credenciales administrativas |

## 2. Preparación

1. Selecciona organización y sucursal.
2. En Configuración habilita las modalidades de cobro. Stripe sólo puede
   encenderse cuando dueño/soporte ya vinculó y verificó la cuenta.
3. Configura las capacidades del kiosco para la sucursal activa.
4. Crea planes/productos/recursos activos y socios con fecha de nacimiento.
5. Para correo, configura SMTP del backend y después remitente/reply-to tenant.
6. Para Stripe local, inicia el listener CLI y reinicia backend con el mismo
   `whsec_...`; consulta `stripe-kiosk-test-mode.md`.

## 3. Kiosco y privacidad

1. Desde Recepción abre el kiosco. Verifica que Gerencia no tenga esa acción.
2. Confirma que una URL directa sin handoff no crea sesión.
3. Verifica al socio por contacto y fecha de nacimiento.
4. Comprueba PII enmascarada, `no-store`, inactividad y cierre/borrado manual.
5. Verifica que no existen rutas o credenciales de POS/CRM/configuración.
6. Finaliza y confirma que la pantalla queda lista para otro socio.

## 4. Membresías y Stripe Checkout

Recorrer por separado:

- socio sin membresía: contratar plan;
- membresía vigente/vencida: renovar;
- membresía activa: cambiar plan;
- recurso con costo: pagar reserva cuando corresponda;
- doble clic, reintento y regreso repetido.

En todos los casos debe mostrarse cotización final. Antes de webhook no cambia
la membresía/reserva. Después del webhook confirmado debe existir una sola
venta, un pago, un recibo cuando Stripe lo provee y un solo efecto de dominio.
El retorno usa el sistema visual GymFort y explica la verificación; no afirma
éxito por sí solo.

## 5. Planes multi-sucursal

1. Asigna al socio un plan `multiBranchAccess=true` originado en sucursal A.
2. Abre el kiosco desde Recepción en sucursal B del mismo tenant.
3. Confirma que encuentra al socio, reconoce su membresía y muestra pagos
   permitidos sin mezclar otro tenant.
4. Reserva un recurso elegible de B y confirma que la reserva pertenece a B.
5. Cotiza un plan local de B: la nueva operación debe usar B y no mutar la
   membresía original antes del pago.
6. Repite con plan no multi-sucursal y confirma rechazo/ocultamiento en B.

## 6. Domiciliación

1. Obtén términos vigentes y acepta expresamente.
2. Completa Setup alojado; confirma que GymFort sólo muestra marca y últimos cuatro.
3. Reenvía el webhook y verifica que no duplica mandato.
4. Ejecuta cargo exitoso y confirma una sola extensión.
5. Prueba rechazo, reintentos, `PAST_DUE` y `requires_action`.
6. Sustituye la tarjeta y recupera el mandato.
7. Revoca y confirma que no se materializan nuevos cargos.

## 7. Historial de pagos

- Estados claros: pendiente, pagado, rechazado y comprobante.
- Intentos pendientes repetidos no deben aparentar cargos completados.
- `Continuar pago` sólo aparece con una URL todavía válida.
- Los historiales técnicos de mandatos/cargos/webhooks están paginados y
  aislados al tenant, sin PAN, CVV, secretos ni payloads sensibles.

## 8. Automatizaciones

1. Crea una regla desde receta y revisa trigger, destinatario, contenido y
   `retryLimit`.
2. Ejecuta manualmente y confirma historial/entrega.
3. Genera eventos de pago, membresía, domiciliación y reserva.
4. Repite un evento y confirma una sola ocurrencia/efecto por identidad.
5. Fuerza fallo, backoff y `DEAD_LETTER`; corrige la causa y reintenta con motivo.
6. Pausa un tenant y confirma que otros continúan; reanuda con auditoría.
7. Verifica backlog, leases, duración y resultado en métricas.

## 9. Correo

1. Configura SMTP y remitente tenant; envía prueba.
2. Confirma transición `QUEUED` → `PROCESSING` → `SENT`.
3. Simula timeout hasta `DEAD_LETTER` y recupera manualmente.
4. Confirma preferencia/supresión y destinatarios sanitizados.
5. Para Zoho, además valida TLS, SPF, DKIM, DMARC y entrega real. Este último
   recorrido permanece pendiente mientras no se implemente/conecte Zoho.

## 10. Comandos

```powershell
cd backend
.\gradlew.bat test
.\gradlew.bat postgresIntegrationTest

cd ..\frontend
npm.cmd test
npm.cmd run lint
npm.cmd run typecheck
npm.cmd run build
npm.cmd run test:e2e
```

`stripeTest` requiere `GYMOS_STRIPE_TEST_SECRET_KEY=sk_test_...`; rechaza llaves
live y debe ejecutarse con una cuenta Connect test para reproducir la topología
completa.
