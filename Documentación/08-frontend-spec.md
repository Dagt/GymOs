# 8. Especificación de Frontend Web

## 8.1 Principios

- Web responsive.
- Frontend disponible desde el primer hito funcional.
- Recepción y administración son experiencias distintas.
- Acciones frecuentes visibles.
- Navegación simple.
- Permisos controlan acceso a funciones.
- Formularios cortos.
- Confirmaciones claras.
- Estados y errores visibles.

## 8.1.1 Primer hito frontend obligatorio

El primer hito debe incluir una aplicación Next.js funcional con:

- Inicio de sesión real.
- Manejo de sesión, refresh token y cierre de sesión.
- Selección o visualización de organización y sucursal activa.
- Layout base para recepción y administración.
- Control visible de permisos.
- Manejo uniforme de errores de API.
- Pantallas mínimas para el primer caso de uso implementado.

## 8.1.2 Kiosco de autoservicio

GymOS debe contemplar una experiencia de pantalla táctil para clientes dentro del gimnasio.

Principios:
- Interfaz muy simple, con botones grandes y texto legible a distancia corta.
- Sesión temporal con cierre automático por inactividad.
- Sin navegación administrativa.
- Sin exposición de datos sensibles innecesarios.
- Confirmaciones claras antes de pagos o cambios de membresía.
- Estados visibles para pagos pendientes, en revisión, aprobados o rechazados.

Funciones esperadas:
- Identificación del cliente.
- Resumen de membresía.
- Pagos pendientes.
- Renovación de membresía.
- Solicitud de cambio de plan.
- Inicio de pago manual.
- Carga o captura de comprobante.
- Actualización de teléfono, correo y contacto de emergencia cuando esté permitido.

## 8.2 Layout de recepción

Pantalla principal con accesos directos:

- Caja / POS para ventas, pagos y membresias.
- Buscar o registrar socio.
- Revisar ventas y pagos pendientes.
- Reservas.
- Beneficios.
- Kiosco o pantalla para socios.

Debe mostrar:
- nombre y logo del gimnasio
- sucursal activa
- rol, nombre de usuario y alcance
- salida de sesion visible
- senales rapidas del turno
- acciones operativas principales en una sola cuadricula

La portada `/reception` es una consola rapida de mostrador. No debe duplicar la navegacion lateral en esa primera pantalla ni esconder acciones en una seccion secundaria de "mas herramientas". Las pantallas internas como POS, socios, reservas y ventas si pueden usar `AppShell` lateral para navegacion entre modulos.

## 8.2.1 Equipo y alcance de sucursal

- Si un gerente solo tiene una sucursal asignada, los formularios de alta de recepcionistas o coaches deben mostrar esa sucursal seleccionada y bloqueada.
- El gerente no debe ver usuarios con rol `Dueno del gimnasio`, `Gerente` o `Administrador GymOS` en la gestion de equipo.
- El dueno del gimnasio y Admin GymOS en soporte pueden ver y administrar el equipo completo del tenant, respetando que un tenant nunca crea administradores de plataforma.
- La UI debe evitar estados donde se pueda enviar un usuario operativo sin sucursal cuando el rol requiere alcance local.

## 8.2.2 Recursos reservables

- La agenda permite crear reservas a perfiles operativos con `booking.manage`.
- La creacion, edicion y eliminacion de recursos se muestra solo a Admin GymOS en soporte o dueno del gimnasio con `resource.manage`.
- Las acciones de recurso deben vivir en el mismo lenguaje visual de la agenda y abrir panel/modal, no formularios permanentes que saturen la pagina.
- La eliminacion de recurso siempre pide confirmacion y debe mostrar error claro si hay reservas activas.

## 8.3 Alta de cliente

Secciones:
- Datos personales.
- Foto.
- Contacto.
- Beneficios.
- Plan inicial.
- Método de pago.

Debe permitir alta rápida y alta completa.

Correccion implementada:
- El formulario de socios usa nombre, apellido paterno y apellido materno.
- La fecha de nacimiento se captura visualmente como `dd/mm/aaaa` y se envia a la API como `YYYY-MM-DD`.
- El genero es un desplegable con solo `Hombre` y `Mujer`.
- El alta de socio incluye plan inicial y metodo de pago; si el pago queda validado, la membresia se activa por el flujo existente de ventas.
- La ficha del socio usa el mismo `AppShell` lateral que el resto del sistema y separa datos personales, membresias y actividad.
- La lista y ficha de socios respetan la sucursal activa: socios con plan normal no aparecen globalmente; socios con membresia activa multi-sucursal aparecen en todas las sucursales del gimnasio.

## 8.4 Ficha de cliente

Pestañas:
- Resumen.
- Membresías.
- Pagos.
- Accesos.
- Reservas.
- Rutinas.
- Beneficios.
- Auditoría visible según permisos.

Resumen:
- foto
- estado
- plan
- vencimiento
- último acceso
- próximas reservas
- saldo o pagos pendientes

## 8.5 Punto de venta

- Buscador de cliente.
- Catálogo unificado.
- Filtros por categoría.
- Carrito.
- Beneficios aplicables.
- Método de pago.
- Confirmación.
- Recibo.

Regla de catalogo:
- Productos y servicios de mostrador se capturan en el formulario de producto.
- Las membresias se capturan en un formulario separado de plan de membresia.
- Al crear un plan, el frontend debe usar `POST /catalog/membership-plans`; el backend crea en una sola transaccion el producto tecnico `MEMBERSHIP_PLAN` y el registro `membership_plan` con duracion y dias de gracia.
- Los beneficios pueden capturarse como texto separado por comas o saltos de linea; el backend los guarda como JSON valido.
- El formulario de plan incluye `Acceso multi-sucursal`; cuando esta activo, esa membresia permite entrada en todas las sucursales del gimnasio sin duplicar al socio ni el plan.
- La lista de planes permite gestionar planes existentes: precio, duracion, dias de gracia, beneficios, estado y alcance multi-sucursal.
- La UI no debe presentar membresias junto a suplementos, ropa u otros productos fisicos, aunque el modelo de ventas use un producto tecnico para poder cobrarlas en POS.
- El alta de socio solo muestra planes reales de `GET /catalog/plans`; un producto `MEMBERSHIP_PLAN` sin `membership_plan` no debe considerarse una membresia seleccionable.

## 8.6 Reservas

Vista:
- calendario diario, semanal y agenda.
- filtros por recurso.
- disponibilidad.
- capacidad.
- estados.

## 8.7 Inventario

- listado
- existencias
- mínimos
- movimientos
- ajustes
- alertas

## 8.8 Administración

- planes
- productos
- beneficios
- recursos
- profesionales
- coaches
- automatizaciones
- usuarios
- roles
- permisos
- integraciones

## 8.9 Dashboard del dueño

Widgets:
- ingresos
- membresías activas
- renovaciones
- altas
- vencimientos
- accesos
- reservas
- inventario
- desempeño de coaches
- profesionales y servicios de bienestar
- actividad operativa por sucursal

Pantallas implementadas hasta Hito 5:
- `/reception`: hub operativo con accesos a clientes, POS, catalogo, ventas, reportes, kiosco, inventario, reservas, accesos, coaches, bienestar, automatizaciones y dashboard.
- `/coaches`: alta de coaches, rutinas, versiones, asignacion a clientes y metricas base.
- `/wellness`: alta de profesionales, servicios, disponibilidad y consulta por profesional.
- `/automations`: reglas internas, ejecucion manual y notificaciones operativas.
- `/dashboard`: vista del dueno por rango de fechas.
- `/onboarding`: checklist vendible y setup piloto de organizacion, sucursal, catalogo base y kiosco.
- `/clients/import`: importacion inicial CSV con dry-run, duplicados y resultado por fila.

Reservas:
- `/bookings` usa calendario mensual tipo agenda comun; al seleccionar un dia muestra el detalle diario por recurso y color estable.
- Nueva reserva no debe usar un desplegable de todos los socios; debe buscar por nombre, telefono o email.
- La duracion se define al crear el recurso y la reserva solo captura dia/hora de inicio; la hora final se calcula automaticamente.
- El alta de recurso no expone tipo ni capacidad al usuario; internamente el recurso se crea con capacidad operativa base.
- La duracion de recurso se captura manualmente en minutos u horas.
- Los recursos capturan dias operativos L M M J V S D con horario de apertura y cierre por dia.
- La pantalla no muestra seccion de pago de reserva en el flujo base, porque el acceso depende de membresias permitidas.
- Disponibilidad y agenda se integran en calendario mensual con detalle del dia seleccionado, evitando listas largas por hora.

## 8.10 Diseño visual

- Interfaz limpia.
- Tipografía legible.
- Botones grandes en recepción.
- Modo kiosco con controles táctiles grandes.
- Estados por iconos y etiquetas.
- No depender exclusivamente del color.
- Accesibilidad mínima WCAG AA.

## 8.11 Hito 6.5 - Rediseno frontend y marca

Objetivo:
- Mejorar la interfaz antes de continuar con nuevas fases funcionales.
- Reducir friccion para usuarios comunes de recepcion, administracion y direccion.
- Mantener contratos HTTP existentes.
- Permitir presentaciones comerciales con colores y logo del gimnasio cliente.

Implementacion frontend:
- Layout global con tokens de marca: color principal, acento, superficie, texto, bordes y tarjetas.
- Navegacion principal reutilizable por areas: Operacion, Gestion y Direccion.
- Portada convertida en centro operativo, no en pagina tecnica.
- Login redisenado con campos claros, mensajes comprensibles y continuidad hacia recepcion.
- Recepcion redisenada como tablero operativo con acciones criticas, contexto de sucursal, reportes y modulos.
- Dashboard y reporte de ventas integrados al mismo cascaron visual.
- Configuracion de organizacion integrada al layout principal.
- Personalizador de marca local con nombre visible, URL de logo y colores por cliente.

Reglas de producto:
- La personalizacion visual ya pertenece al gimnasio/tenant y se persiste en backend.
- El panel de Administrador GymOS conserva marca neutral de plataforma; gerencia, recepcion, coaches y kiosco usan la marca del gimnasio activo.
- La marca tambien viaja en `CurrentUser` para que roles sin permiso de administracion puedan ver logo, color principal, acento y fondo del gimnasio activo.
- Ninguna regla de negocio se mueve al frontend.
- Los formularios deben usar etiquetas de negocio entendibles para recepcion y administracion.

Correccion UX posterior:
- La pagina inicial no debe mostrar informacion tecnica de stack, hitos ni estado de desarrollo.
- La pagina inicial debe funcionar como entrada por rol: administracion, recepcion y kiosco.
- La administracion no debe mostrar todos los modulos al mismo nivel visual.
- Las acciones frecuentes deben tener prioridad: cobrar, buscar socio, reportar ventas y abrir kiosco.
- Las herramientas menos frecuentes deben agruparse en secciones secundarias o plegables.
- Los permisos y datos internos del usuario no deben ocupar espacio principal en la vista administrativa.
- La paleta debe combinar color principal, acento y tonos funcionales para evitar una interfaz monotona.

## 8.12 Hito 7.8 - Consolas separadas SaaS/tenant y navegacion por rol

Objetivo:
- Evitar que Admin GymOS, dueño del gimnasio, gerente, recepcion y coaches vean el mismo panel.
- Mantener un solo menu lateral visible y estable.
- Hacer que el login lleve automaticamente a la consola correcta.
- Permitir regresar a administracion de gimnasios desde cualquier pantalla cuando Admin GymOS este en modo soporte.

Consolas:
- `PLATFORM_OWNER`: entra a `/platform/gymnasiums`. Administra CRM de gimnasios cliente, configuracion base y soporte auditable.
- `TENANT_OWNER`: entra a `/dashboard`. Ve metricas, salud del negocio, sucursales, equipo y administracion de su gimnasio.
- El dueño recibe automaticamente la primera sucursal activa permitida al iniciar sesion; puede cambiar de sucursal desde el selector del shell antes de crear recursos, socios u operaciones locales.
- `TENANT_MANAGER`: entra a `/operations`. Gestiona operacion diaria, pendientes, agenda, inventario y equipo autorizado; no ve `/settings/organization`.
- `RECEPTION`: entra a `/reception`. Ve recepcion, POS, socios, ventas y reportes operativos permitidos.
- `COACH`: entra a `/coaches`. Ve coaches, rutinas y asignaciones permitidas.

Implementacion:
- `frontend/src/lib/role-model.ts` resuelve consola, ruta post-login y acceso por `console`, permisos y `supportMode`.
- `AppShell` es el unico shell visible. `GlobalSessionNav` queda como guardia silencioso de sesion y redireccion.
- `/admin` queda como redirect temporal hacia `/platform/gymnasiums`.
- El menu se declara por consola, con secciones visuales y sin duplicar navegacion lateral/superior.
- La logica de rol no usa nombres de usuario ni `username.includes("admin")`; se basa en `console`, `platformAdmin`, `supportMode` y permisos.
- La regresion automatizada de frontend debe cubrir, como minimo, el ruteo por consola de Admin GymOS, dueno, gerente, recepcion y soporte. Estas pruebas viven en `frontend/e2e` y se ejecutan con `npm run test:e2e`.
- Las pruebas de consola deben validar ausencia de rutas indebidas: recepcion no ve administracion de plataforma o equipo, gerente no ve CRM de gimnasios cliente y Admin GymOS fuera de soporte conserva experiencia de plataforma.

Administracion de gimnasios:
- `/platform/gymnasiums` funciona como CRM SaaS de gimnasios cliente y usa maestro-detalle para evitar listas largas de cards.
- La lista izquierda permite buscar y seleccionar gimnasios.
- El detalle derecho muestra datos del tenant, marca, sucursales y entrada a soporte.
- Las sucursales se presentan en tabla compacta con acciones de seleccion.
- La creacion de gimnasio se realiza en panel dedicado; no debe vivir como formulario permanente que alarga la pagina.
- El lenguaje de "piloto" y "alta rapida" no pertenece al flujo principal de administracion.

Correccion Hito 7.9:
- El alta de gimnasio en `/platform/gymnasiums` ya no mezcla la primera sucursal en el mismo formulario.
- La sucursal se crea desde una accion independiente `Crear sucursal` dentro del gimnasio seleccionado.
- La tabla de sucursales del CRM se carga con `GET /organizations/{organizationId}/branches`, por gimnasio seleccionado, sin depender del tenant activo en la sesion.
- Los botones `Datos, marca y sucursales` y `Equipo y accesos` deben entrar primero en modo soporte cuando el gimnasio seleccionado no es el contexto activo, y despues navegar a la pantalla correspondiente.
- El CRM debe limpiar la tabla de sucursales al cambiar de gimnasio y recargarla explicitamente para evitar mostrar sucursales stale de otro tenant.
- En modo soporte, la accion de una sucursal siempre debe permitir abrir herramientas; si la sucursal ya es la activa se muestra como entrada a soporte, no como boton inutil bloqueado.
- El CRM no debe duplicar herramientas operativas debajo del detalle; para entrar a administracion o soporte operativo se usa la accion de la sucursal.
- Si un gimnasio todavia no tiene sucursales, el detalle muestra un estado vacio claro y mantiene disponible la accion de crear sucursal.
- El Admin GymOS puede crear tenants completos sin dejarlos "a medias"; sucursales, usuarios y marca avanzada son pasos explicitos del detalle del cliente.
- El menu lateral usa secciones plegables; la seccion activa queda abierta y las demas permanecen compactas para reducir saturacion visual.
- En pantallas de configuracion sin sucursal activa, el menu lateral oculta modulos operativos que requieren sucursal y conserva solo opciones de configuracion seguras.
- El login y refresh de usuarios tenant reciben la primera sucursal accesible cuando existe; si una sesion guardada queda sin sucursal, `AppShell` intenta recuperar el contexto con `select-organization`.
- Las pantallas operativas de gerencia (`/inventory`, `/bookings`, `/coaches`) usan el mismo `AppShell` lateral que dashboard, ventas, usuarios y catalogo.
- En consolas tenant no se muestran acciones superiores redundantes; la navegacion principal queda centralizada en el menu lateral. `Caja / POS` forma parte del menu del gerente cuando tiene permiso `sale.create`.
- El CRM muestra estado comercial del gimnasio y accion para suspender/reactivar.
- `/users` muestra estado de cada cuenta y accion para suspender/reactivar usuarios internos.

Modo soporte:
- Admin GymOS entra a un gimnasio con `supportMode = true`.
- El shell muestra banda "Modo soporte GymOS".
- Las acciones quedan auditadas con origen `PLATFORM_SUPPORT`.
- Fuera de soporte, Admin GymOS mantiene marca neutral de plataforma.

Marca por gimnasio:
- `TenantThemeProvider` aplica tokens CSS desde la organizacion activa.
- Campos soportados: `brandName`, `logoUrl`, `primaryColor`, `accentColor`, `surfaceColor`.
- Plataforma usa tema neutral; tenant usa su propia marca en dueño, gerencia, recepcion, coaches, kiosco y pantallas internas.
- La marca lateral usa los colores del tenant y muestra el logo configurado cuando `logoUrl` existe.
- Las sucursales no tienen marca propia; todas heredan logo y colores configurados en el gimnasio.
- El logo se carga desde un selector de archivo, no escribiendo una URL manualmente.
- Los logos cargados desde frontend local se guardan en `frontend/public/tenants/<slug-del-gimnasio>/logo.*` y se exponen como `/tenants/<slug-del-gimnasio>/logo.*`.
- Docker Compose monta `frontend/public/tenants` en el contenedor frontend para preservar logos cargados durante pruebas locales.
- `primaryColor` se usa en acciones principales y titulos; `accentColor` se usa en bordes activos, chips, botones secundarios y detalles de marca; `surfaceColor` se usa como fondo real de la consola tenant.

Formularios:
- Los campos compartidos viven en `frontend/src/components/forms/FormFields.tsx`.
- Validaciones frontend minimas: email, telefono, contrasena de al menos 8 caracteres, codigo de sucursal, moneda, zona horaria y colores hexadecimales.
- Validaciones obligatorias para altas administrativas:
  - RFC mexicano de gimnasio con 4 letras, 6 numeros y 3 caracteres alfanumericos.
  - Telefonos de gimnasio, sucursal y usuario interno en formato `+52` mas 10 digitos; si el usuario captura 10 digitos, el frontend antepone `+52`.
  - Confirmacion modal nativa antes de suspender/reactivar gimnasios o usuarios.
- Al crear un usuario con rol `Dueno del gimnasio`, el frontend asigna automaticamente todas las sucursales existentes del tenant. Gerentes, recepcionistas y coaches siguen seleccionando sucursales concretas.
- Un gerente no ve ni puede seleccionar `Dueno del gimnasio` ni `Gerente` al crear o modificar usuarios; solo puede asignar roles operativos permitidos.
- Un gerente no ve la entrada `Datos y sucursales` y `canAccessRoute` no permite abrir `/settings/organization` con esa consola.
- `/users` permite cambiar el rol de usuarios existentes desde la tabla, excepto la propia cuenta del usuario autenticado.
- `AppShell` resuelve el nombre real de la sucursal activa cuando una pagina no carga la lista de sucursales por si misma, evitando mostrar solo "Sucursal seleccionada".
- Beneficios, accesos y automatizaciones deben renderizarse dentro del shell lateral unificado.
- Los errores de backend deben mostrar mensaje, codigo y `correlationId` cuando existan.
- `/reception` no debe depender de permisos administrativos como lectura completa de organizacion. Si el usuario tiene `branchId`, debe mostrar dashboard operativo aunque no pueda consultar `/organizations/current`.
- El estado sin sucursal para recepcion no debe mostrar onboarding, alta de gimnasio ni configuracion de tenant; solo selector de sucursal permitida o instruccion de pedir acceso al dueño/gerente.
- Si un usuario de recepcion tiene exactamente una sucursal permitida pero la sesion guardada no trae `branchId`, el frontend debe seleccionarla automaticamente antes de pintar la consola.
- La portada de recepcion usa `AppShell` sin menu lateral y muestra una tarjeta superior con marca, sucursal, perfil, usuario, alcance y cierre de sesion.
- Las acciones de recepcion se agrupan en la misma cuadricula: caja/POS, socios, ventas, kiosco, reservas y beneficios. No se usa una seccion desplegable secundaria para herramientas normales de mostrador.
- La paleta de recepcion debe derivar de la marca del tenant (`primaryColor`, `accentColor`, `surfaceColor`) y no de colores fijos por modulo.
- El dashboard de recepcion prioriza una banda visual con el color principal y acciones asimetricas; evita verse como una lista plana de tarjetas independientes.
- El kiosco muestra logo, nombre y colores del gimnasio propietario del terminal.
- `/settings/organization` permite configurar marca solo en la seccion del gimnasio; la seccion de sucursales solo administra datos operativos de cada sede.

## Coaches y rutinas

La pantalla actual `/coaches` con textarea JSON es un prototipo operativo, no la
experiencia final.

La solución objetivo separa:

- `/coaches`: equipo, perfiles, sucursales, estado y desempeño;
- `/exercises`: catálogo paginado, filtros, aliases, creación rápida y archivado;
- `/workouts`: biblioteca de borradores/publicadas, duplicado y versionado;
- `/workouts/new` y `/workouts/{id}/edit`: constructor visual por sesiones,
  bloques y ejercicios reordenables;
- `/workout-assignments`: asignación, historial, reemplazo, estado y PDF;
- dashboard de dueño/gerencia: semana actual/anterior, tendencia, socios únicos,
  primeras rutinas, reasignaciones y coaches sin actividad.

El constructor combina campos estructurados con prescripción libre y representa
`3x20`, `3x12, 10, 8 subiendo`, `3x24 pasos`, `3 series`, alternativas `VS`,
opcionales y notas narrativas. Crear un ejercicio inline no pierde el borrador. El
reordenamiento tiene alternativa accesible al arrastrar y soltar.

La vista previa de PDF muestra marca, socio, coach, sesiones, prescripción,
indicaciones e imágenes opcionales de movilidad/estiramiento. El archivo final se
descarga desde la asignación. La UI nunca muestra precio, cobro, comisión ni POS
para rutinas; el socio no tiene vista remota en V1.
