# Activar correo transaccional con ZeptoMail

Esta guía conecta el canal transaccional de GymFort con ZeptoMail mediante el adaptador SMTP existente. La arquitectura de notificaciones no cambia: las credenciales viven únicamente en el backend y cada gimnasio conserva su configuración visible de nombre, `From` y `Reply-To`.

## 1. Preparar ZeptoMail

1. Crea o selecciona un **Mail Agent** para GymFort.
2. Agrega al Agent los dominios remitentes que vayas a utilizar y completa su verificación DNS.
3. Configura SPF, DKIM y DMARC antes de enviar a clientes reales.
4. En **Agent > SMTP/API > SMTP**, copia las credenciales exactas que muestra ZeptoMail.
5. Mantén en GymFort únicamente direcciones remitentes verificadas y asociadas a ese Agent.

Configuración SMTP oficial de ZeptoMail:

- servidor: `smtp.zeptomail.com`;
- puerto recomendado para GymFort: `587` con TLS/STARTTLS;
- alternativa: `465` con SSL;
- usuario: el mostrado por el Agent (normalmente `emailapikey`);
- contraseña: la contraseña SMTP generada para el Agent.

ZeptoMail soporta TLS 1.2 para SMTP. GymFort exige STARTTLS y validación de identidad del servidor en la configuración recomendada.

Referencias oficiales: [SMTP de ZeptoMail](https://www.zoho.com/zeptomail/help/smtp-home.html), [SMTP/API](https://www.zoho.com/zeptomail/help/smtp-and-api.html) y [Mail Agents](https://www.zoho.com/zeptomail/help/agents.html).

## 2. Configurar el backend

Copia `infra/.env.example` como `infra/.env` y reemplaza los valores sensibles con los del Agent:

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
```

`GYMOS_SMTP_ALLOWED_FROM_ADDRESSES` admite varias direcciones separadas por coma. Si GymFort envía con identidad propia de un gimnasio, su dominio y dirección deben estar verificados/asociados al Agent antes de agregarlos a esta lista.

Ejemplo:

```dotenv
GYMOS_SMTP_ALLOWED_FROM_ADDRESSES=notificaciones@gymfort.mx,avisos@powergym.mx,notificaciones@ironhouse.mx
```

Las credenciales SMTP son globales para el ambiente; no se guardan en PostgreSQL ni se envían al frontend.

Recrea el backend para inyectar los secretos:

```powershell
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d --build --force-recreate backend
docker compose --env-file infra/.env -f infra/docker-compose.yml logs -f backend
```

## 3. Configurar cada gimnasio

1. Entra como dueño o usuario con `notification.settings.manage`.
2. Abre **Configuración > Correo electrónico**.
3. Confirma que **Servidor** muestre `ZeptoMail configurado`.
4. Define el nombre visible del gimnasio.
5. Selecciona un `From` autorizado.
6. Define el `Reply-To` del gimnasio si las respuestas deben llegar a su propia bandeja.
7. Activa **Habilitar entregas por correo** y guarda.
8. Envía una prueba real.

Ejemplo de identidad visible:

```text
From: Power Gym <avisos@powergym.mx>
Reply-To: contacto@powergym.mx
```

También es válido usar un `From` central de GymFort y mantener el nombre/`Reply-To` propios del gimnasio.

## 4. Automatizaciones

Cuando el canal esté listo, las automatizaciones pueden utilizar `EMAIL`. Los eventos de membresías, pagos, domiciliaciones y reservas resuelven el correo del socio relacionado. Los mensajes se persisten antes de enviarse y conservan idempotencia, preferencias, leasing, reintentos y dead letter.

## 5. Diagnóstico

- `Servidor sin configurar`: faltan variables `GYMOS_SMTP_*` o el backend no fue recreado.
- `Remitente no autorizado`: la dirección no está en `GYMOS_SMTP_ALLOWED_FROM_ADDRESSES` o no está verificada/asociada al Agent.
- `ZeptoMail rechazó las credenciales`: copia nuevamente usuario y contraseña desde **Agent > SMTP/API**.
- Timeout: verifica DNS, firewall y salida TCP al puerto `587` desde el backend.
- Mensaje aceptado pero no visible en inbox: revisa SPF, DKIM, DMARC y los logs de **Processed emails** del Agent.

Para revisar la cola y reintentar fallos definitivos usa **Automatizaciones > Entregas recientes / Fallos definitivos**.
