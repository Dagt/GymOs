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

- Nuevo cliente.
- Inscripción.
- Renovación.
- Venta rápida.
- Reserva.
- Buscar cliente.
- Acceso manual.
- Pagos por validar.
- Kioscos activos.

Debe mostrar:
- clientes por vencer hoy
- reservas próximas
- pagos pendientes
- alertas operativas

## 8.3 Alta de cliente

Secciones:
- Datos personales.
- Foto.
- Contacto.
- Beneficios.
- Plan inicial.
- Método de pago.

Debe permitir alta rápida y alta completa.

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

## 8.12 Hito 7.7 - Consolas separadas y navegacion por rol

Objetivo:
- Evitar que el Administrador GymOS, el administrador del gimnasio, gerencia, recepcion y coaches vean el mismo panel.
- Mantener un solo menu lateral visible y estable.
- Hacer que el login lleve automaticamente a la consola correcta.
- Permitir regresar a administracion de gimnasios desde cualquier pantalla cuando el usuario sea Administrador GymOS.

Consolas:
- `PLATFORM_ADMIN`: entra a `/platform/gymnasiums`. Administra altas de gimnasios, seleccion de tenant, sucursales base, marca y usuarios iniciales.
- `GYM_ADMIN`: entra a `/dashboard`. Opera como dueno/administrador del gimnasio dentro del tenant activo.
- `MANAGER`: entra a `/dashboard`. Consulta direccion, reportes y operacion autorizada segun permisos.
- `RECEPTION`: entra a `/reception`. Ve recepcion, POS, socios, ventas y reportes operativos permitidos.
- `COACH`: entra a `/coaches`. Ve coaches, rutinas y asignaciones permitidas.

Implementacion:
- `frontend/src/lib/role-model.ts` resuelve consola, ruta post-login y acceso por permisos.
- `AppShell` es el unico shell visible. `GlobalSessionNav` queda como guardia silencioso de sesion y redireccion.
- `/admin` queda como redirect temporal hacia `/platform/gymnasiums`.
- El menu se declara por consola, con secciones visuales y sin duplicar navegacion lateral/superior.
- La logica de rol no usa nombres de usuario ni `username.includes("admin")`; se basa en permisos, especialmente `platform.admin`.

Administracion de gimnasios:
- `/platform/gymnasiums` usa maestro-detalle para evitar listas largas de cards.
- La lista izquierda permite buscar y seleccionar gimnasios.
- El detalle derecho muestra datos del tenant, marca y sucursales.
- Las sucursales se presentan en tabla compacta con acciones de seleccion.
- La creacion de gimnasio se realiza en panel dedicado; no debe vivir como formulario permanente que alarga la pagina.
- El lenguaje de "piloto" y "alta rapida" no pertenece al flujo principal de administracion.

Marca por gimnasio:
- `TenantThemeProvider` aplica tokens CSS desde la organizacion activa.
- Campos soportados: `brandName`, `logoUrl`, `primaryColor`, `accentColor`, `surfaceColor`.
- Plataforma usa tema neutral; tenant usa su propia marca en gerencia, recepcion, kiosco y pantallas internas.

Formularios:
- Los campos compartidos viven en `frontend/src/components/forms/FormFields.tsx`.
- Validaciones frontend minimas: email, telefono, contrasena de al menos 8 caracteres, codigo de sucursal, moneda, zona horaria y colores hexadecimales.
- Los errores de backend deben mostrar mensaje, codigo y `correlationId` cuando existan.
