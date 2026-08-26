# Notificaciones

Estado: `FUNCIONAL / ZEPTOMAIL OPERATIVO`
Última revisión: 26 de agosto de 2026
Rama auditada: `feature/automations-zoho-email`

## Propósito

`notifications` desacopla la creación de mensajes de su entrega. Automatizaciones y otros módulos crean una notificación; este módulo resuelve destinatario, preferencias, configuración del gimnasio, branding, cola y proveedor.

## Canales ejecutables

- `IN_APP`
- `EMAIL`

El diseño mantiene abierta la extensión futura a WhatsApp y push sin acoplar esos proveedores al motor de automatizaciones.

## Correo y ZeptoMail

La integración productiva elegida para correo transaccional es **ZeptoMail** mediante el puerto existente `EmailDeliveryPort` y un adaptador SMTP. No se introdujo una segunda cola ni un servicio paralelo.

Configuración recomendada del ambiente:

- `smtp.zeptomail.com:587`;
- TLS/STARTTLS requerido;
- validación de identidad del servidor;
- usuario/contraseña SMTP como secretos del backend;
- lista de direcciones `From` autorizadas por ambiente.

Las credenciales son globales para el ambiente y nunca se guardan por gimnasio ni se exponen al frontend.

## Identidad visible por gimnasio

Cada organización configura:

- canal email habilitado/deshabilitado;
- `providerKey`;
- `fromEmail`;
- `fromName`;
- `replyTo`.

La operación recomendada actual es usar un remitente transaccional central verificado, por ejemplo:

```text
From: Appex Hub Deportivo <notificaciones@gymfort.mx>
Reply-To: contacto-del-gimnasio@...
```

El gimnasio no necesita tener un buzón empresarial en `notificaciones@gymfort.mx`: ZeptoMail entrega el mensaje y `Reply-To` dirige las respuestas a la bandeja real del gimnasio.

También pueden autorizarse remitentes de dominios propios si el dominio/dirección fue verificado y asociado al Mail Agent y agregado a `GYMOS_SMTP_ALLOWED_FROM_ADDRESSES`.

## Branding del email

Cuando no se proporciona un HTML específico, el adaptador genera una plantilla transaccional con:

- nombre/marca del gimnasio;
- logo del gimnasio cuando existe una URL pública accesible;
- color principal;
- color secundario/acento;
- color de superficie;
- asunto visible como título;
- cuerpo en texto seguro;
- bloque de ayuda indicando que se puede responder al gimnasio;
- atribución discreta `Enviado mediante GymFort`.

El logo almacenado por GymFort puede ser una ruta relativa `/api/tenant-logo/...`; el backend la convierte usando `GYMOS_FRONTEND_BASE_URL`. En producción debe ser una URL pública como `https://gymfort.mx`. En desarrollo, si la base apunta a `localhost`/`127.0.0.1`, el `<img>` se omite para no producir una imagen rota en Gmail/Outlook.

Se mantiene `text/plain` como alternativa del mensaje HTML.

Estado vigente: la plantilla resuelve nombre, logo, colores y `Reply-To` desde
la organización. El objetivo `CFG-003` es conservar la sucursal de origen en
cada notificación y resolver desde ella la marca efectiva; cualquier campo no
configurado hereda el valor corporativo. Así, dos marcas que operan como
sucursales del mismo tenant no mezclan identidad visual ni dirección de
respuesta.

## Resolución de destinatarios

Destinatarios soportados:

- `CLIENT`
- `USER`
- `DIRECT`

Para `CLIENT`/`USER`, el backend resuelve dirección y nombre dentro de la organización/sucursal. Las reglas de automatización reciben el `clientId` del evento; no guardan emails hardcodeados.

Variables de plantilla soportadas incluyen `{{recipientName}}` y variables de evento como fechas, montos y referencias según el trigger.

## Prueba de correo

Existen dos recorridos:

1. **Configuración → Correo electrónico → Enviar prueba**: valida configuración del tenant y aceptación SMTP real.
2. **Automatizaciones → Probar ahora**: para una regla email obliga a buscar/seleccionar un socio activo con correo y envía el asunto/mensaje real de la regla sin fabricar un evento de negocio.

## Cola durable

`notification_log` funciona como cola PostgreSQL durable:

- `QUEUED`
- `PROCESSING`
- `SENT`
- `RETRY_SCHEDULED`
- `FAILED`/`DEAD_LETTER`
- `SUPPRESSED`

El worker usa reclamación con lease, recuperación de locks vencidos, lotes y reparto justo entre tenants. Los mensajes conservan `Message-ID`, `X-GymOS-Notification-Id` y `X-GymOS-Idempotency-Key` estables para trazabilidad/deduplicación.

## Reintentos y recuperación

- máximo configurable de intentos (por defecto 5);
- backoff exponencial acotado;
- errores permanentes de configuración/autenticación no se reintentan indefinidamente;
- dead letters pueden devolverse a la cola después de corregir la causa;
- la UI del cliente muestra el problema de forma simplificada y conserva el detalle técnico fuera de la vista cotidiana.

## Preferencias y privacidad

- preferencias por destinatario protegidas mediante HMAC-SHA-256;
- preferencias se revalidan inmediatamente antes de entregar;
- `TRANSACTIONAL` permitido por defecto salvo opt-out aplicable;
- `MARKETING` bloqueado por defecto sin consentimiento explícito;
- logs e historiales no deben exponer secretos ni payloads sensibles.

## Permisos

- configuración de email: `notification.settings.manage`;
- lectura: `notification.read`;
- gestión/reintento: `notification.manage` según endpoint;
- V80 concede a Gerencia la gestión operativa necesaria para automatizaciones/notificaciones, sin conceder controles de plataforma.

## Estado validado

En el corte del 22 de agosto de 2026 se verificó el dominio `gymfort.mx` en ZeptoMail mediante DKIM y CNAME/return-path y se confirmó entrega real a bandeja desde el ambiente local usando el Mail Agent. El `From` central `notificaciones@gymfort.mx` y el `Reply-To` tenant forman el patrón operativo previsto.

## Pendiente posterior al cierre

No bloquea el funcionamiento actual:

- webhooks de rebote/queja/feedback de ZeptoMail para enriquecer estados posteriores a `SENT`;
- monitorización continua de reputación y entregabilidad en producción;
- DMARC/política final y runbook de reputación según el dominio productivo;
- múltiples integraciones/proveedores por tenant si producto lo requiere;
- branding y configuración de comunicación por sucursal, con fallback de
  organización, para que correos originados en marcas distintas del mismo
  tenant usen identidad visual y Reply-To correctos (`CFG-003`);
- WhatsApp, push y campañas promocionales con consentimiento específico.

Runbooks: `../../docs/operations/email-delivery.md` y `../../docs/operations/zeptomail-email.md`.
