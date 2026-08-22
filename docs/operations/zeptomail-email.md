# Activar correo transaccional con ZeptoMail

Corte: 22 de agosto de 2026
Estado: VALIDADO CON ENTREGA REAL

Esta guía conecta GymFort con ZeptoMail mediante el adaptador SMTP existente. La arquitectura de notificaciones no cambia: las credenciales viven únicamente en el backend y cada gimnasio conserva su identidad visible (`fromName`, `From`, `Reply-To`) dentro de los remitentes autorizados del ambiente.

## Arquitectura operativa recomendada

```text
Automatización / notificación
        ↓
notification_log
        ↓
NotificationDeliveryWorker
        ↓
EmailDeliveryPort (SMTP)
        ↓
ZeptoMail Mail Agent
        ↓
Destinatario
```

Patrón recomendado para V1:

```text
From: Nombre del gimnasio <notificaciones@gymfort.mx>
Reply-To: correo real del gimnasio
```

`notificaciones@gymfort.mx` no necesita ser un buzón real. ZeptoMail puede enviar desde una dirección autorizada del dominio verificado; las respuestas se dirigen al `Reply-To`.

## 1. Preparar ZeptoMail

1. Crear o seleccionar el **Mail Agent** de GymFort.
2. Agregar `gymfort.mx` al Agent.
3. Publicar en DNS los registros que entregue ZeptoMail:
   - DKIM (`TXT`, selector `..._domainkey`);
   - return-path/bounce (`CNAME`).
4. Si el DNS está en Cloudflare, el CNAME de bounce debe quedar **DNS only**, no proxied.
5. Esperar a que ZeptoMail marque ambos registros como verificados.
6. Autorizar el sender `notificaciones@gymfort.mx` (o cualquier otro `From` que vaya a usarse).
7. En **Agent > SMTP/API > SMTP**, copiar usuario y contraseña del Agent.

Configuración usada por GymFort:

- servidor: `smtp.zeptomail.com`;
- puerto: `587`;
- seguridad: TLS/STARTTLS;
- usuario: el mostrado por el Agent (normalmente `emailapikey`);
- contraseña: secreto SMTP del Agent.

Referencias oficiales: [SMTP](https://www.zoho.com/zeptomail/help/smtp-home.html), [SMTP/API](https://www.zoho.com/zeptomail/help/smtp-and-api.html) y [Mail Agents](https://www.zoho.com/zeptomail/help/agents.html).

## 2. Configurar el backend

```dotenv
GYMOS_SMTP_ENABLED=true
GYMOS_SMTP_PROVIDER_NAME=ZeptoMail
GYMOS_SMTP_HOST=smtp.zeptomail.com
GYMOS_SMTP_PORT=587
GYMOS_SMTP_USERNAME=emailapikey
GYMOS_SMTP_PASSWORD=tu-password-smtp-del-agent
GYMOS_SMTP_ALLOWED_FROM_ADDRESSES=notificaciones@gymfort.mx
GYMOS_SMTP_AUTH=true
GYMOS_SMTP_STARTTLS=true
GYMOS_SMTP_STARTTLS_REQUIRED=true
GYMOS_SMTP_SSL=false
GYMOS_SMTP_CHECK_SERVER_IDENTITY=true
GYMOS_FRONTEND_BASE_URL=https://gymfort.mx
```

`GYMOS_SMTP_PASSWORD` es un secreto. No debe vivir en Git, frontend, capturas ni documentación versionada.

`GYMOS_SMTP_ALLOWED_FROM_ADDRESSES` admite varias direcciones separadas por coma. Cada una debe estar previamente autorizada/verificada en el Mail Agent.

Las credenciales SMTP son globales para el ambiente. No se guardan en PostgreSQL por tenant.

## 3. URL pública y logo

Los correos usan el logo del gimnasio almacenado en GymFort. Si `logoUrl` es relativa (`/api/tenant-logo/...`), el backend la convierte usando `GYMOS_FRONTEND_BASE_URL`.

En producción:

```dotenv
GYMOS_FRONTEND_BASE_URL=https://gymfort.mx
```

En local normalmente apunta a `http://localhost:3000`. Gmail/Outlook no pueden acceder al localhost de la computadora del desarrollador; por eso el renderer omite imágenes que resolverían a localhost/127.0.0.1 para evitar un placeholder roto. Para probar el logo antes del despliegue se necesita una URL pública/túnel; no es requisito del funcionamiento productivo.

## 4. Reiniciar/redeployar backend

Local con Compose:

```powershell
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d --build --force-recreate backend
docker compose --env-file infra/.env -f infra/docker-compose.yml logs -f backend
```

En un ambiente hospedado, cargar las mismas variables como secrets/environment variables y redeployar Spring Boot.

## 5. Configurar cada gimnasio

En **Administración > Configuración > Correo electrónico**:

1. confirmar que el servidor muestre ZeptoMail disponible;
2. definir nombre visible del gimnasio;
3. usar un `From` autorizado (para V1: `notificaciones@gymfort.mx`);
4. definir `Reply-To` con la bandeja real del gimnasio;
5. habilitar entregas;
6. guardar;
7. enviar una prueba real.

Ejemplo:

```text
From: Appex Hub Deportivo <notificaciones@gymfort.mx>
Reply-To: contacto@appex.com
```

## 6. Validación de entrega

Después de enviar una prueba:

1. confirmar recepción en Gmail/Outlook;
2. verificar en ZeptoMail **Processed emails** que el mensaje fue aceptado/procesado;
3. revisar headers/original del correo para SPF/DKIM/DMARC cuando corresponda;
4. pulsar `Responder` y confirmar que el destinatario es el `Reply-To` del gimnasio;
5. verificar que nombre, colores y logo se correspondan con la organización.

En el corte del 22 de agosto de 2026 ya se verificaron DKIM + CNAME para `gymfort.mx` y se recibieron correos reales desde ZeptoMail durante pruebas locales.

## 7. Probar una automatización

En **Automatizaciones**:

1. crear/configurar una regla `EMAIL`;
2. pulsar `Probar ahora`;
3. buscar al socio por nombre o correo;
4. seleccionar un socio activo con email;
5. enviar la prueba;
6. revisar **Actividad** y la bandeja del socio.

La prueba usa el asunto/mensaje real de la regla y un destinatario real; no fabrica un pago, una reserva ni una domiciliación en el dominio.

## 8. Estados y reintentos

La cola conserva:

```text
QUEUED → PROCESSING → SENT
           ↓
    RETRY_SCHEDULED
           ↓
      DEAD_LETTER
```

`SUPPRESSED` significa que una preferencia impidió la entrega.

Los fallos recuperables usan backoff y los definitivos pueden reintentarse desde **Automatizaciones > Problemas** después de corregir la causa.

## 9. Diagnóstico

- **Servidor sin configurar**: faltan `GYMOS_SMTP_*` o backend no fue reiniciado.
- **Remitente no autorizado**: el `From` no está en `GYMOS_SMTP_ALLOWED_FROM_ADDRESSES` o no fue autorizado en ZeptoMail.
- **Credenciales rechazadas**: regenerar/copiar usuario/password desde Agent > SMTP/API.
- **Timeout**: revisar DNS, firewall y salida TCP 587.
- **Aceptado pero no llega**: revisar Processed emails, spam, DKIM/SPF/DMARC y reputación.
- **Logo ausente en local**: comportamiento esperado si `GYMOS_FRONTEND_BASE_URL` apunta a localhost.
- **Respuesta llega al lugar equivocado**: revisar `Reply-To` del tenant.

## 10. Seguridad

- secretos sólo en backend;
- TLS/STARTTLS requerido;
- validación de identidad del servidor;
- lista cerrada de remitentes permitidos;
- no incluir secretos en reglas/JSON/logs;
- no permitir HTML arbitrario del usuario en las reglas;
- marketing sigue bloqueado por defecto sin consentimiento.

## 11. Trabajo posterior no bloqueante

- webhooks de rebote/queja de ZeptoMail;
- dashboards de reputación/entregabilidad productiva;
- política DMARC final del dominio productivo;
- remitentes/dominios propios por gimnasio si producto lo exige;
- proveedores adicionales, WhatsApp y push.
