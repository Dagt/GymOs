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

Estado: `PENDING_CODE_REVIEW`

### 2. Autorización

- Permisos comprobados en controladores o servicios backend.
- Operaciones administrativas protegidas independientemente del menú frontend.
- Diferenciación entre administrador de plataforma, dueño, gerente, recepción y socio.
- Denegación por defecto para operaciones no declaradas.

Estado: `PENDING_CODE_REVIEW`

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

### 4. Aislamiento por sucursal

Cuando el dominio sea específico de una sede, la operación debe acotarse también por `branch_id`. Debe definirse explícitamente qué datos son globales para la organización y cuáles pertenecen a una sucursal.

Estado: `PENDING_DOMAIN_CLASSIFICATION`

### 5. Kiosco y autoservicio

- La sesión pública debe estar asociada a una terminal, organización y sucursal válidas.
- La verificación de identidad debe limitarse al socio de esa sesión.
- Los endpoints posteriores deben usar el cliente verificado almacenado en sesión, no un `clientId` arbitrario del request.
- La disponibilidad de reservas no debe exponer nombres, notas ni identificadores de otros socios.
- Rate limiting y expiración deben aplicarse a verificaciones y mutaciones.

Estado: `PARTIAL_REVIEW`

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

1. Identity y configuración de seguridad.
2. Organizations y resolución de tenant.
3. Sales, payments y memberships.
4. Self-service y kiosco.
5. Clients, inventory, access y bookings.
6. Reporting, audit, archivos e integraciones.
