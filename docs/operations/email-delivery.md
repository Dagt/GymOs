# Operación del correo electrónico

Corte: 21 de agosto de 2026

GymFort incluye cola durable, leasing, reintentos, preferencias, métricas y
adaptador SMTP. El perfil operativo actual usa ZeptoMail como servicio de correo
transaccional; las credenciales pertenecen al entorno y nunca se guardan por
gimnasio. La identidad visible (`From`, nombre y `Reply-To`) permanece
configurada por tenant dentro de los remitentes verificados del Agent.

## Variables del backend

- `GYMOS_SMTP_ENABLED=true`
- `GYMOS_SMTP_PROVIDER_NAME=ZeptoMail`
- `GYMOS_SMTP_HOST=smtp.zeptomail.com`
- `GYMOS_SMTP_PORT=587`
- `GYMOS_SMTP_USERNAME`, `GYMOS_SMTP_PASSWORD`: valores de **Agent > SMTP/API**
- `GYMOS_SMTP_ALLOWED_FROM_ADDRESSES`: remitentes verificados permitidos
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
prueba. El dominio del `From` debe estar verificado y asociado al Mail Agent de
ZeptoMail. SPF, DKIM y DMARC deben estar publicados antes de producción.

El `Reply-To` puede apuntar a la bandeja propia del gimnasio aunque la entrega se
realice mediante la infraestructura transaccional central de GymFort.

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

## Checklist ZeptoMail

1. Crear o seleccionar el Mail Agent de GymFort.
2. Copiar las credenciales desde **Agent > SMTP/API**.
3. Usar `smtp.zeptomail.com:587` con TLS/STARTTLS.
4. Asociar y verificar los dominios/remitentes que se usarán como `From`.
5. Publicar y validar SPF, DKIM y DMARC.
6. Configurar `GYMOS_SMTP_ALLOWED_FROM_ADDRESSES` con las identidades aprobadas.
7. Confirmar una entrega real desde Configuración → Correo.
8. Revisar **Processed emails** en ZeptoMail ante rechazos o problemas de entregabilidad.

Runbook detallado: `docs/operations/zeptomail-email.md`.

## Privacidad

Las preferencias comparan direcciones mediante HMAC-SHA-256 y se revalidan
antes de entregar. Logs e historiales enmascaran destinatarios. Marketing se
suprime por defecto hasta disponer de consentimiento explícito; el alcance
actual es transaccional y operativo.
