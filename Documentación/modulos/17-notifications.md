# Notificaciones

Estado: `FUNCIONAL PARA EMAIL / PREPARADO PARA EXTENSIÓN`
Última revisión: 21 de agosto de 2026

## Implementado

- registro multicanal con `IN_APP` y `EMAIL` como canales ejecutables;
- contrato desacoplado `EmailDeliveryPort`, sin dependencias de Automatizaciones
  hacia SMTP ni hacia un proveedor comercial;
- perfil operativo para Zoho Mail con TLS requerido, validación de identidad del
  servidor y lista de buzones/alias autorizados;
- adaptador SMTP configurable exclusivamente mediante secretos de entorno;
- configuración por gimnasio de habilitación, proveedor, remitente, nombre y
  `Reply-To`, administrable por dueño, gerente autorizado o soporte;
- prueba síncrona con estado real de aceptación SMTP, última verificación
  exitosa y envío directo desde el centro de Automatizaciones;
- resolución tenant de correo para destinatarios `CLIENT` y `USER`;
- automatizaciones de pagos, membresías, domiciliaciones y reservas capaces de
  enviar al socio del evento sin capturar manualmente su dirección;
- variables de evento (`{{endsOn}}`, `{{amountCents}}`, etc.) y
  `{{recipientName}}` en asunto/cuerpo;
- cola PostgreSQL durable con leasing atómico, recuperación de leases vencidos,
  equidad entre tenants y `Message-ID` estable;
- cinco intentos con backoff exponencial, `DEAD_LETTER` y reintento manual;
- idempotencia de creación y cabeceras estables para deduplicación del
  proveedor;
- preferencias protegidas con HMAC-SHA-256, revalidadas antes de enviar, y
  estado `SUPPRESSED`;
- correo promocional bloqueado por defecto sin consentimiento explícito;
- métricas y tiempos Micrometer por tenant, gauges operativos y reglas
  Prometheus;
- pruebas unitarias del servicio, dispatcher y adaptador, además de pruebas
  PostgreSQL reales de concurrencia, recuperación y aislamiento.

## Pendiente de operación

- inyectar credenciales reales y verificar el dominio remitente en cada ambiente
  de integración/producción conforme a `docs/operations/zoho-email.md`;
- validar reputación, límites, SPF, DKIM, DMARC y entregabilidad con el proveedor
  elegido;
- incorporar webhooks de rebote/queja si el proveedor seleccionado los ofrece.

## Evolución posterior

- WhatsApp: adaptador separado, plantillas aprobadas y consentimiento por
  destinatario;
- push móvil: registro de dispositivos/tokens revocables y adaptador del
  proveedor de push;
- campañas promocionales: flujo público de suscripción/baja antes de habilitar
  su creación en la interfaz.

Runbook: `../../docs/operations/email-delivery.md`.
