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

- Coaches vinculados con usuarios y alcance por sucursal.
- Catálogo amplio, buscable y extensible de ejercicios.
- Constructor de rutinas por sesiones, bloques y prescripciones flexibles.
- Borradores, publicación y versionado inmutable.
- Asignación idempotente a socios y PDF imprimible.
- Métricas semanales por coach, socio y sucursal.
- Profesionales.
- Servicios.
- Agenda.

Para Coaches y rutinas, quedan fuera de esta fase el cobro por plataforma, registro
del efectivo del coach, comisiones y app remota del socio. El plan ejecutable es
`modulos/14-coaching.md`.

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

## Fase 10 - Hito 6.5: Rediseño frontend y experiencia comercial

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

## Hito 7.1 - Calidad y regresion

Estado: IN_PROGRESS

- Suite transversal multi-tenant.
- Regresion de autenticacion, ventas, pagos, membresias, kiosco, inventario, reservas, acceso e importacion.
- Pruebas de concurrencia e idempotencia.
- Testcontainers como base para flujos criticos.

## Hito 7.2 - Refactorizacion tecnica

Estado: IN_PROGRESS

- Separacion gradual de servicios grandes por casos de uso.
- Eliminacion de `JdbcClient` en `application`.
- Division del cliente frontend API por dominio.
- Extraccion de paginas frontend grandes en componentes y hooks.

## Hito 7.3 - Operacion y produccion

Estado: IN_PROGRESS

- Perfiles `local`, `test`, `development`, `staging` y `production`.
- Seed demo limitado a `local`.
- Reverse proxy, HTTPS, backups, restore, observabilidad y runbooks.
- CI ampliado con validacion Docker.

## Hito 7.4 - Cierre funcional minimo

Estado: PLANNED

- Beneficios.
- Expiracion y recordatorios de membresia.
- Renovacion automatica con pasarela abstracta.
- Visitas.
- Cancelaciones y reembolsos.
- Reservas pagadas.
- Automatizaciones programadas y correo.

## Hito 7.5 - Preparacion del piloto

Estado: PLANNED

- Checklist operativo del gimnasio piloto.
- Plantillas de importacion.
- Manuales por rol.
- Operacion paralela.
- Feedback, soporte y pricing.

## Hito 8 - Integraciones reales

- Torniquetes, reconocimiento facial, WhatsApp, SMS, pasarela de pagos, facturacion, webhooks y modo offline.

## Hito 9 - Aplicacion de clientes

- Login cliente, membresia, pagos, reservas, rutinas, notificaciones, perfil, QR e historial.

## Hito 10 - SaaS avanzado

- Provisionamiento, suscripciones B2B, licenciamiento, modulos contratados, facturacion GymOS y marketplace.

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
