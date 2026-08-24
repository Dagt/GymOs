# Despliegue piloto

Corte: 24 de agosto de 2026

## Entornos

- `local`: seed demo permitido sólo con opt-in y credenciales explícitas.
- `test`: pruebas automatizadas, seed deshabilitado.
- `development`: validación compartida.
- `staging`: ensayo similar a producción.
- `production`: datos reales, seed deshabilitado.

## Configuración obligatoria

Inyectar por entorno/gestor de secretos:

- base: `SPRING_PROFILES_ACTIVE`, `GYMOS_SEED_ENABLED=false`, base de datos,
  Redis, MinIO, JWT, CORS y `NEXT_PUBLIC_API_BASE_URL`;
- Stripe: `GYMOS_STRIPE_SECRET_KEY`, `GYMOS_STRIPE_WEBHOOK_SECRET`, URLs de
  retorno/refresco y `GYMOS_PAYMENT_CREDENTIAL_KEY`;
- Mercado Pago: credenciales/callback y clave de cifrado según el entorno;
- correo: `GYMOS_SMTP_*` y `GYMOS_NOTIFICATION_PREFERENCE_KEY`;
- proxy: `GYMOS_TRUSTED_PROXIES` sólo para balanceadores directos.

El primer administrador SaaS se crea una sola vez con el procedimiento
`platform-identity-bootstrap.md`. No dejes `GYMOS_PLATFORM_BOOTSTRAP_ENABLED`
activo ni la contraseña de bootstrap en Railway después de verificar la cuenta.

Nunca usar credenciales demo fuera de `local`, secretos en imágenes/Git ni
llaves de backend en `NEXT_PUBLIC_*`.

## Pasos

1. Construir imágenes desde un commit etiquetado con CI verde.
2. Validar migración limpia y upgrade de una base de referencia.
3. Iniciar PostgreSQL, Redis, MinIO, backend y frontend.
4. Validar `/actuator/health` y `/api/v1/system/info`.
5. Configurar proxy HTTPS, CORS y orígenes de retorno.
6. Registrar webhooks Stripe/Mercado Pago y probar firmas/idempotencia.
7. Si se habilita correo, probar SMTP/remitente y entregabilidad.
8. Ejecutar smoke de login, sucursal, socio, venta, pago, membresía, reserva,
   kiosco y reporte.
9. Confirmar `GYMOS_SEED_ENABLED=false`, que el perfil `local` no está activo,
   bootstrap deshabilitado, backup programado, métricas y alertas.
10. Validar por separado login `PLATFORM`, login `TENANT`, entrada/salida de
    soporte y que un tenant no alcance `/api/v1/platform/**`.

El perfil Docker `stripe` es para desarrollo/test local; producción debe usar un
endpoint público HTTPS registrado en Stripe, no un listener CLI permanente.

## Roll forward

Preferir una nueva imagen etiquetada y una migración nueva. No editar
migraciones ya aplicadas. Usar `rollback.md` sólo cuando un roll forward seguro
no sea viable y nunca revertir el esquema de forma destructiva sin plan de datos.
