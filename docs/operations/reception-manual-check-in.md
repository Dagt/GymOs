
# Registro presencial de entradas en Recepción

## Operación diaria

1. Selecciona la sucursal donde está el socio y abre **Recepción → Registrar entrada**.
2. Busca por nombre, teléfono o identificador del socio. Elige el resultado verificando el nombre y el teléfono.
3. Revisa membresía, gracia si aplica, paquete, saldo y cobertura. Si hay varios derechos de acceso, elige el que corresponde a la entrada. Con un solo derecho, la pantalla lo selecciona.
4. Confirma la entrada. Si la consulta devuelve cero derechos aplicables, la única acción disponible es registrar el rechazo. Si el derecho deja de estar disponible antes de confirmar, se muestra la decisión del servidor y se actualiza la consulta.
5. Si se pierde la respuesta, pulsa **Reintentar entrada**. La pestaña conserva la misma operación al recargar y la consulta nuevamente con la clave original.

**Entradas recientes** muestra hasta 30 registros de la sucursal en su zona horaria. Actualiza la lista si otra persona está atendiendo en paralelo. Las entradas rechazadas, repetidas y revertidas quedan visibles.

## Derechos y saldo

Se evalúa el beneficio `ACCESS` elegido en la fecha operativa de la sucursal. La membresía que concede ese derecho tiene prioridad y conserva el saldo del paquete. Si la membresía no lo concede, un paquete vigente con cobertura puede autorizarlo cuando su snapshot define `CONSUMES_VISIT`. Recepción muestra los beneficios `INCLUDED_WITH_VISIT` de la sucursal cuando el servidor ofrece una opción de entrada con ese paquete. Los paquetes vencidos o agotados conservan su estado y saldo visibles, y una membresía vigente puede seguir autorizando el acceso. El socio debe tener estado activo.

## Duplicados y correcciones

- Una entrada concedida y sin reversión activa una ventana de 60 segundos por socio y sucursal. Otro intento dentro de esa ventana queda marcado como duplicado y señala la entrada anterior.
- Si se trata de dos entradas distintas dentro del minuto, Dueño o Gerente puede confirmar la segunda con **Es una entrada distinta** y un motivo. Se vuelve a evaluar el derecho y el saldo. Si la ventana terminó o cambió la entrada previa, consulta de nuevo al socio.
- Dueño o Gerente puede usar **Revertir entrada** en una entrada autorizada. Es obligatorio indicar el motivo. Cuando hubo consumo, el ledger conserva el consumo original como revertido y la corrección acredita una unidad al paquete original.
- El crédito a un paquete vencido conserva el estado vencido. Si la devolución reabriría un paquete y ya existe otro activo o pendiente, el sistema rechaza la corrección completa. Resuelve primero el paquete abierto mediante su flujo comercial autorizado. La entrada original y ambos saldos permanecen disponibles para investigar el caso.
- El check-in y su reversión son eventos operativos. Los reportes de ventas y pagos siguen leyendo sus propios movimientos. La bitácora de **Accesos** identifica la fuente manual y marca las reversiones.

## Instalación y rollback

Flyway aplica V105 sobre V103. La migración agrega datos y permisos de check-in. Ante una retirada de la versión, vuelve a la aplicación anterior manteniendo V105 y sus registros. Cualquier restauración de base sigue el [runbook de restauración](restore.md) y requiere reconciliar los check-ins registrados después del respaldo.
