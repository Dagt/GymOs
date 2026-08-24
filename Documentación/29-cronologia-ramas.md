# 29. Cronología y situación de ramas

Estado: AUDITADO

Corte: 24 de agosto de 2026

Rama de trabajo: `feature/platform-identity-separation`

Base integrada: `main` en `88981b4` (`Merge pull request #15`)

## 1. Situación vigente

El PR #15 ya integró en `main` la administración comercial, la separación
`PLATFORM`/`TENANT`, el bootstrap inicial y el aprovisionamiento transaccional de
gimnasios. La rama actual fue adelantada a ese merge y contiene exclusivamente
el cierre posterior: concurrencia PostgreSQL del bootstrap, detección por
permiso efectivo, retiro seguro del fixture histórico, Compose, CI y runbook.

Este cierre debe publicarse en un PR nuevo; el PR #15 fusionado es inmutable. No
debe fusionarse hasta que el nuevo CI y la revisión de seguridad estén verdes.

| Rama | Estado | Acción |
|---|---|---|
| `main` | Incluye PR #15 en `88981b4` | Recibir el hardening sólo por un PR nuevo y verde |
| `feature/platform-identity-separation` | Activa sobre el `main` fusionado | Publicar PR de cierre; revisar CI/seguridad; no fusionar automáticamente |
| `feature/platform-client-billing-management` | Integrada mediante PR #15 | Histórica; no fusionar otra vez |
| `feature/physical-access-control` | Integrada mediante PR #14 | Histórica; no fusionar otra vez |
| `feature/pricing-and-billing` | Integrada en `main` | Histórica; no fusionar otra vez |
| `feature/automations-zoho-email` | Integrada | Histórica; no fusionar otra vez |
| `fix/kiosk-memberships-stripe` | Integrada | Histórica; no fusionar otra vez |

## 2. Hitos recientes

| Fecha | Hito |
|---|---|
| 23 ago 2026 | `feature/pricing-and-billing` se integra en `main` en `a9ca485` |
| 23 ago 2026 | V83/V84 completan el control plane, Connector API, hardening y consola de acceso físico |
| 24 ago 2026 | PR #14 integra `feature/physical-access-control` en `main` (`f95abfa`) |
| 24 ago 2026 | `71810b3` agrega soporte de autenticación Redis y se incorpora a la rama activa |
| 24 ago 2026 | `8ba97a6` completa cartera, cobranza y gestión profesional de gimnasios clientes |
| 24 ago 2026 | V86/V87 separan cuentas de plataforma/tenant y agregan invitaciones de propietario de un uso |
| 24 ago 2026 | JWT/refresh, soporte temporal, bootstrap y rutas frontend adoptan `accountScope` explícito |
| 24 ago 2026 | PR #15 integra administración comercial e identidad en `main` (`88981b4`) |
| 24 ago 2026 | V88 y el hardening posterior coordinan bootstrap, retiran fixture vacío y amplían CI |

## 3. Alcance material de la rama activa

### Implementado

- administración comercial de gimnasios desde la navegación lateral de
  plataforma;
- identidades `PLATFORM` sin organización/sucursal e identidades `TENANT` con
  organización obligatoria, protegidas también por restricciones PostgreSQL;
- JWT y refresh con scope explícito, sin promoción implícita entre contextos;
- soporte temporal por gimnasio con motivo, expiración, auditoría y salida
  explícita;
- bootstrap concurrente coordinado por PostgreSQL, con detección por permiso
  efectivo y variables temporales sin conservar el secreto;
- alta transaccional de organización, primera sucursal y propietario tenant;
- invitación de un solo uso almacenada únicamente como hash;
- retiro V88 del fixture histórico exacto cuando está vacío y conservación
  diagnóstica ante actividad o ambigüedad;
- guards y rutas frontend que separan la consola SaaS del gimnasio cliente;
- actualización segura de dependencias frontend y auditoría sin advisories.

### No incluido

- borrado de cualquier dato demo ambiguo, modificado o con actividad;
- fusión automática a `main`;
- GymFort Local Connector, adapter HikCentral o integración con hardware;
- cobro SaaS recurrente automático, CFDI o conciliación bancaria completa;
- una suite E2E homogénea para todos los recorridos críticos.

## 4. Evidencia de validación local

- 413 pruebas backend unitarias;
- 59 pruebas PostgreSQL/Testcontainers, incluidas migraciones limpias/upgrade;
- `build` backend y construcción de imagen Docker;
- 94/94 pruebas frontend, typecheck, lint y build Next;
- bundle OpenNext para Cloudflare;
- `npm audit` con cero vulnerabilidades;
- construcción completa de imágenes backend/frontend mediante Compose;
- `git diff --check` y revisión de que no se versionen secretos.

La evidencia local no sustituye los checks del PR. Ausencia de un status
publicado no se interpreta como pipeline verde.

## 5. Regla de integración

1. La rama actual se publica y abre contra `main` como PR revisable.
2. CI, migraciones y revisión de seguridad deben terminar correctamente.
3. El bootstrap se habilita sólo durante la creación inicial y sus variables se
   eliminan después, conforme al runbook.
4. No se fusiona ni despliega automáticamente desde esta tarea.
5. Las ramas remotas sólo se eliminan por decisión explícita.
