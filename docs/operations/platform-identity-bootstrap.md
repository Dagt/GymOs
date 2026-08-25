# Bootstrap e identidades de plataforma

Corte: 24 de agosto de 2026

## Modelo y garantías

- `PLATFORM`: administra GymFort como SaaS; no tiene organización ni sucursal
  permanentes.
- `TENANT`: pertenece a un gimnasio y requiere organización.
- Soporte: una identidad `PLATFORM` sólo entra a un tenant mediante una sesión
  temporal, motivada, expirable y auditada.
- V86 protege estos ámbitos en PostgreSQL; V87 conserva únicamente el hash de
  las invitaciones; V88 retira el fixture histórico exacto sólo cuando no tiene
  actividad real.
- El bootstrap usa un `pg_advisory_xact_lock`: varias instancias pueden arrancar
  juntas, pero sólo una crea la cuenta inicial dentro de su transacción.

La detección de administrador exige cuenta y rol `PLATFORM`, ambos vigentes, y
el permiso efectivo `platform.admin`. Una cuenta activa impide otro bootstrap.
Una cuenta administrativa no eliminada pero deshabilitada, bloqueada o
incompleta detiene el bootstrap con `PLATFORM_BOOTSTRAP_ADMIN_INACTIVE`; debe
recuperarse operativamente, no reemplazarse en silencio. Una cuenta eliminada o
un rol de soporte sin `platform.admin` no bloquean una creación inicial.

## 1. Preflight antes del merge o despliegue

1. Generar un backup PostgreSQL y demostrar que puede restaurarse en un entorno
   aislado.
2. Confirmar en Railway que el backend tiene `GYMOS_SEED_ENABLED=false` y no usa
   el perfil `local`.
3. Confirmar que sólo existe una estrategia de despliegue productivo desde
   `main`; desactivar deploys paralelos desde ramas o imágenes antiguas.
4. Ejecutar las siguientes consultas de sólo lectura y resolver cualquier
   resultado ambiguo antes de desplegar.

Usernames activos duplicados:

```sql
SELECT lower(username) AS username, count(*)
FROM user_account
WHERE deleted_at IS NULL
GROUP BY lower(username)
HAVING count(*) > 1;
```

Usuarios con roles `PLATFORM` y `TENANT` mezclados:

```sql
SELECT ua.id, ua.username,
       array_agg(DISTINCT r.account_scope ORDER BY r.account_scope) AS scopes
FROM user_account ua
JOIN user_role ur ON ur.user_id = ua.id
JOIN role r ON r.id = ur.role_id
WHERE ua.deleted_at IS NULL AND r.deleted_at IS NULL
GROUP BY ua.id, ua.username
HAVING count(DISTINCT r.account_scope) > 1;
```

Personas compartidas entre plataforma y tenant, o reutilizadas como cliente:

```sql
SELECT p.id, p.email,
       array_agg(DISTINCT ua.account_scope ORDER BY ua.account_scope) AS account_scopes,
       count(DISTINCT c.id) AS client_uses
FROM person p
JOIN user_account ua ON ua.person_id = p.id AND ua.deleted_at IS NULL
LEFT JOIN client c ON c.person_id = p.id AND c.deleted_at IS NULL
GROUP BY p.id, p.email
HAVING count(DISTINCT ua.account_scope) > 1
    OR (bool_or(ua.account_scope = 'PLATFORM') AND count(DISTINCT c.id) > 0);
```

Estado del fixture demo y actividad principal:

```sql
WITH demo AS (
  SELECT '11111111-1111-1111-1111-111111111111'::uuid AS organization_id,
         '22222222-2222-2222-2222-222222222222'::uuid AS branch_id
)
SELECT 'organization' AS source, count(*) FROM organization o, demo d WHERE o.id = d.organization_id
UNION ALL SELECT 'branch', count(*) FROM branch b, demo d WHERE b.id = d.branch_id
UNION ALL SELECT 'person', count(*) FROM person x, demo d WHERE x.organization_id = d.organization_id
UNION ALL SELECT 'user_account', count(*) FROM user_account x, demo d WHERE x.organization_id = d.organization_id
UNION ALL SELECT 'client', count(*) FROM client x, demo d WHERE x.organization_id = d.organization_id
UNION ALL SELECT 'sale', count(*) FROM sale x, demo d WHERE x.organization_id = d.organization_id
UNION ALL SELECT 'payment', count(*) FROM payment x, demo d WHERE x.organization_id = d.organization_id
UNION ALL SELECT 'membership', count(*) FROM membership x, demo d WHERE x.organization_id = d.organization_id
UNION ALL SELECT 'inventory_movement', count(*) FROM inventory_movement x, demo d WHERE x.organization_id = d.organization_id
UNION ALL SELECT 'booking', count(*) FROM booking x, demo d WHERE x.organization_id = d.organization_id
UNION ALL SELECT 'access_log', count(*) FROM access_log x, demo d WHERE x.organization_id = d.organization_id
UNION ALL SELECT 'automation_execution', count(*) FROM automation_execution x, demo d WHERE x.organization_id = d.organization_id
UNION ALL SELECT 'notification_log', count(*) FROM notification_log x, demo d WHERE x.organization_id = d.organization_id
UNION ALL SELECT 'platform_billing_payment', count(*)
  FROM platform_billing_payment x JOIN branch b ON b.id = x.branch_id, demo d
  WHERE b.organization_id = d.organization_id
UNION ALL SELECT 'audit_log', count(*) FROM audit_log x, demo d WHERE x.organization_id = d.organization_id;
```

Para descubrir referencias agregadas por migraciones futuras:

```sql
SELECT conrelid::regclass AS referencing_table, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE contype = 'f'
  AND confrelid IN ('organization'::regclass, 'branch'::regclass)
ORDER BY 1;
```

V88 verifica dinámicamente todas esas claves foráneas. Si la identidad exacta
no coincide o aparece actividad no técnica, conserva el fixture completo,
mantiene `data_origin='DEMO'` y emite `V88 retained...` en el log Flyway. Tras el
despliegue, repetir la consulta anterior: que el fixture permanezca no es un
fallo de migración, sino una señal para revisión manual. Nunca usar `CASCADE`.

## 2. Variables temporales en Railway

Configurar únicamente en **Runtime Variables** del servicio backend:

```dotenv
GYMOS_PLATFORM_BOOTSTRAP_ENABLED=true
GYMOS_PLATFORM_ADMIN_EMAIL=<correo real>
GYMOS_PLATFORM_ADMIN_NAME=<nombre real>
GYMOS_PLATFORM_ADMIN_PASSWORD=<secreto fuerte temporal>
```

No son variables de build ni del frontend. No copiarlas a Cloudflare, imágenes,
Dockerfiles, tickets, comandos versionados, logs o capturas. La contraseña debe
tener al menos 12 caracteres, mayúscula, minúscula, número y símbolo.

## 3. Primer despliegue

1. Fusionar el PR sólo después de aprobación, CI verde, backup verificado y
   preflight sin ambigüedades.
2. Confirmar en los logs que Flyway aplicó las migraciones nuevas. Revisar el
   diagnóstico de V88.
3. Verificar `GET /actuator/health` y `GET /api/v1/system/info`.
4. Verificar en OpenAPI que existan `accountScope`,
   `POST /api/v1/platform/customers` y
   `POST /api/v1/auth/invitations/accept`.
5. Confirmar el evento `PLATFORM_ADMIN_BOOTSTRAPPED` sin contraseña, hash ni
   valores de variables temporales:

```sql
SELECT action, entity_type, entity_id, origin, reason, occurred_at
FROM audit_log
WHERE action = 'PLATFORM_ADMIN_BOOTSTRAPPED'
ORDER BY occurred_at DESC;
```

6. Iniciar sesión en una ventana privada y confirmar redirección a `/platform`.
7. Consultar `GET /api/v1/auth/me` y confirmar:
   `organizationId=null`, `branchId=null`, `accountScope=PLATFORM` y
   `supportMode=false`.
8. Crear un gimnasio de prueba controlado, o ejecutar un smoke no destructivo
   equivalente; comprobar organización, primera sucursal e invitación de dueño
   de un uso.
9. Confirmar que un usuario `TENANT` recibe `403` en `/api/v1/platform/**`.
10. Confirmar que una identidad `PLATFORM` sin soporte activo no puede ejecutar
    operaciones tenant y que una sesión de soporte vencida no renueva.

Las sesiones anteriores a V86 no tienen `scope` y son inválidas; todos los
usuarios deben iniciar sesión nuevamente.

## 4. Cierre inmediato del bootstrap

Después del primer acceso exitoso:

1. Cambiar `GYMOS_PLATFORM_BOOTSTRAP_ENABLED=false`.
2. Eliminar `GYMOS_PLATFORM_ADMIN_PASSWORD`.
3. Retirar correo y nombre si ya no se necesitan.
4. Desplegar nuevamente el backend.
5. Reiniciar una vez más y confirmar que no crea ni modifica cuentas.
6. Invalidar o cerrar sesiones de smoke/prueba.

No reutilizar el bootstrap para crear administradores adicionales; debe existir
un flujo administrativo explícito para ello.

## 5. Frontend Cloudflare

1. Desplegar desde `main` sólo después del merge aprobado.
2. Mantener en build:

```dotenv
NEXT_PUBLIC_API_BASE_URL=https://api.gymfort.mx/api/v1
GYMOS_INTERNAL_API_BASE_URL=https://api.gymfort.mx/api/v1
```

3. No copiar secretos del backend a variables de Cloudflare ni a variables
   `NEXT_PUBLIC_*`.
4. En ventana privada, comprobar que el login dirige a `/platform` para
   plataforma y a la consola tenant para gimnasio.
5. Verificar rutas del Worker y DNS para asegurar que el frontend no intercepta
   `api.gymfort.mx`.

## 6. Alta de gimnasios e invitaciones

**Plataforma → Gimnasios** crea en una transacción la organización, primera
sucursal, cuenta `TENANT`, rol, acceso e invitación. El enlace se entrega una
sola vez y PostgreSQL conserva únicamente SHA-256 del token. Si falla cualquier
paso, no debe quedar una identidad huérfana.

Una vez confirmado el commit, el backend intenta el correo del dueño mediante
el adapter SMTP/ZeptoMail. Este envío inicial es directo deliberadamente: poner
el enlace en la cola durable persistiría el token de un solo uso dentro del
cuerpo. V89 guarda sólo `email_delivery_status`, contador y fechas. Si SMTP no
está listo o rechaza el mensaje, el alta sigue siendo exitosa, la consola muestra
el enlace de respaldo y permite **Reenviar invitación**. Reenviar rota el token,
revoca el enlace anterior y aplica el cooldown
`GYMOS_USER_INVITATION_RESEND_COOLDOWN_SECONDS` (60 segundos por defecto).

La cartera del administrador se refresca con `GET /api/v1/platform/customers`;
no debe usar `GET /api/v1/organizations`, reservado a capacidades tenant. Para
smoke, confirmar en la ficha del cliente:

- cuenta `INVITED` e invitación pendiente/vencida;
- `Aceptado por ZeptoMail`, `Falló la entrega` o `ZeptoMail no configurado`;
- después de aceptar, cuenta activada y acción de reenvío oculta.

## 7. Rollback y recuperación

- No editar, borrar ni fabricar filas en `flyway_schema_history`.
- No deshacer V86, V87, V88, V89 ni migraciones posteriores manualmente.
- Preferir roll forward mediante una migración compensatoria y una imagen nueva.
- Para restaurar el backup, detener escrituras y aceptar explícitamente la
  pérdida de operaciones posteriores al punto restaurado.
- No desplegar un binario antiguo contra el esquema nuevo sin demostrar
  compatibilidad.
- Si se restaura, alinear backend y frontend a la misma versión antes de abrir
  tráfico.
- Considerar inválidas las sesiones anteriores y exigir nuevo inicio de sesión.

## 8. Smoke final

- administrador `PLATFORM` inicia sin tenant y llega a `/platform`;
- tenant recibe `403` en rutas de plataforma;
- soporte exige motivo, expira y permite volver al panel;
- tenant suspendido no inicia ni renueva sesión;
- invitación sólo se consume una vez y la base no contiene el token en claro;
- una invitación vencida puede rotarse y el enlace anterior queda inválido;
- `GYMOS_SEED_ENABLED=false` y bootstrap deshabilitado;
- reiniciar backend no crea ni restablece administradores;
- no existen secretos de bootstrap en logs, auditoría, respuestas o frontend.
