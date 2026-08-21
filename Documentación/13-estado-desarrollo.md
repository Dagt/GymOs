# 13. Estado actual de desarrollo

Estado: FUENTE CANÓNICA / EN PROGRESO
Corte: 21 de agosto de 2026
Código auditado: `feature/automations-zoho-email`, incluyendo V78.

## 1. Resumen ejecutivo

GymFort ya opera como monolito modular multi-tenant y multi-sucursal. El piloto
incluye las consolas SaaS y de gimnasio, recepción/POS, kiosco aislado,
comercio y membresías, reservas, inventario, coaches, wellness,
automatizaciones y correo durable.

La identidad comercial visible es GymFort. `GymOS`/`gymos` continúa en nombres
técnicos para mantener compatibilidad. La siguiente línea de trabajo es la
integración real y validación de correo con Zoho; no está declarada terminada.

Estados usados: `FUNCIONAL`, `ESTABILIZADO`, `PARCIAL` y `PLANIFICADO`. Ningún
módulo se considera completo por el nombre de una rama: la evidencia proviene
del código, migraciones y pruebas.

## 2. Estado por módulo

| Módulo | Estado | Implementado | Pendiente principal |
|---|---|---|---|
| [Organizaciones](modulos/01-organizations.md) | FUNCIONAL | Plataforma, tenants, sucursales, equipo, soporte y marca persistente | Matriz negativa transversal (`ORG-001`) |
| [Identidad y seguridad](modulos/02-identity-security.md) | FUNCIONAL | JWT, refresh revocable, selección de sucursal, RBAC y separación 401/403 | Endurecer almacenamiento/rotación de refresh (`AUTH-001`) |
| [Configuración](modulos/03-configuration.md) | FUNCIONAL | Marca tenant, cobros, kiosco y correo por permisos; UI unificada | Completar ciclo de eliminación/reemplazo de logo (`CFG-001`) |
| [Onboarding](modulos/04-onboarding.md) | FUNCIONAL | Checklist, setup piloto idempotente e importación inicial | Recuperación probada de altas parciales (`ONB-001`) |
| [Socios](modulos/05-clients.md) | FUNCIONAL | Alta, búsqueda, detalle, importación y visibilidad multi-sucursal | PII, duplicados y pruebas CRUD (`CLI-001`) |
| [Catálogo](modulos/06-catalog.md) | ESTABILIZADO | Productos, planes, categorías, unidades y asociación por sucursal | Volumen, legacy y archivos (`CAT-001`) |
| [Ventas](modulos/07-sales.md) | FUNCIONAL | POS, ventas contextualizadas, pagos, reembolsos, recibos e inventario | Concurrencia/conciliación E2E (`SAL-001`) |
| [Membresías](modulos/08-memberships.md) | ESTABILIZADO | Periodos inclusivos, activa única, alta, renovación, cambio y cotización | E2E financiero completo |
| [Beneficios/elegibilidad](modulos/09-benefits.md) | ESTABILIZADO | Descuentos y acceso heredado mediante política transversal | Concurrencia y negativos de permisos |
| [Acceso](modulos/10-access.md) | FUNCIONAL | Visitas, decisión por membresía/beneficio y bitácora | Doble consumo/privacidad (`ACC-001`); hardware offline fuera de alcance |
| [Kiosco](modulos/11-selfservice.md) | FUNCIONAL / ACEPTACIÓN | Apertura solo por Recepción, sesión aislada, membresías, pagos, domiciliación, reservas, wellness y datos | Publicar ejecución automatizada de Stripe sandbox (`SELF-001`) |
| [Reservas](modulos/12-bookings.md) | FUNCIONAL | Recursos, disponibilidad, pago y acceso desde sucursal secundaria con plan multi-sucursal | Colisiones/cancelación/E2E (`BKG-001`) |
| [Inventario](modulos/13-inventory.md) | ESTABILIZADO | Existencias, movimientos, transferencias, conteos y valuación | Paginación y feedback por partida (`INV-001`, `INV-002`) |
| [Coaches](modulos/14-coaching.md) | ESTABILIZADO | Perfiles, catálogo, rutinas versionadas, asignación, métricas, CSV y PDF | Cleanup durable de medios (`COA-018`) |
| [Wellness](modulos/15-wellness.md) | ESTABILIZADO | Profesionales, servicios, disponibilidad, agenda y citas | E2E y hardening multi-tenant |
| [Automatizaciones](modulos/16-automations.md) | FUNCIONAL / ACEPTACIÓN | Reglas, recetas, inbox durable, leases, reintentos, pausa, historial y telemetría | Mantener pruebas PostgreSQL/CI |
| [Notificaciones](modulos/17-notifications.md) | FUNCIONAL SMTP | Cola durable, preferencias, remitente tenant, prueba y recuperación | Zoho, dominio y entregabilidad reales (`NOT-001`) |
| [Reportes](modulos/18-reporting.md) | PARCIAL | Reportes por dominio y métricas de coaches/operación | Definiciones KPI y filtros homogéneos (`REP-001`) |
| [Auditoría](modulos/19-audit.md) | FUNCIONAL | Registro de mutaciones sensibles y motivos operativos | Matriz/retención/inmutabilidad (`AUD-001`) |
| [Operación común](modulos/20-common-operations.md) | FUNCIONAL PARA LOCAL | Errores correlacionados, Flyway, métricas, Docker, CI y runbooks | Restore, staging y simulacros (`DB-001`, `OPS-001`) |
| [Pagos](modulos/21-payments.md) | FUNCIONAL / ACEPTACIÓN | Manuales, Mercado Pago, Stripe Connect/Checkout, webhooks, recibos y recurrencia | Sandbox CI y reembolso/cancelación por proveedor (`PAY-001`) |

## 3. Decisiones operativas vigentes

- La venta y la membresía conservan la sucursal donde se originaron. Un plan
  `multiBranchAccess=true` permite que el socio sea reconocido, acceda y reserve
  en cualquier sucursal activa del tenant.
- Si desde una sucursal secundaria se contrata un plan local, la nueva operación
  queda contextualizada en esa sucursal; no se reasigna silenciosamente la
  membresía original antes de confirmar el pago.
- Solo Recepción recibe `selfservice.session.launch`. Dueño, administrador,
  gerente y soporte configuran únicamente lo permitido por su rol, pero no
  abren sesiones de socios desde su consola.
- Gerencia conserva `payment.settings.manage` para activar/desactivar modalidades
  ya vinculadas. Conectar o refrescar Stripe exige
  `payment.provider.stripe.manage` o `platform.admin`.
- El retorno del navegador desde Stripe no confirma un cobro. Webhook firmado y
  consulta autoritativa producen, de forma idempotente, venta, pago, recibo y
  periodo de membresía.
- Configuración y Automatizaciones comparten las superficies claras de Catálogo,
  el encabezado oscuro y el espaciado del shell; los mensajes se muestran como
  notificaciones flotantes accesibles, visibles sin importar el scroll.
- El menú prioriza logo, gimnasio y sucursal; después muestra usuario y salida.
  GymFort conserva atribución permanente y discreta en el pie del lateral.

## 4. Validación del corte

Sobre la rama vigente se verificó:

- migración V78 sobre PostgreSQL limpio y suites críticas de autorización HTTP
  y servicio de autoservicio exitosas;
- 80 pruebas unitarias de frontend exitosas;
- lint, typecheck y build de producción exitosos;
- revisión visual local de Configuración, marca tenant y atribución GymFort;
- en el corte anterior, 8 recorridos Playwright de ruteo por consola,
  `docker compose config` y ejecución real de `stripe/stripe-cli:v1.50.3`.

La suite `stripeTest` permanece opt-in porque requiere `sk_test_...` y, para la
topología completa, una cuenta Connect test. Las pruebas manuales realizadas en
modo Stripe test confirmaron Checkout, webhook y domiciliación; esa evidencia
no sustituye automatizar el contrato sandbox en CI.

## 5. Próximo bloque

1. Integrar Zoho como proveedor SMTP real sin acoplar el dominio de
   notificaciones a un proveedor.
2. Verificar SPF, DKIM, DMARC, remitente, reply-to, rebotes y entregabilidad.
3. Ejecutar Stripe sandbox automatizado y escenarios de recuperación conectados.
4. Cerrar deuda de autorización multi-tenant, migración limpia/upgrade,
   backup/restore y observabilidad de piloto.

El detalle vive en `modulos/`, la deuda única en el documento 28 y la situación
Git en el documento 29.
