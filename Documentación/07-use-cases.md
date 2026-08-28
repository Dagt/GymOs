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
9. Si la reserva tiene importe, crear una operación de pago y abrir Stripe Checkout; confirmar el cobro sólo después del webhook firmado y la consulta autoritativa al proveedor.
10. Reflejar el movimiento confirmado en la actividad de caja y habilitar el comprobante cuando Stripe lo proporcione.
11. Generar la notificación durable aplicable; su entrega depende de la configuración SMTP activa.

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

1. Dueño o Gerente con `access.manual.open` selecciona un punto explícito y,
   opcionalmente, un socio.
2. Sistema exige motivo y permiso dedicado.
3. Registrar decisión, actor, sucursal, punto, motivo y correlación.
4. Mantener `SIMULATED_OPEN` hasta que exista un adapter físico validado.
5. Auditar sin registrar secretos ni datos biométricos.

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
- dueño y gerencia comparan el periodo contra otro anterior de igual duración;
- la asignación conserva su contenido aunque se publique otra versión;
- no se registra venta, pago, comisión, ingreso ni movimiento de caja.

Alternativas: una versión en borrador no se asigna; un coach/socio fuera de alcance
se rechaza; cancelar exige motivo; un reemplazo conserva y muestra el histórico;
un reintento devuelve el resultado original sin duplicar la métrica.

## UC-014 Reservar consulta profesional

1. Seleccionar profesional.
2. Seleccionar servicio.
3. Consultar disponibilidad.
4. Cobrar si aplica.
5. Crear Booking.
6. Notificar.

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
- Recepción abrió una sesión de navegador para una sucursal con perfil lógico activo.
- Cliente existente o capacidad de registro de socios habilitada en la sucursal.
- Acciones de autoservicio configuradas para el perfil de la sucursal.

Flujo:
1. Cliente recibe una sesión temporal en el kiosco abierto por Recepción.
2. El socio existente se identifica o el nuevo elige `Quiero registrarme`,
   captura sus datos y queda verificado sin depender de Stripe.
3. Sistema muestra resumen limitado: estado, membresía vigente, vencimiento y pagos pendientes.
4. Cliente selecciona contratar, renovar, cambiar plan, domiciliar, reservar o actualizar datos permitidos.
5. Backend valida elegibilidad, reglas de membresía, beneficios, montos y la
   disponibilidad técnica del proveedor al ejecutar una operación monetaria.
6. Si hay pago, se crea venta o pago según corresponda.
7. Si existe cobro, Stripe Checkout/Setup captura la tarjeta y el pago permanece pendiente.
8. Sólo webhook firmado y conciliación autoritativa completan el pago y sus efectos.
9. Sistema emite eventos correspondientes.
10. Sistema audita acción con origen `SELF_SERVICE_KIOSK`.
11. Sesión termina manualmente o por inactividad.

Errores:
- Sesión expirada.
- Cliente no verificado.
- Acción no permitida para autoservicio.
- Membresía no elegible para cambio.
- Proveedor no disponible, pago rechazado o todavía pendiente de webhook.

Criterios:
- El kiosco no muestra información administrativa.
- No se activa ni renueva membresía sin pago completo.
- Una capacidad configurada conserva la intención aunque Stripe esté pendiente;
  en ese estado los planes son visibles y Checkout permanece bloqueado.
- Al reiniciar la sesión se vuelve a leer la configuración vigente del terminal.
- Las reglas de negocio se ejecutan en backend.
- La sesión se cierra automáticamente por inactividad.
- Toda acción queda auditada.

## UC-017A Inscripción e inactividad del socio

Actor: Recepción, kiosco o worker de ciclo de vida.

Flujo:
1. La organización configura `inactivityMonths` entre 1 y 120.
2. La primera membresía cobra inscripción si `registrationFeeEnabled=true`.
3. Para un socio con historial se obtiene el final de gracia más reciente.
4. Si `latestMembershipGraceEnd + inactivityMonths <= effectiveDate`, una nueva
   alta vuelve a cobrar inscripción; antes de esa fecha no la cobra.
5. Independientemente, si `autoInactivationEnabled=true`, el worker cambia al
   socio a `INACTIVE` con motivo `MEMBERSHIP_INACTIVITY` al alcanzar el mismo
   umbral.

Criterios:
- `inactivityMonths` es el único umbral temporal para determinar periodo de
  inactividad y reinscripción.
- Desactivar la baja automática no desactiva el nuevo cobro de inscripción.
- La baja no elimina expediente, membresías, ventas, pagos ni auditoría.
- Sólo una baja con motivo `MEMBERSHIP_INACTIVITY` puede reactivarse desde el
  kiosco; una baja `MANUAL` requiere intervención del personal.

## UC-018 Renovacion idempotente por pago completado

Actor: Recepcionista, kiosco o sistema de pagos.

Flujo:
1. `memberships` solicita a `sales` una venta de renovacion mediante API interna.
2. `sales` crea venta, partida y pago manual o de proveedor con `idempotencyKey`.
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
3. Al completarse el pago, `memberships` conserva la membresía operativa,
   actualiza el plan vigente y agrega un periodo histórico de cambio.
4. El evento de pago es idempotente.

Criterios:
- No se permite cambiar al mismo plan.
- El plan/periodo anterior queda en el historial con auditoría.
- El plan destino se vuelve operativo sólo con pago completado.

## UC-020 Seguridad de sesion de kiosco

Flujo:
1. Recepción con `selfservice.session.launch` crea el handoff de un solo uso.
2. Backend devuelve `sessionId` y `sessionToken`.
3. Cada operacion posterior envia `X-Self-Service-Session-Token`.
4. Verificaciones fallidas incrementan intentos.
5. Al superar el limite, la sesion queda bloqueada temporalmente.
6. Rate limiting protege por IP efectiva, perfil lógico y sesión.
7. Al cerrar sesion, no puede reutilizarse.

## UC-021 Setup piloto vendible

Actor: Administrador.

Flujo:
1. Administrador abre `/onboarding`.
2. Sistema muestra checklist de organizacion, sucursal, planes, productos, kiosco y clientes.
3. Administrador ajusta organización, sucursal, plan base, producto base y capacidades de kiosco.
4. Sistema actualiza organizacion/sucursal activa.
5. Sistema crea productos, planes y perfil lógico de kiosco faltantes sin duplicar SKU ni perfil.
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

Flujo objetivo:
1. Recepcion busca y selecciona un cliente activo del tenant.
2. Sistema muestra los paquetes del socio y su saldo único, sin limitar la
   consulta a la sucursal donde se vendieron.
3. Recepcion vende un paquete indicando total de visitas, vigencia y sucursales
   admitidas (`todas` o una selección).
4. Sistema registra el paquete como `ACTIVE` con
   `remainingVisits = totalVisits`.
5. Cuando el cliente usa una visita en cualquier sucursal admitida, el sistema
   descuenta del mismo saldo mediante actualización atómica e idempotente.
6. El consumo registra la sucursal y el acceso que lo originaron.
7. Si no quedan visitas, el paquete cambia a `CONSUMED`.
8. La venta, creación y consumos quedan auditados.

Criterios:
- No se crean paquetes para clientes inactivos.
- No se consume un paquete fuera de vigencia.
- No se consume un paquete ya agotado.
- La operación nunca cruza organizaciones; sólo usa las sucursales permitidas
  dentro del mismo tenant.
- Dos consumos concurrentes no pueden gastar la misma visita.
- El paquete nace de una venta y conserva `saleId`.

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
6. Si la acción es notificación, se crea entrega interna o correo durable según
   el destinatario/configuración.
7. Sistema registra `automation_execution`.
8. Sistema libera el bloqueo y reprograma `nextRunAt`.

Criterios:
- Una regla reclamada no se ejecuta simultaneamente por otro ciclo.
- El intervalo se limita entre 1 minuto y 10080 minutos.
- Los errores quedan como ejecucion `FAILED`.
- La notificación interna no requiere proveedor; `EMAIL` requiere SMTP operativo.

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

## UC-029 Configurar marca por gimnasio y sucursal

Actor: Admin GymOS en soporte o dueño del gimnasio con permisos de configuracion.

Precondiciones:
- Organizacion existente.

Flujo objetivo:
1. Usuario abre configuración del gimnasio y selecciona una sucursal.
2. Captura nombre de marca, logo opcional, colores y datos de comunicación de
   esa sucursal; también puede conservar valores heredados de la organización.
3. Frontend valida colores `#RRGGBB` y previsualiza la marca efectiva.
4. Backend guarda el perfil con `organizationId` y `branchId`.
5. Gerencia, recepción, coaches y kiosco usan la marca de la sucursal activa.
6. Correos, recibos y PDFs usan la sucursal de la operación para resolver marca.
7. El shell mantiene atribución permanente GymFort junto a la marca operativa.

Resultado esperado:
- La marca no afecta el panel neutral de Admin GymOS fuera de soporte.
- Dos sucursales del mismo tenant pueden verse distintas sin separar sus socios,
  catálogo, planes o beneficios.

## UC-030 Crear usuarios internos por rol

Actor: Admin GymOS en soporte, dueño del gimnasio o gerente con permiso.

Precondiciones:
- Organizacion existente.
- Sucursal activa o sucursales disponibles si el rol requiere operacion local.

Flujo:
1. Usuario abre Equipo y accesos.
2. Frontend muestra solo roles validos para el contexto.
3. Usuario captura nombre, apellido, email, telefono, usuario, contrasena temporal, rol y sucursal.
4. Si selecciona `Coach`, el mismo formulario muestra especialidades y
   biografia como datos complementarios.
5. Frontend valida email, telefono `+52` mas 10 digitos y contrasena minima antes de enviar.
6. Si el rol es `Dueno del gimnasio`, el frontend asigna automaticamente todas las sucursales existentes.
7. Backend valida que rol y sucursales pertenezcan al tenant y que el rol no sea de plataforma.
8. Backend crea persona, cuenta, rol y acceso de sucursal; para `Coach` crea
   tambien su perfil y replica los datos complementarios en cada sucursal,
   dentro de la misma transaccion.

Resultado esperado:
- Un gimnasio no ofrece el rol `Administrador GymOS` como rol operativo.
- El dueño puede crear gerentes, recepcionistas y coaches de su gimnasio.
- El gerente con permiso de equipo solo puede crear recepcionistas y coaches.
- Si el gerente tiene una sola sucursal, esa sucursal queda seleccionada y bloqueada automaticamente al crear usuarios operativos.
- Un recepcionista creado inicia sesion en recepcion y no ve administracion de plataforma.
- Un Coach no requiere una segunda edicion para completar sus datos iniciales.
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

Actor: Admin GymOS en soporte, dueño del gimnasio o gerente con permiso `user.manage`.

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

## UC-033 Administrar y cobrar suscripciones GymFort

Actor: Administrador GymOS.

Precondiciones:
- Usuario con permiso `platform.admin`.
- Organización cliente y al menos una sucursal disponibles.

Flujo:
1. Administrador abre `/platform/billing`.
2. Sistema muestra el total comercial por cliente y el detalle facturable por sucursal: socios con membresía activa, usuarios internos, planes de membresía, plan sugerido, plan contratado, importe, implementación, próxima fecha y estado de cobro.
3. Administrador configura el plan contratado, la fecha de corte y las condiciones comerciales de una sucursal.
4. Administrador registra un pago recibido indicando concepto, medio, monto, fecha, referencia y nota.
5. Sistema guarda un renglón inmutable en el historial, audita la acción y, si es mensualidad, calcula el siguiente corte; si completa implementación, la marca como liquidada.
6. La pantalla actualiza el MRR, el expediente de la sucursal y el total del cliente.

Reglas:
- El total del cliente suma únicamente las sucursales con suscripción comercial activa; cada sede conserva su contrato y pago por separado.
- La recomendación de plan se basa en membresías activas de la sede; personal y planes internos son indicadores, no reglas de precio.
- No se generan CFDI ni cargos automáticos de GymFort en este flujo.
- El historial no permite edición ni eliminación; correcciones futuras deben ser movimientos explícitos y auditables.
- Perfiles tenant nunca pueden acceder a estos endpoints ni a la ruta.

## UC-034 Preparar y operar una conexión de acceso físico

Actor administrativo: Dueño o Gerente con `access.physical.manage`.

Precondiciones:

- sucursal activa;
- planes y beneficios `ACCESS` configurados;
- inventario real de puertas/torniquetes y niveles externos;
- descubrimiento técnico del proveedor antes de conectar hardware.

Flujo administrativo:

1. Actor selecciona un proveedor configurable y guarda una conexión única para
   la sucursal.
2. Registra cada punto productivo con exactamente un beneficio `ACCESS`.
3. Vincula el punto con una referencia externa inequívoca.
4. Mapea cada beneficio con el nivel de acceso existente en el proveedor.
5. Genera un código de activación que se muestra una vez.
6. Instala el Connector en la LAN, intercambia el código y guarda localmente el
   secreto recibido una vez.
7. El Connector reporta heartbeat e importa candidatos de persona; el actor
   confirma o descarta vínculos sin usar similitud de nombre.
8. GymFort recalcula estado deseado ante altas, renovaciones, cambios,
   vencimientos, estado del socio y configuración de derechos.
9. El Connector reclama jobs con lease, aplica cambios idempotentes y confirma o
   reporta fallos sanitizados.
10. La consola muestra salud, pendientes, fallos y última sincronización.

Flujo de recuperación:

1. Actor verifica membresía, vínculo, mapeo, heartbeat y último error.
2. Recupera el Connector o la conectividad local.
3. Fuerza reconciliación completa.
4. Comprueba que los jobs concluyan y que el estado externo coincida.
5. Rota o revoca la credencial si existe sospecha de exposición.

Reglas:

- Recepción puede operar la evaluación normal si tiene `access.read/manage`,
  pero no configura el proveedor ni ve candidatos físicos.
- El Connector no usa JWT de empleado y queda limitado a su organización,
  sucursal y conexión por su propia credencial.
- Eventos repetidos no duplican `access_log`.
- GymFort Cloud no recibe credenciales LAN, fotos, rostros, huellas ni plantillas
  biométricas.
- `HIKCENTRAL` identifica el primer contrato cloud; la operación contra un
  torniquete real sigue pendiente del Connector y adapter validados en sitio.

## UC-035 Dar de alta un producto con sabores y códigos múltiples

Actor: usuario con `catalog.create` y sucursal activa.

Flujo:
1. Abre Catálogo y elige `Producto con variantes`.
2. Por defecto captura un producto base nuevo con nombre y marca. La familia es
   interna y se crea automáticamente; si quiere ampliar un producto anterior,
   usa `Agregar variantes a producto existente` o la acción del catálogo.
3. Captura una sola vez presentación, precio, costo y demás datos comunes.
4. Agrega filas compactas para variante, código principal y SKU autogenerado
   pero editable. La variante describe lo que cambia: sabor, tamaño/porciones o
   ambos. Enter después del código mueve el foco sin enviar el formulario.
5. Sólo una variante excepcional despliega `Precio / tamaño` para cambiar
   presentación, precio o costo; la promoción queda plegada y los códigos
   alternativos se agregan con el `+` contiguo al código principal.
6. Backend crea el lote transaccionalmente y adjunta cada variante a la sucursal
   activa.
7. POS puede identificar la variante correcta con cualquiera de sus códigos;
   ventas e inventario operan sobre su `catalog_product` individual.

Criterios:
- no se repiten nombre, marca ni configuración común por sabor;
- los datos comunes sólo son defaults de creación y no acoplan ediciones
  posteriores entre variantes;
- cada variante mantiene precio, costo, SKU, stock y movimientos propios;
- dos presentaciones del mismo sabor (por ejemplo `Manzana · 35 porciones` y
  `Manzana · 60 porciones`) son variantes distintas con SKU, código e inventario
  independientes;
- si comparten identidad comercial, marca y fórmula pero cambia el contenido o
  número de porciones, pertenecen al mismo producto base; cada tamaño lleva su
  propio precio y costo mediante overrides de la variante;
- una colisión de SKU o código revierte el lote completo;
- un código no puede apuntar a dos variantes activas de la misma organización;
- el alta individual no solicita sabor: muestra el código principal y el botón
  compacto para aliases en el mismo bloque, antes de `Más datos`;
- productos, ventas y movimientos existentes conservan sus IDs y relaciones al
  aplicar V95.

## Anexo Hito 3.5 final

La verificación segura de kiosco, la idempotencia por hash y los eventos por
módulo propietario forman parte de este contrato y del estado en
`modulos/11-selfservice.md`.
