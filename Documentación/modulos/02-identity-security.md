# Identidad y seguridad

Estado: `FUNCIONAL`
Última revisión: 23 de agosto de 2026

## Implementado

- login, refresh, selección de sucursal y contexto de sesión;
- roles y permisos aplicados en backend;
- V78 limita estrictamente el lanzamiento de kiosco a Recepción y V77 separa
  administración de cuenta Stripe de su configuración operativa;
- consolas y navegación condicionadas por rol;
- rotación y gracia de refresh, suspensión y soporte de plataforma;
- respuestas diferenciadas: `401 AUTHENTICATION_REQUIRED` sólo para ausencia o
  invalidez de autenticación y `403 PERMISSION_DENIED` para un JWT válido sin
  la autoridad requerida;
- V84 separa `access.physical.manage` de lectura/operación normal y lo concede a
  Dueño y Gerente, no a Recepción; `access.manual.open` continúa como permiso
  sensible independiente y exige punto/motivo explícitos; soporte de plataforma
  conserva ambas capacidades sólo dentro de su contexto auditado;
- el GymFort Local Connector no suplanta a un empleado ni usa JWT: activa un
  secreto de un solo despliegue y autentica en rutas propias mediante
  `X-GymFort-Connector-Key` + `X-GymFort-Connector-Secret`;
- códigos de activación y secretos del Connector se almacenan como hash BCrypt,
  pueden rotarse/revocarse y una credencial inválida produce
  `401 CONNECTOR_AUTH_INVALID` auditado;
- las claves compuestas y consultas del control físico limitan conexión, jobs,
  dispositivos, personas y eventos a organización y sucursal;
- GymFort Cloud rechaza metadatos físicos con biometría, imágenes, plantillas,
  contraseñas, tokens o secretos;
- pruebas de autorización representativas.

## Pendiente

- almacenamiento seguro del refresh token (`AUTH-001`);
- autorización uniforme por propiedad/tenant/sucursal (`GEN-001`);
- ampliar pruebas negativas por módulo y endurecer perfiles de documentación.

Referencia vigente: `../../docs/security/permissions-matrix.md`.
