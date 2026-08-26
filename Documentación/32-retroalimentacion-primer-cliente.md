# Retroalimentación del primer cliente

Estado: ANÁLISIS COMPLETO / LOTE 1 IMPLEMENTADO

Corte: 26 de agosto de 2026

Rama: `feature/client-onboarding-lifecycle`, basada en `main` `a5ea75f`

## Agrupación de trabajo

### Lote 1 — Alta y ciclo de vida del socio

Incluye inscripción, baja automática reversible, autenticación sin plan y alta
desde kiosco. Las cuatro necesidades comparten la identidad del socio y el
momento de su primera venta, por lo que se implementan juntas en V93.

- Configuración por organización para activar el cargo, definir importe,
  concepto y meses para volver a cobrarlo.
- La cotización autoritativa aplica descuentos únicamente al plan y agrega la
  inscripción como partida financiera separada.
- La inscripción se cobra en la primera membresía o cuando han transcurrido los
  meses configurados desde el fin de la última vigencia.
- Un socio sin membresía puede autenticarse en el kiosco de su sucursal de alta.
- El kiosco puede habilitar por sucursal el registro de un socio nuevo y llevarlo
  directamente a la contratación de su primer plan.
- Un worker marca como `INACTIVE` al socio sin membresía vigente después del
  plazo configurado. No se borra físicamente: ventas, pagos, membresías y
  auditoría deben conservarse. Sólo una baja automática puede reactivarse desde
  el kiosco; una baja manual requiere intervención del personal.

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

### Lote 3 — Paquetes de visitas entre marcas

Existe `visit_pass`, pero es manual, pertenece a una sola organización y una
sola sucursal, y `AccessService` lo consume con ese mismo alcance. Los planes
`multiBranchAccess` sólo cruzan sucursales del mismo tenant. Dos marcas
independientes deben mantenerse como organizaciones distintas para conservar
branding, configuración y aislamiento.

La solución requiere un grupo/portafolio propietario y un derecho comercial
compartido entre organizaciones: paquete de N visitas, ventana de vigencia,
sedes admitidas, ledger de consumos e idempotencia. También debe integrarse con
Catálogo, Ventas, Acceso y reportes. Reutilizar el `visit_pass` local sin ese
modelo rompería el aislamiento multi-tenant.

### Lote 4 — Beneficios multi-sucursal

Los beneficios son locales cuando `benefit.branch_id` tiene valor. La evaluación
de elegibilidad exige que el beneficio sea global (`branch_id IS NULL`) o que
coincida con la sucursal activa. Vincularlo a un plan multi-sucursal no cambia
ese alcance, por eso el socio hereda el beneficio sólo en la sede donde se creó.

La mejora debe hacer explícito el alcance al crear el beneficio: una sucursal,
todas las sucursales de la organización o una selección de sucursales. Para dos
marcas/organizaciones distintas debe apoyarse en el modelo de grupo del lote 3,
no duplicar filas silenciosamente.

Código afectado: `BenefitRepository`, `EligibilityRepository`, contratos y UI de
beneficios, además de las proyecciones de acceso.

### Lote 5 — Multibranding de comunicaciones

El branding vigente está en `organization`; las columnas de marca por sucursal
que aparecieron en V26 fueron retiradas en V27. `NotificationBrandRepository` y
`notification_channel_configuration` también resuelven una sola identidad y un
solo remitente por organización.

Para dos marcas independientes, el camino seguro inmediato es una organización
por marca. El modelo de grupo del lote 3 permitirá que compartan derechos sin
compartir identidad visual. Si una misma organización debiera operar varias
marcas, sería necesario agregar perfil de marca por sucursal y resolverlo en
correo, consola, kiosco, recibos y almacenamiento de logos.

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
