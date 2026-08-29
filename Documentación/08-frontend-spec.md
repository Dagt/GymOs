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

GymFort debe contemplar una experiencia de pantalla táctil para clientes dentro del gimnasio.

Principios:
- Interfaz muy simple, con botones grandes y texto legible a distancia corta.
- Sesión temporal con cierre automático por inactividad.
- Sin navegación administrativa.
- Sin exposición de datos sensibles innecesarios.
- Confirmaciones claras antes de pagos o cambios de membresía.
- Estados visibles para pagos pendientes, en revisión, aprobados o rechazados.

Funciones esperadas:
- Identificación del socio existente y registro de uno nuevo cuando la capacidad
  por sucursal está configurada.
- Resumen de membresía.
- Pagos pendientes.
- Renovación de membresía.
- Solicitud de cambio de plan.
- Cotización y redirección a Stripe Checkout/Setup cuando existe un cobro.
- Consulta de estado y comprobante confirmado por proveedor.
- Actualización de teléfono, correo y contacto de emergencia cuando esté permitido.

Estados obligatorios para un socio sin membresía:
- contratación configurada y Stripe listo: catálogo, cotización y Checkout;
- contratación configurada y Stripe pendiente: catálogo y cotización visibles,
  aviso explícito y CTA de pago deshabilitado;
- contratación deshabilitada: instrucción de acudir a recepción sin prometer
  que puede contratar;
- catálogo vacío: aviso distinto a la indisponibilidad de Stripe.

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

La renovación rápida se realiza dentro de esta portada: busca al socio, muestra
su plan y cotización y registra el cobro sin navegar a una pantalla dedicada.
No debe existir una opción lateral ni una tarjeta de navegación separada para
`/reception/renewals`; la ruta histórica redirige a `/reception`.

## 8.2.1 Equipo y alcance de sucursal

- Si un gerente solo tiene una sucursal asignada, los formularios de alta de recepcionistas o coaches deben mostrar esa sucursal seleccionada y bloqueada.
- El gerente no debe ver usuarios con rol `Dueno del gimnasio`, `Gerente` o `Administrador GymOS` en la gestion de equipo.
- El dueno del gimnasio y Admin GymOS en soporte pueden ver y administrar el equipo completo del tenant, respetando que un tenant nunca crea administradores de plataforma.
- La UI debe evitar estados donde se pueda enviar un usuario operativo sin sucursal cuando el rol requiere alcance local.

## 8.2.2 Recursos reservables

- La agenda permite crear reservas a perfiles operativos con `booking.manage`.
- La creacion, edicion y eliminacion de recursos se muestra a Admin GymOS en
  soporte, dueño o gerente que tenga `resource.manage`; recepción sólo conserva
  la operación de reservas.
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
- La fecha de nacimiento es obligatoria, se captura visualmente como
  `dd/mm/aaaa` y se envia a la API como `YYYY-MM-DD` tanto en alta como edición.
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

La ficha muestra explícitamente accesos heredados del plan activo y descuentos
asignados al socio. Cambiar plan abre un drawer funcional que explica que el
periodo actual se cancela, el nuevo inicia en la fecha operativa, no existe
prorrateo y los accesos heredados se recalculan después del pago.

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
- `Producto con variantes` ofrece un alta masiva sencilla: producto base nuevo o
  existente, datos comunes principales visibles y datos secundarios plegados en
  `Más datos del producto`. Inicia con una sola variante y muestra renglones
  mínimos de variante, código principal, SKU editable/autogenerado y `Detalles`. El
  nombre de variante puede expresar sabor, tamaño o ambos (`Manzana · 60
  porciones`). `Detalles` reemplaza temporalmente la lista con un editor enfocado
  de presentación, precio y costo; los aliases permanecen plegados dentro de ese
  editor. El alta masiva no incorpora promociones.
- `Controlar inventario` es un control compacto junto a `Stock mínimo`; no ocupa
  una tarjeta de ancho completo.
- El alta masiva no expone un control fiscal adicional que no exista en el alta
  individual; conserva el valor canónico utilizado actualmente por catálogo.
- La UI no expone `familia comercial` como entidad administrable: inicia con
  `Producto base nuevo` y ofrece `Agregar variantes a producto existente` como
  acción secundaria. El catálogo permite abrir ese flujo con el producto base
  ya seleccionado.
- `Nuevo artículo o cargo` no solicita sabor al crear un producto individual.
  Muestra el botón compacto `+ Código alternativo` inmediatamente debajo del
  código principal; no desplaza aliases a `Más datos` ni muestra explicaciones
  permanentes. Al editar una variante real sí conserva su nombre de variante.
- Enter en el código principal nunca envía el formulario: mueve el foco a la
  siguiente variante o al botón para agregarla. El flujo admite hasta 50 filas.
- El formulario individual permite varios códigos, uno por línea; el primero es
  principal y todos identifican la misma variante en POS.
- Listados y detalle muestran familia, variante y cantidad/lista de códigos sin
  fusionar existencias ni precios entre sabores.
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
- La personalización visual pertenece al tenant, admite una marca corporativa y
  perfiles de marca por sucursal, y se persiste en backend.
- El panel de plataforma conserva tema neutral; gerencia, recepción, coaches y
  kiosco usan la marca efectiva de la sucursal activa. En el menú lateral tenant
  aparecen primero el logo, nombre de marca y sucursal; debajo se muestra la sesión
  activa y la atribución GymFort queda discreta y permanente en el pie.
- La marca efectiva también viaja en `CurrentUser` para que roles sin permiso de
  administración puedan ver logo, color principal, acento y fondo de la
  sucursal activa.
- Ninguna regla de negocio se mueve al frontend.
- Los formularios deben usar etiquetas de negocio entendibles para recepcion y administracion.

Correccion UX posterior:
- La pagina inicial no debe mostrar informacion tecnica de stack, hitos ni estado de desarrollo.
- La pagina inicial debe funcionar como entrada por rol: administracion, recepcion y kiosco.
- La administracion no debe mostrar todos los modulos al mismo nivel visual.
- Las acciones frecuentes deben tener prioridad: cobrar, buscar socio y
  reportar ventas; abrir kiosco se ofrece únicamente dentro de Recepción.
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
- `TENANT_MANAGER`: entra a `/operations`. Gestiona operacion diaria, pendientes,
  agenda, inventario y equipo autorizado. Puede abrir `/settings/organization`
  cuando tiene permisos operativos de cobro, kiosco o correo, pero no ve los
  controles para editar la marca ni crear/modificar sucursales.
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
- La navegación de dueño y gerente se agrupa por propósito: `Dirección` o
  `Gerencia`, `Gestión operativa`, `Administración` y `Mostrador`. Beneficios,
  catálogo, automatizaciones y configuración pertenecen a `Administración`;
  `Mostrador` se reserva para POS y accesos físicos. La apertura del kiosco no
  forma parte de las consolas de dueño, administrador o gerente.
- La consola de recepción separa `Mostrador`, `Atención a socios` y
  `Seguimiento`, evitando mezclar reportes de pagos con las acciones inmediatas
  de caja.
- En pantallas de configuracion sin sucursal activa, el menu lateral oculta modulos operativos que requieren sucursal y conserva solo opciones de configuracion seguras.
- El login y refresh de usuarios tenant reciben la primera sucursal accesible cuando existe; si una sesion guardada queda sin sucursal, `AppShell` intenta recuperar el contexto con `select-organization`.
- Las pantallas operativas de gerencia (`/inventory`, `/bookings`, `/coaches`) usan el mismo `AppShell` lateral que dashboard, ventas, usuarios y catalogo.
- En consolas tenant no se muestran acciones superiores redundantes; la navegacion principal queda centralizada en el menu lateral. `Caja / POS` forma parte del menu del gerente cuando tiene permiso `sale.create`.
- El CRM muestra estado comercial del gimnasio y accion para suspender/reactivar.
- `/users` muestra estado de cada cuenta y accion para suspender/reactivar usuarios internos.

Modo soporte:
- Admin GymOS entra a un gimnasio con `supportMode = true`.
- El shell muestra la banda "Modo soporte GymFort".
- Las acciones quedan auditadas con origen `PLATFORM_SUPPORT`.
- Fuera de soporte, Admin GymOS mantiene la marca neutral de GymFort.

Marca por tenant y sucursal:
- `TenantThemeProvider` aplica tokens CSS del perfil efectivo: sucursal activa
  con fallback de organización.
- Campos soportados: `brandName`, `logoUrl`, `primaryColor`, `accentColor`, `surfaceColor`.
- Plataforma usa tema neutral; el contexto tenant usa la marca efectiva de la
  sucursal en dueño, gerencia, recepción, coaches, kiosco y pantallas internas.
- La marca lateral usa los colores efectivos y muestra el logo de sucursal o su
  fallback cuando `logoUrl` existe.
- Una sucursal puede definir nombre de marca, logo y colores propios. Los campos
  no configurados heredan la marca corporativa de la organización. Cambiar de
  sucursal cambia el tema efectivo sin cambiar de tenant (`CFG-003`).
- El logo se carga desde un selector de archivo, no escribiendo una URL manualmente.
- Los logos se persisten mediante los endpoints administrados de organizaciones
  y almacenamiento S3-compatible; el frontend usa el proxy de lectura
  `/api/tenant-logo/{organizationId}` y no escribe archivos del repositorio.
- `primaryColor` se usa en acciones principales y titulos; `accentColor` se usa en bordes activos, chips, botones secundarios y detalles de marca; `surfaceColor` se usa como fondo real de la consola tenant.

Formularios:
- Los campos compartidos viven en `frontend/src/components/forms/FormFields.tsx`.
- Validaciones frontend minimas: email, telefono, contrasena de al menos 8 caracteres, codigo de sucursal, moneda, zona horaria y colores hexadecimales.
- Validaciones obligatorias para altas administrativas:
  - RFC mexicano de gimnasio con 4 letras, 6 numeros y 3 caracteres alfanumericos.
  - Telefonos de gimnasio, sucursal y usuario interno en formato `+52` mas 10 digitos; si el usuario captura 10 digitos, el frontend antepone `+52`.
  - Confirmacion modal nativa antes de suspender/reactivar gimnasios o usuarios.
- Al crear un usuario con rol `Dueno del gimnasio`, el frontend asigna automaticamente todas las sucursales existentes del tenant. Gerentes, recepcionistas y coaches siguen seleccionando sucursales concretas.
- Al seleccionar el rol `Coach`, el mismo drawer de alta muestra especialidades
  y biografia; no existe un proceso de alta separado para ese tipo de usuario.
- La tabla de Equipo conserva una altura y banda de acciones uniforme para todos
  los roles; `Datos de Coach` es una accion adicional en linea, no una fila o
  panel especial.
- Un gerente no ve ni puede seleccionar `Dueno del gimnasio` ni `Gerente` al crear o modificar usuarios; solo puede asignar roles operativos permitidos.
- Un gerente no ve controles de `Datos y sucursales`; `canAccessRoute` sólo le
  permite abrir `/settings/organization` cuando tiene alguno de
  `payment.settings.manage`, `selfservice.settings.manage` o
  `notification.settings.manage`.
- `/users` permite cambiar el rol de usuarios existentes desde la tabla, excepto la propia cuenta del usuario autenticado.
- `AppShell` resuelve el nombre real de la sucursal activa cuando una pagina no carga la lista de sucursales por si misma, evitando mostrar solo "Sucursal seleccionada".
- Beneficios, accesos y automatizaciones deben renderizarse dentro del shell lateral unificado.
- Los errores de backend deben mostrar mensaje, codigo y `correlationId` cuando existan.
- Éxitos, advertencias y errores se presentan mediante el sistema global de
  notificaciones flotantes. Debe mantenerse visible sin importar el scroll,
  admitir cierre, semántica accesible y evitar banners locales duplicados.
- Automatizaciones y Configuración usan el mismo encabezado oscuro y las mismas
  superficies claras, tipografía, tarjetas y espaciado del resto de `AppShell`,
  tomando Catálogo como referencia. Configuración separa el resumen del
  gimnasio de una navegación fija de servicios conectados; Automatizaciones
  separa acciones, salud operativa, reglas y entregas recientes.
- Los títulos de grupo del menú lateral tienen mayor jerarquía tipográfica que
  los enlaces internos; la descripción activa permanece secundaria para evitar
  que submenús extensos compitan con la estructura principal.
- Gerencia ve los interruptores operativos de una cuenta Stripe existente, pero
  los botones de conectar/refrescar sólo aparecen con
  `payment.provider.stripe.manage` o `platform.admin`.
- La apertura de `/kiosk/launch` sólo aparece cuando la consola resuelta es
  `RECEPTION` y existe `selfservice.session.launch`. Dueño, administrador,
  gerente y soporte no ven la acción, no acceden por URL directa y el backend
  rechaza la creación de sesión aunque exista una concesión heredada.
- `/settings/organization` incluye la sección navegable y enlazable
  `Inscripciones`.
  Dentro de ella, `Socios e inscripción` contiene un solo campo `Periodo de
  inactividad`, colocado en el mismo bloque que `Dar de baja automáticamente`.
  Inscripción e inactividad se presentan en tarjetas compactas y sus valores
  pueden prepararse aunque la función correspondiente permanezca apagada; el
  bloque no se renderiza permanentemente fuera de las pestañas.
- Las configuraciones de kiosco y medios de cobro usan autosave para sus
  toggles. En `Inscripciones`, toggles, concepto, importe y periodo se guardan
  automáticamente con debounce controlado. Estos formularios muestran
  `Guardando...`/`Guardado`, serializan cambios rápidos y, ante error, restauran
  el último estado confirmado y muestran `AppNotice`. No existe botón global de
  guardado ni una leyenda redundante de autosave.
- Los toggles monetarios representan intención y permanecen configurables con
  Stripe pendiente. Readiness se muestra como el estado secundario discreto
  `Stripe pendiente` únicamente cuando la capacidad está encendida; si está
  apagada, la interfaz indica `Función deshabilitada` y no promete una
  habilitación automática. Readiness no apaga ni sobrescribe la preferencia
  guardada.
- En la identificación pública, el selector `Ya soy socio` / `Quiero
  registrarme` permanece centrado dentro de la tarjeta. Un fallo de verificación
  se presenta dentro de esa misma tarjeta, no altera su eje horizontal y nunca
  expone status HTTP, rutas, códigos internos ni correlation IDs al socio.
- Cambiar entre esas dos opciones reinicia primero la sesión segura en backend,
  limpia contacto, fecha y formulario y evita que el siguiente visitante herede
  una identidad asociada. La búsqueda de identidad es organizacional; la sede
  activa se aplica después a derechos y operaciones.
- `/reception` no debe depender de permisos administrativos como lectura completa de organizacion. Si el usuario tiene `branchId`, debe mostrar dashboard operativo aunque no pueda consultar `/organizations/current`.
- El estado sin sucursal para recepcion no debe mostrar onboarding, alta de gimnasio ni configuracion de tenant; solo selector de sucursal permitida o instruccion de pedir acceso al dueño/gerente.
- Si un usuario de recepcion tiene exactamente una sucursal permitida pero la sesion guardada no trae `branchId`, el frontend debe seleccionarla automaticamente antes de pintar la consola.
- La portada de recepción usa `AppShell` sin menú lateral y muestra una tarjeta
  superior con marca, sucursal, perfil, usuario, alcance y cierre de sesión.
- La portada no muestra una banda decorativa de `Caja lista`, `Pagos por revisar`
  o `Sucursal activa`; prioriza búsqueda de socio y acciones operativas reales.
- Las acciones de recepcion se agrupan en la misma cuadricula: caja/POS, socios, ventas, kiosco, reservas y beneficios. No se usa una seccion desplegable secundaria para herramientas normales de mostrador.
- La paleta de recepción debe derivar de la marca efectiva de la sucursal
  (`primaryColor`, `accentColor`, `surfaceColor`) y no de colores fijos por
  módulo; la organización aporta el fallback.
- El dashboard de recepcion prioriza una banda visual con el color principal y acciones asimetricas; evita verse como una lista plana de tarjetas independientes.
- El kiosco muestra logo, nombre y colores del gimnasio propietario del terminal.
- `/settings/organization` conserva la marca corporativa en la sección del
  gimnasio y permite editar/previsualizar la marca efectiva de cada sede desde
  la sección de sucursales. La UI debe distinguir claramente valores propios de
  valores heredados.

## Coaches y rutinas

La pantalla `/coaches` se adapta al rol. Un Coach autorizado ve Constructor,
Ejercicios, Asignaciones y Métricas. El dueño del gimnasio sólo ve Métricas y
el frontend no solicita `/workouts`, `/exercises` ni `/coaches/profiles` para
ese rol, incluso si su sesión conserva permisos antiguos.

- Equipo y accesos (`/users`): el renglón del Coach abre un único panel con
  especialidades, biografía y selección de múltiples sucursales. El estado usa
  el control existente de suspender/reactivar, evitando dos administradores de
  la misma persona.
- Constructor: biblioteca, alta/edición/duplicado/publicación, sesiones, bloques
  y ejercicios reordenables con controles de subir/bajar/eliminar.
- Ejercicios: búsqueda, alta, edición propia, archivo administrativo, aliases y
  carga/eliminación de imagen o video.
- Asignaciones: búsqueda de socio, versión publicada, vigencia, notas privadas e
  imprimibles, historial, PDF, completar, cancelar con motivo y reemplazar.
- Métricas: fechas, coach, búsqueda/selección directa de socio, periodo anterior
  comparable, KPIs, tabla por coach, historial paginado y CSV. El selector de
  8, 12, 26 o 52 semanas alimenta tres gráficas: asignaciones, socios únicos y
  primeras rutinas. El socio seleccionado se aplica a resumen, gráficas,
  detalle y exportación.

En la vista del dueño, el historial es de sólo lectura: no aparecen PDF,
completar, cancelar ni reemplazar. Estas acciones permanecen disponibles para
el Coach con sus permisos operativos.

El constructor combina campos estructurados con prescripción libre y representa
`3x20`, `3x12, 10, 8 subiendo`, `3x24 pasos`, `3 series`, alternativas `VS`,
opcionales y notas narrativas. Crear un ejercicio inline no pierde el borrador. El
reordenamiento usa botones accesibles además de puntero.

El PDF descargable muestra marca o inicial de respaldo, socio, coach, sucursal,
vigencia, sesiones, bloques, prescripción e indicaciones. El archivo final se
descarga desde la asignación. La UI nunca muestra precio, cobro, comisión ni POS
para rutinas; el socio no tiene vista remota en V1.

## Planes y paquetes de visitas

- El editor de plan muestra “Disponible para contratar en kiosco” e
  “Inscripción: configuración general / no cobrar”. Un plan privado sigue
  disponible para Recepción.
- `SPECIAL_PRICE` exige seleccionar planes; `ACCESS` permite alcance local o de
  cobertura del plan sin copiar el beneficio a otra sede. La UI aclara primero
  qué planes lo incluyen y después si será válido sólo en la sucursal actual o
  en las sucursales cubiertas por cada plan.
- Catálogo incorpora una sección propia “Paquetes de visitas” con un formulario
  breve: nombre, SKU, precio, visitas, vigencia, sedes, accesos, inscripción,
  kiosco y estado.
- En ese formulario, la sucursal activa se presenta como “Sucursal base”, sin
  checkbox ni forma de quitarla. La lista sólo muestra otras sucursales que se
  pueden agregar; por ello no existe visualmente la combinación “sólo B” al
  operar en A. “Accesos que
  consumen una visita” muestra el alcance propio de cada acceso y explica que el
  paquete no lo amplía; Sauna local de Centro sigue siendo sólo de Centro aunque
  el saldo cubra también Tecnológica. Una sede sin ningún acceso disponible no
  puede seleccionarse; al usar varias sedes, la unión de accesos elegidos debe
  cubrirlas todas. Los accesos incompatibles se deshabilitan y muestran en qué
  sedes sí descuentan.
- Cada alta vive dentro de su propia pestaña: artículos/cargos y variantes en
  “Artículos”, planes en “Planes de membresía”, paquetes en “Paquetes de visitas”
  y categorías en “Categorías”. No existe una barra global que mezcle acciones.
- El expediente muestra como máximo un paquete abierto, su saldo/vigencia y el
  historial de consumos. Si existe uno pendiente o activo, oculta el CTA de
  compra y explica el motivo.
- Kiosco distingue intención de compra y readiness de pago igual que las
  membresías: con Stripe pendiente puede mostrar opciones, pero no inicia
  Checkout.
- En el kiosco, planes de membresía y paquetes de visitas comparten una sola
  cuadrícula de opciones. Una membresía muestra únicamente su duración; un
  paquete muestra visitas y vigencia (por ejemplo, “15 visitas · 30 días”). No
  se muestran días de gracia y el botón de revisión conserva un ancho compacto,
  acorde con su texto.
- La pestaña de configuración del kiosco ofrece por sucursal el toggle
  “Comprar paquetes de visitas” con autosave. El indicador de Stripe sólo
  aparece si el toggle está encendido; apagarlo no altera las definiciones del
  catálogo.
- El POS genérico identifica los paquetes configurados y dirige al flujo
  dedicado; no permite cobrar el servicio sin crear su entitlement.
- Registro e identificación se consideran completos al recibir el resumen del
  socio. Si falla después una consulta opcional del dashboard, éste conserva el
  acceso y muestra un aviso breve; nunca presenta falsamente “datos incorrectos”
  ni expone ruta HTTP, código interno o diagnóstico técnico.

- `Socios e inscripción` y `Medios de cobro` indican explícitamente la sucursal
  activa. Las cuentas de proveedor se comparten, pero inscripción, inactividad,
  terminal, checkout y domiciliación se habilitan independientemente por sede.
- En el kiosco, renovación y cambio usan la misma acción primaria; no aparecen
  numeraciones decorativas ni una tarjeta duplicada de domiciliación dentro de
  Membresías. La revisión de una contratación queda centrada.
- En paquetes, los accesos ajenos a las sucursales elegidas se ocultan y cada
  acceso seleccionado presenta su modo en una fila compacta.
