# Identidad y seguridad

Estado: `FUNCIONAL`

## Implementado

- login, refresh, selección de sucursal y contexto de sesión;
- roles y permisos aplicados en backend;
- consolas y navegación condicionadas por rol;
- rotación y gracia de refresh, suspensión y soporte de plataforma;
- respuestas diferenciadas: `401 AUTHENTICATION_REQUIRED` sólo para ausencia o
  invalidez de autenticación y `403 PERMISSION_DENIED` para un JWT válido sin
  la autoridad requerida;
- pruebas de autorización representativas.

## Pendiente

- almacenamiento seguro del refresh token (`AUTH-001`);
- autorización uniforme por propiedad/tenant/sucursal (`GEN-001`);
- ampliar pruebas negativas por módulo y endurecer perfiles de documentación.

Referencia vigente: `../../docs/security/permissions-matrix.md`.
