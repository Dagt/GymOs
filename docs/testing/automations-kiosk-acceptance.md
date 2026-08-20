# Guía de aceptación: automatizaciones y kiosco

Esta guía reúne el alcance funcional implementado y los recorridos recomendados para probarlo. El kiosco es una sesión web lógica por sucursal: no requiere registrar, nombrar ni asignar una computadora.

## Perfiles y puntos de entrada

| Perfil | Dónde entrar | Uso esperado |
| --- | --- | --- |
| Dueño del gimnasio | `Configuración` en el menú | Configurar cobros, Stripe, capacidades del kiosco y correo. |
| Administrador GymOS en soporte | Entrar al gimnasio y seleccionar una sucursal | Puede revisar y configurar el mismo alcance con auditoría de soporte. |
| Gerente | `Automatizaciones` | Consultar reglas, historial y estado operativo según sus permisos. |
| Recepción | `Inicio recepción` → `Abrir kiosco seguro` | Abrir una pestaña separada de autoservicio; recepción conserva su consola. |
| Dueño o administrador | `Automatizaciones` | Crear reglas, ejecutar pruebas, pausar el procesamiento y recuperar fallos según sus permisos. |

## Preparación inicial

1. Entra a `Configuración` (`/settings/organization`).
2. Abre `Empezar aquí`. La pantalla muestra qué es obligatorio, qué es opcional y el orden recomendado; sólo presenta un bloque de configuración a la vez.
3. Entra a `Medios de cobro` y deja habilitada al menos una opción de recepción:
   - terminal física independiente, con validación manual posterior en GymOS; o
   - Mercado Pago Point integrado, después de vincular la cuenta y elegir la terminal Point.
4. Para pagos del socio en kiosco, vincula Stripe y habilita `Stripe para self checkout`.
5. Para cargos recurrentes, habilita además `Domiciliación con Stripe`.
6. Elige la sucursal en el selector de la esquina superior derecha. En `Kiosco web`, decide qué acciones puede realizar el socio en esa sucursal y guarda. Para configurar otra, cambia primero el selector global.
7. En `Correo electrónico`, configura el remitente sólo si usarás correos. Las notificaciones internas funcionan sin SMTP. El bloque separa el paso técnico de conexión y el paso operativo del gimnasio.

## Funcionalidades del kiosco

### Apertura sin registro de computadoras

- Recepción abre `/reception` y selecciona `Abrir kiosco seguro`.
- GymOS crea o reutiliza la configuración lógica de la sucursal y abre `/kiosk/reception` en otra pestaña mediante un traspaso temporal que no aparece en la URL.
- La pestaña del kiosco no conserva la sesión administrativa; la pestaña de recepción sigue disponible para el personal. El socio no recibe acceso al POS, clientes, reportes ni otras funciones.
- Si alguien abre la URL directamente sin una sesión de recepción, la pantalla explica cómo iniciar correctamente. No solicita un ID de computadora.
- La pantalla puede maximizarse o ponerse en modo pantalla completa desde el navegador.

### Seguridad y privacidad

- Verificación del socio mediante correo o teléfono y fecha de nacimiento.
- Datos de contacto enmascarados después de verificar.
- Sesión pública con token independiente, expiración, inactividad, reinicio entre socios y encabezados `no-store`.
- Límites de intentos y resolución segura de IP detrás de proxies de confianza.
- Acción visible `Terminar y borrar mis datos` para dejar la pantalla lista para el siguiente socio.
- El logo tiene una inicial de respaldo si la imagen configurada no puede cargarse.

### Membresías con Stripe Checkout

- Consulta de planes activos y precio.
- El catálogo sólo muestra planes asociados a la sucursal desde la que se abrió el kiosco.
- Cotización final antes de confirmar una contratación, renovación o cambio de plan.
- Contratación de una membresía para un socio que todavía no tiene una.
- Renovación de membresía vigente o vencida según las reglas del dominio.
- Cambio de plan de una membresía activa.
- Redirección exclusiva a Stripe Checkout para capturar la tarjeta; GymOS no almacena PAN ni CVV.
- Claves idempotentes para impedir ventas o extensiones duplicadas por doble clic, reintento o webhook repetido.
- La membresía sólo se activa o extiende después de confirmar el pago con Stripe.

### Actividad de pagos

- Lista de pagos recientes del socio con estado entendible.
- Continuación de un Checkout pendiente cuando Stripe conserva una URL válida.
- Acceso al comprobante cuando el proveedor ya lo emitió.
- Pantalla de retorno que aclara que el redirect no confirma el pago: el webhook firmado y la consulta a Stripe son autoritativos.

### Domiciliación con Stripe

- Texto versionado de consentimiento y aceptación expresa.
- Captura alojada en Stripe y método de pago tokenizado.
- Consulta de tarjeta enmascarada, próximo cargo y estado del mandato.
- Ciclo por vigencia con cargos recurrentes, reintentos y estados `PAST_DUE`/pausado.
- Webhooks de éxito, fallo y `requires_action`.
- Extensión idempotente de la membresía sólo después del pago confirmado.
- Sustitución de tarjeta para recuperar un mandato con problema.
- Revocación del consentimiento desde el kiosco.

### Otras acciones del socio

- Actualización opcional de correo y teléfono, habilitable por sucursal.
- Reserva de recursos gratuitos con consulta de disponibilidad y protección de datos de otros socios.
- Los recursos restringidos por plan se ocultan antes de mostrar el catálogo o sus horarios; la API vuelve a validar la elegibilidad al reservar.
- Reserva de citas wellness gratuitas con selección de profesional, servicio y horario.
- Los recursos reservables con costo y los servicios de wellness con costo no están implementados ni se exponen en el kiosco; el backend también los rechaza.

## Configuración del kiosco por sucursal

En `Configuración` → `Kiosco web` (`/settings/organization#kiosk-settings`) se controlan:

- contratar membresía;
- renovar membresía;
- cambiar de plan;
- domiciliación con Stripe;
- reservas gratuitas;
- citas wellness gratuitas;
- actualización de contacto.

La tarjeta muestra únicamente la sucursal activa del selector superior y señala si Stripe Checkout y la domiciliación están listos. Activar una capacidad no sustituye la conexión de Stripe: mientras el proveedor no esté listo, el kiosco explica el requisito y bloquea el cobro.

## Funcionalidades de automatizaciones

### Creación de reglas

En `Automatizaciones` (`/automations`) primero se elige una receta común o `Crear automatización`. Las organizaciones nuevas no incluyen reglas activas por defecto. Las recetas sólo precargan el formulario: no guardan ni activan nada hasta confirmar. El formulario presenta tres pasos:

- cuándo debe ejecutarse;
- a quién debe avisar;
- qué asunto y mensaje entregar.

También permite revisar:

- nombre para reconocer la automatización;
- condición tipada cuando corresponde;
- vista previa antes de guardar;
- límite de reintentos de la regla.

Las recetas incluidas cubren membresía por vencer, pago pendiente de validar, recordatorio previo de reservación, reserva cancelada y fallo de domiciliación.

### Disparadores disponibles

- ejecución manual;
- intervalo programado;
- pago completado;
- pago pendiente de validación, filtrable por método y monto mínimo;
- membresía próxima a vencer, con días de anticipación;
- membresía vencida;
- inicio, éxito y fallo de un cobro domiciliado;
- reserva creada, confirmada o cancelada.
- recordatorio previo de reservación, configurable entre 15 minutos y 7 días antes.

Los eventos que identifican a un socio permiten `Correo al socio`. Los eventos manuales o programados sin socio relacionado sólo permiten una notificación interna, evitando destinatarios ambiguos.

### Entrega de notificaciones y correo

- Notificación manual interna o por correo.
- Cola durable de correo, leasing, reintentos con backoff y estado de fallo definitivo.
- Destinatarios enmascarados en logs e historiales y hash con clave del despliegue para preferencias.
- Remitente y `reply-to` configurables por gimnasio.
- Prueba de correo desde la pantalla de configuración.
- Adaptador SMTP listo para conectar al proveedor elegido mediante variables de entorno.
- Reintento manual de correos que agotaron sus intentos.
- Las campañas de marketing y su flujo público de alta/baja permanecen deshabilitados; el alcance actual es transaccional y operativo.

### Operación y recuperación

- El botón `Ver monitoreo avanzado` muestra la operación técnica; si existe una incidencia crítica, el bloque se hace visible automáticamente.
- Estado saludable, de advertencia o crítico separado por gimnasio.
- Conteos de eventos pendientes, reintentos, bloqueos vencidos y fallos definitivos.
- Pausa y reanudación del procesador por tenant con motivo auditado.
- Ejecución manual de prueba para reglas habilitadas.
- Historial paginado por regla con estado, intento, referencia, duración y error sanitizado.
- Bandeja paginada de fallos definitivos.
- Reintento o descarte manual con motivo obligatorio y auditoría.
- Inbox de eventos e idempotencia para impedir doble procesamiento.
- Leasing y reparto justo entre tenants para que un gimnasio no monopolice el worker.

### Telemetría

- Métricas de automatizaciones, notificaciones y pagos etiquetadas por tenant y sucursal cuando aplica.
- Métricas de backlog, reintentos, bloqueos vencidos, duración y resultado.
- Reglas de alerta de Prometheus para fallos, atrasos y operaciones estancadas.
- La interfaz nunca mezcla historiales ni métricas operativas de gimnasios distintos.

## Historiales de Stripe

En `Configuración` → `Historial técnico` (`/settings/organization#payment-operations`) están paginados:

- cargos recurrentes;
- consentimientos/mandatos;
- webhooks recibidos.

Se muestran estados, intentos, referencias enmascaradas y errores sanitizados. No se exponen números completos de tarjeta ni payloads sensibles del proveedor.

## Recorridos mínimos de aceptación

### A. Kiosco sin Stripe conectado

1. Habilita contratación, renovación, cambio de plan, reservas gratuitas y actualización de contacto para una sucursal.
2. Abre el kiosco desde recepción.
3. Verifica un socio.
4. Confirma que los botones con pago estén deshabilitados y expliquen que Stripe está pendiente.
5. Actualiza un dato de contacto.
6. Consulta un recurso gratuito y selecciona un horario disponible.
7. Termina la sesión y confirma que los datos del socio desaparecen.

### B. Recepción con terminal manual

1. Habilita `Terminal independiente en recepción`.
2. Registra una venta o renovación desde POS.
3. Cobra el monto en la terminal física, sin integración directa.
4. Valida manualmente el pago en GymOS.
5. Confirma que la venta, membresía y automatización de pago completado se actualizan una sola vez.

### C. Automatización interna

1. Elige la receta `Revisar un pago pendiente`.
2. Revisa los tres pasos, cambia el texto si lo deseas y selecciona `Guardar y activar`.
3. Usa `Probar ahora`.
4. Abre `Ver actividad` y confirma una ejecución completada.
5. Confirma el aviso en `Entregas recientes`.

### D. Automatización por correo

1. Conecta SMTP, guarda el remitente y envía el correo de prueba desde `Configuración`.
2. Elige la receta `Avisar que la membresía vencerá`.
3. Usa asunto y variables como `{{recipientName}}` y `{{endsOn}}`.
4. Genera el evento y confirma que el mensaje pasa de `En cola` a `Enviada`.
5. Simula un proveedor caído, agota los intentos y usa `Reintentar correo` después de recuperar el proveedor.

### E. Recuperación de automatizaciones

1. Provoca un error controlado en una regla.
2. Confirma los reintentos automáticos y su historial.
3. Cuando llegue a `Fallos definitivos`, registra un motivo y reintenta.
4. Repite con descarte y confirma que desaparece de pendientes pero conserva auditoría.

### F. Stripe en modo test

1. Vincula una cuenta de Stripe test y habilita Checkout y domiciliación.
2. Prueba contratación, renovación y cambio de plan.
3. Reenvía el mismo webhook y confirma que no duplica la venta ni la extensión.
4. Prueba fallo, timeout, reintento y `requires_action`.
5. Simula una caída después de guardar el evento y antes de procesarlo; al reiniciar, confirma la recuperación mediante leasing.
6. Activa una domiciliación, ejecuta un cargo, sustituye la tarjeta y revoca el mandato.

Las pruebas del bloque F requieren credenciales y webhooks reales de Stripe test; el resto puede probarse con la infraestructura local y un servidor SMTP de desarrollo.

El procedimiento detallado, variables, tarjetas de prueba y resultados esperados se encuentran en [`stripe-kiosk-test-mode.md`](stripe-kiosk-test-mode.md).
