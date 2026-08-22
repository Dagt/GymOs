# Membresías

Estado: `ESTABILIZADO`
Última revisión: 22 de agosto de 2026

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

## Pendiente

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

La renovación rápida vive dentro de `/reception`. La entrada lateral y la
tarjeta que enviaban a `/reception/renewals` fueron retiradas; esa URL sólo
redirige al inicio para conservar marcadores antiguos. El bloque embebido
permite buscar socio, renovar el mismo plan, cambiar a otro plan activo o
contratar el inicial, siempre con cotización y registro de pago.

## Planes de visita diaria

La decisión vigente para visitas es reutilizar el motor de membresías en lugar
de desarrollar paquetes comerciales de N accesos.

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

Los paquetes históricos `VisitPass` dejan de ser el modelo comercial objetivo.
Su infraestructura puede mantenerse temporalmente hasta retirarla de manera
segura, pero no debe condicionar nuevas integraciones entre Catálogo, Ventas y
Accesos.
