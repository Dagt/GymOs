# 9. Integraciones e infraestructura

Corte: 23 de agosto de 2026

## 9.1 Arquitectura de proveedores

Los proveedores externos se aíslan detrás de puertos de aplicación. Los dominios no almacenan secretos ni confían en datos devueltos por navegador.

- Pagos: terminal manual, Mercado Pago OAuth/Point y Stripe Connect/Checkout/Setup/off-session.
- Correo: `EmailDeliveryPort` con adaptador SMTP y ZeptoMail como proveedor operativo actual.
- Almacenamiento: MinIO/S3 para logos, medios, comprobantes y documentos.
- Acceso físico: control plane provider-neutral, Connector API y entrega durable
  implementados; Connector local y adapter productivo HikCentral pendientes.
- WhatsApp, push y facturación: puertos previstos, adaptadores productivos fuera del corte.

## 9.2 Acceso físico y HikCentral Professional

GymFort es fuente de verdad para:

```text
Client -> Membership -> MembershipPlan -> Benefit ACCESS
```

El sistema físico ejecutará localmente esos derechos. La lógica de membresías y
elegibilidad no depende de una marca.

Primer piloto:

```text
GymFort Cloud
  -> estado deseado + jobs PostgreSQL
  -> Connector API saliente/autenticada
  -> GymFort Local Connector
  -> HikCentral Professional (LAN)
  -> terminal/controlador Hikvision
  -> torniquete/puerta
```

La parte cloud ya está implementada: catálogo de proveedor/capacidades,
conexión y mapeos por sucursal, vínculos opacos, estado deseado, jobs durables con
lease/reintento/idempotencia, activación y rotación hasheadas, heartbeat, eventos
físicos idempotentes y candidatos de persona. No contiene URL, puerto,
credenciales ni llamadas HikCentral.

El Connector usa únicamente comunicaciones salientes a GymFort Cloud. Se activa
con un código de 15 minutos y después autentica con clave pública + secreto de
un solo despliegue; Cloud conserva sólo hashes BCrypt. Los detalles de LAN y del
proveedor permanecen en el almacén seguro local.

Se sincronizan autorizaciones en vez de consultar Cloud por cada paso.
HikCentral conserva decisión local y biometría; GymFort no procesa plantillas
biométricas. Un corte de Internet no debe impedir la decisión con el último
estado distribuido.

Antes de implementar el Connector y adapter deben validarse versión, licencia,
API/SDK, topología y operaciones oficiales de la instalación real. Hasta tener
esa evidencia, `HIKCENTRAL` significa “contrato cloud configurable”, no
“integración física operativa”.

## 9.3 Stripe y Mercado Pago

- secretos sólo en backend;
- Stripe usa Express conectado por gimnasio y captura alojada;
- Mercado Pago usa OAuth/PKCE, terminal Point y órdenes idempotentes;
- todo pago se persiste pendiente antes de invocar proveedor;
- webhook firmado + consulta autoritativa concilian monto, moneda, tenant, sucursal y contexto;
- workers recuperan operaciones no terminales e idempotentes.

## 9.4 Correo / ZeptoMail

### Diseño

`notifications` posee la entrega; Automatizaciones sólo genera `CREATE_NOTIFICATION`.

```text
Automation / módulo productor
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

No existe una segunda cola específica para Zoho/ZeptoMail.

### Credenciales

El backend recibe por entorno:

- proveedor visible;
- host/puerto;
- usuario/password;
- remitentes autorizados;
- TLS/SSL;
- timeouts.

Cada gimnasio configura sólo:

- habilitación;
- `fromName`;
- `fromEmail`;
- `replyTo`.

Las credenciales nunca se guardan por tenant.

### Patrón V1

```text
From: Nombre del gimnasio <notificaciones@gymfort.mx>
Reply-To: correo real del gimnasio
```

La cuenta `notificaciones@gymfort.mx` no necesita buzón empresarial. ZeptoMail entrega desde el dominio autorizado y `Reply-To` dirige respuestas al gimnasio.

Remitentes de dominio propio sólo se habilitan después de verificar dominio/dirección y agregarlos a `GYMOS_SMTP_ALLOWED_FROM_ADDRESSES`.

### DNS y validación

El 22 de agosto de 2026 se verificó `gymfort.mx` en ZeptoMail con:

- DKIM TXT;
- CNAME de return-path/bounce.

En Cloudflare, el CNAME de bounce debe permanecer `DNS only`.

Se confirmó entrega real desde ZeptoMail durante las pruebas del módulo.

### Seguridad

Configuración recomendada:

- `smtp.zeptomail.com:587`;
- STARTTLS requerido;
- identidad del servidor verificada;
- secretos fuera de Git/frontend;
- allowlist de `From`;
- mensajes del usuario escapados; no HTML arbitrario en reglas.

### Branding

El email consume marca tenant (`brandName`, `logoUrl`, `primaryColor`, `accentColor`, `surfaceColor`). Los logos relativos se convierten usando `GYMOS_FRONTEND_BASE_URL`.

En producción debe ser público (`https://gymfort.mx`). `localhost`/`127.0.0.1` no son accesibles para Gmail/Outlook y se omiten del HTML para evitar imágenes rotas.

### Durabilidad

- cola PostgreSQL;
- lease recuperable;
- reparto justo entre tenants;
- backoff;
- dead letter;
- preferencias;
- HMAC para comparación de destinatarios;
- `Message-ID` e idempotency headers estables.

### Pruebas

- prueba inmediata desde Configuración;
- prueba de una regla desde Automatizaciones seleccionando un socio real por búsqueda incremental;
- Processed emails en ZeptoMail para diagnóstico.

Trabajo posterior no bloqueante: bounce/complaint webhooks, reputación productiva, política DMARC final, múltiples proveedores y nuevos canales.

## 9.5 Automatizaciones como integración interna

Los eventos de pagos, membresías, reservas y recurrencia se publican/consumen mediante inbox durable. El motor no llama directamente proveedores externos.

`RECURRING_CHARGE_DUE` representa la llegada de la fecha programada de una ocurrencia, no la activación inicial del mandato. Si producto requiere avisar al activar domiciliación, debe añadirse un trigger específico.

## 9.6 Despliegue local

Docker Compose levanta frontend, backend, PostgreSQL 18, Redis 8 y MinIO. El perfil opcional `stripe` usa `stripe/stripe-cli:v1.50.3`.

Para email local pueden realizarse entregas reales con ZeptoMail, pero los clientes de correo no pueden cargar imágenes servidas desde `localhost`. La validación visual completa del logo requiere despliegue público o túnel.

El futuro GymFort Local Connector es un componente distinto del stack local de desarrollo.

## 9.7 CI/CD vigente

Pipeline:

- pruebas backend;
- pruebas/lint/typecheck/build frontend;
- Compose build/config;
- imagen Stripe CLI fijada.

Pendiente transversal: staging automático, escaneo formal de imágenes, restore y `stripeTest` con secretos sandbox protegidos.

## 9.8 Observabilidad y resiliencia

- Correlation ID y errores sanitizados.
- Métricas Prometheus de pagos, recurrencia, automatizaciones y correo.
- Idempotencia, timeouts, backoff, leasing y dead letters.
- Alertas de backlog/leases/fallos definitivos.
- Auditoría separada de logs técnicos.

## 9.9 Kiosco

El kiosco es perfil lógico por sucursal. Recepción crea el handoff; la pestaña usa sesión temporal con `no-store`, rate limit, inactividad, verificación del socio y capacidades cerradas por defecto.

Compra/renovación/cambio requieren Stripe self checkout listo; domiciliación exige además recurring.

## 9.10 Operación

Runbooks vigentes en `docs/operations/` cubren despliegue, rollback, backup, restore, incidentes, secretos, correo, kiosco, Stripe y mantenimiento.

Runbooks de email:

- `docs/operations/email-delivery.md`
- `docs/operations/zeptomail-email.md`

RPO piloto: 24 h. RTO piloto: 4 h; ambos deben validarse mediante simulacro antes de producción.
