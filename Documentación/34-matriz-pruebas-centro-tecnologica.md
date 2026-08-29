# Matriz de pruebas: Centro y Tecnológica

Última actualización: 28 de agosto de 2026
Rama: `feature/multibranch-plans-benefits-visits`

## 1. Objetivo

Esta matriz valida que la cobertura comercial de planes y paquetes nunca amplíe
por accidente la cobertura física de un acceso. También valida el consumo de
paquetes, sus snapshots, el origen de creación y los recorridos de kiosco,
ventas y acceso.

La matriz cubre las combinaciones de negocio significativas. Los UUID, nombres
y precios pueden variar; las invariantes de sucursal, Benefit, saldo y auditoría
son obligatorias.

## 2. Gimnasio de referencia

| Sucursal | Instalaciones y puntos físicos |
|---|---|
| Sucursal Centro | Acceso General, Sauna, Tina de crioterapia y Lockers premium |
| Sucursal Tecnológica | Acceso General; no tiene Sauna, Crioterapia ni Lockers premium |

Catálogo de Benefits de referencia:

| Código | Nombre | Alcance normal | Cobertura efectiva |
|---|---|---|---|
| `ACC-GEN-CTR` | Acceso General Centro | `LOCAL` | Centro |
| `ACC-GEN-TEC` | Acceso General Tecnológica | `LOCAL` | Tecnológica |
| `ACC-SAU` | Sauna | `LOCAL` | Centro |
| `ACC-CRYO` | Tina de crioterapia | `LOCAL` | Centro |
| `ACC-LOCK` | Lockers premium | `LOCAL` | Centro |

Un Benefit configurado como `PLAN_COVERAGE` constituye un caso diferente e
intencional: se reconoce en las sucursales cubiertas por los planes asociados.
No debe emplearse para representar una instalación que físicamente no existe.

## 3. Reglas que deben permanecer verdaderas

1. Un plan o paquete siempre incluye la sucursal activa desde la que se crea.
2. Desde Centro se puede crear para Centro o Centro + Tecnológica, nunca sólo
   para Tecnológica. Desde Tecnológica se aplica la regla espejo.
3. Un plan de una sola sucursal pertenece a la sucursal activa. Un plan
   multisucursal cubre las sucursales del tenant según el contrato actual.
4. Seleccionar Centro + Tecnológica en un paquete no crea Sauna, Crioterapia ni
   Lockers en Tecnológica.
5. La cobertura efectiva de un acceso es la intersección entre las sucursales
   del producto y las sucursales donde ese Benefit se reconoce.
6. Cada sucursal de un paquete necesita por lo menos un acceso
   `CONSUMES_VISIT` que pueda usarse efectivamente allí.
7. `INCLUDED_WITH_VISIT` exige un consumo base del mismo entitlement, fecha
   operativa y sucursal. Nunca descuenta otra visita.
8. Una membresía que concede el acceso tiene prioridad sobre el paquete.
9. La configuración vendida se conserva en snapshots; editar el catálogo no
   cambia compras históricas.
10. Todas las consultas y mutaciones se aíslan por `organization_id`.

## 4. Matriz de planes de membresía

| ID | Origen | Plan y Benefits | Acción | Resultado esperado |
|---|---|---|---|---|
| M-01 | Centro | Una sede + General Centro | Usar General en Centro/Tecnológica | Concede en Centro; no existe derecho en Tecnológica |
| M-02 | Centro | Una sede + Sauna local | Usar Sauna en Centro | Concede mientras la membresía esté vigente |
| M-03 | Centro | Una sede | Intentar crearlo sólo para Tecnológica | La UI no ofrece la opción y el backend conserva Centro como sede propietaria |
| M-04 | Centro | Multisucursal + generales locales de ambas sedes | Entrar en ambas | Concede General en ambas sin duplicar membresía |
| M-05 | Centro | Multisucursal + Sauna/Crioterapia/Lockers `LOCAL` Centro | Usarlos en ambas | Concede sólo en Centro; deniega en Tecnológica |
| M-06 | Centro | Multisucursal + Benefit `PLAN_COVERAGE` | Usarlo en ambas | Concede en ambas sólo cuando ésa fue la configuración intencional |
| M-07 | Tecnológica | Una sede + General Tecnológica | Entrar en ambas | Concede en Tecnológica; no concede en Centro |
| M-08 | Tecnológica | Una sede | Intentar crearlo sólo para Centro | No existe modalidad “sucursal contraria solamente” |
| M-09 | Tecnológica | Multisucursal + generales de ambas + premium `LOCAL` Centro | General y premium en ambas | General en ambas; premium únicamente en Centro |
| M-10 | Tecnológica | Multisucursal + premium `PLAN_COVERAGE` | Usar premium en ambas | Concede en ambas; debe mostrarse como propagación intencional en configuración |
| M-11 | Cualquiera | Plan oculto en kiosco | Alta/cambio y renovación | No aparece para alta/cambio; quien ya lo tiene sí puede renovarlo |
| M-12 | Cualquiera | Inscripción `WAIVED` | Cotizar alta | No genera concepto de inscripción |
| M-13 | Cualquiera | Inscripción `DEFAULT` | Cotizar alta/reinscripción | Aplica la configuración general vigente |
| M-14 | Centro | Plan multisucursal vendido en Centro | Renovar en Tecnológica | Aparece y renueva; conserva home branch y registra venta/periodo en la sede operativa correcta |
| M-15 | Cualquiera | Membresía vencida o socio inactivo | Solicitar acceso | Deniega aunque el Benefit esté asociado al plan |
| M-16 | Otro tenant | Plan/Benefit con UUID coincidente o ajeno | Consultar/usar | No se encuentra o se rechaza; nunca cruza organización |

## 5. Matriz de definición de paquetes

| ID | Origen | Sucursales del paquete | Configuración | Resultado esperado |
|---|---|---|---|---|
| P-01 | Centro | Centro | General Centro consume | Válido |
| P-02 | Centro | Centro + Tecnológica | General Centro y General Tecnológica consumen | Válido; saldo único compartido |
| P-03 | Centro | Tecnológica | Cualquier configuración | Rechazado: falta la sucursal activa Centro |
| P-04 | Tecnológica | Tecnológica | General Tecnológica consume | Válido |
| P-05 | Tecnológica | Centro + Tecnológica | Ambos generales consumen | Válido; puede incluir premium local de Centro |
| P-06 | Tecnológica | Centro | Cualquier configuración | Rechazado: falta la sucursal activa Tecnológica |
| P-07 | Tecnológica | Tecnológica | General Tecnológica consume + Sauna local Centro incluido | Rechazado: Sauna no tiene cobertura efectiva dentro del paquete |
| P-08 | Tecnológica | Centro + Tecnológica | General Tecnológica consume + premium Centro incluido, sin General Centro | Rechazado: Centro no tiene un acceso base consumible |
| P-09 | Tecnológica | Centro + Tecnológica | Ambos generales consumen + premium Centro incluido | Válido; premium queda snapshotteado sólo para Centro |
| P-10 | Centro | Centro | Sauna consume | Válido como producto “5 sesiones de Sauna”, sin requerir General |
| P-11 | Centro | Centro | Sauna y Crioterapia sólo incluidos | Rechazado: no existe ningún acceso que origine la visita base |
| P-12 | Cualquiera | Sede activa incluida | Benefit no `ACCESS`, inactivo o ajeno | Rechazado antes de persistir |
| P-13 | Cualquiera | Sedes duplicadas o inexistentes | Configuración válida restante | Normaliza duplicados; rechaza UUID inexistente/ajeno según contrato |
| P-14 | Cualquiera | Una o varias | Código/SKU duplicado | Rechazado sin alta parcial |
| P-15 | Cualquiera | Una o varias | Paquete oculto | Recepción puede venderlo; kiosco no lo ofrece para compra nueva |
| P-16 | Cualquiera | Una o varias | Inscripción `WAIVED`/`DEFAULT` | Respeta la política seleccionada igual que un plan |

## 6. Matriz de compra, acceso y consumo

Producto principal de aceptación, creado desde Tecnológica:

- paquete “10 visitas multisucursal + premium Centro”;
- General Tecnológica: `CONSUMES_VISIT`, sólo Tecnológica;
- General Centro: `CONSUMES_VISIT`, sólo Centro;
- Sauna, Crioterapia y Lockers premium: `INCLUDED_WITH_VISIT`, sólo Centro.

| ID | Precondición/acción | Decisión | Saldo y auditoría esperados |
|---|---|---|---|
| A-01 | Venta pendiente, antes del pago autoritativo | Deniega | No existe entitlement activo ni consumo |
| A-02 | Pago confirmado | Activa | Saldo inicial 10 y snapshots de sedes/Benefits/modos |
| A-03 | Día 1, General Tecnológica | Concede por paquete | 10→9; un `visit_package_consumption` en Tecnológica |
| A-04 | Mismo día, Sauna en Centro después de A-03 | Deniega | Sigue 9; el consumo de Tecnológica no habilita Centro |
| A-05 | Mismo día, General Centro | Concede por paquete | 9→8; consumo base Centro |
| A-06 | Mismo día, Sauna Centro después de A-05 | Concede incluido | Sigue 8; ledger incluido referencia la visita base |
| A-07 | Mismo día, Crioterapia Centro después de A-05 | Concede incluido | Sigue 8; registro independiente sin decremento |
| A-08 | Mismo día, Lockers Centro después de A-05 | Concede incluido | Sigue 8; registro independiente sin decremento |
| A-09 | Mismo día, cualquier premium en Tecnológica | Deniega | Sigue 8; no existe snapshot físico allí |
| A-10 | Día 2, Sauna Centro antes de General | Deniega | Sigue 8; la visita base del día anterior no aplica |
| A-11 | Día 2, General Centro y después Sauna | Ambas conceden | General 8→7; Sauna mantiene 7 |
| A-12 | Included con consumo de otro entitlement | Deniega | No reutiliza evidencia de otro paquete |
| A-13 | Repetir exactamente el evento General | Misma decisión idempotente | Un solo decremento y un solo consumo |
| A-14 | Repetir exactamente el evento incluido denegado | Mantiene denegación | No cambia a concedido aunque luego exista una visita base |
| A-15 | Dos eventos concurrentes con saldo 1 | Un consumo exitoso | 1→0, exactamente un ledger; nunca saldo negativo |
| A-16 | Saldo cero, General | Deniega | Entitlement `CONSUMED`, sin decremento adicional |
| A-17 | Entitlement vencido | Deniega | No cambia saldo |
| A-18 | Membresía concede General y paquete tiene saldo | Concede por membresía | Paquete sin cambios |
| A-19 | Membresía concede General pero no Sauna; paquete Sauna es incluido | Sauna después de General | Deniega en V1: no hubo consumo base de ese entitlement |
| A-20 | Membresía concede Sauna | Concede por membresía | No consulta ni consume el paquete |
| A-21 | Editar Sauna de incluido a consume después de vender | Usar compra antigua/nueva | Antigua conserva incluido; nueva snapshottea consume |
| A-22 | Editar sedes/visitas/vigencia después de vender | Consultar compra antigua | Conserva todos los snapshots vendidos |
| A-23 | Segundo paquete pendiente/activo para el mismo socio | Comprar | Rechazado; sólo un saldo abierto por socio |
| A-24 | Paquete consumido/vencido | Comprar otro | Permitido según reglas comerciales |
| A-25 | Otro tenant intenta usar entitlement | Acceso | Deniega/no encuentra; no escribe ledger ajeno |
| A-26 | Cambio de día cerca de medianoche | Included | Usa `business_date` y zona de la sucursal, no UTC arbitrario |
| A-27 | Escritura por API legacy `visit_pass` | Crear/consumir | Rechazada o sólo lectura; no crea saldo paralelo |
| A-28 | Refund total después de consumos | Reembolsar | Se bloquea para revisión administrativa según política vigente |

## 7. Matriz de interfaz y contratos

| ID | Superficie | Caso | Resultado esperado |
|---|---|---|---|
| U-01 | Catálogo | Abrir Planes | Acción “Nuevo plan” vive dentro de la pestaña |
| U-02 | Catálogo | Abrir Paquetes | Acción “Nuevo paquete” vive dentro de la pestaña |
| U-03 | Catálogo | Abrir Artículos/Categorías | Sus altas aparecen en la pestaña correspondiente |
| U-04 | Paquetes | Operar en Tecnológica | Tecnológica está seleccionada y no puede quitarse; Centro es opcional |
| U-05 | Paquetes | Elegir sólo Tecnológica | No ofrece como válidos Benefits `LOCAL` exclusivos de Centro |
| U-06 | Paquetes | Elegir ambas sedes | Cada acceso muestra dónde es efectivo; premium indica “sólo Centro” |
| U-07 | Paquetes | Elegir modo de acceso | Textos visibles: “Consume una visita” / “Se incluye con la visita” |
| U-08 | Paquetes | Todo incluido, ningún consumo | Validación clara antes de enviar y validación autoritativa backend |
| U-09 | Kiosco | Identificación fallida y cambio a registro | Crea sesión limpia; no queda ligada al visitante anterior |
| U-10 | Kiosco | Registro exitoso con consulta secundaria fallida | No duplica socio y no muestra detalles HTTP/correlation ID |
| U-11 | Kiosco secundario | Plan multisucursal vigente | Aparece para renovación/administración según visibilidad |
| U-12 | API | Payload legacy sin `usageMode` | Se interpreta conservadoramente como `CONSUMES_VISIT` |

## 8. Evidencia automatizada

| Invariante | Pruebas principales |
|---|---|
| Origen Tecnológica + premium local Centro | `VisitPackagePlanServiceTests.acceptsPackageCreatedInTecnologicaWithPremiumAccessRestrictedToCentro` |
| Tecnológica sola no puede usar Sauna Centro | `VisitPackagePlanServiceTests.rejectsTecnologicaOnlyPackageWhenItsPremiumAccessExistsOnlyInCentro` |
| UX de paquete originado en Tecnológica | `catalog-inventory.spec.ts: un paquete creado en Tecnológica sólo habilita premium al incluir Centro` |
| Saldo compartido y premium local sin doble descuento | `VisitPackageEntitlementRepositoryIntegrationTests.packagePurchasedInTecnologicaKeepsCentroPremiumAccessLocalAndUsesOneSharedBalance` |
| Cobertura `LOCAL|PLAN_COVERAGE` | `VisitPackageEntitlementRepositoryIntegrationTests.benefitCoverageUsesBenefitScopeRatherThanExpandingToPackageBranches` |
| Included: entitlement/fecha/sucursal | `VisitPackageEntitlementRepositoryIntegrationTests` y `AccessBenefitDecisionTests` |
| Prioridad de membresía | `AccessBenefitDecisionTests` |
| Renovación multisucursal | `MembershipCommercePersistenceIntegrationTests.completedMultiBranchRenewalFromSecondaryBranchPreservesHomeBranchAndTracksSaleBranch` |
| Plan multisucursal visible en sede secundaria | `CatalogPersistenceIntegrationTests.multiBranchPlanIsAvailableInSecondaryKioskWithoutDuplicatingProductBranchAttachment` |
| Idempotencia y concurrencia real | `VisitPackageEntitlementRepositoryIntegrationTests` contra PostgreSQL/Testcontainers |
| Flyway limpio y upgrade V99 | `DatabaseMigrationTests` |
| Flujo de catálogo y kiosco | pruebas frontend y Playwright de catálogo/kiosco |

## 9. Criterio de aprobación

La matriz se aprueba únicamente cuando:

- suites backend unitarias y PostgreSQL terminan sin fallos ni omisiones por
  Docker;
- migración limpia y upgrade V99→última versión pasan;
- typecheck, lint, build y pruebas frontend pasan;
- E2E de catálogo, kiosco, membresías, paquetes y acceso pasan;
- el diff final confirma aislamiento por tenant, idempotencia, snapshots y
  ausencia de expansión de Benefits locales.

El resultado exacto de la ejecución se registra en los documentos 13, 28 y 29.

## 10. Resultado de esta ejecución

| Validación | Resultado 28/08/2026 |
|---|---|
| Backend unitario | PASS — 495/495, sin fallos ni omisiones |
| PostgreSQL/Testcontainers | PASS — 114/114, sin fallos ni omisiones |
| Flyway limpio y upgrades históricos | PASS — incluidos en `DatabaseMigrationTests` reales |
| Concurrencia con última visita | PASS — dos solicitudes, un consumo, saldo final 0 |
| Frontend unitario | PASS — 114/114 |
| TypeScript | PASS — `tsc --noEmit` |
| ESLint | PASS |
| Build Next.js 16.3.2 | PASS — 38 rutas generadas |
| Playwright completo | PASS — 102/102, incluido origen Tecnológica |
| Stripe sandbox conectado | NOT RUN — requiere credenciales/cuenta Connect test externa |
| Torniquetes y lectores físicos de ambas sedes | NOT RUN — requiere hardware del cliente |

Los dos puntos `NOT RUN` permanecen como aceptación externa; no se presentan
como aprobados ni invalidan la evidencia local de dominio, base de datos y UI.
