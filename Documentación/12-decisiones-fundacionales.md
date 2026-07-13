# 12. Decisiones Fundacionales

Estado: confirmado  
Fecha: 13 de julio de 2026

## 12.1 Objetivo comercial

GymOS debe construirse como producto vendible desde la primera versión operativa. Los primeros clientes objetivo son gimnasios concretos, pero la solución no debe quedar acoplada a una operación específica.

## 12.2 Modelo comercial y operativo

- El sistema debe soportar gimnasios independientes.
- El sistema debe soportar cadenas con varias sucursales.
- `Organization` representa al cliente comercial.
- `Branch` representa una sucursal física u operativa.
- Los usuarios, permisos, ventas, accesos, reservas, inventario y reportes deben considerar contexto de organización y sucursal cuando aplique.

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

Los pagos de la primera versión se procesan internamente y de forma manual:

- Efectivo.
- Transferencia con comprobante y validación manual.
- Tarjeta registrada manualmente si el gimnasio usa terminal externa.

Las pasarelas externas quedan fuera del primer alcance obligatorio, pero deben existir puertos para integrarlas posteriormente.

## 12.5.1 Kiosco de autoservicio

GymOS debe permitir implementar una pantalla o kiosco dentro del gimnasio para que los clientes puedan:

- Consultar su membresía.
- Ver pagos pendientes.
- Renovar membresía.
- Solicitar cambio de plan.
- Iniciar pagos internos/manuales.
- Cargar comprobantes.
- Actualizar datos de contacto permitidos.

El kiosco usa sesiones temporales y restringidas. No es una sesión administrativa ni reemplaza al portal móvil/remoto. Ninguna regla de negocio se ejecuta en el frontend del kiosco.

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
