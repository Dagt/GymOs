# 29. Cronología y situación de ramas

Estado: AUDITADO

Corte: 27 de agosto de 2026

Rama de trabajo: `feature/catalog-product-variants-barcodes`

Base actual: `main` después de integrar PR #19. El lote 2 permanece en su rama
y no está fusionado.

## 1. Situación vigente

El PR #19 ya incorporó el primer lote. La rama activa implementa el lote 2 con
V95: familias comerciales, variantes vendibles, múltiples códigos por variante
y alta masiva simple, preservando los IDs usados por ventas e inventario.

| Rama | Estado | Acción |
|---|---|---|
| `main` | Contiene el lote 1 mediante PR #19 | Base del lote 2; no fusionar sin revisión |
| `feature/catalog-product-variants-barcodes` | Activa | Validar y publicar sin merge automático |
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

## 3. Alcance material de la rama activa

### Implementado

- familia comercial por organización y variante vendible con ID estable;
- hasta 20 códigos por variante, código principal compatible y alias exactos en
  catálogo, POS e inventario;
- alta masiva transaccional con datos comunes capturados una vez, filas compactas
  sabor/código/SKU, overrides por excepción y reutilización de familias existentes;
- V95 validada desde una base vacía y sobre V94 con conservación de IDs;
- cargo de inscripción configurable, cotizado y persistido como partida
  financiera independiente;
- baja automática reversible de socios sin vigencia, con fecha, motivo,
  auditoría y evento de cambio de estado;
- verificación de socios sin plan y reactivación segura de bajas automáticas;
- capacidad opt-in por sucursal para alta desde kiosco y continuación inmediata
  al primer plan;
- sección `Inscripciones` con un solo periodo de inactividad y V94 compatible con bases
  donde V93 ya fue aplicada;
- capacidades deseadas del kiosco persistidas sin readiness, autosave con
  rollback y estados distintos para Stripe pendiente, función deshabilitada y
  catálogo vacío;
- prueba PostgreSQL real desde settings hasta `allowed_actions_json`, launch y
  reset de la sesión;
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

- paquetes de visitas compartidos entre sucursales del mismo tenant, alcance
  multi-sucursal de beneficios y branding por sucursal; quedan en los lotes
  3–5 del análisis del primer cliente;
- borrado de cualquier dato demo ambiguo, modificado o con actividad;
- GymFort Local Connector, adapter HikCentral o integración con hardware;
- cobro SaaS recurrente automático, CFDI o conciliación bancaria completa;
- una suite E2E homogénea para todos los recorridos críticos.

## 4. Evidencia de validación local

- `CatalogProductServiceTests` cubre códigos múltiples y lotes de variantes;
- `CatalogPersistenceIntegrationTests` demuestra resolución exacta de alias y
  alcance por sucursal;
- `DatabaseMigrationTests` valida instalación limpia y upgrade V94 → V95 sin
  cambiar IDs de producto;
- suite frontend, typecheck y lint exitosos; `pos-model.test.ts` cubre alias;
- `e2e/catalog-inventory.spec.ts` valida herencia, override aislado, captura HID
  sin submit accidental, diez sabores, familia existente, permisos y lectura
  POS con código secundario;
- `git diff --check` y revisión de que no se versionen secretos.

## 5. Regla de integración

1. `feature/catalog-product-variants-barcodes` no se fusiona automáticamente.
2. CI y revisión de aceptación deben terminar antes de cualquier merge.
3. El despliegue y la migración V95 siguen siendo acciones independientes.
4. Las ramas remotas sólo se eliminan por decisión explícita.
