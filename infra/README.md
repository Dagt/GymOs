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

El listener reenvía únicamente los eventos soportados de cuentas Connect a
`http://backend:8080/api/v1/payment-webhooks/stripe`. En el primer arranque
imprime `whsec_...`; cópialo a `GYMOS_STRIPE_WEBHOOK_SECRET` en `infra/.env` y
recrea el backend:

```powershell
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d --force-recreate backend
```

No guardar llaves Stripe en imágenes, commits o variables `NEXT_PUBLIC_*`.
Para tarjetas sandbox y recorridos consulta
[`../docs/testing/stripe-kiosk-test-mode.md`](../docs/testing/stripe-kiosk-test-mode.md).

## Correo transaccional con ZeptoMail

El backend ya incluye el cliente SMTP, la cola durable y el worker; ZeptoMail es
un servicio externo y no requiere un contenedor adicional. Configura los valores
`GYMOS_SMTP_*` de `infra/.env.example` con las credenciales de **Agent > SMTP/API**,
recrea `backend` y ejecuta la prueba real desde **Configuración > Correo electrónico**.

Los `From` admitidos deben estar verificados y asociados al Agent. El `Reply-To`
permanece configurable por gimnasio para que las respuestas lleguen a su propia
bandeja.

La guía completa, incluyendo TLS, remitentes, SPF, DKIM, DMARC y diagnóstico,
está en
[`../docs/operations/zeptomail-email.md`](../docs/operations/zeptomail-email.md).

## Datos y observabilidad

PostgreSQL, Redis y MinIO se ejecutan como servicios separados. Los perfiles de
aplicación y secretos se inyectan por entorno. Prometheus usa
`infra/monitoring/prometheus.yml`; los procedimientos de despliegue, respaldo,
restore y rotación viven en `docs/operations/`.
