# Membresías

Estado: `ESTABILIZADO`
Última revisión: 26 de agosto de 2026

## Implementado

- alta, activación, renovación y cambio de plan;
- vínculo con ventas y pagos;
- Checkout para alta, renovación y cambio; ningún efecto se aplica antes del
  pago confirmado;
- la membresía conserva sucursal de origen aunque un plan multi-sucursal
  habilite uso en otras sedes;
- vigencia, estado y operación en kiosco;
- planes multi-sucursal y restricciones de reservas;
- herencia dinámica de beneficios `ACCESS` desde el plan vigente mediante la
  política transversal de elegibilidad.
- cotización de alta con desglose autoritativo entre plan, inscripción,
  descuentos y total; la inscripción sólo aplica cuando la política de ciclo de
  vida del socio determina que corresponde mediante el único umbral
  `inactivityMonths`.

## Pendiente

- cerrar E2E/aceptación de paquetes de N visitas y consumo en dos sedes
  (`ACC-005`);
- completar la matriz física/E2E de beneficios `PLAN_COVERAGE` (`BEN-003`);
- E2E HTTP/navegador con pagos rechazados y validación posterior;
- parametrizar zona operativa por tenant si GymFort sale del alcance México;
- optimizar el alta rápida de un cliente nuevo desde recepción antes de contratar
  un plan de visita diaria.

## Estado vigente de los periodos y operaciones

El catálogo visual sigue incluyendo planes y sus operaciones generan ventas,
pagos, métricas y movimientos de caja. V54 incorpora `membership_period`,
backfill, reconciliación determinista de activas duplicadas, índice único por
socio y bloqueo pesimista para renovación/cambio. El cotizador backend ya
calcula beneficios y Sales conserva subtotal, descuento y total. La ficha del
socio muestra sólo la membresía operativa actual; los periodos y sus ventas
viven en el Timeline de actividad para no presentar una membresía cancelada
como si fuera otro plan actual. El carrito minorista ya no acepta planes. El
diseño del catálogo permanece sin cambios.

La duración usa extremos inclusivos (`durationDays - 1`), la renovación activa
continúa al día siguiente y la vencida reinicia en la fecha operativa. Un worker
y una reconciliación previa a lectura/mutación cambian a `EXPIRED` después de la
gracia. La prueba PostgreSQL simultánea validó el bloqueo y descubrió/corrigió
que el lock no debía unir la venta mutable antes de releer el agregado.

La ficha usa los permisos reales `membership.renew`,
`membership.change-plan` y `sale.create`; se retiraron comprobaciones frontend
contra los códigos inexistentes `membership.update` y `membership.create`.
Renovar y cambiar plan abren un drawer en la propia ficha con cotización y
pago. El cambio sólo admite una membresía activa: cancela el periodo actual,
crea una membresía del nuevo plan desde la fecha operativa, no prorratea ni
transfiere días pendientes y actualiza inmediatamente los accesos heredados.

La primera membresía cobra inscripción cuando está habilitada. En altas
posteriores sólo vuelve a cobrarla cuando
`latestMembershipGraceEnd + inactivityMonths <= effectiveDate`; desactivar la
baja automática no altera esta regla.

El dashboard `/reception` enlaza a «Membresías y paquetes» en `/reception/memberships`.
También se puede abrir desde el menú lateral. La pantalla busca al socio,
muestra la membresía y el saldo del paquete, y cotiza las operaciones
habilitadas por los permisos de la sesión. La ficha y recepción comparten
el formulario de venta de paquetes. Los marcadores de `/reception/renewals`
redirigen al nuevo espacio. El API de paquetes de mostrador admite efectivo,
transferencia, tarjeta manual y Point. Si Point falla después de registrar
la venta, recepción recupera el envío con el identificador del pago existente.
La operación sigue pendiente hasta que el servidor confirma o cierra el pago.

## Planes de visita diaria y paquetes

La decisión vigente para visitas individuales es reutilizar el motor de
membresías. Los paquetes comerciales de N accesos son una capacidad adicional
implementada y no sustituyen ese flujo.

Una visita normal es un `membership_plan` con `durationDays = 1`. Al usar fechas
inclusivas, una contratación para la fecha operativa genera:

```text
startsOn = fecha operativa
endsOn   = fecha operativa
```

No representa 24 horas móviles desde la hora del cobro.

Ejemplos esperados:

```text
Visita diaria
  duración: 1 día
  beneficios: GYM_ACCESS

Visita diaria Premium
  duración: 1 día
  beneficios: GYM_ACCESS + SAUNA_ACCESS
```

La persona que llega por primera vez debe crearse como `Client` mediante un alta
rápida y después contratar el plan diario con el mismo flujo financiero de una
membresía normal. No existe una entidad de dominio especial `Visitor`.

Este diseño permite que Access trate de forma idéntica una mensualidad, una
anualidad o una visita diaria: sólo importa que exista una membresía vigente y
qué beneficios `ACCESS` hereda de su plan.

Los paquetes históricos `VisitPass` no cubren el objetivo confirmado porque su
saldo está limitado a una sucursal. El modelo vigente vende N visitas con
vigencia y alcance de sedes dentro del mismo tenant; el socio tendrá un solo
saldo y cada consumo registrará la sucursal. Su infraestructura puede mantenerse
temporalmente, pero no debe condicionar el nuevo contrato entre Catálogo,
Ventas y Acceso.

V96 inicia ese contrato sin convertir paquetes en membresías. Cada plan agrega
`selfServiceAcquisitionEnabled` y `registrationFeePolicy`; una membresía
multi-sucursal se muestra y puede renovarse desde una sede cubierta. Un socio
puede tener una membresía y un paquete, pero no dos paquetes abiertos. Si la
membresía concede el acceso, no se consume el paquete.

El paquete no altera el alcance de los beneficios de la membresía. Su saldo
puede cubrir varias sucursales, pero cada acceso conserva `LOCAL` o
`PLAN_COVERAGE`; V100 materializa esa intersección para cada compra.

Estas políticas sí aplican a los planes de membresía, no sólo a los paquetes:
un plan oculto queda fuera de altas/cambios del kiosco pero se vende en
Recepción y se renueva si el socio ya lo posee; `WAIVED` omite inscripción en
alta, renovación o cambio cuyo plan objetivo tenga esa política.

## Registro manual en recepción

ACC-006 usa `EligibilityPolicy` con el beneficio `ACCESS` elegido y la fecha
de la sucursal. Se respeta la gracia vigente y se registra el ID de la membresía
que concede ese derecho. La membresía tiene prioridad sobre el paquete para el
mismo beneficio. La reversión de una entrada por membresía conserva la vigencia
y registra la corrección operativa. Ver [Access](10-access.md).
