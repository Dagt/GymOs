# 11. Roadmap de implementación

Corte: 22 de agosto de 2026

Este documento expresa secuencia y estado, no sustituye el estado verificable del documento 13 ni la deuda del 28.

## Entregado para piloto

### Cimientos y SaaS multi-tenant

- Monorepo, Spring Modulith, Next.js, Flyway, PostgreSQL, Redis y MinIO.
- JWT/refresh, RBAC granular, organización, sucursal, soporte y auditoría.
- Consolas separadas para plataforma, dueño, gerente, recepción y coach.
- Identidad GymFort permanente más personalización visual por gimnasio.

### Operación del gimnasio

- CRM de socios, importación, catálogo, POS, caja y reportes operativos.
- Membresías con periodos inclusivos, alta, renovación, cambio y beneficios.
- Acceso lógico, dispositivos, bitácora, autorizaciones temporales y beneficios `ACCESS`; apertura física aún simulada.
- Coaches, ejercicios, rutinas versionadas, asignaciones, métricas, CSV/PDF.
- Profesionales wellness, servicios, disponibilidad, agenda y citas.

### Pagos y autoservicio

- Efectivo, transferencia y terminal externa con validación manual.
- Mercado Pago OAuth/Point.
- Stripe Connect, Checkout, Setup, webhook, conciliación y recibos.
- Domiciliación con consentimiento, cargos por vigencia, reintentos, recuperación, cambio de tarjeta y revocación.
- Kiosco lógico por sucursal, abierto sólo por Recepción, con sesión aislada, membresías, pagos, reservas, wellness y actualización de contacto.
- Elegibilidad multi-sucursal en sedes secundarias.

### Automatizaciones

- Reglas manuales, programadas y por eventos de pagos, membresías, reservas y recurrencia.
- Inbox durable, idempotencia, leasing, reparto justo entre tenants, reintentos, dead letters, pausa y métricas.
- UI simplificada en **Automatizaciones / Actividad / Problemas**.
- Creación desde cero sin plantillas recomendadas.
- Toggle + `Probar ahora` directo en cada regla.
- Prueba de email usando búsqueda dinámica y un socio real.
- Gerencia autorizada para crear/administrar/probar reglas mediante V80.

### Correo transaccional / ZeptoMail

- `EmailDeliveryPort` y adaptador SMTP desacoplado.
- ZeptoMail configurado como proveedor operativo mediante secretos de entorno.
- `gymfort.mx` verificado con DKIM y CNAME/return-path.
- Entrega real a bandeja confirmada el 22 de agosto de 2026.
- `From` central recomendado `notificaciones@gymfort.mx` + nombre/`Reply-To` tenant.
- Cola durable, preferencias, leasing, retry/dead letter y prueba inmediata.
- Plantilla HTML con branding del gimnasio y logo público cuando está disponible.

### Calidad y operación local

- Suites backend/frontend y E2E críticos.
- PostgreSQL y Stripe sandbox como suites opt-in.
- Docker Compose, Stripe CLI fijado, CI y runbooks.
- Sistema visual unificado y feedback global.

## En curso inmediato

### Administración comercial de GymFort

- Panel de plataforma por cliente y sucursal: socios activos, personal interno,
  planes operativos, recomendación comercial, plan contratado, MRR, corte y
  estado de cobranza.
- Registro auditable e inmutable de mensualidad, implementación y otros cobros
  recibidos; el siguiente corte y la liquidación de implementación se actualizan
  sin manipular los pagos de los socios del gimnasio.
- Completar catálogo de precios/descuentos/impuestos, historial exportable,
  facturación fiscal, ajustes y cobro recurrente de la suscripción SaaS.

### Aceptación conectada de pagos

- Ejecutar `stripeTest` en CI protegido con cuenta Connect test.
- Ampliar E2E de timeout, reintento, recuperación y webhook duplicado.
- Validar cancelación/reembolso autoritativo por proveedor.
- Mantener regresión de membresías/reservas multi-sucursal.

### Hardening transversal

- matriz completa de autorización por tenant/sucursal;
- migración limpia + upgrade de base real;
- backup/restore ensayado;
- staging/HTTPS/secret management;
- dashboards/alertas y respuesta a incidentes.

## Mejoras posteriores de Automatizaciones/Notificaciones

Estas mejoras no bloquean el cierre V1:

- trigger específico de **domiciliación activada** distinto de `RECURRING_CHARGE_DUE`;
- webhooks de rebote/queja de ZeptoMail;
- seguimiento productivo de reputación y DMARC;
- remitentes/dominios propios por gimnasio si producto lo requiere;
- WhatsApp y push mediante adaptadores nuevos de `notifications`;
- campañas promocionales únicamente con consentimiento.

## Siguiente bloque: acceso físico en gimnasio piloto

El primer caso real está definido: gimnasio con torniquete/terminal Hikvision administrado por HikCentral Professional en red local.

Secuencia:

1. ajustar UX de Access y permisos/pruebas;
2. configurar `GYM_ACCESS`, `SAUNA_ACCESS` y planes de visita diaria;
3. confirmar versión/licencia/API/SDK/topología de HikCentral;
4. introducir puerto de integración física independiente;
5. crear GymFort Local Connector y adaptador HikCentral;
6. mantener enrolamiento facial manual inicialmente y sincronizar Access Levels;
7. añadir reconciliación/estado/errores;
8. importar eventos a `access_log`;
9. automatizar persona/fotografía sólo si la interfaz oficial lo permite;
10. abstraer segundo fabricante sólo con caso real.

El modo inicial será de **sincronización de permisos**: HikCentral y terminales deciden localmente; GymFort Cloud no debe ser requisito en línea para abrir el torniquete.

## Antes de producción

- matriz completa de autorización;
- migración limpia y upgrade de referencia;
- staging, HTTPS, escaneo de imágenes y secretos gestionados;
- simulacro backup/restore contra RPO/RTO;
- dashboards/alertas ensayados;
- volumen/concurrencia para comercio, inventario y reservas;
- acceso físico con reconciliación/runbook/privacidad probados;
- manuales por rol y piloto controlado.

## Fuera de V1

- aplicación móvil/portal remoto completo del socio;
- pagos/comisiones directos entre coach y socio;
- múltiples fabricantes de acceso sin caso real;
- procesamiento biométrico propio de GymFort;
- WhatsApp, push, facturación y marketplace SaaS avanzados.

## Definición de terminado

Una capacidad está terminada cuando tiene contrato, permisos, reglas de tenant/sucursal, migración cuando aplica, auditoría, errores seguros, pruebas, documentación y validación en el entorno objetivo. Para proveedores externos se requiere además evidencia sandbox/productiva controlada y runbook de recuperación.
