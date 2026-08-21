# Activar correo real con Zoho Mail

Esta guía deja el canal transaccional de GymFort enviando desde el backend. Las credenciales SMTP nunca se guardan en PostgreSQL, el navegador ni variables `NEXT_PUBLIC_*`.

## 1. Preparar Zoho

1. Usa un buzón dedicado, por ejemplo `notificaciones@tu-dominio.com`.
2. Si la cuenta usa MFA, crea una contraseña específica de aplicación en Zoho Accounts.
3. En Zoho Mail Admin verifica el dominio y publica SPF, DKIM y DMARC antes de enviar a clientes reales.
4. Conserva como remitentes únicamente el buzón autenticado y los alias que Zoho haya verificado.

Zoho documenta dos hosts habituales:

- organizaciones de pago con dominio propio: `smtppro.zoho.com`;
- cuentas personales o de organización gratuita: `smtp.zoho.com`.

La configuración recomendada es TLS explícito en el puerto `587`. También puede usarse SSL en `465`, desactivando STARTTLS.

Referencias oficiales: [SMTP de Zoho Mail](https://www.zoho.com/mail/help/zoho-smtp.html), [seguridad y contraseñas de aplicación](https://help.zoho.com/portal/en/kb/accounts/manage-your-zoho-account/articles/securing-your-zoho-account), [DKIM](https://www.zoho.com/mail/help/adminconsole/dkim-configuration.html), [SPF](https://www.zoho.com/mail/help/adminconsole/spf-configuration.html) y [DMARC](https://www.zoho.com/mail/help/adminconsole/dmarc-policy.html).

## 2. Configurar el backend local

Copia `infra/.env.example` como `infra/.env` y reemplaza únicamente en el archivo local:

```dotenv
GYMOS_SMTP_ENABLED=true
GYMOS_SMTP_PROVIDER_NAME=Zoho Mail
GYMOS_SMTP_HOST=smtppro.zoho.com
GYMOS_SMTP_PORT=587
GYMOS_SMTP_USERNAME=notificaciones@tu-dominio.com
GYMOS_SMTP_PASSWORD=tu-contrasena-especifica-de-aplicacion
GYMOS_SMTP_ALLOWED_FROM_ADDRESSES=notificaciones@tu-dominio.com
GYMOS_SMTP_AUTH=true
GYMOS_SMTP_STARTTLS=true
GYMOS_SMTP_STARTTLS_REQUIRED=true
GYMOS_SMTP_SSL=false
GYMOS_SMTP_CHECK_SERVER_IDENTITY=true
```

Si existen alias verificados, agrégalos separados por coma en `GYMOS_SMTP_ALLOWED_FROM_ADDRESSES`. GymFort rechazará antes de encolar cualquier remitente fuera de esa lista.

Recrea el backend para inyectar los secretos:

```powershell
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d --build --force-recreate backend
docker compose --env-file infra/.env -f infra/docker-compose.yml logs -f backend
```

No hace falta un contenedor de Zoho: el backend abre una conexión saliente TLS al servicio SMTP. La red local o corporativa debe permitir salida al puerto seleccionado.

## 3. Habilitar y verificar desde GymFort

1. Entra como dueño o usuario con `notification.settings.manage`.
2. Abre **Configuración > Correo electrónico**.
3. El estado **Servidor** debe indicar `Zoho Mail configurado`.
4. Define el nombre visible, selecciona el buzón o alias autorizado y, opcionalmente, un `Reply-To`.
5. Activa **Habilitar entregas por correo** y guarda.
6. Escribe una dirección propia en **Validar con una entrega real** y pulsa **Enviar y verificar**.

La prueba se intenta inmediatamente. Sólo se muestra como verificada cuando Zoho acepta el mensaje; la fecha queda calculada desde la entrega `SENT` auditada. Un fallo de autenticación pasa a fallo definitivo sin reintentos inútiles; los fallos transitorios conservan backoff y dead letter.

## 4. Activar automatizaciones por correo

En **Automatizaciones**, el bloque **Correo automatizado** muestra si el canal está listo. La opción **Correo al socio relacionado** permanece deshabilitada hasta que servidor, remitente y habilitación estén listos.

Los eventos de membresías, pagos, domiciliaciones y reservas resuelven el correo del socio relacionado. Los mensajes se persisten antes de entregarse, son idempotentes, respetan preferencias y se recuperan mediante leases, reintentos y dead letter.

## 5. Diagnóstico rápido

- `Servidor sin configurar`: faltan variables `GYMOS_SMTP_*` o no se recreó el backend.
- `Remitente no autorizado`: el correo no está en `GYMOS_SMTP_ALLOWED_FROM_ADDRESSES` o no es un alias verificado.
- `Zoho rechazó las credenciales`: usa una contraseña específica de aplicación y confirma el host correspondiente al tipo de cuenta.
- Timeout: verifica DNS, firewall y salida TCP al puerto 587/465 desde el contenedor backend.
- Zoho aceptó pero no aparece en bandeja: revisa spam y la alineación SPF/DKIM/DMARC; la aceptación SMTP no garantiza ubicación en inbox.

Para revisar la cola y reintentar fallos definitivos usa **Automatizaciones > Entregas recientes / Fallos definitivos**.
