# Operación del correo electrónico

Corte: 22 de agosto de 2026
Estado: OPERATIVO CON ZEPTOMAIL

GymFort incluye cola durable, leasing, reintentos, preferencias, métricas y un puerto desacoplado de entrega. El proveedor operativo actual es **ZeptoMail** mediante SMTP. Las credenciales pertenecen al ambiente y nunca se guardan por gimnasio; la identidad visible (`From`, nombre y `Reply-To`) se configura por tenant.

## 1. Arquitectura

```text
Módulo productor / Automatización
        ↓
NotificationService
        ↓
notification_log
        ↓
NotificationDeliveryWorker
        ↓
EmailDeliveryPort
        ↓
SmtpEmailDeliveryAdapter
        ↓
ZeptoMail
```

Automatizaciones no conoce SMTP ni secretos.

## 2. Variables del backend

- `GYMOS_SMTP_ENABLED=true`
- `GYMOS_SMTP_PROVIDER_NAME=ZeptoMail`
- `GYMOS_SMTP_HOST=smtp.zeptomail.com`
- `GYMOS_SMTP_PORT=587`
- `GYMOS_SMTP_USERNAME`, `GYMOS_SMTP_PASSWORD`: valores de **Agent > SMTP/API**
- `GYMOS_SMTP_ALLOWED_FROM_ADDRESSES`: remitentes verificados permitidos
- `GYMOS_SMTP_AUTH=true`
- `GYMOS_SMTP_STARTTLS=true`
- `GYMOS_SMTP_STARTTLS_REQUIRED=true`
- `GYMOS_SMTP_SSL=false`
- `GYMOS_SMTP_CHECK_SERVER_IDENTITY=true`
- `GYMOS_SMTP_CONNECTION_TIMEOUT_MS`
- `GYMOS_SMTP_READ_TIMEOUT_MS`
- `GYMOS_SMTP_WRITE_TIMEOUT_MS`
- `GYMOS_NOTIFICATION_PREFERENCE_KEY`: clave aleatoria para HMAC de destinatarios
- `GYMOS_FRONTEND_BASE_URL=https://gymfort.mx` en producción para resolver recursos públicos como el logo tenant.

No usar `NEXT_PUBLIC_*`, configuración tenant ni archivos versionados para secretos SMTP.

## 3. Identidad tenant

Patrón operativo V1 recomendado:

```text
From: Nombre del gimnasio <notificaciones@gymfort.mx>
Reply-To: bandeja real del gimnasio
```

La dirección `notificaciones@gymfort.mx` puede ser únicamente identidad transaccional; no requiere buzón. El `Reply-To` permite que el socio responda al gimnasio.

Remitentes alternativos sólo pueden utilizarse si están autorizados por el ambiente y verificados en ZeptoMail.

## 4. Branding

Cuando `bodyHtml` no viene pre-renderizado, el adaptador crea una plantilla segura con:

- nombre de marca;
- logo público del gimnasio;
- colores principal/acento/superficie;
- asunto como título;
- cuerpo escapado;
- ayuda de respuesta;
- footer con atribución GymFort.

Se conserva `text/plain` como alternativa MIME.

Los logos relativos se convierten con `GYMOS_FRONTEND_BASE_URL`. Un recurso local (`localhost` o `127.0.0.1`) se omite porque los clientes externos no pueden descargarlo.

## 5. Estados

```text
QUEUED → PROCESSING → SENT
           ↓
    RETRY_SCHEDULED
           ↓
      DEAD_LETTER
```

`SUPPRESSED` representa una entrega bloqueada por preferencias.

Los errores permanentes de configuración/autenticación se clasifican para evitar reintentos inútiles; los recuperables usan backoff exponencial acotado.

## 6. Idempotencia y leasing

- lease recuperable;
- selección justa entre tenants;
- `attempt_count` persistente;
- `Message-ID` estable;
- `X-GymOS-Notification-Id`;
- `X-GymOS-Idempotency-Key`.

La semántica de proveedor es al menos una vez; la identidad estable reduce duplicados y facilita auditoría.

## 7. Pruebas disponibles

### Configuración tenant

**Configuración → Correo electrónico → Enviar prueba** verifica aceptación SMTP real y configuración del gimnasio.

### Regla de automatización

**Automatizaciones → Probar ahora** en una regla email solicita un socio activo con correo. La búsqueda es incremental por nombre/email y la prueba crea una notificación real `CLIENT` usando el asunto/mensaje de la regla.

La prueba no altera pagos, reservas ni membresías.

## 8. Recuperación

Para el cliente, los fallos se presentan en **Automatizaciones → Problemas**. Después de corregir la causa puede reintentarse una entrega definitiva.

Los detalles internos siguen disponibles para soporte mediante logs, métricas, auditoría y endpoints técnicos; no se muestran como centro de la experiencia cotidiana.

## 9. Alertas y métricas

- `GymOsEmailDeadLetter`
- `GymOsEmailExpiredLease`
- `gymos.notification.delivery`
- `gymos.notification.delivery.duration`

Mantener cardinalidad acotada y no incluir PII como etiquetas.

## 10. Privacidad y preferencias

Las preferencias comparan direcciones mediante HMAC-SHA-256 y se revalidan antes de entregar. Marketing permanece deshabilitado por defecto sin consentimiento explícito. El alcance actual es transaccional/operativo.

## 11. Evidencia del corte

El 22 de agosto de 2026 se verificó `gymfort.mx` en ZeptoMail con DKIM y CNAME de return-path y se recibieron correos reales durante las pruebas. El flujo From central + nombre/Reply-To tenant quedó validado. En local, la carga del logo externo no puede validarse con una URL localhost; requiere `https://gymfort.mx` o un túnel público.

## 12. Pendiente no bloqueante

- webhooks de bounce/complaint de ZeptoMail;
- seguimiento productivo de reputación/entregabilidad;
- política DMARC final;
- proveedores adicionales/canales futuros.

Runbook detallado: `docs/operations/zeptomail-email.md`.
