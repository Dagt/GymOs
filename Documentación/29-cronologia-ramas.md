# 29. Cronología y situación de ramas

Estado: AUDITADO

Corte: 23 de agosto de 2026

Rama de trabajo: `feature/physical-access-control`

Base integrada: `main` en `a9ca485` (`Merge pricing and billing`)

## 1. Situación vigente

Cobranza comercial, Automatizaciones/ZeptoMail y métricas ya forman parte de la
base integrada. La rama actual concentra exclusivamente el control de acceso
físico cloud y la preparación del primer piloto Hikvision/HikCentral.

La rama inició con la fundación de sincronización `f0b375c` y la consola
`bbf7ea8`. El corte actual agrega V84, el contrato seguro del GymFort Local
Connector, jobs durables, automatización de estado deseado, eventos/candidatos,
hardening tenant/sucursal, UX operativa, pruebas y documentación. No incluye un
adapter HikCentral funcional ni afirma apertura del torniquete real.

| Rama | Estado | Acción |
|---|---|---|
| `main` | Base estable hasta cobranza comercial | Recibir cambios sólo por PR verde |
| `feature/physical-access-control` | Activa | Validar, revisar, publicar PR; no fusionar desde esta tarea |
| `feature/pricing-and-billing` | Integrada en `main` | Histórica; no fusionar otra vez |
| `feature/automations-zoho-email` | Integrada | Histórica; no fusionar otra vez |
| `fix/kiosk-memberships-stripe` | Integrada | Histórica; no fusionar otra vez |
| `feature/coaches-workouts` | Integrada en la evolución del árbol | Referencia histórica; no merge lateral |
| `feature/wellness-services` | Funcionalidad reaplicada en historia vigente | Referencia; no fusionar de nuevo |

## 2. Hitos integrados y de rama

| Periodo | Hito |
|---|---|
| 13–25 jul 2026 | Fundación, multi-tenant, identidad, CRM, catálogo, POS, membresías, reservas, inventario, consolas y operación por sucursal |
| 26–27 jul | Coaches/rutinas, perfiles, catálogo de ejercicios, métricas, PDF y Wellness |
| 30 jul–3 ago | Automatizaciones durables, kiosco lógico, proveedores, Stripe Checkout/recurrencia, correo durable y observabilidad |
| agosto | Membresías Stripe, idempotencia, beneficios, retornos, pagos y multi-sucursal |
| 20 ago | PR #10 integra `fix/kiosk-memberships-stripe` en `main` |
| 21–22 ago | Configuración unificada de pagos/kiosco, ZeptoMail real, automatizaciones simplificadas y permisos de Gerencia |
| 23 ago | `feature/pricing-and-billing` se integra a `main` en `a9ca485` |
| 23 ago | `f0b375c` agrega V83, puerto provider-neutral, vínculos/mapeos y estado de sincronización |
| 23 ago | `bbf7ea8` reorganiza `/access` en operación, configuración física y bitácora |
| 23 ago | V84 completa control plane, Connector API, cola durable, seguridad, pruebas y documentación del piloto físico |

## 3. Alcance material de la rama activa

### Cloud implementado

- catálogo provider-neutral con proveedores planificados no seleccionables;
- conexión única por sucursal;
- puntos productivos con exactamente un beneficio `ACCESS`;
- vínculos de dispositivo/persona y mapeos de niveles externos;
- estado deseado automático por eventos de membresía, socio y beneficio;
- jobs PostgreSQL idempotentes con claim exclusivo, lease, retry y resultado;
- activación de un uso, secretos hasheados, rotación/revocación y heartbeat;
- eventos físicos idempotentes y candidatos con matching exacto/manual;
- permiso `access.physical.manage`, aislamiento compuesto tenant/sucursal y
  auditoría;
- consola de configuración/salud sin biometría ni credenciales LAN.

### Fuera de esta rama

- ejecutable instalable GymFort Local Connector;
- adapter contra una versión/licencia confirmada de HikCentral Professional;
- endpoints, puertos o credenciales HikCentral;
- enrolamiento biométrico en GymFort Cloud;
- apertura física distinta de `SIMULATED_OPEN`;
- aceptación con hardware y runbook de caída/recuperación en sitio.

## 4. Evidencia requerida para cierre

- suites backend unitarias y de arquitectura;
- migración limpia y upgrade V83 → V84 en PostgreSQL;
- integración PostgreSQL de claim/lease/retry/idempotencia/aislamiento;
- typecheck, lint, pruebas y build frontend;
- smoke visual local de `/access` con permisos reales;
- `git diff --check`, revisión de secretos y comparación contra `origin/main`;
- commits claros sólo en `feature/physical-access-control`.

Los checks CI deben revisarse sobre el commit final antes de abrir o fusionar un
PR. Ausencia de un status publicado no se interpreta como pipeline verde.

## 5. Regla futura

1. Una rama representa un objetivo claro.
2. Antes de integrar: fetch/comparación contra `origin/main`, pruebas, revisión
   documental y PR.
3. No hacer merges laterales de ramas históricas.
4. Todo cierre material actualiza documentos 13, 28, 29 y fichas afectadas.
5. Las ramas remotas sólo se eliminan por decisión explícita.
