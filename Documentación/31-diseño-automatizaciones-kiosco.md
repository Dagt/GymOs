# 31. Diseño rector de Automatizaciones y Kiosco

Estado: IMPLEMENTADO / ACEPTACIÓN CONECTADA PENDIENTE
Corte: 21 de agosto de 2026
Base integrada: PR #10; rama siguiente `feature/automations-zoho-email`

## 1. Decisión central

Automatizaciones orquesta; Kiosco presenta y solicita; los módulos propietarios
deciden y ejecutan. Automatizaciones no escribe directamente membresías, pagos
o reservas. El kiosco no calcula precios, elegibilidad, vigencias ni aprueba
pagos.

El kiosco es un perfil lógico de capacidades por sucursal, no una computadora
registrada. Sólo Recepción inicia el handoff desde una sesión autenticada. La
pestaña aislada elimina credenciales administrativas y conserva únicamente un
token temporal de autoservicio.

## 2. Fronteras

- `selfservice`: sesión, verificación, privacidad, capacidades y coordinación.
- `payments`: proveedores, Checkout/Setup, webhooks, conciliación y recurrencia.
- `memberships`: cotización, alta, renovación, cambio, periodos y vigencia.
- `eligibility`: plan, sucursal, beneficios y acceso efectivo.
- `bookings`/`wellness`: disponibilidad y creación de reservas/citas.
- `automations`: reglas, eventos, ejecución y recuperación.
- `notifications`: mensajes internos y entrega externa durable.

La comunicación entre módulos usa contratos públicos/eventos; las capas de
aplicación no consultan tablas ajenas directamente.

## 3. Seguridad del kiosco

- `selfservice.session.launch` pertenece a Recepción; soporte de plataforma
  conserva acceso explícito. V76 lo retira de Gerencia.
- Handoff de navegador de un solo uso sin IDs ni secretos en la URL.
- Verificación genérica por correo/teléfono y fecha de nacimiento.
- PII enmascarada, respuestas `no-store` y acción para borrar la sesión.
- Inactividad verificada de tres minutos; ejecución máxima de doce horas.
- Rate limit por perfil lógico, IP efectiva e identificador anonimizado.
- `Forwarded`/`X-Forwarded-For` sólo se confían desde `GYMOS_TRUSTED_PROXIES`.
- Ninguna ruta del kiosco concede acceso a POS, CRM, reportes o configuración.

## 4. Capacidades por sucursal

Cada sucursal puede habilitar contratación, renovación, cambio de plan,
domiciliación, reservas, wellness y actualización de contacto. Activar una
capacidad no sustituye tener listo el proveedor requerido.

Un plan `multiBranchAccess=true` conserva la sucursal de origen de la membresía,
pero permite reconocer al socio, consultar su elegibilidad y reservar en otras
sucursales activas del mismo tenant. Si en una sede secundaria se contrata un
plan local, la nueva operación queda contextualizada allí y sólo se aplica tras
el pago confirmado.

## 5. Comercio y Stripe

1. El kiosco solicita una cotización autoritativa.
2. Muestra plan, lista, descuentos, total y operación propuesta.
3. Con aceptación explícita crea venta/pago pendiente e idempotente.
4. Stripe Checkout captura la tarjeta; GymFort no recibe PAN ni CVV.
5. El redirect sólo muestra que el resultado está siendo verificado.
6. Webhook firmado y consulta autoritativa concilian el contexto.
7. Un éxito produce exactamente una venta completada, un pago y el efecto de
   membresía/reserva; un fallo conserva un estado recuperable.

Las llaves idempotentes se conservan durante reintentos y doble clic. Los
historiales evitan presentar intentos redundantes como cargos completados.

## 6. Domiciliación

- Consentimiento versionado y aceptación expresa.
- Checkout `mode=setup` y método tokenizado.
- Sólo identificadores opacos, marca y últimos cuatro en GymFort.
- Un cargo materializado por vigencia, no una suscripción ciega.
- Cobro off-session con webhook/conciliación, reintentos y `PAST_DUE`.
- `requires_action`, sustitución de tarjeta, recuperación y revocación.
- Extensión idempotente únicamente después del pago confirmado.

## 7. Configuración y permisos de pagos

`payment.settings.manage` permite activar/desactivar terminal manual, Mercado
Pago, Stripe Checkout y recurrencia. Gerencia puede usar este permiso con una
cuenta ya vinculada.

V77 crea `payment.provider.stripe.manage` para conectar/refrescar la cuenta
Express. Ese permiso corresponde al dueño y soporte de plataforma; Gerencia no
administra cuentas Stripe.

## 8. Automatizaciones

Las reglas soportan ejecución manual, intervalo y eventos de pagos,
membresías, domiciliación y reservas. Cada ocurrencia entra a un inbox durable
con versión de regla e identidad idempotente.

El worker reclama con lease, reparte trabajo entre tenants, respeta pausa
auditada, aplica backoff y termina en éxito o `DEAD_LETTER`. La consola ofrece
recetas, vista previa, historial paginado, monitoreo, reintento y descarte con
motivo.

Las acciones vigentes son notificación interna y correo al socio cuando el
evento identifica un destinatario válido.

## 9. Correo

`notifications` posee cola durable SMTP, preferencias, supresión, configuración
tenant, prueba, leasing, reintentos y métricas. Zoho debe conectarse mediante el
puerto existente; el diseño no permite introducir credenciales del proveedor
en reglas, logs o tablas tenant.

## 10. Evidencia y cierre pendiente

El corte `88a6b58` pasó suite backend, 77 pruebas frontend, lint, typecheck,
build, 8 E2E de ruteo, Compose y ejecución real de Stripe CLI `v1.50.3`.
Recorridos manuales Stripe test confirmaron Checkout, webhook, recibo y
domiciliación.

Para cerrar aceptación financiera faltan:

- ejecutar `stripeTest` con `GYMOS_STRIPE_TEST_SECRET_KEY` y cuenta Connect test
  dentro de CI protegido;
- ampliar E2E de expiración, timeout, caída y recuperación multi-tenant;
- verificar cancelación/reembolso autoritativo por proveedor;
- completar integración y entregabilidad real de Zoho.
