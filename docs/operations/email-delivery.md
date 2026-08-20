# Operación del correo electrónico

GymOS incluye cola durable, leasing, reintentos, preferencias, métricas y un
adaptador SMTP. Las credenciales del proveedor pertenecen al entorno de
ejecución y nunca se guardan por gimnasio en la base de datos.

## Conectar un proveedor SMTP

Configurar como secretos del backend:

- `GYMOS_SMTP_ENABLED=true`;
- `GYMOS_SMTP_HOST` y `GYMOS_SMTP_PORT`;
- `GYMOS_SMTP_USERNAME` y `GYMOS_SMTP_PASSWORD` cuando el servidor use
  autenticación;
- `GYMOS_NOTIFICATION_PREFERENCE_KEY`, clave aleatoria independiente para
  calcular HMAC de destinatarios en preferencias;
- `GYMOS_SMTP_AUTH`, `GYMOS_SMTP_STARTTLS` y `GYMOS_SMTP_SSL` según el
  proveedor;
- los timeouts `GYMOS_SMTP_*_TIMEOUT_MS` cuando la red lo requiera.

Después de reiniciar el backend, un dueño o administrador debe abrir
Configuración del gimnasio > Correo electrónico, definir remitente y
`Reply-To`, habilitar el canal y enviar una prueba. El dominio del remitente
debe estar verificado en el proveedor y publicar SPF, DKIM y DMARC conforme a
sus instrucciones.

## Estados y recuperación

Los correos recorren `QUEUED`, `PROCESSING`, `RETRY_SCHEDULED` y `SENT`.
Después de agotar los intentos quedan en `DEAD_LETTER`; desde Automatizaciones
pueden devolverse manualmente a la cola. `SUPPRESSED` indica que una preferencia
vigente impidió contactar al destinatario.

El worker usa un lease recuperable y selección equitativa entre tenants. Los
timeouts tienen backoff exponencial acotado. Cada entrega incluye
`X-GymOS-Notification-Id`, `X-GymOS-Idempotency-Key` y un `Message-ID` estable.
SMTP no ofrece una transacción distribuida entre aceptar el mensaje y marcarlo
en PostgreSQL, por lo que la garantía es al menos una vez; se debe conservar el
identificador estable al implementar adaptadores API para deduplicar en el
proveedor.

Alertas relevantes:

- `GymOsEmailDeadLetter`;
- `GymOsEmailExpiredLease`;
- métricas `gymos.notification.delivery` y
  `gymos.notification.delivery.duration`, siempre con `organization_id`.

## Privacidad y evolución multicanal

Las direcciones de preferencia se comparan mediante HMAC-SHA-256; la tabla de
preferencias no duplica el correo en texto claro. Una baja se revalida justo
antes de entregar. Los mensajes promocionales se suprimen por defecto hasta
que exista consentimiento explícito.

Los adaptadores futuros deben implementar `EmailDeliveryPort` o el puerto
equivalente del nuevo canal. WhatsApp necesitará plantillas/opt-in aprobados y
push necesitará un registro tenant de dispositivos y tokens revocables. Ninguno
de esos canales debe reutilizar credenciales SMTP ni introducir secretos en
`notification_log`.
