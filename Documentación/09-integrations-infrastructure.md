# 9. Integraciones e infraestructura

Corte: 21 de agosto de 2026

## 9.1 Arquitectura de proveedores

Los proveedores externos se aíslan detrás de puertos de aplicación. Los
dominios no almacenan secretos ni confían en datos devueltos por el navegador.

- Pagos: terminal manual, Mercado Pago OAuth/Point y Stripe
  Connect/Checkout/Setup/off-session.
- Correo: `EmailDeliveryPort` con adaptador SMTP, cola durable y configuración
  de remitente por tenant. El perfil Zoho, remitentes autorizados, prueba
  inmediata y evidencia de última verificación ya están implementados.
- Almacenamiento: MinIO/S3 para logos, medios, comprobantes y documentos.
- Acceso físico, biometría, WhatsApp, push y facturación: puertos previstos,
  adaptadores productivos fuera del corte.

## 9.2 Stripe y Mercado Pago

- Las credenciales permanecen en secretos del backend; nunca en
  `NEXT_PUBLIC_*`, imágenes o Git.
- Stripe usa cuenta Express conectada por gimnasio, Account Links y captura
  alojada. GymFort conserva sólo identificadores opacos y máscara.
- Mercado Pago usa OAuth/PKCE, selección de Point y órdenes idempotentes.
- Todo pago se persiste pendiente antes de invocar al proveedor.
- Webhook firmado más consulta autoritativa concilian monto, moneda, tenant,
  sucursal, venta, pago y método antes de publicar efectos.
- Los workers recuperan operaciones no terminales y preservan idempotencia.

## 9.3 Correo

El backend recibe proveedor visible, host, puerto, usuario, contraseña,
remitentes autorizados, TLS/SSL y timeouts SMTP por entorno. Cada gimnasio
configura nombre, reply-to y habilitación, no las credenciales. La cola usa
lease, backoff, dead letter, preferencias y destinatarios enmascarados/HMAC.

Zoho reutiliza el puerto desacoplado. La prueba administrativa intenta la
entrega inmediatamente, clasifica autenticación/configuración como fallo
permanente y conserva la última entrega de prueba `SENT`. SPF/DKIM/DMARC,
reputación y credenciales reales siguen siendo responsabilidades del ambiente.

## 9.4 Despliegue local

Docker Compose levanta frontend, backend, PostgreSQL 18, Redis 8 y MinIO. El
perfil opcional `stripe` usa `stripe/stripe-cli:v1.50.3` para reenviar eventos
Connect. El primer arranque entrega un `whsec_...` que debe inyectarse al backend
antes de validar firmas.

El repositorio contiene perfiles `local`, `test`, `development`, `staging` y
`production`, proxy de ejemplo, Prometheus y scripts de backup/restore. El seed
demo sólo se habilita en `local`.

## 9.5 CI/CD vigente

El pipeline ejecuta pruebas backend, pruebas/lint/typecheck/build frontend,
validación y build de Compose. También descarga la imagen fijada de Stripe CLI
y ejecuta su comando de versión. Permanecen pendientes despliegue automático a
staging, escaneo formal de imágenes, simulacro restore y `stripeTest` con
secretos sandbox protegidos.

## 9.6 Observabilidad y resiliencia

- Correlation ID y errores sanitizados.
- Métricas Prometheus de pagos, recurrencia, webhooks, automatizaciones y correo
  con etiquetas acotadas por tenant/sucursal.
- Idempotencia, timeouts, backoff, leasing recuperable y dead letters.
- Alertas para backlog, lease vencido, fallos de proveedor y correo definitivo.
- Auditoría separada de logs técnicos; nunca PAN, CVV, secretos o payloads
  sensibles.

## 9.7 Kiosco

El kiosco es un perfil lógico por sucursal, no una computadora registrada.
Recepción crea el handoff de navegador; la pestaña elimina tokens
administrativos y usa una sesión temporal con `no-store`, rate limit,
inactividad, verificación del socio y capacidades cerradas por defecto.

## 9.8 Operación

Runbooks vigentes: `docs/operations/` para despliegue, rollback, backup, restore,
incidentes, secretos, correo, kiosco, Stripe y mantenimiento. RPO piloto: 24
horas. RTO piloto: 4 horas. Ambos deben validarse mediante simulacro antes de
producción.
