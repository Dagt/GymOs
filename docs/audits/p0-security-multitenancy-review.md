# Auditoría P0 de seguridad y aislamiento multi-tenant

## Estado

`IN_PROGRESS`

Esta auditoría cubre toda la plataforma GymOs. No se limita al kiosco ni al módulo de reservas.

## Objetivo

Verificar que autenticación, autorización, acceso a datos y operaciones críticas estén protegidos en backend y correctamente acotados por organización y sucursal.

## Regla de evidencia

Un frente sólo podrá marcarse como `VERIFIED` cuando exista evidencia en código y una prueba negativa ejecutable. La presencia de filtros visuales o comprobaciones en frontend no cuenta como autorización.

## Frentes P0

### 1. Autenticación y sesiones

- Emisión, firma, expiración y revocación de tokens.
- Renovación de sesión.
- Sesiones de kiosco y autoservicio.
- Protección frente a reutilización y fijación de sesión.
- Ausencia de credenciales o secretos en logs y respuestas.

Estado: `PARTIAL_CODE_REVIEW`

Evidencia revisada:

- `JwtAuthenticationFilter` sólo construye el contexto autenticado cuando `JwtService` valida el bearer token.
- El principal autenticado contiene `userId`, `organizationId`, `branchId`, permisos, roles y modo de soporte.
- `organizationId` y `branchId` usados por el backend provienen de claims firmados, no de headers de tenant enviados por el navegador.
- La firma HMAC se compara en tiempo constante y los tokens alterados se rechazan.
- La expiración del access token se valida al parsearlo.
- Se añadieron pruebas de caracterización para conservación de claims de tenant, alteración de firma y firma con un secreto diferente.

Pendiente:

- Revisar refresh tokens, rotación, revocación y cierre de sesión.
- Confirmar que el secreto por defecto de desarrollo nunca sea aceptado en perfiles de producción.
- Revisar expiración y revocación de sesiones de kiosco.

### 2. Autorización

- Permisos comprobados en controladores o servicios backend.
- Operaciones administrativas protegidas independientemente del menú frontend.
- Diferenciación entre administrador de plataforma, dueño, gerente, recepción y socio.
- Denegación por defecto para operaciones no declaradas.

Estado: `PARTIAL_CODE_REVIEW`

Evidencia revisada:

- `SecurityConfig` aplica permisos granulares por método y ruta para usuarios, organizaciones, sucursales, onboarding, clientes, catálogo, beneficios, membresías, ventas, pagos, accesos, inventario, recursos, reservas, coaching, profesionales, notificaciones, automatizaciones y reportes.
- Las rutas no declaradas terminan en `anyRequest().authenticated()`, por lo que la configuración no es una denegación completa por defecto: un endpoint nuevo puede quedar accesible a cualquier usuario autenticado si no se agrega explícitamente a la matriz.
- Los endpoints `/api/v1/self-service/**` son públicos a nivel de Spring Security y dependen completamente de la validación de sesión propia del módulo.
- La creación de recursos está protegida por `resource.manage` en backend; ocultar el formulario en frontend no es la única barrera.

Hallazgo P0-S01:

- Clasificación: `REQUIRES_TESTS_FIRST`.
- Riesgo: un endpoint interno nuevo olvidado en `SecurityConfig` queda sólo como `authenticated()`.
- Propuesta: añadir una prueba de matriz endpoint-permiso y evaluar una política `denyAll` para rutas internas no declaradas en un bloque separado, después de inventariar todos los endpoints.

### 3. Aislamiento por organización

Toda lectura y escritura de datos de negocio debe incluir el `organization_id` resuelto de una fuente autenticada o de una sesión pública validada.

Se revisarán especialmente:

- clientes;
- ventas y pagos;
- membresías;
- inventario;
- accesos y visitas;
- reservas;
- reportes;
- auditoría;
- archivos y branding;
- configuración.

Estado: `PENDING_QUERY_REVIEW`

Fuente de confianza confirmada para endpoints autenticados:

- El `organizationId` del principal se obtiene del access token firmado.
- No basta con esta fuente: cada repositorio y mutación debe usar ese valor en sus consultas. La revisión SQL sigue pendiente módulo por módulo.

### 4. Aislamiento por sucursal

Cuando el dominio sea específico de una sede, la operación debe acotarse también por `branch_id`. Debe definirse explícitamente qué datos son globales para la organización y cuáles pertenecen a una sucursal.

Estado: `PARTIAL_REVIEW`

Evidencia existente:

- La selección de sucursal verifica `userHasBranchAccess(userId, branchId)` antes de emitir un contexto con otra sucursal.
- Existe una prueba negativa para selección de una sucursal sin acceso.

Pendiente:

- Verificar que la consulta de acceso también compruebe pertenencia a la organización activa.
- Clasificar cada dominio como organizacional o específico de sucursal.
- Revisar queries que sólo filtran por organización cuando deberían incluir sucursal.

### 5. Kiosco y autoservicio

- La sesión pública debe estar asociada a una terminal, organización y sucursal válidas.
- La verificación de identidad debe limitarse al socio de esa sesión.
- Los endpoints posteriores deben usar el cliente verificado almacenado en sesión, no un `clientId` arbitrario del request.
- La disponibilidad de reservas no debe exponer nombres, notas ni identificadores de otros socios.
- Rate limiting y expiración deben aplicarse a verificaciones y mutaciones.

Estado: `PARTIAL_REVIEW`

Riesgo de configuración:

- Todo `/api/v1/self-service/**` está marcado `permitAll`; por ello, cada endpoint del controlador debe exigir y validar el token de sesión de autoservicio cuando corresponda.
- Esta revisión se hará endpoint por endpoint y deberá incluir pruebas de sesión ausente, inválida, expirada y asociada a otra terminal.

### 6. Pagos, membresías e idempotencia

- Una misma solicitud no debe cobrar, renovar o registrar dos veces.
- El importe y plan deben validarse en backend.
- Toda mutación debe registrar actor, organización, sucursal y correlation ID.
- No debe confiarse en precios o totales enviados por frontend.

Estado: `PENDING_CODE_REVIEW`

### 7. Acceso a archivos e integraciones

- URLs firmadas con expiración adecuada.
- Objetos separados por organización.
- Credenciales de proveedores únicamente por variables de entorno o almacén de secretos.
- Timeouts y manejo seguro de errores externos.

Estado: `PENDING_CODE_REVIEW`

## Pruebas negativas mínimas

1. Un usuario de la organización A no puede leer ni mutar datos de B.
2. Un usuario limitado a la sucursal A no puede operar sobre B cuando el recurso es de sucursal.
3. Un gerente o recepción no puede crear recursos aunque invoque el endpoint directamente.
4. Una sesión de kiosco no verificada no puede consultar resumen, membresías, pagos ni disponibilidad privada.
5. Una sesión verificada para un socio no puede operar sobre otro socio.
6. Un identificador de reserva, venta, pago o membresía de otra organización debe responder como no encontrado o prohibido sin filtrar existencia.
7. Repetir una solicitud idempotente no debe duplicar el efecto.
8. Tokens expirados, alterados o de otra terminal deben rechazarse.

Cobertura localizada hasta ahora:

- `AuthServiceTests.selectingBranchWithoutUserAccessIsDenied`.
- `JwtServiceTests.accessTokenPreservesAuthenticatedTenantAndAuthorities`.
- `JwtServiceTests.alteredAccessTokenIsRejected`.
- `JwtServiceTests.tokenSignedWithAnotherSecretIsRejected`.

## Método de revisión

Por cada módulo:

1. Identificar endpoints y casos de uso.
2. Localizar la fuente de `organizationId` y `branchId`.
3. Revisar autorización backend.
4. Revisar todas las consultas y mutaciones.
5. Añadir prueba negativa.
6. Registrar hallazgo, severidad y corrección propuesta.
7. No refactorizar la capa crítica hasta tener cobertura suficiente.

## Próximo recorrido

1. Refresh tokens, revocación y selección de organización/sucursal.
2. Organizations y resolución de tenant.
3. Sales, payments y memberships.
4. Self-service y kiosco.
5. Clients, inventory, access y bookings.
6. Reporting, audit, archivos e integraciones.
