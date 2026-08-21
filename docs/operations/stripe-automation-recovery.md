# Operación y recuperación de Stripe/Automatizaciones

Corte: 21 de agosto de 2026

## Diagnóstico por tenant

Empieza en Configuración → Historial técnico. Revisa mandatos vencidos, cargos
en reintento/fallo, webhooks fallidos y operaciones no terminales por más de 15
minutos. Automatizaciones muestra eventos pendientes, reintentos, leases
vencidos y dead letters de la sucursal actual.

No copies payloads, tarjeta, correos ni secretos en logs, alertas o tickets.

## Recuperación después de una caída

1. Restaura PostgreSQL, red y Stripe antes de modificar registros.
2. Revisa `/actuator/health`, Prometheus y los historiales tenant.
3. Espera el vencimiento del lease de cinco minutos; los workers recuperan con
   la misma identidad idempotente.
4. Reintenta dead letters sólo tras corregir la causa y registra un motivo.
5. Verifica exactamente un pago completado y un efecto de dominio.
6. Nunca extiendas una membresía porque la página de retorno indique éxito.

## Pausa de automatizaciones

Dueño/soporte con `automation.scheduler.manage` puede pausar un tenant desde
Automatizaciones. Otros tenants continúan. Pausar no elimina, desbloquea ni
descarta trabajo; reanudar exige motivo auditado.

## Desconectar Stripe

1. Desactiva self checkout y recurrencia en Configuración.
2. Confirma que no haya operaciones no terminales ni cargos
   `PROCESSING`/`AWAITING_PROVIDER`.
3. Conserva historiales tenant para el incidente.
4. Revoca la cuenta/acceso en Stripe después de desactivar la aplicación.
5. No borres mandatos activos en SQL; usa revocación soportada o migración
   controlada.

Gerencia puede desactivar las modalidades, pero sólo dueño/soporte con
`payment.provider.stripe.manage` administra la cuenta conectada.

## Rotación de secretos

1. Valida una llave test restringida con Checkout, Setup, firma y cargo
   off-session.
2. Actualiza `GYMOS_STRIPE_SECRET_KEY` en el gestor y reinicia backend.
3. Rota por separado `GYMOS_STRIPE_WEBHOOK_SECRET`; despliega el nuevo secreto
   antes de revocar el endpoint anterior.
4. Verifica métricas y deduplicación de webhook y revoca la llave anterior.

## Proxy y navegador

`GYMOS_TRUSTED_PROXIES` contiene únicamente CIDR de balanceadores que conectan
directamente al backend. Vacío significa ignorar `Forwarded` y
`X-Forwarded-For`; nunca agregar redes públicas o de clientes.

El kiosco es una sesión de navegador abierta exclusivamente por Recepción y
configurada por sucursal. No existe ID de computadora/terminal que registrar.
