# 11. Roadmap de implementación

PAY-001 y SELF-001 (#34) incorporan recuperación del pago y rotación de la
credencial del kiosco. La puerta de piloto sigue siendo la [aceptación en
Stripe test y staging](../docs/testing/stripe-kiosk-test-mode.md), con
conciliación de reembolsos y política de derechos separadas en #35.

El issue #29 incorpora el [inventario de autorización](../docs/security/endpoint-permissions.md) y la [matriz de aislamiento](../docs/security/multi-tenant-verification.md) a la verificación de piloto, con pruebas de matchers y regresiones HTTP/PostgreSQL. Las verificaciones de sesiones, consistencia financiera y proveedores conectados se siguen en #28, #33 y #34.

> **Adenda 29/10/2026:** El bloque histórico «En curso inmediato» refleja agosto. Los planes/beneficios/paquetes multi-sucursal (V98–V103) ya están en main, pero faltan E2E conectadas/seguridad/hardware para certificar. El Connector existe en rama posterior no fusionada. Usar [MVP](35-mvp-y-criterios-de-salida.md), [backlog](36-epicas-backlog-codex.md) y [auditoría](37-auditoria-repositorio-2026-10-29.md) como corte operativo actual.


Corte: 26 de agosto de 2026

Este documento expresa secuencia y estado, no sustituye el estado verificable del documento 13 ni la deuda del 28.

## Entregado para piloto

### Cimientos y SaaS multi-tenant

- Monorepo, Spring Modulith, Next.js, Flyway, PostgreSQL, Redis y MinIO.
- JWT/refresh, RBAC granular, organización, sucursal, soporte y auditoría. AUTH-001 añade familias revocables y refresh HttpOnly. El piloto requiere validar el corte y rollback en staging.
- Consolas separadas para plataforma, dueño, gerente, recepción y coach.
- Identidad GymFort permanente más personalización visual por gimnasio.

### Operación del gimnasio

- CRM de socios, importación, catálogo, POS, caja y reportes operativos.
- Membresías con periodos inclusivos, alta, renovación, cambio y beneficios. Recepción concentra esas operaciones y la venta de paquetes en una pantalla dedicada que permite elegir la operación antes o después de buscar al socio y consultar las ventas recientes autorizadas.
- Acceso lógico, dispositivos, bitácora, autorizaciones temporales y beneficios
  `ACCESS`; control plane/Connector API durables implementados y apertura física
  aún simulada.
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

### Ajustes del primer cliente

- completar inscripción inicial, autenticación y alta de socios sin membresía,
  registro desde kiosco y baja lógica automática por inactividad;
- modelar paquetes de visitas con un solo saldo utilizable en las sucursales
  permitidas del mismo tenant, nunca entre organizaciones;
- hacer que los beneficios ligados a un plan multi-sucursal se hereden en todas
  las sucursales donde el plan es válido, salvo alcance local explícito;
- resolver nombre, logo, colores y comunicación por sucursal, usando la marca de
  la organización como fallback corporativo.

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

## En curso: acceso físico en gimnasio piloto

El primer caso real está definido: gimnasio con torniquete/terminal Hikvision administrado por HikCentral Professional en red local.

Entregado en `feature/physical-access-control`:

- UX y permiso `access.physical.manage` separados;
- catálogo provider-neutral con HikCentral como primer contrato configurable;
- punto productivo con exactamente un beneficio `ACCESS`;
- vínculos explícitos de recursos y personas externas;
- estado deseado automático por eventos de membresía/socio/beneficio;
- jobs PostgreSQL con idempotencia, lease, reintentos y reconciliación;
- activación/rotación/revocación segura y heartbeat del Connector;
- contratos para eventos idempotentes y candidatos de persona;
- aislamiento tenant/sucursal y pruebas PostgreSQL/migración.

Secuencia restante:

1. configurar `GYM_ACCESS`, niveles, puntos y planes de visita diaria del cliente;
2. confirmar versión/licencia/API/SDK/topología de HikCentral en sitio;
3. implementar y empaquetar GymFort Local Connector;
4. implementar el adapter HikCentral sobre la interfaz oficial confirmada;
5. mantener enrolamiento facial manual inicialmente y sincronizar Access Levels;
6. validar concesión, revocación, reconciliación y eventos contra hardware real;
7. probar caída/recuperación y cerrar runbook/observabilidad/rotación;
8. automatizar persona/fotografía sólo si la interfaz oficial lo permite y pasa
   revisión de privacidad;
9. abstraer un segundo fabricante únicamente con un caso real.

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

## Check-in sin torniquete

ACC-006 incorpora al piloto cloud-only el registro presencial, consumo
transaccional y corrección auditada. El criterio de salida incluye CI verde del
SHA final y un recorrido de aceptación por Recepción. [Issue #61](https://github.com/Dagt/GymOs/issues/61).

## SAL-001

El piloto con cobros depende de la verificación de SAL-001 (#33), Stripe test (#34) y los recorridos operativos de paquetes (#35).

La habilitación piloto de Point requiere verificar moneda e importe pagado en la consulta autoritativa de una orden acreditada.

## Recepción: composición de membresías — 10/11/2026

Pulido incremental de acciones, resumen y formulario bajo UX #26, con [capturas y pruebas locales](../docs/testing/reception-memberships-polish.md). La aceptación visual del usuario y los gates conectados siguen pendientes.
