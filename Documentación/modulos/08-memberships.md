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

- paquetes de N visitas con vigencia y saldo único consumible en todas o en una
  selección de sucursales del mismo tenant (`ACC-005`);
- garantizar que un beneficio ligado a un plan multi-sucursal se herede en
  todas las sucursales cubiertas por el plan, salvo alcance local explícito
  (`BEN-003`);
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

La renovación rápida vive dentro de `/reception`. La entrada lateral y la
tarjeta que enviaban a `/reception/renewals` fueron retiradas; esa URL sólo
redirige al inicio para conservar marcadores antiguos. El bloque embebido
permite buscar socio, renovar el mismo plan, cambiar a otro plan activo o
contratar el inicial, siempre con cotización y registro de pago.

## Planes de visita diaria y paquetes

La decisión vigente para visitas individuales es reutilizar el motor de
membresías. Los paquetes comerciales de N accesos son una capacidad adicional
pendiente y no sustituyen ese flujo.

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
saldo está limitado a una sucursal. El modelo futuro debe vender N visitas con
vigencia y alcance de sedes dentro del mismo tenant; el socio tendrá un solo
saldo y cada consumo registrará la sucursal. Su infraestructura puede mantenerse
temporalmente, pero no debe condicionar el nuevo contrato entre Catálogo,
Ventas y Acceso.
