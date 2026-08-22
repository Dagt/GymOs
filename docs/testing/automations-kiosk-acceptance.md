# Aceptación de automatizaciones y kiosco

Corte: 22 de agosto de 2026

## 1. Roles y entradas

| Perfil | Entrada | Alcance |
|---|---|---|
| Dueño | Configuración y Automatizaciones | Cuenta Stripe, modalidades, kiosco, correo y reglas |
| Gerente | Configuración y Automatizaciones | Modalidades ya conectadas, capacidades, correo, creación/administración/prueba de automatizaciones; no administra la cuenta Stripe ni abre kiosco |
| Recepción | `Inicio recepción` → `Abrir kiosco seguro` | Abre la pestaña aislada y conserva su consola |
| Soporte | Modo soporte auditado | Mismo alcance técnico mediante `platform.admin` |
| Socio | Pestaña `/kiosk/reception` | Sesión temporal sin credenciales administrativas |

V80 concede a Gerencia `automation.read`, `automation.manage`, `automation.execute`, `notification.read` y `notification.manage`. Después de aplicar la migración debe renovarse la sesión/JWT.

## 2. Preparación

1. Seleccionar organización y sucursal.
2. En Configuración habilitar modalidades de cobro. Stripe sólo puede encenderse cuando dueño/soporte ya vinculó y verificó la cuenta.
3. Configurar capacidades del kiosco para la sucursal activa.
4. Crear planes/productos/recursos activos y socios con fecha de nacimiento.
5. Para correo, configurar ZeptoMail SMTP en backend y después `From`/nombre/`Reply-To` tenant.
6. Para Stripe local, iniciar listener CLI y reiniciar backend con el mismo `whsec_...`; consultar `stripe-kiosk-test-mode.md`.

## 3. Kiosco y privacidad

1. Desde Recepción abrir el kiosco. Confirmar que Dueño/Gerente no tienen esa acción.
2. Confirmar que URL directa sin handoff no crea sesión.
3. Verificar socio por contacto y fecha de nacimiento.
4. Comprobar PII enmascarada, `no-store`, inactividad y cierre/borrado manual.
5. Confirmar ausencia de rutas/credenciales POS/CRM/configuración.
6. Finalizar y verificar pantalla limpia para el siguiente socio.

## 4. Membresías y Stripe Checkout

Recorrer por separado:

- socio sin membresía: contratar plan;
- membresía vigente/vencida: renovar;
- membresía activa: cambiar plan;
- doble clic, reintento y regreso repetido.

Antes del webhook no cambia la membresía. Después del webhook confirmado debe existir una sola venta, pago, recibo cuando Stripe lo provee y un solo efecto de dominio. El retorno del navegador nunca confirma éxito por sí solo.

## 5. Planes multi-sucursal

1. Asignar plan `multiBranchAccess=true` originado en A.
2. Abrir kiosco desde Recepción en B del mismo tenant.
3. Confirmar reconocimiento del socio/membresía sin mezclar otro tenant.
4. Reservar recurso elegible de B y confirmar sucursal B.
5. Cotizar plan local de B sin mutar la membresía original antes del pago.
6. Repetir con plan no multi-sucursal y confirmar rechazo/ocultamiento en B.

## 6. Domiciliación

1. Obtener términos vigentes y aceptar expresamente.
2. Completar Setup alojado; confirmar que GymFort sólo muestra marca/últimos cuatro.
3. Reenviar webhook y verificar idempotencia.
4. Ejecutar cargo exitoso y confirmar una sola extensión.
5. Probar rechazo, reintentos, `PAST_DUE` y `requires_action`.
6. Sustituir tarjeta y recuperar mandato.
7. Revocar y confirmar que no se materializan nuevos cargos.

Importante para Automatizaciones: activar una domiciliación y llegar a la fecha de cobro son eventos distintos. `RECURRING_CHARGE_DUE` se dispara cuando llega la fecha programada de una ocurrencia; no al vincular inicialmente el mandato.

## 7. Automatizaciones - interfaz

1. Entrar como Dueño y Gerente; ambos deben ver y administrar reglas.
2. Confirmar pestañas **Automatizaciones / Actividad / Problemas**.
3. Crear una regla desde cero; no deben mostrarse plantillas/recetas recomendadas.
4. Confirmar que la card de una regla contiene toggle y `Probar ahora` directamente, sin menú `Opciones` intermedio.
5. Activar/desactivar y comprobar persistencia.
6. Verificar que logs técnicos de scheduler/leases/intentos no saturan la vista normal.

## 8. Probar ahora - email

1. Pulsar `Probar ahora` en una regla `EMAIL`.
2. Confirmar que aparece búsqueda por nombre/correo, no un selector con todos los socios.
3. Escribir al menos dos caracteres y verificar resultados incrementales.
4. Confirmar que sólo se muestran socios activos con correo.
5. Seleccionar un socio y enviar.
6. Confirmar aparición en **Actividad** y recepción en la bandeja.
7. Confirmar que la prueba no crea ventas, pagos, reservas ni membresías falsas.

La búsqueda debe ser usable sin scroll anidado en la lista; si el contenido excede el viewport, sólo el drawer completo debe desplazarse.

## 9. Eventos reales de automatización

Probar al menos:

- `PAYMENT_COMPLETED`;
- `MEMBERSHIP_EXPIRING`;
- `MEMBERSHIP_EXPIRED`;
- reserva creada/confirmada/cancelada;
- `BOOKING_REMINDER`;
- `RECURRING_CHARGE_DUE`;
- `RECURRING_CHARGE_SUCCEEDED`;
- `RECURRING_CHARGE_FAILED`.

Repetir un evento y confirmar una sola ocurrencia/efecto por identidad idempotente.

## 10. Fallos y recuperación

1. Forzar un fallo recuperable y comprobar `RETRY_SCHEDULED`/backoff.
2. Agotar intentos y comprobar `DEAD_LETTER`.
3. En UI, confirmar que aparece en **Problemas** con lenguaje operativo.
4. Corregir la causa y reintentar.
5. Pausar un tenant mediante permiso avanzado y confirmar que otros continúan.
6. Verificar backlog, leases y resultados en métricas/Actuator.

## 11. ZeptoMail

1. Verificar dominio `gymfort.mx` con DKIM y CNAME/return-path.
2. En Cloudflare, dejar el CNAME de bounce como **DNS only**.
3. Configurar `smtp.zeptomail.com:587`, STARTTLS y credenciales del Mail Agent.
4. Autorizar `notificaciones@gymfort.mx`.
5. Configurar un gimnasio con nombre visible y `Reply-To` real.
6. Enviar prueba desde Configuración y confirmar recepción.
7. Probar una regla con `Probar ahora` y confirmar entrega.
8. Revisar Processed emails y headers SPF/DKIM/DMARC.
9. Confirmar que `Responder` apunta al `Reply-To` tenant.
10. Confirmar branding visual del gimnasio.

Evidencia actual: al 22 de agosto de 2026 DKIM/CNAME de `gymfort.mx` quedaron verificados y se recibieron correos reales desde ZeptoMail.

## 12. Logo del email

En producción `GYMOS_FRONTEND_BASE_URL` debe ser público (`https://gymfort.mx`) para que Gmail/Outlook descarguen `/api/tenant-logo/...`. En local, una URL `localhost` no es accesible por el cliente de correo; el renderer omite el `<img>` en ese caso.

## 13. Comandos

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

`stripeTest` requiere `GYMOS_STRIPE_TEST_SECRET_KEY=sk_test_...`; rechaza llaves live y debe ejecutarse con cuenta Connect test para reproducir la topología completa.

## 14. Criterio de cierre

Automatizaciones/email se considera aceptado para el alcance V1 cuando:

- Dueño y Gerente pueden crear/administrar/probar reglas;
- eventos reales producen una sola notificación;
- prueba manual usa socio real con email;
- cola/reintentos/dead letter funcionan;
- ZeptoMail entrega correo real con identidad tenant;
- no se exponen secretos ni logs técnicos innecesarios al cliente;
- documentación/runbook coincide con el comportamiento del código.
