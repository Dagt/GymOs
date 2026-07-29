# GymOS Multi-Tenant Verification Plan

Estado: VIGENTE / EJECUCIÓN PARCIAL
Última revisión documental: 2026-07-26

La deuda y criterios pendientes derivados de este plan se registran en
`../../Documentación/28-registro-vivo-deuda-tecnica.md` (`GEN-001` y deudas por
módulo).

GymOS debe aislar cada operación por `organization_id` y, cuando el flujo sea físico o localizable, por `branch_id`. Este documento define la estrategia mínima de pruebas y revisión para declarar el aislamiento apto para piloto.

## Principios

1. Todo recurso operativo pertenece a una organización.
2. Todo recurso de operación física pertenece además a una sucursal.
3. El frontend puede ocultar opciones, pero la seguridad real vive en backend.
4. Las respuestas ante UUID ajenos no deben permitir enumerar recursos.
5. Archivos en MinIO deben tener prefijo por organización y recurso propietario.
6. Redis debe usar claves con contexto suficiente: organización, sucursal, terminal o sesión según aplique.

## Recursos críticos

| Recurso | Organización requerida | Sucursal requerida | Riesgo principal |
| --- | --- | --- | --- |
| Usuario interno | Sí | Sucursales permitidas | acceso cruzado por rol global |
| Cliente/persona | Sí | No siempre | lectura o edición cruzada |
| Venta | Sí | Sí | pago/reembolso de venta ajena |
| Pago | Sí | Sí | validación o recibo ajeno |
| Membresía | Sí | Sí | activación/renovación cruzada |
| Kiosco | Sí | Sí | terminal pública de otro gimnasio |
| Acceso | Sí | Sí | apertura/registro en sucursal equivocada |
| Visit pass | Sí | Sí | consumo de saldo ajeno |
| Inventario | Sí | Sí | stock compartido por error |
| Reserva | Sí | Sí | doble reserva o pago cruzado |
| Coach/rutina | Sí | Sí | asignación a cliente ajeno |
| Wellness | Sí | Sí | agenda cruzada |
| Automatización | Sí | No siempre | ejecución sobre tenant incorrecto |
| Notificación | Sí | No siempre | envío a destinatario ajeno |
| Reporte | Sí | Opcional | agregados con datos de otro tenant |
| Archivo/recibo | Sí | Sí | descarga por object key conocido |

## Casos negativos obligatorios

Cada caso debe crearse con dos organizaciones (`org_a`, `org_b`) y al menos una sucursal por organización.

| Código | Caso | Resultado esperado |
| --- | --- | --- |
| MT-AUTH-001 | Usuario de `org_a` intenta seleccionar sucursal de `org_b` | 403 o error semántico sin filtrar datos |
| MT-CLIENT-001 | Leer cliente de `org_b` con token de `org_a` | 404/403 |
| MT-CLIENT-002 | Editar cliente de `org_b` con token de `org_a` | 404/403 |
| MT-SALE-001 | Consultar venta de `org_b` | 404/403 |
| MT-SALE-002 | Agregar pago a venta de `org_b` | 404/403 |
| MT-SALE-003 | Validar pago de `org_b` | 404/403 |
| MT-SALE-004 | Solicitar reembolso de venta de `org_b` | 404/403 |
| MT-MEM-001 | Renovar membresía de `org_b` | 404/403 |
| MT-KIOSK-001 | Usar terminal de `org_b` desde contexto de `org_a` | respuesta genérica |
| MT-ACCESS-001 | Consumir visita de `org_b` | 404/403 |
| MT-INVENTORY-001 | Crear movimiento sobre stock de `org_b` | 404/403 |
| MT-BOOKING-001 | Vincular pago de `org_a` a reserva de `org_b` | 404/403 |
| MT-AUTO-001 | Ejecutar automatización de `org_b` | 404/403 |
| MT-REPORT-001 | Reporte de `org_a` incluye datos de `org_b` | test falla si aparece agregado cruzado |
| MT-FILE-001 | Descargar o asociar recibo con key de `org_b` | 404/403 |

## Revisión de MinIO

Estado observado:

- Los recibos se guardan con prefijo `receipts/{organizationId}/{saleId}/{paymentId}/...`.

Pendiente:

- Prueba negativa de acceso/uso de `receiptObjectKey` ajeno.
- Validación de tamaño máximo y MIME.
- Normalización de nombre de archivo ya existe parcialmente; revisar extensiones permitidas.

## Revisión de Redis

Pendiente:

- Auditar claves de rate limiting.
- Confirmar contexto de organización/terminal/sesión en kiosco.
- Confirmar locks de automatizaciones con organización o regla.

## Estrategia de pruebas

1. Crear fixtures Testcontainers PostgreSQL con dos organizaciones.
2. Crear usuarios con roles equivalentes en cada organización.
3. Generar tokens reales o usar MockMvc con principal equivalente.
4. Ejecutar casos negativos por endpoint.
5. Verificar que no se escriben efectos secundarios en `audit_log`, `payment`, `membership`, `inventory_movement`, `booking_payment` para recursos ajenos.
6. Agregar los casos a CI.

## Criterio para `PILOT_READY`

El sistema requiere al menos:

- Casos negativos de `auth`, `clients`, `sales/payments`, `memberships`, `selfservice`, `access`, `inventory`, `bookings`, `reporting`.
- Evidencia de que object keys de MinIO no pueden reutilizarse entre tenants.
- Evidencia de que Redis no colisiona entre tenants para rate limits, sesiones y locks.
