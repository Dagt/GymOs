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
5. Cobrar si aplica.
6. Crear Booking.
7. Confirmar.
8. Notificar.

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

1. Coach selecciona cliente.
2. Crea rutina estructurada.
3. Guarda versión.
4. Asigna.
5. Registra venta si aplica.
6. Actualiza métrica del coach.

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
