# Secret Rotation Runbook

## Secrets

- JWT signing secret.
- PostgreSQL password.
- Redis credentials when enabled.
- MinIO access and secret keys.
- SMTP credentials.
- External integration keys.
- Stripe platform and webhook secrets. Follow `stripe-automation-recovery.md`
  for overlap, validation and revocation order.

## Rotation Procedure

1. Generate the new secret outside the repository.
2. Update the environment secret store.
3. Restart affected services.
4. Validate login, refresh token behavior and object storage access.
5. Revoke the old secret.

Para SMTP, después de rotar `GYMOS_SMTP_PASSWORD` se debe reiniciar el backend
y enviar un correo de prueba desde Configuración del gimnasio. La contraseña
nunca se captura ni persiste en la configuración tenant. Véase
`email-delivery.md`.

`GYMOS_NOTIFICATION_PREFERENCE_KEY` no debe rotarse sin una migración que
recalcule las preferencias existentes; cambiarla directamente haría que las
bajas previas dejaran de coincidir con el HMAC del destinatario.

## Notes

Rotating the JWT secret invalidates existing access and refresh tokens.
