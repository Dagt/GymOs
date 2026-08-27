# Retroalimentación del primer cliente

Estado: ANÁLISIS COMPLETO / LOTE 1 IMPLEMENTADO

Corte: 26 de agosto de 2026

Rama: `feature/client-onboarding-lifecycle`, basada en `main` `a5ea75f`

## Topología operativa confirmada

El cliente tendrá **un solo tenant/organización con dos sucursales**. Aunque las
sucursales operan con nombres, logos y colores de marcas distintas, comparten el
catálogo, los socios, los planes y los beneficios del tenant. Ninguna de estas
necesidades requiere compartir datos o derechos entre organizaciones.

## Agrupación de trabajo

### Lote 1 — Alta y ciclo de vida del socio

Incluye inscripción, baja automática reversible, autenticación sin plan y alta
desde kiosco. Las cuatro necesidades comparten la identidad del socio y el
momento de su primera venta, por lo que se implementan juntas en V93 y se
refinan de forma compatible mediante V94.

- Configuración por organización para activar el cargo, definir importe y
  concepto. `inactivityMonths` es el único umbral temporal para determinar
  periodo de inactividad y reinscripción.
- La cotización autoritativa aplica descuentos únicamente al plan y agrega la
  inscripción como partida financiera separada.
- La inscripción se cobra en la primera membresía o cuando han transcurrido los
  meses de inactividad desde el fin de gracia de la última vigencia, aunque la
  baja automática esté desactivada.
- Un socio sin membresía puede autenticarse en el kiosco de su sucursal de alta.
- El kiosco puede habilitar por sucursal el registro de un socio nuevo y llevarlo
  directamente a la contratación de su primer plan.
- Un worker marca como `INACTIVE` al socio sin membresía vigente después del
  plazo configurado. No se borra físicamente: ventas, pagos, membresías y
  auditoría deben conservarse. Sólo una baja automática puede reactivarse desde
  el kiosco; una baja manual requiere intervención del personal.

Refinamiento de aceptación del kiosco:

- `allowed_actions_json` representa lo que el gimnasio desea ofrecer por
  sucursal. Stripe readiness sólo representa disponibilidad técnica.
- Una capacidad monetaria puede quedar configurada con Stripe pendiente; los
  planes y cotizaciones son visibles, pero Checkout permanece bloqueado.
- Los toggles administrativos usan autosave serializado, muestran estado y
  revierten la UI cuando falla el backend.
- Una sesión nueva o reiniciada vuelve a resolver la configuración vigente del
  terminal. Por ello `clientRegistration=true` produce
  `clientRegistrationAllowed=true` y muestra `Quiero registrarme` sin depender
  de Stripe, planes o membresías.
- Un socio sin plan siempre distingue entre operación disponible, Stripe
  pendiente, contratación deshabilitada y catálogo vacío.

### Lote 2 — Variantes comerciales y códigos de barras

Hoy `catalog_product` contiene un solo `barcode`, `brand` y `presentation`, y la
unicidad del código se valida por organización. Por ello cada sabor se registra
como producto independiente. La mejora correcta es separar producto base y
variante vendible: por ejemplo, `Psychotic` como familia y sabor/tamaño como
variantes, cada una con SKU, código(s), precio e inventario propios. Debe incluir
alta masiva de variantes y alias de código de barras; no pertenece al ciclo de
vida de socios.

Código afectado: módulo `catalog`, `CatalogProductService`, persistencia de
`catalog_product`, drawer de catálogo, POS e inventario.

### Lote 3 — Paquetes de visitas entre sucursales del mismo tenant

Existe `visit_pass`, pero es manual y queda ligado a una sola sucursal;
`AccessService` lo consume con ese mismo alcance. Esto impide vender un paquete
de N visitas que el socio pueda consumir indistintamente en cualquiera de las
dos sucursales de la organización.

La solución deseada permanece dentro del tenant: un producto/plan de visitas
con cantidad total, ventana de vigencia y alcance de sucursales (`todas` o una
selección). El socio conserva un solo saldo y cada consumo registra en qué
sucursal ocurrió, con atomicidad e idempotencia. Debe integrarse con Catálogo,
Ventas, Acceso y reportes sin duplicar el paquete por sucursal.

### Lote 4 — Beneficios multi-sucursal

Los beneficios son locales cuando `benefit.branch_id` tiene valor. La evaluación
de elegibilidad exige que el beneficio sea global (`branch_id IS NULL`) o que
coincida con la sucursal activa. Vincularlo a un plan multi-sucursal no cambia
ese alcance, por eso el socio hereda el beneficio sólo en la sede donde se creó.

El comportamiento deseado es que un beneficio ligado a un plan
`multiBranchAccess=true` sea heredable en todas las sucursales donde ese plan es
válido, aunque el beneficio se haya creado desde una sucursal concreta. No debe
copiarse una fila por sede. La definición debe distinguir entre un beneficio
del plan con alcance multi-sucursal y un beneficio deliberadamente local; la UI
debe mostrar y validar ese alcance de forma explícita.

Código afectado: `BenefitRepository`, `EligibilityRepository`, contratos y UI de
beneficios, además de las proyecciones de acceso.

### Lote 5 — Multibranding por sucursal

El branding vigente está en `organization`; las columnas de marca por sucursal
que aparecieron en V26 fueron retiradas en V27. `NotificationBrandRepository` y
`notification_channel_configuration` también resuelven una sola identidad y un
solo remitente por organización.

El comportamiento deseado es mantener una sola organización y agregar un perfil
de marca por sucursal: nombre visible, logo, color principal, acento, superficie
y configuración de comunicación aplicable. La marca de la organización funciona
como fallback corporativo. La resolución debe partir siempre de la sucursal de
la operación y aplicarse en consola, recepción, kiosco, correos, recibos, PDFs y
almacenamiento de logos. Un usuario que cambia de sucursal debe ver la marca de
la sucursal seleccionada sin cambiar de tenant.

## Decisiones de seguridad y datos

- La “eliminación automática” se implementa como baja reversible, no como
  `DELETE` ni `deleted_at`: los documentos financieros y la auditoría conservan
  su relación con el socio.
- El registro público sólo existe dentro de una sesión temporal abierta por
  Recepción y con la capacidad `clientRegistration` habilitada.
- Teléfono/correo más fecha de nacimiento siguen siendo necesarios para volver
  a autenticarse; los límites por IP y sesión también aplican al alta.
- La inscripción forma parte del total conciliado por Stripe y de la llave de
  idempotencia, evitando que reintentos alteren el importe de la operación.
- V93 no se modifica. V94 elimina `registration_recharge_months` y conserva el
  valor ya almacenado en `inactivity_months` como canónico.
