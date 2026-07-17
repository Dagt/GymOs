# GymOS Hito 7 Production Readiness Audit

Fecha de auditoría: 2026-07-16  
Rama auditada: `main` en `70f5074`, fast-forward local de `feature/hito-7-production-readiness`  
Repositorio: `Dagt/GymOs`  
Resultado actual recomendado: `STABILIZED`, no `PILOT_READY`, no `PRODUCTION_READY`

## 1. Resumen ejecutivo

GymOS compila localmente en backend, frontend y Docker usando el stack definido para el proyecto. La rama `feature/hito-7-production-readiness` estaba atrasada frente a `main`; se actualizó localmente por fast-forward para evitar que el PR existente valide código obsoleto.

El sistema tiene una base funcional amplia, pero todavía no debe declararse `PILOT_READY` porque faltan pruebas automatizadas de regresión para flujos críticos, evidencia de aislamiento multi-tenant negativo, matriz completa de permisos, validación de backup/restore, staging desplegado y hardening de producción.

## 2. Evidencia de ejecución

### Backend

Comando:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version
.\gradlew.bat clean test --no-daemon
```

Resultado:

- Java usado: `25.0.3`.
- Gradle: `9.5.1`.
- Resultado: `BUILD SUCCESSFUL`.
- Duración observada: `2m 18s`.
- Nota: durante shutdown aparece warning de H2 por tabla `event_publication` no existente después de cerrar contextos de prueba. No falla el build, pero debe limpiarse en un bloque posterior para evitar ruido y falsos positivos.

### Frontend

Comandos:

```powershell
npm.cmd run lint
npm.cmd run typecheck
npm.cmd run build
```

Resultado:

- `lint`: exitoso.
- `typecheck`: exitoso.
- `build`: exitoso.
- Next.js: `16.2.10`.
- Node local: `v24.13.1`.

### Docker

Comandos:

```powershell
docker compose -f infra/docker-compose.yml config
docker compose -f infra/docker-compose.yml build
```

Resultado:

- Compose config: válido.
- Build backend: exitoso.
- Build frontend: exitoso.

### Estado Git y PR

Evidencia:

- `gh` no está instalado localmente, por lo que no se pudo consultar PR #1 ni logs de Actions desde CLI.
- GitHub web/API no expuso información del repo/PR sin autenticación desde esta sesión.
- `feature/hito-7-production-readiness` era ancestro de `main`.
- Acción local realizada: fast-forward de `feature/hito-7-production-readiness` desde `4cad548` hasta `70f5074`.

## 3. Versiones y dependencias principales

| Área | Versión detectada |
| --- | --- |
| Java requerido | 25 |
| Java local en PATH | 1.8.0_361 |
| Java local instalado válido | 25.0.3 |
| Gradle wrapper | 9.5.1 |
| Spring Boot | 4.1.0 |
| Spring Modulith | 2.1.0 |
| Testcontainers | 1.21.3 |
| Node | 24.13.1 |
| npm | 11.8.0 |
| Next.js | 16.2.10 |
| React | 19.2.4 |
| PostgreSQL Docker | 18-alpine |
| Redis Docker | 8-alpine |
| MinIO Docker | latest |

## 4. Hallazgos priorizados

### CI-001

- Severidad: `BLOCKER`
- Módulo: CI/CD
- Descripción: la rama de trabajo del PR estaba atrasada respecto a `main`.
- Evidencia: `git merge-base --is-ancestor feature/hito-7-production-readiness main` confirmó que la feature branch era ancestro de `main`.
- Impacto: PR #1 puede validar código obsoleto y fallar aunque `main` local pase.
- Riesgo: confusión de estado, job Docker omitido si backend/frontend fallan en rama vieja.
- Solución recomendada: fast-forward de la rama de PR a `main`, push sin force y re-ejecutar CI.
- Pruebas necesarias: backend test, frontend lint/typecheck/build, Docker config/build.
- Dependencias: acceso a GitHub Actions o `gh` para confirmar jobs remotos.
- Criterio de aceptación: PR branch actualizada, CI remoto verde.

### CI-002

- Severidad: `HIGH`
- Módulo: entorno local / CI parity
- Descripción: `java` local apunta a Java 8 aunque JDK 25 está instalado.
- Evidencia: `java -version` devuelve `1.8.0_361`; `C:\Program Files\Java\jdk-25.0.3` existe.
- Impacto: falsos fallos locales al correr Gradle sin configurar `JAVA_HOME`.
- Riesgo: reproducibilidad deficiente.
- Solución recomendada: documentar setup local y usar `JAVA_HOME` explícito en scripts de validación.
- Pruebas necesarias: `.\gradlew.bat test` con Java 25.
- Criterio de aceptación: documentación y comandos reproducibles para Windows.

### TEST-001

- Severidad: `HIGH`
- Módulo: QA
- Descripción: cobertura automatizada insuficiente para flujos críticos.
- Evidencia: pruebas existentes principales: arquitectura, migraciones, autorización básica, rate limiter e inventario listener.
- Impacto: alto riesgo de regresión en ventas, pagos, membresías, kiosco, reservas, beneficios, visitas y reembolsos.
- Riesgo: no es defendible declarar `PILOT_READY`.
- Solución recomendada: crear suites por módulo con Testcontainers/PostgreSQL para flujos críticos.
- Pruebas necesarias: las descritas en el roadmap Hito 7.
- Criterio de aceptación: cobertura de flujos críticos con regresión automatizada y CI verde.

### ARCH-001

- Severidad: `HIGH`
- Módulo: backend modular
- Descripción: varias clases `application` dependen directamente de `JdbcClient`.
- Evidencia: `SalesService`, `ClientService`, `ClientImportService`, `CatalogService`, `OrganizationService`, `OnboardingService`.
- Impacto: rompe parcialmente la arquitectura hexagonal definida.
- Riesgo: acoplamiento, pruebas más difíciles, persistencia en capa incorrecta.
- Solución recomendada: mover persistencia a puertos/repositorios de infraestructura por módulo, en cambios pequeños.
- Pruebas necesarias: ArchUnit debe pasar de lista permisiva a prohibición gradual.
- Criterio de aceptación: application no usa `JdbcClient` salvo excepción documentada temporalmente.

### SEC-001

- Severidad: `HIGH`
- Módulo: seguridad / configuración
- Descripción: defaults de desarrollo expuestos en configuración base.
- Evidencia: `GYMOS_JWT_SECRET` default dev, credenciales DB/MinIO default, OpenAPI/Swagger permitidos, métricas expuestas.
- Impacto: inseguro si se despliega con defaults.
- Riesgo: secretos débiles, superficie de información pública.
- Solución recomendada: perfiles `local`, `test`, `staging`, `production` con validación de variables obligatorias y endpoints restringidos.
- Pruebas necesarias: tests de configuración por perfil y smoke de producción.
- Criterio de aceptación: production no arranca con secretos/defaults inseguros.

### SEC-002

- Severidad: `HIGH`
- Módulo: autorización
- Descripción: existe RBAC explícito en `SecurityConfig`, pero falta matriz formal endpoint-permiso-prueba.
- Evidencia: `SecurityAuthorizationTests` cubre casos negativos básicos, no matriz completa.
- Impacto: permisos pueden desviarse sin evidencia.
- Riesgo: acceso indebido por endpoint no revisado.
- Solución recomendada: crear `docs/security/permissions-matrix.md` y ampliar pruebas MVC.
- Criterio de aceptación: cada endpoint interno tiene permiso y prueba asociada.

### MT-001

- Severidad: `HIGH`
- Módulo: multi-tenancy
- Descripción: muchas consultas incluyen `organization_id` y `branch_id`, pero faltan pruebas negativas sistemáticas.
- Evidencia: no hay suite dedicada `multi-tenant-verification`.
- Impacto: no hay evidencia suficiente contra UUID conocidos de otro tenant.
- Riesgo: fuga de datos entre gimnasios.
- Solución recomendada: pruebas Testcontainers con dos organizaciones y recursos cruzados.
- Criterio de aceptación: intentos cruzados devuelven 404/403 semántico sin filtrar existencia.

### FRONT-001

- Severidad: `HIGH`
- Módulo: frontend
- Descripción: UX principal mejoró, pero pantallas secundarias aún tienen navegación/estado/accesibilidad incompletos.
- Evidencia: `GlobalSessionNav` y `AppShell` no cubren homogéneamente todos los módulos.
- Impacto: confusión operativa y flujos poco claros.
- Riesgo: baja adopción en piloto.
- Solución recomendada: extender shell moderno y estados `loading/empty/error/success` por módulo.
- Criterio de aceptación: flujos críticos operables con navegación consistente y mensajes de negocio claros.

### OPS-001

- Severidad: `HIGH`
- Módulo: operaciones
- Descripción: scripts y documentación existen parcialmente, pero no hay evidencia de restore probado.
- Evidencia: `scripts/backup-postgres.ps1`, `scripts/restore-postgres.ps1`, docs operations presentes.
- Impacto: no se puede declarar piloto con confianza operativa.
- Riesgo: pérdida de datos sin recuperación validada.
- Solución recomendada: prueba de backup/restore en base vacía, checksum y reporte.
- Criterio de aceptación: restore probado y documentado.

### DOC-001

- Severidad: `MEDIUM`
- Módulo: documentación
- Descripción: riesgo de mojibake y divergencia entre documentación, roadmap y estado real.
- Evidencia: salidas de consola muestran mojibake; se requiere revisar archivos reales.
- Impacto: ambigüedad para operación y venta.
- Solución recomendada: revisión UTF-8 y actualización de estado por módulo.
- Criterio de aceptación: documentación distingue implementado, verificado localmente, CI, staging y pendiente externo.

## 5. Matriz inicial por módulo

| Módulo | Estado actual | Riesgo principal | Criterio para `PILOT_READY` |
| --- | --- | --- | --- |
| identity | `FUNCTIONAL` | sin administración completa de usuarios internos | CRUD seguro de usuarios, roles, sucursales y pruebas multi-tenant |
| security | `FUNCTIONAL` | hardening prod y matriz incompleta | permisos documentados/probados, perfiles seguros, tokens y CORS validados |
| organizations | `FUNCTIONAL` | application usa `JdbcClient` | puertos/repositorios, pruebas de sucursales ajenas |
| clients | `FUNCTIONAL` | importación y duplicados con cobertura baja | pruebas de alta, edición, búsqueda, import dry-run/real y aislamiento |
| catalog | `FUNCTIONAL` | persistencia en application | pruebas de permisos, duplicados SKU y tenant |
| sales | `FUNCTIONAL` | servicio grande, pagos/reembolsos críticos con cobertura baja | pruebas de pagos, idempotencia, reembolsos y concurrencia |
| payments | `FUNCTIONAL` | modelado dentro de sales | pruebas completas de estados y validación doble |
| memberships | `FUNCTIONAL` | expiración pendiente y cobertura incompleta | expiración idempotente, renovación/cambio/beneficios probados |
| selfservice | `FUNCTIONAL` | seguridad kiosco requiere más pruebas | rate limit, replay, sesión expirada/cerrada y tenant probado |
| access | `FUNCTIONAL` | visitas/concurrencia con cobertura limitada | consumo concurrente y saldo no negativo probado |
| inventory | `FUNCTIONAL` | integración venta-stock necesita más regresión | stock insuficiente, idempotencia y tenant/sucursal |
| bookings | `FUNCTIONAL` | doble reserva/concurrencia y pagos | pruebas de conflicto, pago, cancelación y reembolso manual |
| coaching | `FUNCTIONAL` | permisos y UI secundaria | pruebas de CRUD/asignación y aislamiento |
| wellness | `FUNCTIONAL` | módulo avanzado sin cobertura suficiente | agenda/servicios con permisos y tenant |
| notifications | `IN_PROGRESS` | proveedor interno y estados limitados | outbox/proveedor internal/logging, intentos y plantillas probadas |
| automations | `IN_PROGRESS` | scheduler locks/retry requieren evidencia | dos instancias simuladas, lock vencido, nextRunAt, backoff |
| reporting | `FUNCTIONAL` | reportes por tenant/sucursal sin suite amplia | reportes cruzados negativos y cierre diario |
| onboarding | `FUNCTIONAL` | setup piloto puede duplicar si no se prueba | idempotencia y permisos probados |
| benefits | `IN_PROGRESS` | reglas y evidencia incompletas | catálogo, asignación, vigencia, verificación y auditoría |
| visits | `IN_PROGRESS` | concurrencia y compra/asignación | saldo, vigencia, historial y acceso integrado |
| refunds | `IN_PROGRESS` | política de membresía/inventario por reembolso | parcial/total, límite, auditoría, política explícita |
| audit | `FUNCTIONAL` | cobertura de operaciones sensibles incompleta | pruebas que verifiquen eventos/audit log por flujo crítico |
| configuration | `FUNCTIONAL` | tema visual sólo frontend/local en parte | persistencia por organización con permisos, API y auditoría |

## 6. Plan por bloques

### Bloque 1: Recuperación de CI

- Objetivo: PR branch alineada, validación local completa y CI remoto verde.
- Alcance: branch fast-forward, evidencia, documentación.
- Riesgos: no hay acceso local a Actions; requiere re-ejecución en GitHub.
- Rollback: revertir commit de auditoría; no se usará force push.

### Bloque 2: Calidad y regresión

- Objetivo: cubrir auth, sales/payments, memberships y kiosk.
- Alcance: tests MVC/Testcontainers.
- Riesgos: tiempo de suite; aislar datos por test.

### Bloque 3: Seguridad y multi-tenancy

- Objetivo: matriz de permisos y pruebas negativas de tenant.
- Alcance: `docs/security/*`, tests.
- Riesgos: descubrir endpoints con contratos ambiguos.

### Bloque 4: Arquitectura backend

- Objetivo: eliminar `JdbcClient` de application por prioridad.
- Alcance: primero catalog/organizations/clients, luego sales.
- Riesgos: refactor amplio; se hará por módulo.

### Bloque 5: Frontend

- Objetivo: shell y estados consistentes, persistencia de marca en backend.
- Alcance: UI secundaria, API tema visual, migración.
- Riesgos: cambios visibles; requiere smoke manual.

### Bloque 6: Cierre funcional

- Objetivo: usuarios/roles, expiración de membresías, políticas operativas y cierre diario.
- Alcance: backend, frontend, pruebas y docs.

### Bloque 7: Operación/staging

- Objetivo: perfiles seguros, backup/restore probado, observabilidad y checklist.
- Alcance: infra, scripts, docs, smoke.

## 7. Estado de readiness

| Criterio | Estado |
| --- | --- |
| CI local equivalente | Verificado localmente |
| CI remoto | Pendiente de validación externa |
| Docker build | Verificado localmente |
| Migraciones desde cero | Verificado parcialmente por Testcontainers |
| Multi-tenant negativo | Pendiente |
| Matriz permisos | Pendiente |
| Backup/restore probado | Pendiente |
| Staging desplegado | Acción externa pendiente |
| Manual operativo | Parcial |
| Checklist piloto | Pendiente |

## 8. Recomendación

GymOS debe mantenerse como `STABILIZED` hasta completar los bloques de pruebas, seguridad multi-tenant y operación. No debe declararse `PILOT_READY` ni `PRODUCTION_READY` con la evidencia actual.

