# 31. Diseño rector de Automatizaciones y Kiosco

Estado: IMPLEMENTADO / CIERRE FUNCIONAL DE AUTOMATIZACIONES Y EMAIL
Corte: 22 de agosto de 2026
Base integrada: PR #10; rama `feature/automations-zoho-email`

## 1. Decisión central

Automatizaciones orquesta; Kiosco presenta y solicita; los módulos propietarios deciden y ejecutan. Automatizaciones no escribe directamente membresías, pagos o reservas. El kiosco no calcula precios, elegibilidad, vigencias ni aprueba pagos.

El kiosco es un perfil lógico de capacidades por sucursal, no una computadora registrada. Sólo Recepción inicia el handoff desde una sesión autenticada. La pestaña aislada elimina credenciales administrativas y conserva únicamente un token temporal de autoservicio.

## 2. Fronteras

- `selfservice`: sesión, verificación, privacidad, capacidades y coordinación.
- `payments`: proveedores, Checkout/Setup, webhooks, conciliación y recurrencia.
- `memberships`: cotización, alta, renovación, cambio, periodos y vigencia.
- `eligibility`: plan, sucursal, beneficios y acceso efectivo.
- `bookings`/`wellness`: disponibilidad y creación de reservas/citas.
- `automations`: reglas, eventos, ejecución y recuperación.
- `notifications`: mensajes internos y entrega externa durable.

La comunicación usa contratos públicos/eventos; las capas de aplicación no consultan tablas ajenas directamente.

## 3. Seguridad del kiosco

- `selfservice.session.launch` pertenece exclusivamente a Recepción. V78 elimina concesiones heredadas de dueño, administrador, gerente y soporte y el backend exige rol puro de Recepción.
- Handoff de navegador de un solo uso sin IDs ni secretos en URL.
- Verificación por contacto + fecha de nacimiento.
- PII enmascarada, respuestas `no-store` y reset de sesión.
- Inactividad de tres minutos; ejecución máxima de doce horas.
- Rate limit por perfil lógico, IP efectiva e identificador anonimizado.
- `Forwarded`/`X-Forwarded-For` sólo se confían desde `GYMOS_TRUSTED_PROXIES`.

## 4. Capacidades por sucursal

Cada sucursal puede habilitar contratación, renovación, cambio de plan, domiciliación, reservas, wellness y actualización de contacto. Activar una capacidad no sustituye tener listo el proveedor requerido.

Un plan `multiBranchAccess=true` conserva la sucursal de origen, pero permite reconocimiento/elegibilidad/reserva en otras sucursales activas del tenant.

## 5. Comercio y Stripe

1. Kiosco solicita cotización autoritativa.
2. Muestra plan/lista/descuentos/total.
3. Con aceptación explícita crea venta/pago pendiente e idempotente.
4. Stripe Checkout captura tarjeta; GymFort no recibe PAN/CVV.
5. Redirect sólo indica verificación.
6. Webhook firmado + consulta autoritativa concilian contexto.
7. Éxito produce exactamente una venta, pago y efecto de dominio.

## 6. Domiciliación

- consentimiento versionado;
- Checkout `mode=setup`;
- sólo referencias opacas, marca y últimos cuatro;
- un cargo materializado por vigencia;
- off-session con webhook/conciliación y reintentos;
- `PAST_DUE`, `requires_action`, sustitución de tarjeta y revocación;
- extensión idempotente sólo después del pago confirmado.

Para Automatizaciones se distinguen claramente:

- activación inicial del mandato;
- fecha de una ocurrencia (`RECURRING_CHARGE_DUE`);
- cobro exitoso (`RECURRING_CHARGE_SUCCEEDED`);
- cobro fallido (`RECURRING_CHARGE_FAILED`).

La activación inicial todavía no es un trigger configurable independiente.

## 7. Configuración y permisos de pagos

`payment.settings.manage` permite activar/desactivar terminal manual, Mercado Pago, Stripe Checkout y recurrencia. Gerencia puede usarlo con cuenta ya vinculada.

`payment.provider.stripe.manage` corresponde a dueño/soporte para conectar/refrescar Stripe Express.

## 8. Automatizaciones

Las reglas soportan ejecución manual, intervalo y eventos de pagos, membresías, domiciliación y reservas. Cada ocurrencia entra a un inbox durable con versión de regla e identidad idempotente.

El worker reclama con lease, reparte trabajo entre tenants, respeta pausa auditada, aplica backoff y termina en éxito o `DEAD_LETTER`.

### UX final

La consola del cliente no expone el motor técnico completo. La vista se divide en:

- **Automatizaciones**: reglas, estado, toggle, resumen y `Probar ahora`;
- **Actividad**: resultados comprensibles;
- **Problemas**: fallos accionables y reintentos.

Scheduler, leases, referencias técnicas, intentos y métricas permanecen en soporte/observabilidad, no como interfaz principal.

La creación es guiada **desde cero**, sin recetas/plantillas recomendadas. El usuario define trigger, condiciones, canal y mensaje.

### Prueba manual

Las reglas `EMAIL` exigen seleccionar un socio real mediante búsqueda incremental por nombre/correo. La prueba valida mensaje + destinatario + cola + proveedor sin crear operaciones falsas de dominio.

## 9. Permisos de Automatizaciones

- Dueño: lectura/gestión/ejecución.
- Gerente: V80 concede lectura/gestión/ejecución y gestión operativa de notificaciones.
- `automation.scheduler.manage`: permiso avanzado separado; no se concede por el simple hecho de administrar reglas.

## 10. Notificaciones y ZeptoMail

`notifications` posee la cola durable, preferencias, configuración tenant, branding, leasing, reintentos y métricas. ZeptoMail se conecta mediante `EmailDeliveryPort`; no existen credenciales del proveedor en reglas ni tablas tenant.

Patrón V1 recomendado:

```text
From: Nombre del gimnasio <notificaciones@gymfort.mx>
Reply-To: bandeja real del gimnasio
```

La dirección transaccional central no necesita buzón real. `gymfort.mx` quedó verificado en ZeptoMail mediante DKIM y CNAME/return-path y se confirmó entrega real el 22 de agosto de 2026.

### Branding del mensaje

El email toma `brandName`, `logoUrl`, `primaryColor`, `accentColor` y `surfaceColor` de la organización. El logo relativo se resuelve con `GYMOS_FRONTEND_BASE_URL`; clientes externos no pueden acceder a `localhost`, por lo que esa imagen se omite en desarrollo local.

## 11. Estado y deuda posterior

El alcance de Automatizaciones + email transaccional queda funcionalmente cerrado. Trabajo posterior no bloqueante:

- trigger independiente de **domiciliación activada** si producto lo requiere;
- webhooks de bounce/complaint de ZeptoMail;
- seguimiento productivo de reputación/DMARC;
- nuevos canales (WhatsApp/push) detrás de `notifications`;
- mantener suites PostgreSQL/E2E/Prometheus en CI.

La aceptación financiera conectada de Stripe continúa como deuda separada del cierre de Automatizaciones/email.

## 12. Evidencia

- ZeptoMail real: dominio verificado y entregas recibidas.
- prueba de configuración y prueba de regla con socio real.
- V80 corrige permisos de Gerencia.
- V79 repara perfiles históricos de Coach y es deuda técnica adyacente, no parte del motor de Automatizaciones.
- interfaz simplificada y alineada con estilos compartidos de GymFort.

Runbooks: `docs/operations/zeptomail-email.md`, `docs/operations/email-delivery.md` y `docs/testing/automations-kiosk-acceptance.md`.
