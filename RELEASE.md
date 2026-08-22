# Proceso de release

GymFort usa versionado semántico. El nombre técnico `GymOS` puede permanecer en
artefactos internos hasta completar una migración compatible.

- `MAJOR`: cambio incompatible de producto, datos o contrato.
- `MINOR`: funcionalidad nueva compatible.
- `PATCH`: corrección o mejora operacional segura.

## Checklist

1. Confirmar CI verde y `git diff --check`.
2. Ejecutar pruebas backend y migraciones PostgreSQL.
3. Ejecutar pruebas frontend, lint, typecheck, build y E2E críticos.
4. Validar y construir Docker Compose.
5. Si afecta pagos, verificar Stripe CLI fijado y recorrer sandbox con webhook.
6. Si afecta Automatizaciones:
   - crear/activar/desactivar una regla;
   - verificar permisos de Dueño y Gerente;
   - probar una regla email con un socio real;
   - revisar `Actividad` y `Problemas`;
   - confirmar retry/dead letter cuando aplique.
7. Si afecta correo/ZeptoMail:
   - verificar secretos `GYMOS_SMTP_*`;
   - confirmar `smtp.zeptomail.com:587` + STARTTLS;
   - validar `From` allowlisted y `Reply-To` tenant;
   - enviar prueba real;
   - revisar Processed emails y SPF/DKIM/DMARC;
   - confirmar `GYMOS_FRONTEND_BASE_URL` público para logo en producción.
8. Revisar migración desde cero y upgrade de una base de referencia.
9. Actualizar `CHANGELOG.md`, estado, módulos, deuda, matriz de permisos y runbooks.
10. Ejecutar smoke test, registrar evidencia y etiquetar el commit.

Las versiones piloto usan `v0.x.y-pilot.n`.
