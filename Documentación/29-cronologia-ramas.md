# 29. Cronología y situación de ramas

Estado: AUDITADO

Corte: 24 de agosto de 2026

Rama de trabajo: `feature/platform-identity-separation`

Base integrada: `main` en `71810b3` (`fix: support authenticated Redis connections`)

## 1. Situación vigente

El control plane de acceso físico cloud ya está integrado en `main`. La rama
`feature/platform-client-billing-management` agrega la administración comercial
de gimnasios clientes y permanece publicada en `8ba97a6`; la rama actual parte de
ese commit y añade la separación definitiva entre identidades `PLATFORM` y
`TENANT`, el bootstrap seguro del primer administrador y el aprovisionamiento
transaccional de cada gimnasio con su primer propietario invitado.

El PR de la rama actual hacia `main` incluye ambos bloques porque la remediación
de identidad es necesaria para operar de forma segura la consola comercial. No
debe fusionarse hasta que CI y la revisión de seguridad estén verdes.

| Rama | Estado | Acción |
|---|---|---|
| `main` | Base estable con acceso físico cloud | Recibir cambios sólo por PR verde |
| `feature/platform-identity-separation` | Activa y validada localmente | Publicar PR; revisar CI/seguridad; no fusionar automáticamente |
| `feature/platform-client-billing-management` | Publicada en `8ba97a6`; base de la rama actual | No abrir un merge paralelo; queda incluida en el PR actual |
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

## 3. Alcance material de la rama activa

### Implementado

- administración comercial de gimnasios desde la navegación lateral de
  plataforma;
- identidades `PLATFORM` sin organización/sucursal e identidades `TENANT` con
  organización obligatoria, protegidas también por restricciones PostgreSQL;
- JWT y refresh con scope explícito, sin promoción implícita entre contextos;
- soporte temporal por gimnasio con motivo, expiración, auditoría y salida
  explícita;
- bootstrap idempotente del primer administrador usando sólo variables de
  entorno, sin conservar el secreto;
- alta transaccional de organización, primera sucursal y propietario tenant;
- invitación de un solo uso almacenada únicamente como hash;
- clasificación `PRODUCTIVE`/`DEMO` y exclusión de fixtures demo del portafolio;
- guards y rutas frontend que separan la consola SaaS del gimnasio cliente;
- actualización segura de dependencias frontend y auditoría sin advisories.

### No incluido

- borrado automático de datos demo o de datos productivos existentes;
- fusión automática a `main`;
- GymFort Local Connector, adapter HikCentral o integración con hardware;
- cobro SaaS recurrente automático, CFDI o conciliación bancaria completa;
- una suite E2E homogénea para todos los recorridos críticos.

## 4. Evidencia de validación local

- suite backend completa y pruebas focalizadas de identidad/plataforma;
- suite PostgreSQL/Testcontainers completa y migraciones limpias/upgrade;
- `build` backend y construcción de imagen Docker;
- 94/94 pruebas frontend, typecheck, lint y build Next;
- bundle OpenNext para Cloudflare;
- `npm audit` con cero vulnerabilidades;
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
