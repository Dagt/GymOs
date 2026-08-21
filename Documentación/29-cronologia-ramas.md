# 29. Cronología y situación de ramas

Estado: AUDITADO
Corte: 21 de agosto de 2026
Rama de trabajo: `feature/automations-zoho-email`
Base integrada: PR #10, merge `52a72bc`

## 1. Situación vigente

`fix/kiosk-memberships-stripe` se integró a `main` mediante el PR #10. A partir
de ese merge se creó `feature/automations-zoho-email`, que contiene el
endurecimiento menor previo a Zoho: identidad GymFort, estilo administrativo,
feedback global, permisos Stripe y verificación del contenedor Stripe CLI.

El corte funcional auditado es `88a6b58` y está publicado en
`origin/feature/automations-zoho-email`; la sincronización documental posterior
no cambia ese comportamiento. La implementación específica de Zoho todavía no
comenzó y esta rama es el lugar de trabajo previsto.

| Rama | Estado | Acción |
|---|---|---|
| `main` | Base estable con PR #10 integrado | Recibir cambios sólo por PR verde |
| `fix/kiosk-memberships-stripe` | Integrada | Histórica; no fusionar otra vez |
| `feature/automations-zoho-email` | Activa, publicada | Continuar Zoho y abrir PR al cerrar aceptación |
| `feature/automations-self-service-kiosk` | Ancestro funcional integrado por la línea de fix/PR | Referencia histórica |
| `feature/coaches-workouts` | Trabajo de coaches ya incorporado en la evolución del árbol | No fusionar lateralmente sin auditoría |
| `feature/wellness-services` | Funcionalidad reaplicada en la historia vigente | Referencia; no fusionar de nuevo |

Las ramas antiguas `feature/hito-*`, backups y ramas de estabilización ya
contenidas en `main` son únicamente historia. Una rama divergente nunca se
considera pendiente sólo por conservar commits: se compara por ancestros y se
extraen cambios útiles mediante PR/cherry-pick controlado.

## 2. Hitos integrados

| Periodo | Hito |
|---|---|
| 13–25 jul 2026 | Fundación, multi-tenant, identidad, CRM, catálogo, POS, membresías, reservas, inventario, consolas y operación por sucursal |
| 26–27 jul | Coaches/rutinas, perfiles, catálogo de ejercicios, métricas, PDF y restauración de Wellness |
| 30 jul–3 ago | Automatizaciones durables, kiosco lógico, proveedores, Stripe Checkout/recurrencia, correo durable y observabilidad |
| agosto | Integración y corrección de membresías Stripe, idempotencia, beneficios, retornos, pagos y planes multi-sucursal |
| 20 ago | PR #10 integra `fix/kiosk-memberships-stripe` en `main` |
| 21 ago | `88a6b58`: estilo administrativo, atribución GymFort, feedback global, permisos Stripe y Stripe CLI en Docker/CI |

## 3. Evidencia del corte

- backend Gradle exitoso;
- 77 pruebas frontend, lint, typecheck y build exitosos;
- 8 E2E de ruteo por consola exitosos;
- Docker Compose válido;
- `stripe/stripe-cli:v1.50.3` descargado y ejecutado realmente.

## 4. Regla futura

1. Una rama representa un objetivo claro.
2. Antes de integrar: `fetch`, comparación contra `origin/main`, pruebas,
   revisión documental y PR.
3. No hacer merges laterales de ramas históricas.
4. Todo cierre material actualiza documentos 13, 28, 29 y fichas afectadas.
5. Las ramas remotas sólo se eliminan por decisión explícita.
