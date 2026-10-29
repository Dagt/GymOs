# GymFort

> **Auditoría 29/10/2026:** La existencia de la funcionalidad descrita no certifica su uso en producción. main (136ce72) contiene V103. La rama feature/local-access-connector (8955cdb, 4 commits por encima de main) contiene V104 y el ejecutable local, SIN merge ni certificación de hardware. Los últimos CI consultados reportan fallo. Plan nuevo: [MVP](Documentación/35-mvp-y-criterios-de-salida.md), [Backlog](Documentación/36-epicas-backlog-codex.md), [Auditoría](Documentación/37-auditoria-repositorio-2026-10-29.md).


GymFort es una plataforma SaaS modular para la operación integral de gimnasios
y centros de bienestar. El nombre técnico histórico del repositorio, paquetes,
variables y base de datos continúa siendo `GymOS`/`gymos` mientras se completa
la migración de marca; la identidad visible del producto es GymFort.

## Estado actual

La plataforma ya incluye operación multi-tenant y multi-sucursal, usuarios y
permisos, CRM de socios, catálogo, POS, pagos, membresías, beneficios, accesos,
reservas, inventario, coaches y rutinas, wellness, automatizaciones,
notificaciones, reportes, auditoría y kiosco de autoservicio.

Los cobros soportan efectivo/transferencia/terminal manual en recepción,
Mercado Pago Point y Stripe Checkout. El kiosco usa Stripe para contratación,
renovación, cambio de plan y domiciliación con tarjeta. GymFort no almacena PAN
ni CVV; los efectos financieros se confirman mediante webhook firmado y
conciliación autoritativa.

Automatizaciones está funcional para reglas manuales, programadas y por eventos,
con inbox durable, leases, idempotencia, reintentos/dead letters y una interfaz
simplificada en `Automatizaciones`, `Actividad` y `Problemas`. Dueño y Gerente
pueden crear, administrar y probar reglas según permisos; las pruebas de email
usan un socio real buscado por nombre o correo.

El correo transaccional usa ZeptoMail mediante el `EmailDeliveryPort`/SMTP
existente. `gymfort.mx` quedó verificado con DKIM y CNAME de return-path y se
confirmaron entregas reales el 22 de agosto de 2026. Las credenciales pertenecen
al ambiente; cada gimnasio conserva su nombre visible, `From` autorizado y
`Reply-To`. El patrón V1 recomendado es:

```text
From: Nombre del gimnasio <notificaciones@gymfort.mx>
Reply-To: bandeja real del gimnasio
```

Los mensajes usan branding del tenant y logo público cuando
`GYMOS_FRONTEND_BASE_URL` es accesible desde el cliente de correo. Consulta la
[guía de ZeptoMail](docs/operations/zeptomail-email.md).

El control plane de acceso físico ya incluye estado deseado automático, jobs
durables y una API segura para el GymFort Local Connector. El Connector local,
adapter HikCentral y apertura del torniquete real siguen pendientes de validación
en la instalación del primer cliente; consulta el
[contrato del Connector](docs/operations/physical-access-connector.md).

Consulta el [estado canónico](Documentación/13-estado-desarrollo.md), el
[índice por módulos](Documentación/modulos/README.md) y la
[deuda técnica vigente](Documentación/28-registro-vivo-deuda-tecnica.md).

## Estructura

- `backend/`: API Java 25 con Spring Boot 4, Spring Modulith, Flyway,
  PostgreSQL, Redis y MinIO.
- `frontend/`: Next.js 16, React 19, TypeScript, Tailwind CSS y Playwright.
- `infra/`: Docker Compose local, Stripe CLI opcional, proxy y observabilidad.
- `Documentación/`: producto, arquitectura, contratos, estado y módulos.
- `docs/operations/`: runbooks de despliegue, correo, Stripe, kiosco, acceso
  físico y recuperación.
- `docs/security/`: matrices de permisos y aislamiento multi-tenant.
- `docs/testing/`: aceptación manual y pruebas Stripe sandbox.
- `scripts/`: utilidades operativas locales.

## Requisitos locales

- Docker Desktop.
- Node.js 24 para trabajar en el frontend fuera de Docker.
- JDK 25 para compilar el backend fuera de Docker.

Si `java -version` no muestra Java 25:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

## Arranque rápido

```powershell
docker compose -f infra/docker-compose.yml up --build
```

Servicios locales:

- Frontend: http://localhost:3000
- Backend: http://localhost:8080
- OpenAPI UI: http://localhost:8080/api/v1/docs
- Health: http://localhost:8080/actuator/health
- MinIO: http://localhost:9001

El arranque no crea usuarios demo automáticamente. Para habilitar un tenant de
prueba exclusivamente en el perfil `local`, define explícitamente
`GYMOS_SEED_ENABLED=true`, `GYMOS_DEMO_OWNER_EMAIL` y una contraseña fuerte en
`GYMOS_DEMO_OWNER_PASSWORD`; el acceso será con esos valores. En staging y
producción se debe usar un perfil no local y `GYMOS_SEED_ENABLED=false`.

## Stripe local

El perfil Docker `stripe` descarga y ejecuta Stripe CLI `v1.50.3` para reenviar
eventos Connect al backend:

```powershell
docker compose --env-file infra/.env -f infra/docker-compose.yml --profile stripe pull stripe-listener
docker compose --env-file infra/.env -f infra/docker-compose.yml --profile stripe up stripe-listener
```

La configuración completa está en [infra/README.md](infra/README.md) y la guía
de pruebas en [docs/testing/stripe-kiosk-test-mode.md](docs/testing/stripe-kiosk-test-mode.md).

## Validación

```powershell
cd backend
.\gradlew.bat clean test --no-daemon
.\gradlew.bat postgresIntegrationTest

cd ..\frontend
npm.cmd ci
npm.cmd test
npm.cmd run lint
npm.cmd run typecheck
npm.cmd run build

cd ..
docker compose -f infra/docker-compose.yml config
docker compose -f infra/docker-compose.yml build
```

`stripeTest` es opt-in, rechaza llaves live y requiere
`GYMOS_STRIPE_TEST_SECRET_KEY=sk_test_...`.

## Flujo de trabajo

- Rama estable: `main`.
- Desarrollo: ramas `feature/*` o `fix/*` y PR con CI verde.
- Commits convencionales, por ejemplo `fix: prevent duplicate checkout`.
- Cada cambio material actualiza su ficha modular, el estado general y, si
  aplica, deuda, contratos, runbooks y cronología de ramas.
