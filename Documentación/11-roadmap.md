# 11. Roadmap de Implementación

## Fase 0 — Cimientos

- Monorepo.
- CI/CD.
- Docker.
- Docker Compose local con PostgreSQL, Redis y MinIO.
- Estructura Spring Modulith.
- Estructura Next.js.
- Convenciones.
- Manejo de errores.
- Auditoría.
- Autenticación completa inicial.
- JWT de corta duración.
- Refresh tokens revocables.
- Roles y permisos.
- Contexto de organización y sucursal.
- Registro técnico de kioscos de autoservicio.
- Flyway.
- OpenAPI.
- Observabilidad.

## Fase 1 — Identidad y clientes

- Organization.
- Branch.
- Person.
- User.
- Roles.
- Permisos.
- Client.
- Alta.
- Búsqueda.
- Ficha.
- Frontend de login.
- Frontend de recepción inicial.
- Frontend de alta y ficha de cliente.
- Base visual para modo kiosco.

## Fase 2 — Catálogo y ventas

- Product.
- Plan.
- Benefit.
- Sale.
- SaleItem.
- Payment.
- POS.
- Pagos internos/manuales: efectivo, transferencia y registro manual de tarjeta.

## Fase 3 — Membresías

- Alta.
- Activación.
- Renovación.
- Expiración.
- Beneficios de estudiante.
- Recordatorios.
- Renovación desde kiosco.
- Solicitud de cambio de membresía desde kiosco.

## Fase 3.1 — Autoservicio en kiosco

- Registro y configuración de terminales.
- Sesiones temporales de cliente.
- Resumen de membresía.
- Pagos pendientes.
- Inicio de pagos manuales.
- Carga de comprobantes.
- Renovación y cambio de plan con validación backend.
- Cierre automático por inactividad.
- Auditoría de acciones de autoservicio.

## Fase 4 — Control de acceso

- Puerto.
- Adaptador inicial.
- Accesos.
- Excepciones.
- Bitácora.
- Acceso temporal.

## Fase 5 — Inventario

- Existencias.
- Movimientos.
- Ventas.
- Mermas.
- Alertas.

## Fase 6 — Reservas

- Recursos.
- Disponibilidad.
- Booking.
- Cancelaciones.
- Sauna.
- Tina.
- Clases.
- Consultorios.

## Fase 7 — Coaches y bienestar

- Coaches.
- Rutinas.
- Versionado.
- Profesionales.
- Servicios.
- Agenda.

## Fase 8 — Automatizaciones y reportes

- Motor de eventos.
- Notificaciones.
- Dashboards.
- Reportes.

## Fase 9 - Hito 6: Productizacion por codigo

- Checklist vendible dentro de la aplicacion.
- Onboarding piloto de organizacion y sucursal activa.
- Creacion idempotente de catalogo base: planes, productos y kiosco.
- Importacion inicial de clientes por CSV.
- Dry-run y resultado por fila para importaciones.
- Permisos `onboarding.manage` y `client.import`.
- Pantallas `/onboarding` y `/clients/import`.

## Fase 10 - Hito 6.5: RediseÃ±o frontend y experiencia comercial

- Sistema visual frontend con tokens de marca.
- Navegacion principal por areas: Operacion, Gestion y Direccion.
- Portada como centro operativo del producto.
- Login, recepcion, dashboard, reporte de ventas y configuracion alineados a una UI moderna.
- Personalizacion local de nombre visible, colores y logo para demos y pilotos.
- Sin cambios de contrato HTTP ni reglas de negocio en frontend.

## Fase 11 - Hito 7: Preparacion administrativa y piloto comercial

- Capacitacion.
- Scripts y politica de backups.
- Checklist de venta e instalacion.
- Checklist de produccion.
- Entorno staging formal.
- Manual operativo para el gimnasio piloto.
- Ejecucion del piloto con datos reales.
- Registro de feedback, pricing, soporte y procesos comerciales.

## Definición de terminado

Una funcionalidad está terminada cuando:

- Cumple criterios de aceptación.
- Tiene pruebas.
- Tiene migración.
- Tiene documentación.
- Tiene auditoría.
- Tiene permisos.
- Tiene manejo de errores.
- Está desplegada en staging.
