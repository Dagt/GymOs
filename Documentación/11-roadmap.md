# 11. Roadmap de implementación

Corte: 22 de agosto de 2026

Este documento expresa secuencia y estado, no sustituye el estado verificable
del documento 13 ni la deuda del 28.

## Entregado para piloto

### Cimientos y SaaS multi-tenant

- Monorepo, Spring Modulith, Next.js, Flyway, PostgreSQL, Redis y MinIO.
- JWT/refresh, RBAC granular, organización, sucursal, soporte y auditoría.
- Consolas separadas para plataforma, dueño, gerente, recepción y coach.
- Identidad GymFort permanente más personalización visual por gimnasio.

### Operación del gimnasio

- CRM de socios, importación, catálogo, POS, caja y reportes operativos.
- Membresías con periodos inclusivos, alta, renovación, cambio y beneficios.
- Acceso lógico, dispositivos, bitácora, autorizaciones temporales y beneficios
  `ACCESS`; la apertura física sigue simulada.
- Coaches, ejercicios, rutinas versionadas, asignaciones, métricas, CSV y PDF.
- Profesionales wellness, servicios, disponibilidad, agenda y citas.

### Pagos y autoservicio

- Efectivo, transferencia y terminal externa con validación manual.
- Mercado Pago OAuth/Point.
- Stripe Connect, Checkout, Setup, webhook, conciliación y recibos.
- Domiciliación con consentimiento, cargos por vigencia, reintentos,
  recuperación, cambio de tarjeta y revocación.
- Kiosco lógico por sucursal, abierto sólo por Recepción, con sesión aislada,
  membresías, pagos, reservas, wellness y actualización de contacto.
- Elegibilidad de planes multi-sucursal en sedes secundarias.

### Automatización y correo

- Reglas manuales, programadas y por eventos de pagos, membresías y reservas.
- Inbox durable, idempotencia, leasing, reintentos, dead letters, pausa por
  tenant, historial y métricas.
- Cola SMTP durable, configuración de remitente, preferencias, prueba y
  recuperación.

### Calidad y operación local

- Suites backend/frontend y E2E críticos.
- PostgreSQL y Stripe sandbox como suites opt-in.
- Docker Compose, perfil Stripe CLI fijado, CI de build/config y runbooks.
- Sistema visual unificado y notificaciones flotantes globales.

## En curso inmediato

### Activación operativa de Zoho

- Inyectar las credenciales Zoho del ambiente mediante el perfil SMTP ya
  implementado, sin guardarlas por tenant.
- Ejecutar y conservar evidencia de la prueba real inmediata desde
  Configuración.
- Verificar dominio, SPF, DKIM y DMARC.
- Probar remitente/reply-to, TLS, timeouts, reintentos y dead letter.
- Validar entregabilidad real y estrategia de rebotes/quejas disponible.

### Aceptación conectada

- Ejecutar `stripeTest` en CI protegido con cuenta Connect test.
- Ampliar E2E de timeout, reintento, recuperación y webhook duplicado.
- Validar cancelación/reembolso autoritativo por proveedor.
- Mantener regresión de membresías/reservas multi-sucursal.

## Siguiente bloque: acceso físico en gimnasio piloto

La integración física deja de considerarse fuera de alcance indefinido. El
primer caso real está definido: gimnasio con torniquete/terminal Hikvision
administrado por HikCentral Professional en red local.

La implementación seguirá esta secuencia:

1. Ajustar UX de Access: retirar paquetes de visitas del flujo principal,
   renombrar pase temporal a autorización temporal y reforzar permisos/pruebas.
2. Configurar `GYM_ACCESS`, `SAUNA_ACCESS` y los planes de visita diaria mediante
   el motor normal de membresías.
3. Confirmar versión/licencia/API/SDK/topología de HikCentral y hardware real.
4. Introducir un puerto de integración física independiente de Hikvision.
5. Crear GymFort Local Connector y adaptador HikCentral.
6. Primera entrega: mantener enrolamiento facial manual una sola vez y
   sincronizar automáticamente concesión/revocación de Access Levels desde
   GymFort.
7. Añadir reconciliación, estado de sincronización y manejo de errores.
8. Incorporar eventos físicos de HikCentral a `access_log`.
9. Automatizar alta/fotografía sólo si la interfaz oficial instalada lo permite
   y después de revisar privacidad/seguridad.
10. Abstraer un segundo fabricante únicamente al existir un segundo caso real.

El modo inicial será de **sincronización de permisos**: HikCentral y los
terminales deciden localmente en cada paso. GymFort Cloud no debe ser requisito
en línea para abrir el torniquete.

Esta decisión reactiva funcionalmente el alcance descrito por `ACC-002`; el
registro vivo de deuda debe reflejar que el acceso físico volvió al roadmap y ya
no es una posposición indefinida.

## Antes de producción

- Matriz completa de autorización por tenant/sucursal y mutaciones auditadas.
- Migración limpia y upgrade de una base de referencia.
- Staging, HTTPS, escaneo de imágenes y secretos gestionados.
- Simulacro documentado de backup/restore contra RPO/RTO.
- Dashboards/alertas operativos y respuesta a incidentes ensayada.
- Pruebas de volumen y concurrencia para comercio, inventario y reservas.
- Para acceso físico: reconciliación probada, runbook de caída del conector,
  revocaciones pendientes, privacidad biométrica y operación local sin Internet.
- Manuales por rol y piloto controlado con feedback.

## Fuera de V1

- Aplicación móvil o portal remoto completo del socio.
- Pagos, comisiones o efectivo acordados directamente entre coach y socio.
- Soporte simultáneo de múltiples fabricantes de acceso sin un caso real que lo
  justifique.
- Procesamiento biométrico propio de GymFort.
- WhatsApp, push, facturación y marketplace SaaS avanzados.

## Definición de terminado

Una capacidad está terminada cuando tiene contrato, permisos, reglas de tenant y
sucursal, migración cuando aplica, auditoría, errores seguros, pruebas,
documentación y validación en el entorno objetivo. Para proveedores externos se
requiere además evidencia sandbox/productiva controlada y runbook de
recuperación.
