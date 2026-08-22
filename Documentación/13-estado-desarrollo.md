# 13. Estado actual de desarrollo

Estado: FUENTE CANÓNICA / EN PROGRESO
Corte: 22 de agosto de 2026
Código auditado: `feature/automations-zoho-email`, incluyendo V80 y cierre funcional de Automatizaciones/ZeptoMail.

## 1. Resumen ejecutivo

GymFort opera como monolito modular multi-tenant y multi-sucursal. El piloto incluye consolas SaaS y de gimnasio, recepción/POS, kiosco aislado, comercio y membresías, reservas, inventario, coaches, wellness, automatizaciones y correo transaccional durable.

La identidad comercial visible es GymFort. `GymOS`/`gymos` continúa en nombres técnicos por compatibilidad.

**Automatizaciones + correo transaccional mediante ZeptoMail quedan funcionalmente cerrados para el alcance V1**: reglas, eventos, prueba manual con socio real, permisos de Gerencia, cola durable, retries/dead letters, branding tenant y envío SMTP real están operativos. El 22 de agosto de 2026 se verificó `gymfort.mx` en ZeptoMail con DKIM y CNAME/return-path y se recibieron correos reales de prueba.

Estados usados: `FUNCIONAL`, `ESTABILIZADO`, `PARCIAL` y `PLANIFICADO`. Ningún módulo se considera completo por el nombre de una rama: la evidencia proviene de código, migraciones, pruebas y validación del entorno.

## 2. Estado por módulo

| Módulo | Estado | Implementado | Pendiente principal |
|---|---|---|---|
| [Organizaciones](modulos/01-organizations.md) | FUNCIONAL | Plataforma, tenants, sucursales, equipo, soporte y marca persistente | Matriz negativa transversal (`ORG-001`) |
| [Identidad y seguridad](modulos/02-identity-security.md) | FUNCIONAL | JWT, refresh revocable, selección de sucursal, RBAC y separación 401/403 | Endurecer almacenamiento/rotación de refresh (`AUTH-001`) |
| [Configuración](modulos/03-configuration.md) | FUNCIONAL | Marca tenant, cobros, kiosco y correo por permisos; UI unificada | Completar ciclo eliminación/reemplazo de logo (`CFG-001`) |
| [Onboarding](modulos/04-onboarding.md) | FUNCIONAL | Checklist, setup piloto idempotente e importación inicial | Recuperación probada de altas parciales (`ONB-001`) |
| [Socios](modulos/05-clients.md) | FUNCIONAL | Alta, búsqueda, detalle, importación y visibilidad multi-sucursal | PII, duplicados y pruebas CRUD (`CLI-001`) |
| [Catálogo](modulos/06-catalog.md) | ESTABILIZADO | Productos, planes, categorías, unidades y asociación por sucursal | Volumen, legacy y archivos (`CAT-001`) |
| [Ventas](modulos/07-sales.md) | FUNCIONAL | POS, ventas contextualizadas, pagos, reembolsos, recibos e inventario | Concurrencia/conciliación E2E (`SAL-001`) |
| [Membresías](modulos/08-memberships.md) | ESTABILIZADO | Periodos inclusivos, activa única, alta, renovación, cambio y cotización | E2E financiero completo |
| [Beneficios/elegibilidad](modulos/09-benefits.md) | ESTABILIZADO | Descuentos y acceso heredado mediante política transversal | Concurrencia y negativos de permisos |
| [Acceso](modulos/10-access.md) | FUNCIONAL | Visitas, decisión por membresía/beneficio y bitácora | Doble consumo/privacidad (`ACC-001`); hardware real pendiente |
| [Kiosco](modulos/11-selfservice.md) | FUNCIONAL / ACEPTACIÓN | Apertura sólo por Recepción, sesión aislada, membresías, pagos, domiciliación, reservas, wellness y datos | Publicar ejecución automatizada de Stripe sandbox (`SELF-001`) |
| [Reservas](modulos/12-bookings.md) | FUNCIONAL | Recursos, disponibilidad, pago y acceso multi-sucursal | Colisiones/cancelación/E2E (`BKG-001`) |
| [Inventario](modulos/13-inventory.md) | ESTABILIZADO | Existencias, movimientos, transferencias, conteos y valuación | Paginación y feedback (`INV-001`, `INV-002`) |
| [Coaches](modulos/14-coaching.md) | ESTABILIZADO | Perfiles, catálogo, rutinas versionadas, asignación, métricas, CSV/PDF | Cleanup durable de medios (`COA-018`) |
| [Wellness](modulos/15-wellness.md) | ESTABILIZADO | Profesionales, servicios, disponibilidad, agenda y citas | E2E y hardening multi-tenant |
| [Automatizaciones](modulos/16-automations.md) | FUNCIONAL / CERRADO V1 | Reglas, inbox durable, leases, idempotencia, reintentos, actividad/problemas, prueba con socio real y permisos de Gerencia | Mantener CI; trigger de domiciliación activada es mejora futura |
| [Notificaciones](modulos/17-notifications.md) | FUNCIONAL / ZEPTOMAIL OPERATIVO | Cola durable, ZeptoMail real, remitente central, Reply-To tenant, branding/logo, prueba, preferencias y recuperación | Bounce/complaint webhooks y reputación productiva |
| [Reportes](modulos/18-reporting.md) | PARCIAL | Reportes por dominio y métricas coaches/operación | Definiciones KPI y filtros homogéneos (`REP-001`) |
| [Auditoría](modulos/19-audit.md) | FUNCIONAL | Registro de mutaciones sensibles y motivos operativos | Matriz/retención/inmutabilidad (`AUD-001`) |
| [Operación común](modulos/20-common-operations.md) | FUNCIONAL PARA LOCAL | Errores correlacionados, Flyway, métricas, Docker, CI y runbooks | Restore, staging y simulacros (`DB-001`, `OPS-001`) |
| [Pagos](modulos/21-payments.md) | FUNCIONAL / ACEPTACIÓN | Terminal manual/MP excluyentes, Stripe Connect/Checkout, webhooks, recibos y recurrencia | Sandbox CI y reembolso/cancelación por proveedor (`PAY-001`) |

## 3. Decisiones operativas vigentes

- Venta y membresía conservan sucursal de origen; `multiBranchAccess=true` permite reconocimiento/acceso/reservas en otras sedes activas.
- Sólo Recepción recibe `selfservice.session.launch`.
- Gerencia conserva `payment.settings.manage` para modalidades ya vinculadas; cuenta Stripe exige `payment.provider.stripe.manage` o `platform.admin`.
- V80 concede a Gerencia `automation.read`, `automation.manage`, `automation.execute`, `notification.read` y `notification.manage`; no concede control avanzado del scheduler.
- Terminal manual y Mercado Pago Point son excluyentes.
- Comprar/renovar/cambiar plan en kiosco requiere Stripe self checkout; domiciliación requiere además recurring.
- Redirect de Stripe no confirma cobro: webhook firmado + consulta autoritativa producen efectos idempotentes.
- Automatizaciones no ejecuta lógica de dominio: consume eventos y genera notificaciones.
- `RECURRING_CHARGE_DUE` significa llegada de fecha de cobro recurrente; no activación inicial del mandato.
- La UI de Automatizaciones se divide en **Automatizaciones / Actividad / Problemas**; logs técnicos quedan fuera de la vista normal.
- Crear automatización se hace desde cero, sin plantillas recomendadas.
- `Probar ahora` para EMAIL usa búsqueda dinámica y un socio real con correo.
- Correo V1 usa preferentemente `Nombre del gimnasio <notificaciones@gymfort.mx>` + `Reply-To` propio del tenant.
- Branding del correo usa nombre, colores y logo de la organización; logos locales no pueden cargarse desde clientes externos.

## 4. Validación del corte

Evidencia disponible:

- DKIM y CNAME/return-path de `gymfort.mx` verificados en ZeptoMail;
- entrega real a bandeja mediante ZeptoMail desde ambiente local;
- `From` central y `Reply-To` tenant validados;
- nueva plantilla HTML con branding tenant;
- prueba de regla email con selección de socio real;
- V80 corrige permisos de Gerencia para gestionar Automatizaciones;
- V79 repara perfiles Coach históricos desincronizados;
- suites backend/frontend y CI de rama continúan siendo la red de regresión.

La carga real del logo en correo debe verificarse después del despliegue con `GYMOS_FRONTEND_BASE_URL=https://gymfort.mx`; localhost no es accesible desde Gmail/Outlook.

## 5. Próximo bloque

Automatizaciones/ZeptoMail ya no forman parte del bloque inmediato de implementación. Los siguientes frentes son:

1. ejecutar Stripe sandbox automatizado y escenarios de recuperación conectados;
2. cerrar autorización multi-tenant, migración limpia/upgrade, backup/restore y observabilidad de piloto;
3. iniciar el bloque de acceso físico/HikCentral definido en roadmap;
4. como mejoras posteriores de notificaciones: bounce/complaint webhooks, reputación productiva y canales adicionales.

El detalle vive en `modulos/`, la deuda única en el documento 28 y la situación Git en el documento 29.
