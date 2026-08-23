# 12. Decisiones Fundacionales

Estado: confirmado y actualizado
Última revisión: 21 de agosto de 2026

## 12.1 Objetivo comercial

GymFort debe construirse como producto vendible desde la primera versión operativa. Los primeros clientes objetivo son gimnasios concretos, pero la solución no debe quedar acoplada a una operación específica.

## 12.2 Modelo comercial y operativo

- El sistema debe soportar gimnasios independientes.
- El sistema debe soportar cadenas con varias sucursales.
- `Organization` representa al cliente comercial.
- `Branch` representa una sucursal física u operativa.
- Los usuarios, permisos, ventas, accesos, reservas, inventario y reportes deben considerar contexto de organización y sucursal cuando aplique.

### 12.2.1 Cobranza comercial de GymFort

- `Organization` es el cliente comercial de GymFort; no se asume que dos gimnasios con los mismos dueños sean necesariamente el mismo cliente.
- Cada `Branch` contratada es una línea de servicio facturable. El total del cliente es la suma de sus sucursales con suscripción comercial activa, pero el plan, fecha de corte, importe y evidencia de cobro se conservan por sucursal para poder añadir, pausar o cotizar una sede de forma independiente.
- La sugerencia de plan se calcula por socios con membresía activa en la sede. Los usuarios internos activos y los planes de membresía activos se muestran como contexto comercial; no incrementan automáticamente el precio.
- V1 registra cobros recibidos por transferencia, efectivo, tarjeta, Stripe u otro medio. La bitácora es inmutable y auditable; un pago de mensualidad mueve la siguiente fecha de corte y los pagos de implementación completan automáticamente ese cargo al cubrir su importe.
- Facturación CFDI, cobro automático de la suscripción SaaS, conciliación bancaria y ajustes/reversiones contables requieren un bloque posterior; no deben simularse como pagos confirmados.

## 12.3 Primer hito

El primer hito debe incluir:

- Backend Spring Boot con Spring Modulith.
- Frontend Next.js funcional.
- Autenticación real.
- Roles y permisos.
- Contexto de organización y sucursal.
- Manejo uniforme de errores.
- Auditoría base.
- Migraciones Flyway.
- OpenAPI.
- Docker Compose local.

## 12.4 Autenticación y seguridad

La autenticación no será simulada en el primer hito. Debe incluir:

- Login.
- JWT de corta duración.
- Refresh tokens revocables.
- Logout.
- Usuario actual.
- RBAC.
- Permisos configurables.
- Evaluación de permisos por organización y sucursal.

## 12.5 Pagos V1

Los pagos de la primera versión admiten modalidades manuales e integradas:

- Efectivo.
- Transferencia con comprobante y validación manual.
- Tarjeta procesada en terminal externa y registrada para validación manual.
- Mercado Pago Point integrado en recepción.
- Stripe Checkout y Setup alojados para kiosco y domiciliación.

El navegador nunca confirma un pago. Webhooks firmados, consulta autoritativa e
idempotencia controlan los efectos comerciales.

## 12.5.1 Kiosco de autoservicio

GymFort debe permitir implementar una pantalla o kiosco dentro del gimnasio para que los clientes puedan:

- Consultar su membresía.
- Ver pagos pendientes.
- Renovar membresía.
- Solicitar cambio de plan.
- Contratar, renovar o cambiar plan mediante Stripe Checkout.
- Autorizar, sustituir o revocar domiciliación con captura alojada.
- Consultar pagos y comprobantes conciliados.
- Actualizar datos de contacto permitidos.

El kiosco usa sesiones temporales y restringidas. No es una sesión administrativa ni reemplaza al portal móvil/remoto. Ninguna regla de negocio se ejecuta en el frontend del kiosco.

Sólo Recepción abre la sesión para socios. Gerencia configura las capacidades y
puede activar/desactivar una cuenta Stripe ya vinculada, pero conectar o
refrescar esa cuenta corresponde al dueño o soporte de plataforma.

## 12.6 Repositorio e infraestructura local

La estructura recomendada es monorepo:

- `backend/`
- `frontend/`
- `infra/`
- `Documentación/`

El entorno local recomendado usa Docker Compose con:

- PostgreSQL.
- Redis.
- MinIO como almacenamiento S3-compatible.
- Backend Spring Boot.
- Frontend Next.js.

## 12.7 Criterio de producto vendible

Una entrega no se considera vendible si depende de datos hardcodeados, autenticación simulada, permisos omitidos, ausencia de auditoría en acciones críticas o flujos principales sin frontend. Cuando una funcionalidad se exponga en kiosco, tampoco se considera vendible si no tiene expiración de sesión, origen auditado y límites claros de información visible.
