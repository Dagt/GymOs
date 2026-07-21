# Revisión de seguridad y aislamiento del módulo organizations

## Estado

`PARTIAL_REVIEW`

## Alcance

Esta revisión cubre los endpoints y casos de uso relacionados con organizaciones, sucursales, selección de tenant y operaciones de plataforma.

## Hallazgos verificados

### ORG-S01 — Creación de tenants restringida a plataforma

- Endpoint: `POST /api/v1/organizations`.
- Riesgo original: estaba protegido por `organization.update`, permiso que también puede existir dentro de un tenant.
- Corrección aplicada: el endpoint exige `platform.admin` en `SecurityConfig`.
- Estado: `FIXED_AT_HTTP_BOUNDARY`.
- Pendiente: añadir una defensa equivalente dentro de `OrganizationService.createOrganization` cuando pueda validarse localmente la suite completa.

### ORG-S02 — Selección de organización activa

- La selección de organización y la renovación de sesiones de soporte validan que el tenant exista y esté activo.
- No se emiten tokens para UUID arbitrarios, organizaciones eliminadas o suspendidas.
- Estado: `FIXED_AND_COVERED`.

### ORG-S03 — Actualización de organización actual

- `updateOrganization` usa exclusivamente `principal.organizationId()`.
- El `organizationId` no se recibe desde el cuerpo o path del cliente.
- Estado: `VERIFIED_IN_CODE`.

### ORG-S04 — Creación de sucursales

- La sucursal nueva se asocia a `principal.organizationId()`.
- El cliente no puede elegir otro `organizationId` en la solicitud.
- Estado: `VERIFIED_IN_CODE`.

### ORG-S05 — Actualización de sucursales

- La comprobación de acceso utiliza `userId`, `organizationId` autenticado y `branchId` solicitado.
- El `UPDATE` vuelve a filtrar por `branch.id` y `organization_id`.
- Se añadió una prueba negativa para una sucursal fuera del tenant autenticado.
- Estado: `COVERED_WITH_NEGATIVE_TEST`.

### ORG-S06 — Consulta arbitraria de sucursales

- `branchesForOrganization` sólo permite administradores de plataforma.
- Se añadió una prueba negativa para usuarios tenant.
- Estado: `COVERED_WITH_NEGATIVE_TEST`.

## Deuda estructural

### ORG-D01 — JdbcClient dentro de application

`OrganizationService` ejecuta directamente escrituras SQL. Esto mezcla:

- autorización;
- reglas de negocio;
- persistencia;
- auditoría;
- composición de resultados.

Propuesta posterior:

1. Crear `OrganizationCommandRepository`.
2. Mover inserciones y actualizaciones a infraestructura.
3. Hacer que cada operación de escritura devuelva filas afectadas o un resultado tipado.
4. Mantener en el servicio únicamente autorización, reglas, transacción y auditoría.

Estado: `ARCHITECTURAL_DEBT`.

### ORG-D02 — Auditoría sin comprobar filas afectadas

`updateOrganization` y `updateBranch` registran auditoría después del `UPDATE`, pero actualmente no conservan el número de filas modificadas. Aunque luego consultan el recurso y pueden fallar, es preferible comprobar inmediatamente que el resultado sea exactamente uno antes de auditar.

Estado: `REQUIRES_REPOSITORY_EXTRACTION`.

### ORG-D03 — Plataforma fuera de modo soporte en rutas current

Debe definirse explícitamente si un administrador de plataforma fuera de `supportMode` puede llamar endpoints `/organizations/current/**`. El contexto global usa una organización técnica, por lo que estas rutas podrían operar sobre ella si los permisos lo permiten.

Estado: `REQUIRES_PRODUCT_DECISION_AND_TESTS`.

### ORG-D04 — Estado de sucursal

La selección de sucursal comprueba acceso y pertenencia, pero la auditoría debe confirmar en todos los caminos que una sucursal suspendida o inactiva no pueda convertirse en el contexto operativo.

Estado: `PENDING_REVIEW`.

## Cobertura añadida

- `OrganizationTenantIsolationTests.tenantCannotUpdateBranchOutsideAuthenticatedOrganization`.
- `OrganizationTenantIsolationTests.tenantCannotListBranchesForArbitraryOrganization`.
- Pruebas de selección y renovación de organizaciones inactivas en el módulo de seguridad.

## Validación local requerida

```bash
cd backend
./gradlew test --no-daemon
```

No debe iniciarse la extracción de persistencia hasta que esta suite pueda ejecutarse localmente y los fallos preexistentes estén clasificados.

## Siguiente bloque

1. Revisar `sales`, `payments` y `memberships`.
2. Confirmar aislamiento por organización y sucursal en todas las consultas.
3. Revisar idempotencia de ventas, pagos y renovaciones.
4. Añadir pruebas negativas con identificadores de otro tenant.
5. Registrar qué importes se calculan en backend y cuáles llegan desde frontend.
