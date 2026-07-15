# 3. Software Design Specification

## 3.1 Estilo arquitectónico

GymOS se implementará como monolito modular con Spring Modulith. Cada módulo representa un dominio delimitado y expone únicamente contratos públicos.

## 3.2 Módulos

- identity
- security
- organizations
- selfservice
- clients
- catalog
- sales
- payments
- memberships
- benefits
- inventory
- bookings
- access
- coaching
- wellness
- notifications
- automations
- reporting
- audit
- configuration

## 3.3 Reglas de dependencia

- Ningún módulo accede directamente a tablas de otro módulo.
- Las lecturas cruzadas se realizan mediante API interna o proyecciones.
- Los efectos secundarios se coordinan con eventos.
- Los adaptadores externos no contienen lógica de dominio.
- El frontend nunca decide reglas de negocio.

## 3.4 Patrón interno por módulo

Cada módulo debe contener:

- `domain`
- `application`
- `infrastructure`
- `api`
- `config`
- `tests`

## 3.5 Transacciones

- Una operación dentro del mismo agregado debe ser atómica.
- Los efectos entre módulos se ejecutan mediante eventos.
- Los consumidores deben ser idempotentes.
- Los pagos y renovaciones deben usar claves de idempotencia.

## 3.6 Errores

Formato estándar:

```json
{
  "code": "MEMBERSHIP_NOT_ACTIVE",
  "message": "La membresía no está activa.",
  "details": {},
  "correlationId": "uuid"
}
```

## 3.7 Auditoría

Las acciones críticas deben registrar:

- actor
- rol
- acción
- entidad
- identificador
- valores anteriores
- valores nuevos
- fecha
- origen
- motivo

## 3.8 Seguridad

- Autenticación basada en tokens de corta duración.
- Refresh tokens revocables.
- Contraseñas con algoritmo robusto.
- RBAC con permisos granulares.
- Cifrado de secretos.
- Rate limiting.
- Sesiones auditables.
- Principio de mínimo privilegio.

## 3.9 Configuración por organización

GymOS debe soportar múltiples organizaciones cliente desde la base. Toda tabla de negocio debe incluir `organization_id` cuando pertenezca a datos de un cliente comercial.

El sistema también debe modelar sucursales mediante `branch_id` en las tablas donde la operación sea física o localizable: ventas, pagos, inventario, reservas, accesos, usuarios asignados, reportes operativos y configuración local.

Reglas obligatorias:
- Ninguna consulta operativa puede mezclar organizaciones sin autorización explícita.
- La autorización siempre se evalúa con contexto de organización y, cuando aplique, sucursal.
- Las restricciones únicas deben considerar `organization_id` y, cuando corresponda, `branch_id`.
- Los módulos no pueden resolver aislamiento multi-tenant con filtros manuales dispersos; debe existir una convención común y pruebas que lo verifiquen.

## 3.10 Eventos

Convención:
- Pasado simple.
- Nombre de dominio.
- Versión explícita.

Ejemplos:
- `PersonRegisteredV1`
- `PaymentCompletedV1`
- `MembershipActivatedV1`
- `BookingCancelledV1`

## 3.11 Estrategia de lectura

- CRUD simple para configuración.
- Proyecciones para dashboards.
- Consultas optimizadas para recepción.
- Índices según patrones reales.

## 3.12 Frontend

- Aplicación web separada.
- Contratos TypeScript generados o alineados con OpenAPI.
- Manejo uniforme de errores.
- Autorización por permiso.
- No duplicar reglas.

## 3.13 Kiosco de autoservicio

El kiosco de autoservicio es una superficie frontend restringida para clientes dentro del gimnasio. No es un portal remoto completo ni una sesión administrativa.

Reglas:
- Debe usar sesiones temporales de autoservicio con expiración corta.
- Debe operar dentro de organización y sucursal.
- No debe exponer información sensible innecesaria.
- No puede activar membresías sin pago completado o aprobado.
- No puede aplicar descuentos, beneficios o cambios de plan sin validación backend.
- Debe cerrar sesión automáticamente por inactividad.
- Toda acción debe auditarse con origen `SELF_SERVICE_KIOSK`.
- Los pagos manuales iniciados desde kiosco quedan en estado pendiente o en revisión hasta que el flujo de pagos los complete según reglas.
- Los pagos manuales iniciados desde kiosco quedan en estado pendiente o en revisiÃ³n hasta que el flujo de pagos los complete segÃºn reglas.

## 3.14 Estabilizacion arquitectonica Hito 3.5

Decisiones implementadas:
- `memberships` ya no escribe en tablas `sale`, `sale_item` ni `payment`.
- `sales` es propietario unico de ventas, partidas, pagos, validacion, numeros de venta e idempotencia comercial.
- La API interna publica de ventas vive en `com.gymos.sales.api.operations` y expone `SalesOperations`, comandos de renovacion/cambio de plan y resultados inmutables.
- `memberships` expone `com.gymos.memberships.api.operations.MembershipOperations`; `selfservice` consume esa API publica y ya no depende de `memberships.application`.
- `clients` expone `com.gymos.clients.api.operations.ClientLookupOperations`; la verificacion de identidad del kiosco se resuelve dentro del modulo `clients` y `selfservice` ya no consulta tablas `client` ni `person`.
- La capa `application` ya no depende de DTOs HTTP ni controladores; los controladores mapean request/response hacia comandos/resultados de aplicacion.
- Eventos de negocio versionados implementados en APIs de sus modulos propietarios: `sales.api.events` contiene eventos de venta/pago y `memberships.api.events` contiene eventos de membresias.
- La activacion de membresias por pago se consume desde `PaymentCompletedV1` y se protege con `processed_domain_event`.
- Las operaciones comerciales aceptan `idempotencyKey`; `sale` y `payment` tienen restricciones unicas por organizacion y hash de solicitud para detectar reuse con payload distinto.
- RBAC granular se aplica por ruta interna con permisos como `sale.create`, `payment.manual.validate`, `membership.renew` y `report.sales.read`.
- El kiosco sigue siendo publico sin JWT interno, pero exige token temporal por sesion en `X-Self-Service-Session-Token`, verificacion por contacto + fecha de nacimiento, rate limiting via puerto `RateLimiter`, expiracion, bloqueo por intentos y auditoria.

Pruebas arquitectonicas:
- Spring Modulith verifica fronteras.
- ArchUnit bloquea dependencias de application hacia controladores HTTP.
- ArchUnit bloquea dependencias a infraestructura de otros modulos.
- ArchUnit bloquea que `selfservice` dependa de `memberships.application`, `clients.application` o `clients.infrastructure`.
- ArchUnit bloquea que los eventos de negocio vuelvan a `common.events`.
- `MembershipService` y `SelfServiceService` ya no usan `JdbcClient`; el uso temporal restante en application queda listado explicitamente hasta extraer command repositories.

## 3.15 Operacion Hito 4

Decisiones implementadas:
- `access` gestiona dispositivos, pases temporales y bitacora de accesos manuales o evaluados por membresia.
- `inventory` gestiona existencias, movimientos manuales y descuento automatico por ventas pagadas con bloqueo de stock negativo.
- `bookings` gestiona recursos reservables y reservas operativas por sucursal.
- Las reservas validan capacidad del recurso, horario operativo y traslapes de reservas activas.
- `inventory` consume `PaymentCompletedV1` publicado por `sales` y registra movimientos `SALE` idempotentes por venta y producto.
- Las pantallas `/access`, `/inventory` y `/bookings` son herramientas operativas para recepcion.
