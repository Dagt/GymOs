# 29. Cronología y situación de ramas

Estado: AUDITADO

Corte: 27 de agosto de 2026

Rama de trabajo: `feature/multibranch-plans-benefits-visits`

Base actual: `main` en `2162b7d`, después de integrar PR #20. El lote de planes,
beneficios y visitas permanece en su rama y no está fusionado.

## 1. Situación vigente

Los PR #19 y #20 incorporaron ciclo de vida y catálogo con variantes. La rama
activa implementa políticas comerciales de planes, beneficios multi-sucursal y
paquetes de visitas compartidos dentro de un tenant.

| Rama | Estado | Acción |
|---|---|---|
| `main` | Contiene lotes 1 y 2 mediante PR #19/#20 | Base del lote activo |
| `feature/multibranch-plans-benefits-visits` | Activa | Desarrollar y validar; no fusionar automáticamente |
| `feature/catalog-product-variants-barcodes` | Integrada mediante PR #20 | Histórica; no fusionar otra vez |
| `feature/client-onboarding-lifecycle` | Integrada mediante PR #19 | Histórica; no fusionar otra vez |
| `feature/platform-identity-separation` | Integrada antes del corte actual | Histórica; no fusionar otra vez |
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
| 26 ago 2026 | PR #18 integra paginación y hardening V91–V92 en `main` (`a5ea75f`) |
| 26 ago 2026 | Se crea `feature/client-onboarding-lifecycle` y V93 agrupa el primer lote de retroalimentación del cliente real |
| 26 ago 2026 | V94 conserva `inactivityMonths` como umbral único; configuración de kiosco adopta autosave e intención independiente de Stripe; launch/reset resuelven capacidades vigentes |
| 26 ago 2026 | El cierre visual compacta socios, extiende autosave a cobros/socios, condiciona `Stripe pendiente` al toggle encendido y rediseña la entrada del kiosco |
| 26 ago 2026 | La pestaña pasa a `Inscripciones`, todos sus valores adoptan autosave sin botón y el selector público se compacta y separa del formulario |
| 26 ago 2026 | PR #19 integra el lote; el cierre centra el selector del kiosco y presenta fallos de identidad breves sin exponer diagnóstico HTTP |
| 27 ago 2026 | Se crea `feature/catalog-product-variants-barcodes`; V95 conserva productos históricos, agrega familias/códigos alias y alta masiva de variantes |
| 27 ago 2026 | El alta masiva se refina sin migración: datos comunes una vez, filas sabor/código/SKU, SKU sugerido editable, overrides y aliases bajo demanda |
| 27 ago 2026 | La UX oculta la familia técnica: producto base automático, ampliación desde catálogo y alta individual con un código principal más aliases avanzados |
| 27 ago 2026 | La captura de variantes se compacta en una tabla y admite sabor, tamaño/porciones o ambos sin exponer un control fiscal aislado |
| 27 ago 2026 | El flujo elimina textos explicativos y campos promocionales permanentes del alta masiva |
| 27 ago 2026 | El drawer se rediseña como lista de una fila inicial más editor enfocado; inventario y datos secundarios dejan de ocupar bloques permanentes |
| 27 ago 2026 | PR #20 integra variantes/códigos en `main` (`2162b7d`) |
| 27 ago 2026 | Se crea `feature/multibranch-plans-benefits-visits`; se aprueba documento 33 y se inicia la secuencia V96–V99 |
| 28 ago 2026 | V99 fija vigencia por compra; venta, pago, activación, exclusividad, consumo, Catálogo, expediente y kiosco de paquetes quedan integrados |
| 28 ago 2026 | La aceptación manual desacopla identidad de consultas opcionales, formaliza `204` sin paquete, corrige la lectura JDBC y ubica cada alta dentro de su pestaña de Catálogo |
| 28 ago 2026 | V100 separa saldo de paquete y alcance de acceso: materializa la intersección beneficio–sucursal, obliga a incluir la sucursal activa, agrega historial operativo, bloquea escrituras legacy y protege refunds después de consumos |
| 28 ago 2026 | El cierre de aceptación hace organizacional la identificación de kiosco, reinicia sesión al alternar alta/entrada y exige al menos un acceso consumible por cada sucursal elegida en un paquete |

## 3. Alcance material de la rama activa

### Implementado

- documento 33 fija alcance, arquitectura, invariantes y secuencia de entrega;
- V96 agrega visibilidad de kiosco y exención de inscripción por plan, además de
  aplicabilidad de `SPECIAL_PRICE` a planes explícitos;
- las cotizaciones respetan `WAIVED`; altas/cambios del kiosco no pueden eludir
  un plan privado mediante llamadas directas;
- membresías multi-sucursal pueden mostrarse y renovarse desde otra sede;
- V97 agrega alcance `LOCAL|PLAN_COVERAGE` a beneficios `ACCESS`;
- V98 crea definición vendible, entitlement, snapshots y ledger de paquetes;
- V99 agrega el snapshot de vigencia por entitlement;
- V100 agrega el snapshot `entitlement + benefit + branch`; un paquete A+B no
  amplía Sauna `LOCAL` de A hacia B;
- una restricción impide dos paquetes `PENDING_PAYMENT|ACTIVE` para el mismo
  socio y tenant;
- Catálogo coloca paquetes inmediatamente junto a planes de membresía; los
  planes y paquetes pueden ocultarse de nuevas compras del kiosco y exentarse
  de inscripción;
- venta/activación por pago, bloqueo de pases históricos, consumo idempotente,
  historial en expediente, Recepción y compra de kiosco están implementados;
- la API legacy `visit_pass` es de sólo lectura y un refund total después de
  consumos requiere revisión administrativa.

### Aún pendiente en la rama

- ejecución real de la matriz PostgreSQL concurrente y migraciones; las pruebas
  existen pero Docker no está disponible en el entorno actual;
- HTTP/E2E integral de dos sedes y Stripe test;
- reversión administrativa auditada;
- branding por sucursal, fuera de este lote.

## 4. Evidencia de validación local

- pruebas de arquitectura, políticas de beneficios, inscripción y autoservicio;
- `DatabaseMigrationTests` sobre PostgreSQL desde base vacía y upgrade V95;
- typecheck frontend y suites completas antes de publicar la rama.

## 5. Regla de integración

1. `feature/multibranch-plans-benefits-visits` no se fusiona automáticamente.
2. CI y revisión de aceptación deben terminar antes de cualquier merge.
3. Despliegue V96–V100 y habilitación de compras son acciones independientes.
4. Las ramas remotas sólo se eliminan por decisión explícita.
