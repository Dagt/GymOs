# Operación del correo electrónico

Corte: 21 de agosto de 2026

GymFort incluye cola durable, leasing, reintentos, preferencias, métricas y
adaptador SMTP. Zoho es el siguiente proveedor objetivo, pero su conexión y
entregabilidad real todavía no están validadas. Las credenciales pertenecen al
entorno y nunca se guardan por gimnasio.

## Variables del backend

- `GYMOS_SMTP_ENABLED=true`
- `GYMOS_SMTP_HOST`, `GYMOS_SMTP_PORT`
- `GYMOS_SMTP_USERNAME`, `GYMOS_SMTP_PASSWORD`
- `GYMOS_SMTP_AUTH`, `GYMOS_SMTP_STARTTLS`, `GYMOS_SMTP_SSL`
- `GYMOS_SMTP_CONNECTION_TIMEOUT_MS`
- `GYMOS_SMTP_READ_TIMEOUT_MS`
- `GYMOS_SMTP_WRITE_TIMEOUT_MS`
- `GYMOS_NOTIFICATION_PREFERENCE_KEY`: clave aleatoria para HMAC de destinatarios

No usar variables `NEXT_PUBLIC_*`, archivos versionados ni configuración tenant
para secretos SMTP.

## Activación tenant

Después de reiniciar backend, dueño, gerente autorizado o soporte abre
Configuración → Correo, define remitente/reply-to, habilita el canal y envía una
prueba. El dominio debe estar verificado por el proveedor con SPF, DKIM y DMARC.

## Estados y recuperación

`QUEUED` → `PROCESSING` → `SENT`. Los errores recuperables pasan a
`RETRY_SCHEDULED` con backoff; al agotar intentos pasan a `DEAD_LETTER` y pueden
reintentarse desde Automatizaciones después de corregir la causa. `SUPPRESSED`
indica preferencia vigente.

El worker usa lease recuperable y selección justa entre tenants. SMTP ofrece
entrega al menos una vez: `X-GymOS-Notification-Id`,
`X-GymOS-Idempotency-Key` y `Message-ID` estable permiten rastrear/reducir
duplicados sin exponer destinatarios.

Alertas y métricas:

- `GymOsEmailDeadLetter`
- `GymOsEmailExpiredLease`
- `gymos.notification.delivery`
- `gymos.notification.delivery.duration`

## Checklist Zoho pendiente

1. Crear credencial específica de aplicación/SMTP según la política de la cuenta.
2. Elegir host/puerto/TLS correctos para la región y plan contratado.
3. Publicar y validar SPF, DKIM y DMARC.
4. Probar remitente, reply-to, TLS, timeouts y contraseña rotada.
5. Confirmar una entrega real y el comportamiento de rechazo, límite y timeout.
6. Documentar rebotes/quejas si el servicio contratado expone señales útiles.

No marcar Zoho como operativo hasta completar este checklist en el entorno
objetivo.

## Privacidad

Las preferencias comparan direcciones mediante HMAC-SHA-256 y se revalidan
antes de entregar. Logs e historiales enmascaran destinatarios. Marketing se
suprime por defecto hasta disponer de consentimiento explícito; el alcance
actual es transaccional y operativo.
