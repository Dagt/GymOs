# Bootstrap e identidades de plataforma

Corte: 24 de agosto de 2026

## Modelo operativo

- `PLATFORM`: personal que administra GymFort como SaaS. Su `organization_id` y
  `branch_id` permanentes son nulos.
- `TENANT`: dueño y personal de un gimnasio cliente. Toda cuenta pertenece a una
  organización y su acceso a sucursales se valida dentro de ella.
- Soporte: una identidad `PLATFORM` puede abrir un contexto tenant sólo mediante
  una operación explícita, con motivo, vencimiento y auditoría. No se guarda
  acceso persistente a la sucursal.

V86 hace cumplir estas reglas en PostgreSQL y V87 agrega invitaciones de dueño
de un solo uso. Las migraciones no eliminan datos demo automáticamente.

## Preflight antes de desplegar V86/V87

1. Crear y verificar un backup restaurable de PostgreSQL.
2. Confirmar que producción no usa el perfil `local` y que
   `GYMOS_SEED_ENABLED=false`.
3. Ejecutar consultas de sólo lectura para detectar ambigüedades:

```sql
SELECT lower(username), count(*)
FROM user_account
WHERE deleted_at IS NULL
GROUP BY lower(username)
HAVING count(*) > 1;

WITH classified_role AS (
  SELECT r.id, r.name,
         EXISTS (
           SELECT 1 FROM role_permission rp
           JOIN permission p ON p.id = rp.permission_id
           WHERE rp.role_id = r.id AND p.code = 'platform.admin'
         ) AS is_platform
  FROM role r
)
SELECT ua.id, ua.username, array_agg(DISTINCT cr.name)
FROM user_account ua
JOIN user_role ur ON ur.user_id = ua.id
JOIN classified_role cr ON cr.id = ur.role_id
GROUP BY ua.id, ua.username
HAVING bool_or(cr.is_platform) AND bool_or(NOT cr.is_platform);
```

La segunda consulta sólo identifica candidatos; revisa cada rol antes de
modificarlo. V86 se detiene en lugar de adivinar si encuentra un usuario mixto,
un `person` compartido entre plataforma/tenant o usernames globales duplicados.

## Crear el primer administrador en Railway

Configura temporalmente estas variables únicamente en el servicio backend:

```text
GYMOS_PLATFORM_BOOTSTRAP_ENABLED=true
GYMOS_PLATFORM_ADMIN_EMAIL=administrador@tu-dominio.mx
GYMOS_PLATFORM_ADMIN_NAME=Nombre Apellido
GYMOS_PLATFORM_ADMIN_PASSWORD=<secreto fuerte de al menos 12 caracteres>
```

La contraseña debe incluir mayúscula, minúscula, número y símbolo, sin espacios.
El arranque crea una persona/cuenta `PLATFORM` sin organización, codifica la
contraseña con el `PasswordEncoder` de la aplicación y registra sólo el evento
de bootstrap. Si ya existe cualquier administrador de plataforma, no crea ni
restablece nada, incluso si cambian las variables.

Después del primer despliegue:

1. iniciar sesión y confirmar que abre `/platform` sin organización/sucursal;
2. cambiar `GYMOS_PLATFORM_BOOTSTRAP_ENABLED=false`;
3. eliminar `GYMOS_PLATFORM_ADMIN_PASSWORD` y, de preferencia, las otras
   variables temporales del entorno;
4. volver a desplegar y verificar que la cuenta continúa funcionando.

No copies el secreto a tickets, comandos versionados, logs ni capturas.

## Alta del primer gimnasio cliente

Desde **Plataforma → Gimnasios** captura en una sola operación:

1. datos legales, contacto y marca del gimnasio;
2. primera sucursal;
3. nombre, correo y teléfono del dueño.

La transacción crea organización, sucursal, cuenta `TENANT`, rol de dueño,
acceso a la sucursal e invitación. Si falla cualquiera de esos pasos, no debe
quedar un tenant huérfano. La consola muestra una vez el enlace; la base sólo
conserva SHA-256 del token. Compártelo por un canal seguro. Al aceptarlo, el
dueño define su propia contraseña y el token queda consumido.

## Datos demo históricos

V86 marca `DEMO` únicamente a la organización con ID y `tax_id` exactos del
fixture histórico. Cartera y cobranza de plataforma consultan sólo
`data_origin='PRODUCTIVE'`. Antes de considerar una eliminación manual, revisar:

```sql
SELECT o.id, o.trade_name, o.data_origin,
       (SELECT count(*) FROM client c WHERE c.organization_id = o.id) AS clients,
       (SELECT count(*) FROM sale s WHERE s.organization_id = o.id) AS sales
FROM organization o
WHERE o.data_origin = 'DEMO';
```

No borres el fixture si contiene actividad que deba conservarse. Clasificar y
excluir evita dependencia productiva sin convertir el despliegue en una purga
destructiva.

## Rollback y recuperación

V86 permite `organization_id` nulo para plataforma y los tokens anteriores no
incluyen `scope`; por ello un binario anterior no es compatible con el nuevo
estado. Ante un incidente:

1. no edites ni deshagas V86/V87 a mano;
2. conserva el snapshot previo;
3. prefiere roll forward con una migración compensatoria y una imagen nueva;
4. sólo restaura el snapshot con aprobación explícita y después de detener
   escrituras, asumiendo la pérdida de operaciones posteriores al backup;
5. fuerza nuevo login: los access/refresh anteriores al formato v2 se rechazan.

## Smoke de aceptación

- administrador `PLATFORM` inicia sin tenant y llega a `/platform`;
- usuario `TENANT` llega a su consola y recibe 403 en `/api/v1/platform/**`;
- soporte exige motivo, muestra vencimiento y vuelve al panel al finalizar;
- suspender un tenant impide login y renovación de refresh de sus usuarios;
- el alta de cliente devuelve una invitación de un uso y el dueño sólo ve su
  organización/sucursales;
- `GYMOS_SEED_ENABLED=false` y bootstrap permanece desactivado.
