# 13. Estado actual de desarrollo

## Stripe y kiosco (#34)

La recuperación de Checkout en el PR #69 exige evidencia actual de Stripe
para reutilizar una sesión o abrir un reemplazo. El flujo conserva el pago y
la venta. Las pruebas con un gateway simulado y PostgreSQL no sustituyen la
verificación conectada en Stripe Sandbox.

La propuesta implementa recuperación de Checkout sobre la venta y el pago
existentes, rotación de la sesión al limpiar el kiosco y estados autoritativos
de expiración y fallo. La [matriz QA](40-qa-matriz-de-aceptacion-y-evidencias.md#pay-001-y-self-001-34)
separa pruebas automatizadas, Stripe test y staging. La aceptación externa y
los reembolsos de proveedor continúan pendientes.

## Contrataciones en recepción

La pantalla operativa combina búsqueda rápida, selección de operación y formularios existentes. Las operaciones recientes se leen desde GET /sales para perfiles con sale.read.

El dashboard destaca las contrataciones. El expediente muestra el paquete vigente o pendiente y agrupa los accesos y descuentos en dos columnas cuando hay espacio.

«Membresías y paquetes» centraliza la búsqueda, asignación, renovación,
cambio de plan y venta de paquetes. Comparte la venta de paquetes con
el expediente y actualiza el saldo tras el cobro.

## Aislamiento multi-tenant en revisión

El trabajo de #29 parte del commit `58a5d4d4b24bcc598059b05a4ac70d11a2fe937b`, que incorpora check-in manual y reversiones de #65. El [inventario por operación](../docs/security/endpoint-permissions.md) se coteja con Spring en CI. La [matriz de verificación](../docs/security/multi-tenant-verification.md) identifica qué flujos se probaron además con HTTP/PostgreSQL, las deudas de otros issues y el check externo. El PR #66 conserva los resultados por SHA.

> **ACTUALIZACIÓN DOCUMENTAL 29/10/2026 (prioritaria sobre encabezado histórico):** La rama feature/multibranch-plans-benefits-visits ya tiene el mismo HEAD que main (136ce72, V103); la frase histórica «sin merge» es obsoleta. La rama más reciente feature/local-access-connector está cuatro commits adelantada (8955cdb, V104), pero no está fusionada ni certificada en sitio. CI main y Connector aparecen fallidos sin logs recuperables. Ver [auditoría](37-auditoria-repositorio-2026-10-29.md), [MVP](35-mvp-y-criterios-de-salida.md), [backlog](36-epicas-backlog-codex.md) y [matriz QA](40-qa-matriz-de-aceptacion-y-evidencias.md).


Estado: FUENTE CANÓNICA / EN PROGRESO
Corte: 29 de agosto de 2026
Código auditado: `feature/multibranch-plans-benefits-visits`, incluyendo V103.
No implica despliegue productivo ni merge de la rama.

## Ajuste V103 — operación independiente por sucursal

Inscripción, umbral/baja por inactividad y habilitaciones de medios de cobro ya
no se comparten forzosamente entre sucursales. Se conserva una sola conexión de
proveedor por organización y se aplican controles operativos por sede. El
kiosco elimina indicadores `1/2/A`, evita duplicar domiciliación, unifica las
acciones amarillas y centra la revisión de contratación. El alta de paquetes
oculta accesos sin cobertura y compacta la selección del modo de consumo.
La revisión final de UX agrupa membresía y paquete vigentes como servicios,
aprovecha todo el ancho para renovar y coloca la cotización después de las
opciones. El expediente distingue los accesos del paquete activo de aquellos
heredados por la membresía.

## 1. Resumen ejecutivo

GymFort opera como monolito modular multi-tenant y multi-sucursal. El piloto incluye consolas SaaS y de gimnasio, recepción/POS, kiosco aislado, comercio y membresías, reservas, inventario, coaches, wellness, automatizaciones, correo transaccional durable y cobranza comercial controlada de GymFort.

**La identidad SaaS y la identidad de los gimnasios ya están separadas**:
`PLATFORM` inicia sin organización/sucursal, `TENANT` exige organización y las
restricciones viven tanto en JWT/refresh como en PostgreSQL. El primer
administrador se crea mediante bootstrap efímero de entorno coordinado por
PostgreSQL, mientras el alta
de un cliente crea transaccionalmente gimnasio, primera sucursal y dueño por
invitación de un solo uso. El acceso excepcional de soporte requiere motivo,
expira y queda auditado.

El alta desde la consola de plataforma ya no consulta la colección tenant
`/organizations`: usa exclusivamente `/platform/customers`, por lo que un alta
confirmada no se presenta falsamente como fallida ni se reintenta creando una
colisión. Después del commit, el enlace del dueño se entrega por ZeptoMail sin
persistir el token en claro. La ficha muestra aceptación del proveedor,
expiración y acciones para rotar/reemitir invitaciones; una falla de correo no
revierte ni oculta el gimnasio ya creado.

**La administración de clientes de GymFort queda estabilizada para operación
comercial manual**: el administrador entra desde el menú lateral a una cartera
ejecutiva, expedientes por gimnasio, centro de acción, salud comercial, MRR,
contratos y cobros. V85 protege registros de pago contra reintentos duplicados y
evita sobrescrituras concurrentes. No implica cobro SaaS automático, CFDI ni
contabilidad fiscal.

La identidad comercial visible es GymFort. `GymOS`/`gymos` continúa en nombres técnicos por compatibilidad.

Los listados de socios, catálogo/POS, inventario y ejercicios muestran totales
del servidor y controles de página visibles. Las búsquedas se aplican antes de
paginar; los selectores no dependen de los primeros 50 registros. Los CSV de
inventario se identifican explícitamente como exportaciones de la página.
Detalles: [paginación de listados](../docs/operations/list-pagination.md).

El primer lote derivado de la validación con un cliente real cierra el alta y
ciclo de vida del socio: inscripción configurable y registrada como partida
financiera, baja automática reversible, autenticación en kiosco sin plan y
registro inicial desde el kiosco. El borrado físico se descarta para conservar
ventas, pagos, membresías y auditoría. El análisis y los lotes restantes están
en [retroalimentación del primer cliente](32-retroalimentacion-primer-cliente.md).

El segundo lote resuelve el alta repetitiva de sabores: Catálogo separa familia
comercial de variante vendible, admite múltiples códigos por variante y ofrece
alta masiva transaccional. El refinamiento operativo captura presentación,
precio, costo, proveedor, categoría, unidad, inventario, mínimo y etiquetas una
sola vez; los datos secundarios permanecen plegados y el control de inventario es
compacto. El lote inicia con una fila y la lista requiere sólo variante, código y
SKU sugerido/editable. La variante puede ser sabor, tamaño/porciones o ambos;
`Detalles` abre un editor enfocado de una sola variante para presentación, precio,
costo y aliases, sin promociones ni expansiones acumuladas. Cada variante conserva
SKU, precio, costo, stock, movimientos y ventas propios. V95 permanece sin
cambios, mantiene los IDs existentes y POS/inventario resuelven cualquiera de
sus códigos. El stock mínimo común se inicializa también dentro de la transacción
del lote y sólo está disponible con `inventory.manage`.
La UX ya no obliga a entender ni administrar familias: crea el producto base
automáticamente, permite agregar variantes desde el propio catálogo y separa el
código principal de los aliases avanzados. El formulario individual coloca el
botón de alias junto al código, fuera de `Más datos`, no mezcla la captura normal
con campos de sabor y el alta masiva no introduce un control fiscal distinto al
resto del catálogo.

Topología confirmada para los lotes siguientes: el cliente operará sus dos
marcas como sucursales de un solo tenant. Compartirán socios, catálogo, planes y
beneficios; cada sucursal deberá resolver su propia marca visual y de
comunicación. Paquetes de visitas y beneficios multi-sucursal se mantienen
dentro de esa organización, no entre tenants.

El refinamiento V94 usa `inactivityMonths` como único umbral temporal para
inactividad y reinscripción. La configuración de kiosco separa la intención por
sucursal de la readiness de Stripe: planes y cotizaciones siguen visibles con
Stripe pendiente, las operaciones monetarias continúan bloqueadas y una sesión
reiniciada vuelve a resolver la configuración vigente del terminal.

El cierre visual de aceptación compacta la configuración de inscripción e
inactividad, renombra su pestaña a `Inscripciones`, extiende el autosave con
rollback a medios de cobro y a todos los valores de inscripción, muestra
dependencia de Stripe sólo en capacidades encendidas y mejora la elección entre
identificación y registro en el kiosco. Los mensajes de éxito del kiosco son
temporales y descartables.

La aceptación final centra el selector de entrada y mantiene los errores de
identificación dentro de la tarjeta con copy público, sin desplazar el
formulario ni exponer diagnóstico HTTP.

El corte posterior mantiene los listados compactos con scroll interno oculto y
controles de página visibles. Detecta backend/frontend incompatibles y separa
errores de listas vacías. Coaches exige periodos sin solape por socio/sucursal,
cierra asignaciones vencidas y usa fechas de México; Indicadores muestra series
semanales y evolución temporal por coach. Benefits permite editar los planes de
un acceso existente, y Equipo permite recuperar contraseñas de staff mediante
enlaces de un uso por ZeptoMail con revocación de sesiones anteriores. Reglas,
migraciones, límites y evidencia: [cierre operativo](../docs/operations/operational-hardening.md).

**Automatizaciones + correo transaccional mediante ZeptoMail quedan funcionalmente cerrados para el alcance V1**: reglas, eventos, prueba manual con socio real, permisos de Gerencia, cola durable, retries/dead letters, branding tenant y envío SMTP real están operativos. El 22 de agosto de 2026 se verificó `gymfort.mx` en ZeptoMail con DKIM y CNAME/return-path y se recibieron correos reales de prueba.

**El control plane cloud de acceso físico queda estabilizado para iniciar el
Connector**: modelo
provider-neutral, puntos con un derecho obligatorio, mapeos y vínculos externos,
estado deseado automático, jobs PostgreSQL con lease/reintento/idempotencia,
activación/rotación del Connector, heartbeat, eventos, candidatos y consola por
sucursal. El cierre añade protección distribuida de fallos en producción,
timestamp de autenticación con escritura acotada, proveedor explícito y
diagnóstico visible de puntos legacy inválidos. El GymFort Local Connector,
adapter HikCentral y apertura del torniquete real siguen pendientes de trabajo y
validación en sitio.

Estados usados: `FUNCIONAL`, `ESTABILIZADO`, `PARCIAL` y `PLANIFICADO`. Ningún módulo se considera completo por el nombre de una rama: la evidencia proviene de código, migraciones, pruebas y validación del entorno.

## 2. Estado por módulo

| Módulo | Estado | Implementado | Pendiente principal |
|---|---|---|---|
| [Organizaciones](modulos/01-organizations.md) | FUNCIONAL | Plataforma, tenants, sucursales, equipo, soporte y marca organizacional persistente | Marca distinta por sucursal (`CFG-003`) y matriz negativa (`ORG-001`) |
| [Identidad y seguridad](modulos/02-identity-security.md) | AUTH-001 EN REVISIÓN | Ámbitos PLATFORM/TENANT, bootstrap, JWT corto, familia revocable en Redis, refresh HttpOnly, soporte temporal e invitación y recuperación | Aceptación operativa de sesiones, TLS, Redis y rollback en staging. |
| [Configuración](modulos/03-configuration.md) | FUNCIONAL | Marca tenant, cobros, socios, kiosco con autosave y correo por permisos; UI unificada | Perfil de marca por sucursal (`CFG-003`) y ciclo de logo (`CFG-001`) |
| [Onboarding](modulos/04-onboarding.md) | FUNCIONAL | Checklist, setup piloto idempotente e importación inicial | Recuperación probada de altas parciales (`ONB-001`) |
| [Socios](modulos/05-clients.md) | FUNCIONAL | Alta, búsqueda, detalle, importación, inscripción, baja automática reversible y visibilidad multi-sucursal | PII, duplicados y pruebas CRUD (`CLI-001`) |
| [Catálogo](modulos/06-catalog.md) | ESTABILIZADO | Productos, planes de membresía, paquetes de visitas, categorías, unidades y asociación por sucursal | Volumen, legacy y archivos (`CAT-001`) |
| [Ventas](modulos/07-sales.md) | FUNCIONAL | POS, ventas contextualizadas, inscripción separada, compra dedicada de membresías/paquetes, pagos, recibos e inventario | Concurrencia/conciliación E2E y política de reembolso parcial (`SAL-001`) |
| [Membresías](modulos/08-memberships.md) | ESTABILIZADO | Periodos inclusivos, activa única, alta con inscripción, renovación/cambio multi-sucursal, visibilidad de kiosco y exención por plan | E2E financiero completo |
| [Beneficios/elegibilidad](modulos/09-benefits.md) | ESTABILIZADO | Descuentos por plan y acceso `LOCAL|PLAN_COVERAGE`, incluidas sedes cubiertas sin duplicar beneficios | Matriz PostgreSQL/E2E y mapeos físicos locales (`BEN-003`) |
| [Acceso](modulos/10-access.md) | ESTABILIZADO PARA INICIAR CONNECTOR | Elegibilidad, puntos, bitácora, Connector API y consumo atómico/idempotente de paquetes con prioridad de membresía | Aceptación concurrente/torniquete de paquetes (`ACC-005`) y Connector/HikCentral (`ACC-002`) |
| [Kiosco](modulos/11-selfservice.md) | FUNCIONAL / ACEPTACIÓN | Apertura sólo por Recepción, sesión aislada, registro, membresías, paquetes visibles con Stripe pendiente, Checkout protegido, domiciliación, reservas, wellness y datos | Publicar ejecución automatizada de Stripe sandbox y E2E de paquetes (`SELF-001`) |
| [Reservas](modulos/12-bookings.md) | FUNCIONAL | Recursos, disponibilidad, pago y acceso multi-sucursal | Colisiones/cancelación/E2E (`BKG-001`) |
| [Inventario](modulos/13-inventory.md) | ESTABILIZADO | Existencias, movimientos, alertas, transferencias y conteos paginados; valuación | Feedback por partida (`INV-002`) |
| [Coaches](modulos/14-coaching.md) | ESTABILIZADO | Perfiles, rutinas versionadas, asignación sin solapes, cierre por vencimiento, métricas temporales, CSV/PDF | Cleanup durable de medios (`COA-018`) |
| [Wellness](modulos/15-wellness.md) | ESTABILIZADO | Profesionales, servicios, disponibilidad, agenda y citas | E2E y hardening multi-tenant |
| [Automatizaciones](modulos/16-automations.md) | FUNCIONAL / CERRADO V1 | Reglas, inbox durable, leases, idempotencia, reintentos, actividad/problemas, prueba con socio real y permisos de Gerencia | Mantener CI; trigger de domiciliación activada es mejora futura |
| [Notificaciones](modulos/17-notifications.md) | FUNCIONAL / ZEPTOMAIL OPERATIVO | Cola durable, ZeptoMail real, remitente central, Reply-To tenant, branding/logo, prueba, preferencias y recuperación | Branding/Reply-To por sucursal (`CFG-003`) y bounce/complaint webhooks |
| [Reportes](modulos/18-reporting.md) | PARCIAL | Reportes por dominio y métricas coaches/operación | Definiciones KPI y filtros homogéneos (`REP-001`) |
| [Auditoría](modulos/19-audit.md) | FUNCIONAL | Registro de mutaciones sensibles y motivos operativos | Matriz/retención/inmutabilidad (`AUD-001`) |
| [Operación común](modulos/20-common-operations.md) | FUNCIONAL PARA LOCAL | Errores correlacionados, Flyway, métricas, Docker, CI y runbooks | Restore, staging y simulacros (`DB-001`, `OPS-001`) |
| [Pagos](modulos/21-payments.md) | FUNCIONAL / ACEPTACIÓN | Terminal manual/MP excluyentes, Stripe Connect/Checkout, webhooks, recibos y recurrencia | Sandbox CI y reembolso/cancelación por proveedor (`PAY-001`) |
| [Administración y cobranza comercial GymFort](modulos/22-platform-billing.md) | ESTABILIZADO / ADMINISTRACIÓN COMERCIAL MANUAL | Cartera y centro de acción, expediente por cliente, contrato por sucursal, MRR, pagos inmutables/idempotentes y control de concurrencia | Cargo SaaS automático, CFDI, ajustes y reportes financieros históricos (`BIL-001`) |

## 3. Decisiones operativas vigentes

- Venta y membresía conservan sucursal de origen; `multiBranchAccess=true` permite reconocimiento/acceso/reservas en otras sedes activas.
- Sólo Recepción recibe `selfservice.session.launch`.
- Gerencia conserva `payment.settings.manage` para modalidades ya vinculadas; cuenta Stripe exige `payment.provider.stripe.manage` o `platform.admin`.
- V80 concede a Gerencia `automation.read`, `automation.manage`, `automation.execute`, `notification.read` y `notification.manage`; no concede control avanzado del scheduler.
- Terminal manual y Mercado Pago Point son excluyentes.
- Configurar compra/renovación/cambio/domiciliación en kiosco no requiere que
  Stripe esté listo; ejecutar Checkout sí requiere self checkout ready y
  domiciliación requiere además recurring ready.
- Redirect de Stripe no confirma cobro: webhook firmado + consulta autoritativa producen efectos idempotentes.
- Automatizaciones no ejecuta lógica de dominio: consume eventos y genera notificaciones.
- `RECURRING_CHARGE_DUE` significa llegada de fecha de cobro recurrente; no activación inicial del mandato.
- La UI de Automatizaciones se divide en **Automatizaciones / Actividad / Problemas**; logs técnicos quedan fuera de la vista normal.
- Crear automatización se hace desde cero, sin plantillas recomendadas.
- `Probar ahora` para EMAIL usa búsqueda dinámica y un socio real con correo.
- Correo V1 usa preferentemente `Nombre del gimnasio <notificaciones@gymfort.mx>` + `Reply-To` propio del tenant.
- Branding de correo vigente usa la organización. El objetivo `CFG-003` resuelve
  nombre, colores, logo y Reply-To desde la sucursal que originó la operación,
  con fallback organizacional; logos locales no pueden cargarse desde clientes
  externos.
- Una conexión física V1 es única por sucursal y conserva su proveedor; un punto
  productivo requiere exactamente un beneficio `ACCESS` y un recurso externo
  explícito.
- Ninguna configuración nueva infiere `HIKCENTRAL`: proveedor y modo
  `SIMULATED` se eligen explícitamente.
- La sincronización física normal parte de eventos de membresía, socio y
  beneficio; los botones de reconciliación son recuperación, no operación diaria.
- `access.physical.manage` corresponde a Dueño/Gerente y
  `access.manual.open` protege por separado la apertura excepcional simulada.
- GymFort Cloud nunca recibe credenciales LAN ni material biométrico; el
  Connector usa activación/secretos hasheados y rutas máquina-a-máquina propias.
- Los fallos de autenticación/activación del Connector se limitan mediante Redis
  compartido en producción y memoria por proceso en local/tests, sin claves ni
  credenciales crudas; `last_authenticated_at` se escribe como máximo una vez
  por ventana configurable.
- `PLATFORM` nunca obtiene organización/sucursal persistente ni acceso implícito
  a endpoints tenant; el soporte se inicia para un tenant concreto, con motivo y
  vencimiento, y puede finalizarse explícitamente desde el shell.
- La creación de un gimnasio cliente es una sola transacción que incluye su
  primera sucursal y un dueño `TENANT` en estado `INVITED`; no se comparte una
  contraseña inicial y el token de activación sólo se devuelve una vez.
- Producción usa `GYMOS_SEED_ENABLED=false`; datos históricos con la firma exacta
  del fixture se clasifican `DEMO`, quedan fuera de cartera/cobranza y no se
  eliminan sin revisar actividad.

## 4. Validación del corte

Evidencia disponible:

- DKIM y CNAME/return-path de `gymfort.mx` verificados en ZeptoMail;
- entrega real a bandeja mediante ZeptoMail desde ambiente local;
- `From` central y `Reply-To` tenant validados;
- nueva plantilla HTML con branding tenant;
- prueba de regla email con selección de socio real;
- V80 corrige permisos de Gerencia para gestionar Automatizaciones;
- V79 repara perfiles Coach históricos desincronizados;
- pruebas unitarias de cobranza comercial, autorización de plataforma y
  migración PostgreSQL V82 ejecutadas correctamente;
- V85 añade cartera/expediente comercial, pagos idempotentes y concurrencia
  optimista; `clean test` pasó con 400 pruebas y `postgresIntegrationTest`
  completo validó PostgreSQL limpio, upgrade e invariantes de persistencia;
- frontend pasó 92/92 pruebas, typecheck, lint, build Next.js y bundle OpenNext
  para Cloudflare; el recorrido visual confirmó menú lateral, cartera, filtros,
  expediente y deep links de cobranza con datos representativos;
- V86/V87 pasaron migración PostgreSQL limpia y upgrade; las pruebas cubren
  ámbitos DB, JWT/refresh, bootstrap idempotente, invitación de dueño,
  autorización plataforma/tenant y límites del monolito modular;
- V88 retira sólo el fixture demo histórico exacto y vacío; 413 pruebas
  unitarias y 59 pruebas PostgreSQL cubren concurrencia/rollback del bootstrap,
  permiso efectivo `platform.admin`, instalación limpia, upgrade, conservación
  ante actividad y seed local explícito;
- CI incorpora `postgresIntegrationTest`, `cf:build` y bloqueo por advisories
  npm altos/críticos; build backend, imágenes Docker y Compose pasaron;
- frontend pasó 94/94 pruebas, typecheck, lint, build Next 16.3.2 y bundle
  OpenNext 1.20.2; `npm audit` quedó en 0 vulnerabilidades después de actualizar
  Next y transitivas parcheadas sin usar `--force`;
- suite backend completa, pruebas PostgreSQL V84, upgrade V83 → V84, seguridad
  HTTP, suite frontend (87/87), build de producción y build Docker ejecutados
  correctamente en la rama física;
- recorrido visual de Access en escritorio y móvil, incluidos los formularios de
  proveedor/puntos sin desbordamiento horizontal ni errores de consola;
- suites backend/frontend y CI de rama continúan siendo la red de regresión.
- V95 pasó migración PostgreSQL limpia y upgrade desde V94; la prueba conserva
  IDs de dos productos existentes, los agrupa y migra sus códigos principales;
- catálogo/POS pasaron pruebas unitarias, typecheck y lint; el E2E crea dos
  sabores en una sola operación y escanea un código alias secundario.

La carga real del logo en correo debe verificarse después del despliegue con `GYMOS_FRONTEND_BASE_URL=https://gymfort.mx`; localhost no es accesible desde Gmail/Outlook.

## 5. Bloque activo

Después de integrar variantes/códigos mediante PR #20, la rama
`feature/multibranch-plans-benefits-visits` implementa y valida **planes,
beneficios y paquetes de visitas multi-sucursal dentro del mismo tenant**:

1. producto vendible con cantidad de visitas y ventana de vigencia;
2. alcance a todas o a una selección de sucursales;
3. saldo único por socio, sin duplicarlo por sede;
4. consumo atómico e idempotente que registre la sucursal;
5. integración con ventas, acceso y reportes;
6. exclusividad de un solo paquete `PENDING_PAYMENT` o `ACTIVE` por socio;
7. planes privados de kiosco, exención de inscripción y precios especiales por
   plan;
8. alcance de paquete separado del alcance de cada acceso: el saldo puede cubrir
   A+B sin convertir un beneficio `LOCAL` de A en un beneficio de B.

V96–V101, los contratos, la compra dedicada, la activación por pago, el consumo
desde Access y las superficies de Catálogo/Recepción/kiosco están implementados.
V100 materializa el snapshot acceso–sucursal; la creación de un paquete exige
incluir la sucursal activa y que cada sede elegida tenga al menos un acceso
consumible. V101 separa el acceso que descuenta saldo del acceso incluido con
una visita base, snapshottea ese modo y audita el segundo sin decremento bajo la
regla mismo entitlement + fecha operativa + sucursal. El expediente ya expone historial y últimos consumos,
las escrituras `visit_pass` legacy se rechazan y un reembolso total automático
se bloquea cuando ya hubo consumos.
Los paquetes viven junto a los planes en Catálogo; Configuración sólo conserva
la intención por sucursal del kiosco. Cada plan de membresía también puede
ocultarse de nuevas altas/cambios del kiosco y quedar exento de inscripción.
El alta de paquetes presenta la sede operativa como sucursal base fija y sólo
permite agregar otras sedes. En el kiosco, planes y paquetes comparten una sola
cuadrícula comercial, sin días de gracia visibles y con acciones compactas.
La identificación de kiosco encuentra al socio en toda la organización y el
cambio entre identificación/registro reinicia la sesión para el siguiente
visitante; los fallos opcionales del dashboard ya no contradicen un alta válida.
El catálogo de accesos para configurar paquetes se resuelve a nivel organización:
permite ver desde Tecnológica los accesos premium locales de Centro sólo cuando
Centro forma parte del paquete, sin convertirlos en derechos de Tecnológica. Las
definiciones que pierden cobertura o dejan una sede sin acceso consumible se
excluyen de venta/consulta operativa hasta ser corregidas, mientras los snapshots
ya vendidos permanecen inmutables. El producto técnico `VISIT_PASS` requerido
para contabilizar la venta queda oculto del catálogo y POS genéricos.

La validación final del 29/08/2026 ejecutó 502/502 pruebas unitarias y 116/116
pruebas PostgreSQL reales de
migración, concurrencia, idempotencia, snapshots, tenant isolation y dos sedes,
sin fallos ni omisiones. La matriz Centro/Tecnológica agrega un paquete creado
desde Tecnológica con General local en ambas sedes y Sauna, Crioterapia y
Lockers premium exclusivamente en Centro; el saldo se comparte, pero los
accesos físicos no se expanden. El detalle reproducible está en
`34-matriz-pruebas-centro-tecnologica.md`.
Flyway también se ejecutó contra el volumen histórico local que estaba en V85:
un callback previo a validación reconoce únicamente los checksums exactos del
V84 piloto y del primer V102 de prueba, mientras el V102 final completa
idempotentemente las diferencias; V86–V103 se instalaron
sin `repair` manual, borrado ni pérdida de datos. V84 permanece igual a la
versión publicada en `main`. Otro callback acotado prepara el cambio de
nulabilidad previo a V86 en instalaciones históricas.
Branding por sucursal continúa después. El Connector HikCentral, cobranza SaaS
y hardening operativo permanecen como frentes separados.

El detalle vive en `modulos/`, la deuda única en el documento 28 y la situación Git en el documento 29.

## ACC-006 — Check-in manual de recepción

Rama de entrega basada en `470e1b4bff4cc98d5f030bc49fcfe8cdc9cb94f9`.
Incluye V105 y el recorrido de registro, historial y reversión en Recepción.
La aceptación del piloto depende de CI del SHA final y de la verificación
operativa. Evidencia y estado del gate en [#61](https://github.com/Dagt/GymOs/issues/61).

## SAL-001

SAL-001 incorpora bloqueos transaccionales y recuperación de ventas, pagos y webhooks. La aceptación operativa de cobros integrados sigue sujeta a Stripe test y a comprobar Point antes de su habilitación.

La corrección SAL-001 rechaza órdenes Point sin evidencia de moneda o importe pagado. La prueba HTTP con PostgreSQL usa respuestas simuladas de Point. Sigue pendiente la comprobación con proveedor conectado.

## Pulido de membresías — 10/11/2026

Rama `codex/reception-memberships-visual-polish`, base main `36b50d545eb3def2871b3c2211872f5329373b04` tras merge #73. Presentación de recepción con evidencia de escritorio, tablet y móvil; entrega sin merge ni despliegue. [Resultados locales y límites](../docs/testing/reception-memberships-polish.md).
