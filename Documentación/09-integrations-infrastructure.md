# 9. Integraciones e Infraestructura

## 9.1 Puertos externos

### AccessControlProvider
- registerIdentity
- updateIdentity
- revokeIdentity
- openAccessPoint
- createTemporaryAccess
- receiveAccessEvent
- healthCheck

### BiometricProvider
- enrollFace
- updateFace
- removeFace
- matchReference

### PaymentGateway
- createPaymentIntent
- confirmPayment
- cancelPayment
- refundPayment
- queryPaymentStatus
- handleWebhook

### EmailProvider
- send
- validateTemplate
- healthCheck

### MessagingProvider
- sendWhatsApp
- sendTemplate
- queryStatus

### StorageProvider
- upload
- download
- delete
- generateSignedUrl

Implementación local recomendada:
- MinIO en Docker Compose.
- Buckets separados para fotos, comprobantes y documentos.
- URLs firmadas para acceso temporal.
- Ningún archivo binario debe guardarse directamente en PostgreSQL.

### InvoiceProvider
- createInvoice
- cancelInvoice
- queryInvoice

## 9.2 Resiliencia

- Timeouts.
- Reintentos con backoff.
- Circuit breaker.
- Dead-letter queue o registro de fallos.
- Idempotencia.
- Health checks.
- Alertas.

## 9.3 Despliegue

Componentes:
- frontend Next.js
- backend Spring Boot
- PostgreSQL
- Redis
- reverse proxy
- almacenamiento S3 compatible

Estructura de repositorio recomendada:
- `backend/`
- `frontend/`
- `infra/`
- `Documentación/`

El despliegue inicial debe poder correr en una VPS o cloud pequeña con Docker Compose, sin impedir una evolución posterior a infraestructura más avanzada.

Entornos:
- local
- development
- staging
- production

## 9.4 CI/CD

Pipeline:
1. lint
2. unit tests
3. architecture tests
4. integration tests
5. build
6. image scan
7. deploy staging
8. smoke tests
9. production deploy
10. rollback automático si aplica

## 9.5 Observabilidad

- Logs estructurados.
- Correlation ID.
- Métricas.
- Trazas.
- Dashboard técnico.
- Alertas.
- Auditoría separada de logs.

## 9.6 Backups

- PostgreSQL diario.
- Retención configurable.
- Copia externa.
- Pruebas de restauración.
- Documentar RPO y RTO.

## 9.7 Seguridad operacional

- HTTPS obligatorio.
- Secretos fuera del repositorio.
- Rotación de credenciales.
- CORS restringido.
- Rate limiting.
- Dependencias escaneadas.
- Principio de mínimo privilegio.

## 9.8 Kioscos de autoservicio

Los kioscos son dispositivos o navegadores controlados dentro de una sucursal.

Requisitos:
- Registro de terminal por organización y sucursal.
- Identificador de terminal no sensible.
- Sesiones de cliente con expiración corta.
- Cierre automático por inactividad.
- Modo de pantalla completa recomendado.
- Restricción de rutas al flujo de autoservicio.
- No almacenar tokens de cliente persistentes en el dispositivo.
- Auditoría con origen `SELF_SERVICE_KIOSK`.

## 9.9 Operacion piloto

Artefactos agregados:

- Variables de entorno: `infra/.env.example`.
- Reverse proxy HTTPS: `infra/caddy/Caddyfile.example`.
- Prometheus: `infra/monitoring/prometheus.yml`.
- Backup: `scripts/backup-postgres.ps1`.
- Restore: `scripts/restore-postgres.ps1`.

Runbooks:

- `docs/operations/deployment.md`
- `docs/operations/backup.md`
- `docs/operations/restore.md`
- `docs/operations/incident-response.md`
- `docs/operations/rollback.md`
- `docs/operations/secret-rotation.md`
- `docs/operations/kiosk-troubleshooting.md`
- `docs/operations/access-device-troubleshooting.md`
- `docs/operations/database-maintenance.md`

RPO piloto: 24 horas.

RTO piloto: 4 horas.
