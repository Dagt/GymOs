# Infraestructura local

## Arranque base

```powershell
docker compose -f infra/docker-compose.yml up --build
```

Servicios:

- Frontend: http://localhost:3000
- Backend: http://localhost:8080
- OpenAPI UI: http://localhost:8080/api/v1/docs
- Health: http://localhost:8080/actuator/health
- MinIO Console: http://localhost:9001

Credenciales locales no productivas:

- PostgreSQL: `gymos` / `gymos`
- MinIO: `gymos` / `gymos-secret`

## Stripe CLI en Docker

El archivo Compose incluye el servicio opcional `stripe-listener` con la imagen
fijada `stripe/stripe-cli:v1.50.3`. CI valida el Compose, descarga la imagen y
ejecuta `stripe version`; el listener no se incluye en el arranque normal.

1. Copia `infra/.env.example` a `infra/.env`.
2. Agrega una llave `GYMOS_STRIPE_SECRET_KEY=sk_test_...`.
3. Descarga y levanta el perfil:

```powershell
docker compose --env-file infra/.env -f infra/docker-compose.yml --profile stripe pull stripe-listener
docker compose --env-file infra/.env -f infra/docker-compose.yml --profile stripe up stripe-listener
```

El listener reenvía únicamente eventos soportados de cuentas Connect a
`http://backend:8080/api/v1/payment-webhooks/stripe`. En el primer arranque
imprime `whsec_...`; cópialo a `GYMOS_STRIPE_WEBHOOK_SECRET` en `infra/.env` y
recrea backend:

```powershell
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d --force-recreate backend
```

No guardar llaves Stripe en imágenes, commits o variables `NEXT_PUBLIC_*`.
Para tarjetas sandbox y recorridos consulta
[`../docs/testing/stripe-kiosk-test-mode.md`](../docs/testing/stripe-kiosk-test-mode.md).

## Correo transaccional con ZeptoMail

ZeptoMail es un servicio externo y no necesita contenedor. El backend ya incluye
cola durable, worker, branding y adaptador SMTP.

Configuración mínima recomendada:

```dotenv
GYMOS_SMTP_ENABLED=true
GYMOS_SMTP_PROVIDER_NAME=ZeptoMail
GYMOS_SMTP_HOST=smtp.zeptomail.com
GYMOS_SMTP_PORT=587
GYMOS_SMTP_USERNAME=emailapikey
GYMOS_SMTP_PASSWORD=...
GYMOS_SMTP_ALLOWED_FROM_ADDRESSES=notificaciones@gymfort.mx
GYMOS_SMTP_AUTH=true
GYMOS_SMTP_STARTTLS=true
GYMOS_SMTP_STARTTLS_REQUIRED=true
GYMOS_SMTP_SSL=false
GYMOS_SMTP_CHECK_SERVER_IDENTITY=true
GYMOS_FRONTEND_BASE_URL=http://localhost:3000
```

Después de modificar secretos:

```powershell
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d --build --force-recreate backend
```

El 22 de agosto de 2026 `gymfort.mx` quedó verificado en ZeptoMail con DKIM y
CNAME de return-path y se confirmaron entregas reales.

Patrón V1:

```text
From: Nombre del gimnasio <notificaciones@gymfort.mx>
Reply-To: correo real del gimnasio
```

Los `From` deben estar verificados/autorizados y en
`GYMOS_SMTP_ALLOWED_FROM_ADDRESSES`. El `Reply-To` se configura por gimnasio.

### Logo en desarrollo

Los logos tenant se sirven mediante `/api/tenant-logo/...`. En producción usar:

```dotenv
GYMOS_FRONTEND_BASE_URL=https://gymfort.mx
```

Gmail/Outlook no pueden descargar imágenes desde `localhost`; el renderer omite
el `<img>` si la URL resultante es localhost/127.0.0.1. Para probar el logo antes
del despliegue se necesita una URL pública/túnel.

La guía completa está en
[`../docs/operations/zeptomail-email.md`](../docs/operations/zeptomail-email.md).

## Automatizaciones en local

La página `/automations` permite crear reglas desde cero y probar reglas email
contra un socio real. `Probar ahora` busca socios activos por nombre/correo y no
crea operaciones falsas de pago/reserva/membresía.

Gerencia requiere V80 aplicada y una sesión/JWT renovada para recibir los nuevos
permisos de automatización/notificaciones.

## Datos y observabilidad

PostgreSQL, Redis y MinIO se ejecutan como servicios separados. Los perfiles de
aplicación y secretos se inyectan por entorno. Prometheus usa
`infra/monitoring/prometheus.yml`; los procedimientos de despliegue, respaldo,
restore y rotación viven en `docs/operations/`.
