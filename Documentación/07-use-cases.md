# 7. Casos de Uso

## UC-000 Registrar organización y sucursal inicial

Actor: Administrador de plataforma.

Precondiciones:
- Usuario autenticado con permiso `platform.organization.create`.

Flujo:
1. Capturar datos comerciales de la organización.
2. Crear Organization.
3. Capturar datos de la sucursal inicial.
4. Crear Branch.
5. Crear o asignar usuario administrador de organización.
6. Asignar permisos iniciales.
7. Emitir OrganizationRegistered y BranchRegistered.
8. Auditar la operación.

Errores:
- Organización duplicada por identificador fiscal.
- Código de sucursal duplicado dentro de la organización.
- Datos comerciales inválidos.

Criterios:
- La organización queda lista para operar.
- El usuario administrador puede iniciar sesión.
- Toda operación posterior queda aislada por organización y sucursal.

## UC-001 Registrar cliente

Actor: Recepcionista o administrador.

Precondiciones:
- Usuario autenticado.
- Permiso `client.create`.

Flujo:
1. Capturar nombre, apellidos, fecha de nacimiento, teléfono, correo y foto.
2. Validar formato.
3. Buscar coincidencias.
4. Crear Person.
5. Crear Client.
6. Emitir PersonRegistered y ClientRegistered.
7. Mostrar ficha.

Errores:
- Duplicado por correo o teléfono.
- Datos inválidos.
- Error al almacenar fotografía.

Criterios:
- No se crean duplicados.
- La operación queda auditada.
- El alta tarda menos de un minuto en flujo normal.

## UC-002 Registrar visita

Flujo:
1. Buscar persona.
2. Si no existe, registrar flujo corto.
3. Crear o reutilizar Client.
4. Agregar producto VisitPass.
5. Cobrar.
6. Habilitar acceso según reglas.

## UC-003 Crear venta

1. Crear Sale en Draft.
2. Seleccionar cliente opcional u obligatorio según producto.
3. Agregar ítems.
4. Evaluar beneficios.
5. Calcular totales.
6. Seleccionar método de pago.
7. Confirmar.
8. Emitir PaymentCompleted.
9. Ejecutar efectos por tipo.

## UC-004 Renovar membresía

1. Buscar cliente.
2. Consultar membresía vigente.
3. Seleccionar plan y duración.
4. Crear venta.
5. Cobrar 100%.
6. Extender desde fecha final vigente.
7. Emitir MembershipRenewed.
8. Actualizar acceso.
9. Enviar recibo.

Criterios:
- Renovar antes del vencimiento no pierde días.
- No hay activación con pago incompleto.

## UC-005 Validar transferencia

1. Cliente envía comprobante dentro del sistema.
2. Payment queda UnderReview.
3. Recepción revisa.
4. Aprueba o rechaza.
5. Si aprueba, PaymentCompleted.
6. Si rechaza, se guarda motivo.

## UC-006 Venta de suplemento

1. Seleccionar producto.
2. Indicar cantidad.
3. Cobrar.
4. Si inventario está habilitado, descontar.
5. Registrar movimiento.
6. Alertar si queda bajo mínimo.

## UC-007 Venta de scoop o dosis

1. Seleccionar producto tipo ConsumableService.
2. Cobrar.
3. Registrar ingreso.
4. No descontar gramos exactos salvo configuración futura.

## UC-008 Crear reserva

1. Seleccionar recurso.
2. Consultar disponibilidad.
3. Validar capacidad y elegibilidad.
4. Seleccionar horario.
5. Crear Booking en estado `RESERVED`.
6. Confirmar.
7. Hacer check-in.
8. Completar.
9. Cobrar si aplica en una iteracion posterior.
10. Notificar en una iteracion posterior.

## UC-008B Administrar recursos reservables

Actor: Admin GymOS en soporte o dueno del gimnasio.

Precondiciones:
- Usuario autenticado con permiso `resource.manage`.
- Sucursal activa.

Flujo:
1. Usuario abre la agenda de recursos y reservas.
2. Sistema muestra recursos activos por sucursal.
3. Usuario crea o edita nombre, capacidad, duracion, horario, notas y planes permitidos.
4. Sistema valida que la duracion y horarios sean consistentes.
5. Sistema guarda el recurso y actualiza restricciones de planes.
6. Para eliminar, sistema solicita confirmacion y valida que no existan reservas activas.

Criterios:
- Gerencia y recepcion pueden reservar, pero no administrar recursos.
- Un recurso con reservas activas no se elimina; se informa `RESOURCE_HAS_ACTIVE_BOOKINGS`.
- La eliminacion de recurso es logica para conservar auditoria e historial.

## UC-009 Cancelar reserva

1. Verificar estado.
2. Validar política de cancelación.
3. Cancelar.
4. Liberar horario.
5. Gestionar reembolso o penalización.
6. Notificar.

## UC-010 Solicitar acceso

1. Dispositivo envía identidad o referencia biométrica.
2. Resolver Client.
3. Consultar membresía.
4. Evaluar políticas.
5. Conceder o denegar.
6. Registrar intento.
7. Emitir evento.

## UC-011 Apertura manual

1. Recepcionista selecciona cliente o motivo.
2. Sistema exige permiso.
3. Registrar motivo.
4. Enviar comando al proveedor.
5. Auditar.

## UC-012 Acceso temporal

1. Crear autorización temporal.
2. Definir duración o número de accesos.
3. Registrar responsable.
4. Consumir al usarse.
5. Auditar.

## UC-013 Crear rutina

Actor principal: Coach activo con sucursal habilitada.

1. Crea o duplica una plantilla y define título, objetivo, nivel y sesiones.
2. Busca ejercicios por nombre, alias, músculo, patrón o equipo.
3. Si falta una opción, la crea inline sin perder el borrador; el sistema advierte duplicados.
4. Ordena bloques y ejercicios y captura prescripción estructurada o libre.
5. Puede agrupar superseries/circuitos, alternativas, opcionales, calentamiento,
   movilidad, cardio, core y estiramiento.
6. Guarda el borrador y publica una versión inmutable.
7. Selecciona un socio activo dentro de su alcance y asigna esa versión.
8. El sistema registra una sola asignación idempotente y actualiza la proyección semanal.
9. El coach descarga el PDF para entrega física.

Postcondiciones:

- dueño y gerencia consultan coach, fecha, socio, rutina y versión;
- la asignación conserva su contenido aunque se publique otra versión;
- no se registra venta, pago, comisión, ingreso ni movimiento de caja.

Alternativas: una versión en borrador no se asigna; un coach/socio fuera de alcance
se rechaza; un reemplazo conserva el histórico; un reintento devuelve el resultado
original sin duplicar la métrica.

## UC-014 Agendar o Solicitar Citas de Bienestar

1. Seleccionar o buscar socio (`clientId`) mediante búsqueda dinámica.
2. Seleccionar profesional especialista (se identifica si es De Planta `STAFF` con consultorio e historia semanal, o Externo `EXTERNAL`).
3. El servicio de consulta asociado se selecciona automáticamente según la especialidad (Nutrición, Fisioterapia, Quiropráctica, Bienestar).
4. Para profesionales De Planta: Consultar slots de disponibilidad dinámicos calculados a partir de su horario semanal y citas activas sin traslapes. La cita se registra en estado **`CONFIRMED`** de origen.
5. Para profesionales Externos: La solicitud se registra en estado **`REQUESTED`** y habilita el Modal de Coordinación Externa para que Recepción o el profesional definan la fecha y hora finales acordadas para pasar a **`CONFIRMED`**.
6. Al concluir la sesión, la Recepción o el especialista marcan la cita como **`COMPLETED`**.
7. En caso de inasistencia o cancelación, se registra la cita como **`CANCELLED`** o **`REJECTED`**.
8. Si un profesional es eliminado por el Gerente/Admin (`DELETE /professionals/{id}`), el sistema ejecuta un borrado en cascada suave de sus perfiles, horarios, servicios y todas sus citas activas.

## UC-015 Automatización de vencimiento

1. Evento MembershipExpiring.
2. Evaluar regla.
3. Crear notificación.
4. Enviar.
5. Registrar resultado.
6. Reintentar según política.

## UC-016 Dashboard del dueño

Debe mostrar:
- ventas del día
- ventas del mes
- renovaciones
- nuevas membresías
- clientes por vencer
- inventario bajo
- reservas del día
- accesos por hora

## UC-017 Autoservicio de cliente en kiosco

Actor: Cliente.

Precondiciones:
- Kiosco registrado y activo en una sucursal.
- Cliente existente.
- Acciones de autoservicio habilitadas por la organización.

Flujo:
1. Cliente inicia sesión temporal en el kiosco.
2. Sistema verifica identidad mediante mecanismo configurado.
3. Sistema muestra resumen limitado: estado, membresía vigente, vencimiento y pagos pendientes.
4. Cliente selecciona renovar, cambiar membresía, iniciar pago o actualizar datos permitidos.
5. Backend valida elegibilidad, reglas de membresía, beneficios y montos.
6. Si hay pago, se crea venta o pago según corresponda.
7. Si el pago requiere validación manual, queda pendiente o en revisión.
8. Si el pago queda completado, se ejecutan efectos de negocio.
9. Sistema emite eventos correspondientes.
10. Sistema audita acción con origen `SELF_SERVICE_KIOSK`.
11. Sesión termina manualmente o por inactividad.

Errores:
- Sesión expirada.
- Cliente no verificado.
- Acción no permitida para autoservicio.
- Membresía no elegible para cambio.
- Pago pendiente de validación.

Criterios:
- El kiosco no muestra información administrativa.
- No se activa ni renueva membresía sin pago completo.
- Las reglas de negocio se ejecutan en backend.
- La sesión se cierra automáticamente por inactividad.
- Toda acción queda auditada.

## UC-018 Renovacion idempotente por pago completado

Actor: Recepcionista, kiosco o sistema de pagos.

Flujo:
1. `memberships` solicita a `sales` una venta de renovacion mediante API interna.
2. `sales` crea venta, partida y pago manual con `idempotencyKey`.
3. Si el pago queda pendiente, no se activa membresia.
4. Al validar el pago, `sales` publica `PaymentCompletedV1`.
5. `memberships` consume el evento, registra `processed_domain_event` y extiende desde la fecha final vigente.
6. Si el evento se reprocesa, no se crea otra membresia.
7. `memberships` publica `MembershipRenewedV1`.

Criterios:
- Una renovacion anticipada no pierde dias.
- Una membresia cancelada no se renueva.
- La misma llave de idempotencia no crea dos ventas.

## UC-019 Cambio de plan idempotente

Flujo:
1. `memberships` valida membresia activa y plan destino distinto.
2. `memberships` solicita a `sales` venta de cambio de plan.
3. Al completarse el pago, `memberships` cancela la membresia anterior y activa la nueva.
4. El evento de pago es idempotente.

Criterios:
- No se permite cambiar al mismo plan.
- La membresia anterior queda cancelada con auditoria.
- La nueva membresia se activa solo con pago completado.

## UC-020 Seguridad de sesion de kiosco

Flujo:
1. El kiosco activo crea sesion temporal.
2. Backend devuelve `sessionId` y `sessionToken`.
3. Cada operacion posterior envia `X-Self-Service-Session-Token`.
4. Verificaciones fallidas incrementan intentos.
5. Al superar el limite, la sesion queda bloqueada temporalmente.
6. Rate limiting protege por IP, terminal y sesion.
7. Al cerrar sesion, no puede reutilizarse.

## UC-021 Setup piloto vendible

Actor: Administrador.

Flujo:
1. Administrador abre `/onboarding`.
2. Sistema muestra checklist de organizacion, sucursal, planes, productos, kiosco y clientes.
3. Administrador ajusta datos de organizacion, sucursal, plan base, producto base y codigo de kiosco.
4. Sistema actualiza organizacion/sucursal activa.
5. Sistema crea productos, planes y terminal de kiosco faltantes sin duplicar SKU ni terminal.
6. Sistema devuelve checklist actualizado.

Criterios:
- Repetir el setup no duplica catalogo ni kiosco.
- La operacion requiere `onboarding.manage`.
- La preparacion administrativa externa queda para Hito 7.

## UC-022 Importacion inicial de clientes

Actor: Administrador o responsable de recepcion con permiso.

Flujo:
1. Usuario abre `/clients/import`.
2. Usuario carga CSV o pega contenido con columnas `firstName,lastName,birthDate,gender,phone,email,notes`.
3. Usuario ejecuta `dryRun=true`.
4. Sistema valida formato, duplicados internos y duplicados contra la organizacion.
5. Usuario corrige errores y ejecuta importacion real.
6. Sistema crea clientes validos, omite duplicados y persiste batch/filas.

Criterios:
- `dryRun=true` no crea clientes.
- Una fila invalida no detiene todo el lote.
- La operacion requiere `client.import`.
- El resultado muestra conteos y detalle por fila.

## UC-023 Paquetes de visitas

Actor: Recepcionista.

Precondiciones:
- Usuario autenticado con sucursal activa.
- Cliente activo en la organizacion.
- Permisos `visit.read`, `visit.create` y/o `visit.consume` segun operacion.

Flujo:
1. Recepcion busca y selecciona un cliente activo desde control de acceso.
2. Sistema muestra paquetes de visita recientes del cliente en la sucursal activa.
3. Recepcion crea un paquete indicando tipo, total de visitas y vigencia.
4. Sistema registra el paquete como `ACTIVE` con `remainingVisits = totalVisits`.
5. Cuando el cliente usa una visita, recepcion consume el paquete.
6. Sistema descuenta una visita en una actualizacion atomica.
7. Si no quedan visitas, el paquete cambia a `CONSUMED`.
8. La creacion y el consumo quedan auditados.

Criterios:
- No se crean paquetes para clientes inactivos.
- No se consume un paquete fuera de vigencia.
- No se consume un paquete ya agotado.
- La operacion respeta organizacion y sucursal activa.
- La integracion automatica con ventas queda preparada por `saleId`, pero la creacion manual ya es usable para piloto.

## UC-024 Reembolso manual de venta

Actor: Administrador o encargado con permiso financiero.

Precondiciones:
- Venta existente en la organizacion y sucursal activa.
- La venta tiene pagos completados.
- Usuario con permiso `sale.refund`.

Flujo:
1. Usuario abre el detalle de una venta.
2. Sistema muestra pagos y reembolsos registrados.
3. Usuario captura monto, motivo y opcionalmente pago relacionado.
4. Sistema valida que el monto sea positivo.
5. Si se relaciona a un pago, valida que el pago pertenezca a la venta y este completado.
6. Sistema calcula saldo reembolsable como pagos completados menos reembolsos procesados.
7. Sistema registra `sale_refund` con estado `PROCESSED`.
8. Sistema audita `SALE_REFUND_PROCESSED`.
9. El detalle de venta muestra el historial actualizado de reembolsos.

Criterios:
- Una misma `idempotencyKey` no registra dos reembolsos.
- No se reembolsa mas de lo pagado.
- No se borra ni modifica el historial de pagos.
- El reembolso queda separado para conciliacion manual del piloto.

## UC-025 Reserva pagada vinculada a POS

Actor: Recepcionista o administrador.

Precondiciones:
- Reserva activa.
- Venta existente creada desde POS en la misma sucursal.
- Usuario con permiso `booking.payment.manage`.

Flujo:
1. Usuario crea una venta desde POS para el servicio o reserva.
2. Usuario abre la agenda de reservas.
3. Usuario selecciona la reserva y captura el ID de venta.
4. Usuario define estado `PENDING` o `PAID`.
5. Sistema registra o actualiza `booking_payment`.
6. Si el estado es `PAID` y la reserva estaba `RESERVED`, sistema confirma la reserva.
7. Sistema audita `BOOKING_PAYMENT_LINKED` y, si aplica, `BOOKING_CONFIRMED`.
8. La agenda muestra estado de pago y numero de venta vinculado.

Criterios:
- La venta debe pertenecer a la misma organizacion y sucursal.
- La reserva cerrada no puede cambiar de pago.
- El modulo de reservas no crea ventas por su cuenta en esta version.
- La relacion es actualizable sin duplicar registros por reserva.

## UC-025B Reserva con recurso restringido por plan

Actor: Recepcionista, gerente o administrador.

Precondiciones:
- Recurso reservable activo en la sucursal.
- El recurso puede declarar planes de membresia permitidos.
- Socio existente en la misma organizacion.

Flujo:
1. Usuario abre la agenda de reservas.
2. Usuario selecciona el recurso, socio, dia y hora.
3. Sistema valida disponibilidad, horario operativo y traslapes.
4. Si el recurso tiene planes permitidos, sistema valida que el socio tenga membresia activa y vigente con uno de esos planes.
5. Si el plan es multi-sucursal, la membresia permite reservar en cualquier sucursal del gimnasio; si no lo es, debe coincidir con la sucursal operativa de la membresia.
6. Sistema crea la reserva y registra auditoria.

Criterios:
- Un socio nuevo con membresia inicial activa puede reservar recursos permitidos por su plan.
- Un socio sin membresia activa o con plan no permitido recibe rechazo semantico y no se crea reserva.
- La validacion se cubre con PostgreSQL/Testcontainers para evitar diferencias entre SQL real y pruebas ligeras.

## UC-026 Automatizacion programada

Actor: Sistema.

Precondiciones:
- Regla `ENABLED`.
- `triggerType = SCHEDULED`.
- `conditionJson.intervalMinutes` definido o se usa 1440 minutos.

Flujo:
1. Usuario crea una regla programada desde automatizaciones.
2. Sistema calcula `nextRunAt`.
3. Scheduler interno revisa reglas vencidas de forma periodica.
4. Sistema reclama la regla con `locked_until`.
5. Sistema ejecuta la accion configurada.
6. Si la accion es `CREATE_NOTIFICATION`, se crea una notificacion interna.
7. Sistema registra `automation_execution`.
8. Sistema libera el bloqueo y reprograma `nextRunAt`.

Criterios:
- Una regla reclamada no se ejecuta simultaneamente por otro ciclo.
- El intervalo se limita entre 1 minuto y 10080 minutos.
- Los errores quedan como ejecucion `FAILED`.
- No se requiere proveedor externo para la V1.

## UC-027 Login y entrada por consola

Actor: Admin GymOS, dueño del gimnasio, gerente, recepcionista o coach.

Precondiciones:
- Usuario activo con permisos asignados.
- Si el rol opera dentro de un gimnasio, debe existir organizacion activa y, cuando aplique, sucursal activa.

Flujo:
1. Usuario captura usuario o email y contrasena.
2. Backend valida credenciales y devuelve permisos.
3. Frontend resuelve consola por permisos.
4. El usuario entra automaticamente a la ruta correcta.

Resultado esperado:
- Admin GymOS entra a `/platform/gymnasiums`.
- Dueño del gimnasio entra a `/dashboard`.
- Gerente entra a `/operations`.
- Recepcion entra a `/reception`.
- Coach entra a `/coaches`.
- No se muestra una administracion generica con menus que no correspondan al rol.

## UC-028 Administrar multiples gimnasios

Actor: Administrador GymOS.

Precondiciones:
- Usuario con permiso `platform.admin`.

Flujo:
1. Admin GymOS abre `/platform/gymnasiums`.
2. Sistema muestra un CRM SaaS de gimnasios cliente en formato maestro-detalle.
3. Administrador selecciona un gimnasio existente o crea uno nuevo.
4. Sistema muestra datos del tenant, marca, sucursales y entrada a soporte.
5. Admin GymOS entra a soporte auditable cuando necesita apoyar al cliente.

Resultado esperado:
- La pantalla escala con muchos gimnasios y sucursales sin convertirse en una lista larga de cards.
- Desde cualquier modulo en soporte, Admin GymOS puede volver a administracion de gimnasios.
- Las acciones en soporte quedan auditadas con origen `PLATFORM_SUPPORT`.

## UC-029 Configurar marca por gimnasio

Actor: Admin GymOS en soporte o dueño del gimnasio con permisos de configuracion.

Precondiciones:
- Organizacion existente.

Flujo:
1. Usuario abre configuracion del gimnasio.
2. Captura nombre de marca, logo opcional y colores.
3. Frontend valida colores `#RRGGBB`.
4. Backend guarda la marca en `organization`.
5. Gerencia, recepcion, coaches y kiosco usan la marca del tenant.

Resultado esperado:
- La marca no afecta el panel neutral de Admin GymOS fuera de soporte.
- Cada gimnasio puede verse distinto cuando lo usan sus usuarios internos.

## UC-030 Crear usuarios internos por rol

Actor: Admin GymOS en soporte, dueño del gimnasio o gerente con permiso.

Precondiciones:
- Organizacion existente.
- Sucursal activa o sucursales disponibles si el rol requiere operacion local.

Flujo:
1. Usuario abre Equipo y accesos.
2. Frontend muestra solo roles validos para el contexto.
3. Usuario captura nombre, apellido, email, telefono, usuario, contrasena temporal, rol y sucursal.
4. Frontend valida email, telefono `+52` mas 10 digitos y contrasena minima antes de enviar.
5. Si el rol es `Dueno del gimnasio`, el frontend asigna automaticamente todas las sucursales existentes.
6. Backend valida que rol y sucursales pertenezcan al tenant y que el rol no sea de plataforma.
7. Backend crea persona, cuenta, rol y acceso de sucursal.

Resultado esperado:
- Un gimnasio no ofrece el rol `Administrador GymOS` como rol operativo.
- El dueño puede crear gerentes, recepcionistas y coaches de su gimnasio.
- El gerente con permiso de equipo solo puede crear recepcionistas y coaches.
- Si el gerente tiene una sola sucursal, esa sucursal queda seleccionada y bloqueada automaticamente al crear usuarios operativos.
- Un recepcionista creado inicia sesion en recepcion y no ve administracion de plataforma.
- Si faltan campos o hay permisos invalidos, se muestra error claro con codigo y `correlationId`.

## UC-031 Suspender acceso comercial y usuarios internos

Actor: Admin GymOS; dueño del gimnasio o gerente con permiso para equipo.

Precondiciones:
- Existe un gimnasio cliente y usuarios internos.
- Admin GymOS tiene permiso `platform.admin`.
- El usuario tenant que gestiona equipo tiene permiso `user.manage`.

Flujo de gimnasio:
1. Admin GymOS abre `/platform/gymnasiums`.
2. Selecciona el gimnasio cliente.
3. Cambia el estado comercial a `SUSPENDED` o `INACTIVE`.
4. Frontend solicita confirmacion explicita.
5. Backend conserva el registro para auditoria, pero bloquea login y refresh de usuarios tenant.
6. Admin GymOS puede reactivar el gimnasio cambiandolo a `ACTIVE`.

Flujo de usuario:
1. Usuario autorizado abre `/users`.
2. Localiza una cuenta del gimnasio.
3. Cambia el estado a `SUSPENDED` o la reactiva a `ACTIVE`.
4. Frontend solicita confirmacion explicita.
5. Backend impide suspender la propia cuenta.

Resultado esperado:
- No se borran gimnasios ni usuarios desde el flujo operativo principal.
- Suspender un gimnasio bloquea a sus dueños, gerentes, recepcionistas y coaches.
- Admin GymOS conserva acceso de plataforma y soporte.
- Las acciones quedan auditadas.

## UC-032 Cambiar rol de usuario interno

Actor: Admin GymOS en soporte, dueÃ±o del gimnasio o gerente con permiso `user.manage`.

Precondiciones:
- Usuario autenticado con permiso `user.manage`.
- Usuario interno existente en el mismo tenant.
- Rol destino disponible para el contexto tenant.

Flujo:
1. Usuario abre `/users`.
2. Selecciona el nuevo rol en la tabla de usuarios existentes.
3. Frontend solicita confirmacion.
4. Backend valida que el usuario pertenezca al tenant.
5. Backend valida que el rol no contenga `platform.admin`.
6. Backend reemplaza el rol principal y audita `INTERNAL_USER_ROLE_UPDATED`.

Reglas:
- No se permite cambiar el propio rol.
- No se permite asignar `Administrador GymOS` desde contexto de gimnasio.
- El gerente no puede ver ni administrar usuarios con rol `Dueno del gimnasio` o `Gerente`; solo usuarios operativos dentro de su alcance.

## Anexo Hito 3.5 final

Ver `Documentación/07-use-cases-hito-3-5-final.md` para la definicion vigente de verificacion segura de kiosco, idempotencia por hash de solicitud y eventos por modulo propietario.
