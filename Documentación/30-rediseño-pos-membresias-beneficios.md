# 30. Rediseño de POS, membresías y beneficios

Estado: IMPLEMENTADO / VALIDADO
Rama: `feature/reception-pos-init`
Fecha: 29 de julio de 2026

## Objetivo y decisiones

Catálogo seguirá siendo la experiencia unificada para administrar productos,
servicios y planes. Compartir catálogo y registro financiero no significa
compartir comportamiento:

- los planes permanecen visibles en Catálogo;
- los productos físicos conservan SKU, inventario y código de barras;
- los planes conservan duración, gracia y reglas de membresía;
- ambos generan ventas, pagos, movimientos de caja y métricas;
- cada venta declara contexto: `RETAIL_SALE`, `MEMBERSHIP_ENROLLMENT`,
  `MEMBERSHIP_RENEWAL` o `MEMBERSHIP_PLAN_CHANGE`;
- un plan seleccionado inicia una operación de membresía, no una línea minorista
  sin contexto.

## Membresía e historial

Un socio sólo puede tener una membresía operativa. Alta, renovación y cambio
crean periodos históricos independientes:

```text
membership
  client_id, current_plan_id, status, vigencia_actual, version

membership_period
  membership_id, plan_id, operation_type, vigencia, sale_id

sale + sale_item
  contexto, precio_lista, descuento, total, pago

membership_sale_benefit
  sale_id, benefit_id, código/nombre/descuento snapshot, orden
```

La renovación extiende la membresía sin sobrescribir su venta inicial. Cada
periodo usa fechas inclusivas: un plan de 30 días que inicia el 22 de julio
termina el 20 de agosto. Si la vigencia anterior aún no termina, el siguiente
periodo empieza al día siguiente; si ya terminó, reinicia en la fecha operativa
de México. Al terminar la gracia, el estado pasa a `EXPIRED` mediante
reconciliación programada y también antes de consultas u operaciones críticas.

## Beneficios de precio

Los descuentos asignados al socio se resuelven en un cotizador de backend. Éste
valida vigencia, evidencia, sucursal, prioridad y combinación, y devuelve precio
de lista, beneficios aplicados, descuento y precio final. V55 conserva por venta
el código, nombre, descuento y orden de cada beneficio aplicado, de modo que una
edición posterior no reescribe la historia. El navegador nunca determina el
precio autorizado.

## Beneficios de acceso

El beneficio `ACCESS` es dueño de la relación muchos-a-muchos:

```text
benefit ACCESS
  └── benefit_membership_plan
        ├── plan A
        └── plan B
```

Los accesos efectivos se derivan dinámicamente:

```text
socio → membresía vigente → plan → beneficios ACCESS → puntos físicos
```

No se copian beneficios a cada socio. Agregar un sauna o cambiar las relaciones
del beneficio actualiza inmediatamente el resultado; cambiar o vencer el plan
también.

## Implementación por fases

1. **IMPLEMENTADA:** relación persistente beneficio–plan y contrato `planIds`.
2. **IMPLEMENTADA:** consulta de accesos efectivos y asociación
   dispositivo–beneficio. Un dispositivo sin asociación conserva acceso general;
   uno restringido exige al menos un beneficio efectivo.
3. **IMPLEMENTADA:** cotizador de alta, renovación y cambio. Los tres flujos
   registran precio de lista, descuento y total en Sales. Las llamadas existentes
   de alta inicial usan el endpoint específico sin modificar el diseño frontend.
4. **IMPLEMENTADA CON MIGRACIÓN:** `membership_period`, backfill, reconciliación
   de activas duplicadas, restricción única, bloqueo de operaciones y eventos de
   renovación. La ficha del socio consulta y presenta cada periodo con su venta.
5. **IMPLEMENTADA:** el carrito genérico rechaza planes tanto en frontend como
   en backend; contratación, renovación y cambio usan operaciones específicas.
   Catálogo conserva sus planes y su diseño.
6. **IMPLEMENTADA:** fechas inclusivas, expiración al terminar la gracia,
   renovación de membresías activas o vencidas y cambio de plan sólo para una
   membresía activa. El bloqueo se separó de la lectura enriquecida para releer
   correctamente el estado confirmado después de una renovación concurrente.
7. **IMPLEMENTADA:** snapshot auditable de beneficios por venta, detalle de
   beneficios aplicados y desglose de reportes por `businessContext`.
8. **IMPLEMENTADA:** inserción idempotente atómica de ventas de membresía. Dos
   solicitudes simultáneas con la misma llave producen exactamente una venta,
   una partida, un pago y un conjunto de snapshots.
9. **FUERA DE ALCANCE:** la asociación lógica dispositivo–beneficio queda
   preparada para uso futuro. Adaptadores de proveedor, operación offline y
   hardware físico no forman parte de esta rama ni de sus criterios de cierre.

## Operación del POS

- La búsqueda paginada conserva la cuadrícula actual de productos.
- El lector intenta primero una coincidencia exacta en la página cargada y,
  si no existe, consulta `GET /catalog/products/by-barcode/{barcode}`. Así puede
  cobrar productos activos que no estén dentro de los primeros 50 resultados.
- Un `MEMBERSHIP_PLAN` nunca entra al carrito minorista. `POST /sales` responde
  `SALE_MEMBERSHIP_OPERATION_REQUIRED` si un cliente intenta omitir el flujo
  específico.
- El escaneo rechazado de un plan emite retroalimentación de error y no se
  reporta como lectura exitosa.

## Reglas del cotizador implementado

- Sólo considera beneficios asignados y vigentes del mismo tenant.
- Si el beneficio requiere evidencia, exige estado `VERIFIED`.
- Los beneficios combinables se aplican por prioridad.
- Los no combinables compiten como alternativas exclusivas.
- El servidor elige el resultado válido de menor total.
- `PERCENT`, `FIXED` y `SPECIAL_PRICE` se registran como descuento financiero
  explícito; el pago usa el total neto.
- `ACCESS` no admite descuento ni asignación manual a un socio: siempre se
  hereda dinámicamente desde el plan.
- Una asignación de descuento a socio exige fecha de término para no conservar
  elegibilidad indefinida por accidente.

## Evidencia automatizada

- PostgreSQL real serializa dos renovaciones simultáneas y crea periodos
  consecutivos sin perder días.
- PostgreSQL real demuestra expiración, liberación de la restricción de una sola
  membresía activa, herencia/revocación inmediata de `ACCESS`, filtros de
  elegibilidad y persistencia inmutable del snapshot.
- Dos altas financieras concurrentes con igual llave idempotente producen una
  sola venta y un solo pago.
- Las pruebas frontend cubren coincidencia local, fallback exacto al backend y
  rechazo funcional de planes en el carrito minorista.

## Criterios globales

- Crear un ACCESS con planes persiste y devuelve la relación.
- Agregar o quitar un plan cambia inmediatamente los accesos efectivos.
- Toda operación de membresía aparece en ventas, pagos y corte de caja.
- Dos renovaciones concurrentes no pierden vigencia ni duplican membresía.
- Un plan sigue visible en Catálogo y nunca se procesa como inventario.
- Organización, sucursal, rol e idempotencia tienen pruebas positivas y negativas.

## Validación del corte

- Backend enfocado de membresías, beneficios, acceso lógico, ventas y migraciones:
  exitoso, incluido PostgreSQL 18 real.
- Suite backend completa: todas las pruebas salvo una falla arquitectónica
  preexistente y ajena a este alcance en los lookups PDF de Coaching que
  dependen directamente de `JdbcClient`; el hardening final se revalidó con la
  suite enfocada exitosa.
- Frontend: 54 pruebas exitosas, typecheck y build de producción exitosos.
- Lint sin errores; conserva dos advertencias preexistentes de dependencias de
  hooks en `AppShell.tsx`.
- `git diff --check`: exitoso.
