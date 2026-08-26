# Cierre operativo: listados, coaches, beneficios y acceso del equipo

Corte local: 25 de agosto de 2026. Rama `codex/fix-list-pagination`.
Este documento describe código y validación local; no confirma merge ni despliegue.

## Hallazgos y decisiones

1. Socios/conteos fallan en las capturas y el panel de alertas contradice al
   resumen. Un backend anterior no tiene todos los contratos `/search` ni el
   filtro `lowStockOnly`. Es una hipótesis compatible con el caso, no una causa
   confirmada sin sus logs. Se verifican las consultas actuales sobre PostgreSQL,
   se detectan versiones incompatibles y no se muestran fallos como cero registros.
2. Las asignaciones no protegían fechas ni solapes, ni tenían cierre por fin.
   Se adopta una sola rutina por periodo/socio/sucursal, con programación del
   siguiente periodo y reemplazo explícito que conserva historia. No se borran
   duplicados legacy ni se elige por el usuario cuál conservar.
3. El servidor generaba `assigned_on` con su fecha local/UTC y los reportes
   consultaban México; en la tarde/noche una asignación podía aparecer al día
   siguiente. La primera rutina se duplicaba en altas del mismo día y el resumen
   multiplicaba asignaciones por joins con rutinas. Se corrigen fecha, desempate
   cronológico y agregaciones, además de sustituir el eje de nombres por fechas.
4. Benefits tenía alta, pero no edición. Ahora un mismo Sauna puede vincularse a
   VIP y VIP diario conservando el ID, mapeos físicos e historial.
5. El equipo no tenía recuperación de contraseña. Se reutilizan SMTP/ZeptoMail,
   la política de contraseña y el limitador existentes; no se crea otro sistema
   de correo ni se reutiliza la invitación de alta como cambio de contraseña.

## Listados compactos

Socios, catálogo, POS, inventario, ejercicios, historial de asignaciones,
beneficios y equipo usan áreas desplazables de altura limitada con barras
ocultas. La paginación de listados que ya la tienen permanece visible fuera del
scroll; un desplazamiento no sustituye **Anterior/Siguiente**. Se conserva acceso
por teclado y touch, filtros de servidor y totales completos.

`/api/v1/system/info` anuncia `operational-pagination-v1`; se rechaza un backend
incompatible en vez de interpretar una respuesta parcial como completa. Una
consulta fallida muestra total no disponible y reintento. Parámetros UUID,
fechas o JSON mal formados reciben `400 VALIDATION_ERROR`, sin valores sensibles.

Guía completa: [paginación de listados](list-pagination.md).

## Asignaciones y métricas

- Inicio y fin son obligatorios; inicio no pasado y fin igual o posterior al
  inicio. Fechas de operación en `America/Mexico_City`, fin inclusivo.
- Socio activo y disponible en sucursal, coach activo, versión publicada.
- El lock de la fila del socio serializa altas/reemplazos; se rechaza cualquier
  periodo solapado en la misma sucursal, no sólo una rutina idéntica.
- Se puede programar la siguiente desde el día posterior al fin vigente.
  Reemplazar la actual empieza hoy, conserva el vínculo anterior y exige un fin.
- Estado programada antes del inicio, vigente durante el periodo, completada
  desde el día posterior al fin. No se reabren estados finales. Vencimiento
  significa **periodo concluido**, no cumplimiento de ejercicios por el socio.
- Un worker persiste cierres vencidos con auditoría, lotes y `SKIP LOCKED`.
  Las lecturas aplican el estado efectivo aunque haya retraso del worker.
- Los duplicados previos requieren cancelar los sobrantes con motivo. Una
  asignación legacy sin fin no se completa arbitrariamente: cancelar/reemplazar.
- `GET /workout-assignments/history` consulta todo el historial paginado,
  independiente del periodo de indicadores, respetando tenant/sucursal/propietario.
- Métricas usan la fecha de creación de la asignación, no la de inicio
  programado. Primeriza: primera asignación no cancelada del socio en la
  organización; renovación: posteriores. Un reemplazo conserva la anterior.
- Serie semanal por lunes y rango exacto; días/semanas sin actividad valen cero
  sólo tras una consulta correcta. Los socios únicos semanales no son sumables.
- Indicadores permite hasta cinco líneas de coach con fecha diaria, vista
  acumulada desde el inicio del periodo o por día, colores/trazos y tabla semanal.
  Coaches activos sin actividad también pueden seleccionarse.

Configuración del backend (valores predeterminados):

```dotenv
GYMOS_COACHING_EXPIRATION_ENABLED=true
GYMOS_COACHING_EXPIRATION_DELAY_MS=60000
GYMOS_COACHING_EXPIRATION_BATCH_SIZE=250
```

## Edición de Sauna y otros beneficios

**Beneficios → Editar → marcar los planes → Guardar cambios.** Código, tipo,
sucursal e ID permanecen fijos. Se pueden editar nombre, vigencia, estado,
reglas económicas y planes según el tipo. ACCESS exige al menos un plan.

Los nuevos vínculos deben ser planes activos del tenant; se pueden conservar
vínculos a planes retirados para no quitar derechos históricos silenciosamente.
Retirar un plan tiene efecto sobre los derechos heredados de sus socios.
`expectedVersion` evita sobrescrituras; ante conflicto, el formulario permanece
abierto. Cerrar y reabrir consulta la versión y los planes actuales.

La edición exige `benefit.manage`, tenant/sucursal correctos y publica el evento
existente de configuración ACCESS para todos los planes afectados, retirados
incluidos. No se duplica el beneficio ni se llama al hardware.

## Recuperar la contraseña de gerente, coach o recepción

1. El dueño entra a **Equipo y accesos** y elige **Recuperar contraseña** en la fila
   del empleado. Gerencia sólo puede recuperar subordinados de su sucursal.
2. Confirma la identidad y genera el enlace. Se intenta entrega por el SMTP
   existente (ZeptoMail); la pantalla diferencia `SENT`, `FAILED`,
   `NOT_CONFIGURED` y `NO_EMAIL`. `SENT` significa aceptación del proveedor, no
   garantía de entrega a bandeja. Puede copiarse el enlace para entrega privada.
3. El empleado abre el enlace y elige/confirma su contraseña fuerte. El token
   viaja en fragmento, se retira de la URL y sólo vive en memoria del navegador.
4. Se consume una sola vez y se invalidan JWT/refresh anteriores. Debe volver a
   iniciar sesión. Dos consumos concurrentes no pueden cambiarla dos veces.
5. Si venció o se perdió, volver a **Recuperar contraseña** genera otro y revoca el
   anterior, tras la espera configurada. No se recupera un enlace anterior.

El login tiene **Olvidé mi contraseña** con estas instrucciones. No se introduce
un endpoint público para enumerar usuarios por correo, ni recuperación de dueños,
cuentas de plataforma o del propio administrador por este flujo.

```dotenv
GYMOS_PASSWORD_RECOVERY_EXPIRY_MINUTES=60
GYMOS_PASSWORD_RECOVERY_COOLDOWN_SECONDS=60
GYMOS_PASSWORD_RECOVERY_MAX_ATTEMPTS_PER_MINUTE=10
```

La URL pública usa `GYMOS_FRONTEND_BASE_URL` de identidad; debe ser el frontend
accesible por el destinatario, no localhost en producción. Mantener las variables
SMTP existentes: [correo ZeptoMail](zeptomail-email.md).

Persistencia: hash SHA-256, estado/fechas y estado de entrega; nunca contraseña
plana ni token completo. La nueva contraseña usa el encoder BCrypt existente.
La recuperación valida también su límite de 72 bytes UTF-8 (acentos/emojis
ocupan más de un byte) antes de codificarla: una contraseña demasiado larga
produce un error de validación y no consume el enlace, en lugar de un `500`.
Envío después del commit, sin almacenar el enlace en la cola durable ni registrar
excepciones SMTP que puedan incluirlo. No hay reintento automático del mismo
token; se regenera de forma controlada. La respuesta sensible lleva `no-store`.

La aceptación pública usa el limitador existente, con clave derivada de la IP:
memoria **por proceso** en local/tests, Redis compartido del proyecto en producción.
No se añade infraestructura. El límite conserva el contrato existente
`400 RATE_LIMIT_EXCEEDED`; credenciales vencidas/usadas/revocadas/desconocidas
comparten `PASSWORD_RESET_INVALID`. La versión de credencial se consulta por PK
en cada JWT para revocación inmediata, sin caché que prolongue sesiones antiguas.
Cambiar rol o suspender revoca también enlaces pendientes y sesiones; JWT/refresh
legacy sin versión se interpretan como cero hasta la primera invalidación.

## Migración y despliegue coordinado

- **V91**: corrige sólo `assigned_on` que coincide con el defecto UTC de creación
  y añade índices parciales para vencimiento/solapes. No cambia inicio/fin ni
  elimina duplicados de asignaciones.
- **V92**: `credential_version` y `user_password_reset` con hash único, un enlace
  pendiente por usuario y restricciones de consumo/estado. No modifica passwords
  existentes ni requiere nuevas credenciales SMTP.
- Pruebas de esquema limpio y upgrade desde V90 verifican ambas migraciones.
  No se modificaron migraciones ya publicadas.
- Respaldar el entorno y desplegar backend/frontend del mismo corte; verificar
  Flyway y `system/info`. Revalidar login, un listado, Sauna, asignación y enlace
  de recuperación. Los tests usan bases efímeras, no la base del usuario.
- No mantener instancias antiguas durante la habilitación de recuperación: no
  conocen `credential_version` ni las reglas de solape. El rollback de aplicación
  no puede garantizar revocación de sesiones en un backend anterior.

## Validación

Comandos ejecutados desde sus directorios correspondientes:

```powershell
# backend; Docker disponible
.\gradlew.bat test postgresIntegrationTest --no-daemon --console=plain
# Revalidación del ajuste final de contraseña: suite unitaria completa + PostgreSQL afectado
.\gradlew.bat test postgresIntegrationTest --tests '*OperationalHardeningPostgresIntegrationTests' --tests '*PlatformAdministratorBootstrapPostgresIntegrationTests' --tests '*DevelopmentSeedPostgresIntegrationTests' --no-daemon --console=plain

# frontend
npm test
npm run typecheck
npm run lint
npm run test:e2e -- --workers=2 --reporter=line
npm run build
npm run cf:build
```

Cobertura añadida: `OperationalHardeningPostgresIntegrationTests`,
`PasswordRecoveryControllerTests`, `PasswordRecoveryEmailDeliveryAdapterTests`,
upgrade V90 en `DatabaseMigrationTests`, regresiones de JWT/refresh, permisos y
contratos; `business-date.test.ts`, `coach-trend-model.test.ts`, validación del
contrato paginado y recorridos Playwright de edición, recuperación, gráficas,
errores y scroll. Datos sintéticos, correo simulado y revisión visual en
escritorio/móvil; no se han enviado correos reales ni efectuado cobros en este corte.

Resultados locales finales:

| Validación | Resultado |
|---|---|
| Backend unitario/autorización completo | 446 pruebas, 0 fallos, 0 omitidas |
| PostgreSQL completo con Docker/Testcontainers | 85 pruebas, 0 fallos, 0 omitidas; 12 de migraciones |
| PostgreSQL repetido tras el ajuste final de contraseña | 20 pruebas, 0 fallos, 0 omitidas; incluye 14 de este cierre, bootstrap y seed |
| Frontend unitario | 111 pruebas, 0 fallos, 0 omitidas |
| Playwright completo | 68 recorridos, todos correctos |
| `typecheck` y `lint` | Correctos |
| Build Next.js y bundle OpenNext/Cloudflare | Correctos |
| `git diff --check` | Correcto |

Las 20 pruebas PostgreSQL finales son una reejecución del subconjunto afectado,
no 20 casos adicionales a los 85. La primera ejecución completa precedió al
último ajuste de longitud de contraseña; la suite unitaria completa y ese
subconjunto se repitieron después. Los pasos frontend no requieren ese ajuste
de backend. Se conservan explícitamente ambos cortes de validación.

Next.js y el bundle OpenNext/Cloudflare se generaron correctamente. OpenNext
advierte que Windows no tiene compatibilidad completa; no es una prueba de
ejecución del worker en Cloudflare ni un despliegue. La guía `visualize-data`
se aplicó al eje temporal, comparación de series, distinción entre cero/error y
QA de las gráficas en escritorio y móvil.

Se mantienen fuera de alcance el Local Connector, HikCentral/hardware, cobro
automático SaaS, biometría y refactorizaciones generales. La deuda previa de
almacenamiento de refresh y limpieza durable de medios no se declara resuelta.
