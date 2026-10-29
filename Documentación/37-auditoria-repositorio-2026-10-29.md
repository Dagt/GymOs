# 37. Auditoría de repositorio y estado verificable (29/10/2026)

## 1. Alcance y evidencia

**Repositorio privado** [Dagt/GymOs](https://github.com/Dagt/GymOs), producto comercial GymFort. Inspección mediante API autenticada de GitHub de ramas, árbol recursivo, archivos representativos de backend/frontend, documentación, historial de commits/PRs y GitHub Actions. No se ejecutó una instalación local integral, pruebas de interfaz en navegador autenticado ni un ensayo contra PostgreSQL, Stripe real o torniquete. Se leyeron los documentos rectores y los contratos relevantes; este registro es **auditoría estática/documental y evidencia CI**, no certificación línea a línea ni pentest.

| Referencia | Fecha de commit | Estado al corte |
|---|---|---|
| `main` — `136ce72b4613b0a59c2b37d5734a6bb0b0ef3a92` | 29/08/2026 | Rama base estable; última migración V103 |
| `feature/multibranch-plans-benefits-visits` | 29/08/2026 | HEAD idéntico a main, aunque `13-estado-desarrollo` describía no-merge; corregir |
| `feature/local-access-connector` — `8955cdb7682376bd66fbc733610e84d24ebfe9c9` | 30/08/2026 | 4 commits ahead, 0 behind; **no fusionada ni PR abierto**; agrega V104, connector/ |
| PR históricos | julio/agosto 2026 | #1–20 cerrados, 19 fusionados; #4 cerrado sin merge |
| Inventario de `main` | 29/08/2026 | 1,234 archivos tipo blob, ~853 backend, 276 frontend, 44 Documentación, 20 docs/ y más |

## 2. CI: hallazgo de bloqueo

- [Workflow main #33277270627](https://github.com/Dagt/GymOs/actions/runs/33277270627): **failure**, backend y frontend fallidos, Docker skipped.
- [Workflow Connector #33317890679](https://github.com/Dagt/GymOs/actions/runs/33317890679): **failure**, backend, frontend y connector fallidos; paquete Windows y Docker skipped.
- Los logs remotos para esos jobs devolvieron error de almacenamiento 404 al consultarlos el 29/10/2026. No inferir causa (código, permisos, billing, runners u otro). Reproducir local/CI y capturar evidencia actual.
- Un estado separado `gymfort-backend-production` figura `success` para main: sólo prueba ese contexto, no sustituye el CI completo ni valida UI/pagos.
- `.github/workflows/ci.yml` en main **no ejecuta** `npm run test:e2e` ni `stripeTest`. Sí define Gradle unitarias/PG, npm unitarias/lint/typecheck/build/Cloudflare y Compose. #44, #59.

## 3. Arquitectura y funcionalidad identificadas

Backend Java 25, Spring Boot 4.1.0, Spring Modulith 2.1.0, JPA, PostgreSQL + Flyway V103, Redis, MinIO; frontend Next 16.3.2, React 19.2.4, TypeScript/Tailwind y OpenNext Cloudflare. Módulos: access, audit, automations, benefits, bookings, catalog, clients, coaching, common, configuration, eligibility, identity, inventory, memberships, notifications, onboarding, organizations, payments, platformadmin, platformbilling, reporting, sales, security, selfservice, visitpackages y wellness.

El código y documentación respaldan existencia de: plataforma/tenants, invitación, permisos, socios, catálogo con variantes, POS, pagos manuales/Stripe/Mercado Pago, membresías y paquetes multi-sucursal, acceso lógico/cloud, reservas, inventario, coaches, wellness, automatizaciones/notificaciones y dashboards. **Existencia no equivale a aceptación de producción**.

## 4. Hallazgos puntuales y acciones

| Severidad | Evidencia concreta | Riesgo / acción |
|---|---|---|
| P0 | `frontend/src/lib/api/auth.ts` guarda refresh token en `sessionStorage` y migra legacy desde `localStorage` | Exposición ante XSS, precisar amenaza/alternativa BFF-cookie-HttpOnly y protección CSRF; #28 |
| P0 | Matriz documental de permisos con cobertura parcial | IDOR/lectura cross-tenant o sucursal no descartados por pruebas sistemáticas; #29 |
| P0 | Conciliación/concurrencia financiera E2E no evidenciadas | Doble efecto o estado parcial; #33 y #34 |
| P0 | CI main y Connector reportan fallos sin logs actuales | No aprobar merge/release sin reejecutar; #44, #59 |
| P0 | Migraciones históricas y falta de prueba automática de restore | Riesgo de upgrades/datos; #42 y #43 |
| P0 condicional | Conector solo en rama; numerosos `PENDING-SITE` | No prometer torniquete físico operativo; #38–40 |
| P1 | `frontend/src/lib/api/auth.ts` `formatLoginError` incluye status/ruta/correlationId en error mostrable | Exposición de detalle técnico y UX confusa; #32 |
| P1 | `frontend/package.json` invoca `scripts/apply-coaches-ui.mjs` antes de dev/build/test | Mutación de fuente silenciosa, builds no reproducibles y diffs contaminados; #49 |
| P1 | `frontend/src/app/globals.css` ~175 KB y CSS adiciones | Riesgo de divergencia de componentes/UX; #47–50 |
| P1 | Branding existente por organización, no cada sucursal | Dos marcas mismo tenant no reflejan identidad local; #51 |
| P1 | `Documentación/13` atribuye no-merge a una rama cuyo HEAD coincide con main | Estado engañoso para Codex/negocio; #57 |
| P1 | `connector/README.md` vs `connector/docs/architecture.md` discrepan en eventos y apertura | Documentar por commit/estado real antes de integrar; #41 |
| P2 | `Documentación/modulos/README.md` omite el módulo 22 y `03-sds` duplica payments | Índice/arquitectura obsoletos; #57 y #58 |
| P2 | Correo SMTP `SENT` no certifica entrega final y falta bounce/complaint | Operación y reputación posterior; #56 |

**No hay prueba de exploit** de los riesgos estáticos: las incidencias representan controles no demostrados, decisiones pendientes o problemas de mantenibilidad con evidencia citada; Codex debe verificar antes de declararlas bugs reproducidos.

## 5. La rama Connector y su revisión

Comparación main...Connector muestra:
- `connector/` ejecutable Java, driver HikCentral, simulador, SQLite ledger, DPAPI, cliente cloud, instaladores Windows y pruebas.
- `backend/.../V104__physical_access_online_authorization.sql` más APIs de autorización en línea y polling de eventos.
- Cambios en `.github/workflows/ci.yml`, frontend de acceso y documentación.
- `connector/docs/hikcentral-site-acceptance-matrix.md` y `TEST-MATRIX.md` distinguen `PASS-AUTO` de `PENDING-SITE`; requisito hardware explícito.
- `connector/docs/windows-pilot-installation.md` indica paquete con hash SHA-256 pero no Authenticode, arranque de piloto bajo cuenta con sesión Windows.

Prioridad #38: inspección de integración y pruebas del mismo SHA, revisión de seguridad y split en PRs pequeños; no modificar en la auditoría documental V104 ni fusionar la rama.

## 6. Contradicciones documentales tratadas

Esta auditoría es el corte **más reciente**, pero mantiene intactas las evidencias históricas de agosto. El roadmap previo de `11` y la retroalimentación `32` reflejan trabajo en ese momento; V98–V103 ya están en main, con aceptaciones pendientes. `28` sigue como registro canónico de deuda y se enlaza a issues. El estado de `13` debe distinguir main auditado, rama no fusionada y validación CI pendiente. `README` debe distinguir capacidades implementadas de certificación real.

## 7. Límites y siguiente verificación

Leer con profundidad rutas concretas al entrar en cada issue, reproducir en entorno controlado, correr unitarias/PG/Playwright, dependencias/audit, pruebas manuales de roles y app real. Validar permisos configurados en ambientes y deployment; no se analizaron secretos ni datos reales. Conectar pruebas físicas únicamente bajo supervisión en instalaciones del cliente. No actualizar el resumen a «production-ready» hasta cumplir `35`/`40`/`42`.