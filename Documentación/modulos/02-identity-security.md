# Identidad y seguridad

Estado: `ESTABILIZADO PARA PRODUCCIÓN PILOTO`
Última revisión: 24 de agosto de 2026

## Implementado

- identidades explícitas `PLATFORM` y `TENANT`: una cuenta de plataforma no
  pertenece a organización/sucursal y una cuenta tenant exige organización;
- V86 aplica estas invariantes también en PostgreSQL para `person`,
  `user_account`, roles, asignaciones de rol y accesos persistentes a sucursal;
- login global por `user_account.username`, JWT con `scope` obligatorio y
  refresh v2 que vuelve a validar estado, ámbito y contexto antes de renovarse;
- login, refresh, selección de sucursal y contexto de sesión;
- roles y permisos aplicados en backend;
- V78 limita estrictamente el lanzamiento de kiosco a Recepción y V77 separa
  administración de cuenta Stripe de su configuración operativa;
- consolas y navegación condicionadas por rol;
- rotación y gracia de refresh, suspensión y soporte de plataforma explícito;
  entrar a un tenant exige motivo, genera auditoría, tiene expiración
  configurable y salir revoca el refresh de soporte;
- bootstrap opcional e idempotente del primer administrador `PLATFORM` mediante
  variables de entorno y lock transaccional PostgreSQL; exige el permiso
  efectivo `platform.admin`, no confunde soporte con administración, no crea
  organización ficticia, no restablece una cuenta existente y nunca registra
  la contraseña;
- alta transaccional de gimnasio + primera sucursal + dueño `TENANT` invitado;
  V87 almacena únicamente el hash SHA-256 del token de invitación y la
  contraseña sólo se persiste codificada al aceptar el enlace de un uso;
- el seed demo está limitado al perfil `local`, deshabilitado por defecto y
  requiere correo/contraseña explícitos; V88 elimina únicamente el fixture
  histórico exacto cuando no tiene actividad y lo conserva como `DEMO` ante
  cualquier dependencia ambigua;
- respuestas diferenciadas: `401 AUTHENTICATION_REQUIRED` sólo para ausencia o
  invalidez de autenticación y `403 PERMISSION_DENIED` para un JWT válido sin
  la autoridad requerida;
- V84 separa `access.physical.manage` de lectura/operación normal y lo concede a
  Dueño y Gerente, no a Recepción; `access.manual.open` continúa como permiso
  sensible independiente y exige punto/motivo explícitos; una identidad de
  plataforma no recibe esas capacidades en su sesión SaaS y sólo puede ejercer
  permisos tenant dentro del contexto temporal de soporte;
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
- pruebas de autorización, JWT/refresh, bootstrap concurrente real,
  invitaciones, límites de módulo, migración limpia/upgrade y restricciones
  PostgreSQL.

## Pendiente

- almacenamiento seguro del refresh token (`AUTH-001`);
- autorización uniforme por propiedad/tenant/sucursal (`GEN-001`);
- ampliar pruebas negativas por módulo y endurecer perfiles de documentación.

Referencias vigentes: `../../docs/security/permissions-matrix.md` y
`../../docs/operations/platform-identity-bootstrap.md`.
