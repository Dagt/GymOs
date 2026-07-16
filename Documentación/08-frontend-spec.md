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
- La personalizacion visual local sirve para demos, pilotos y venta temprana.
- La persistencia multi-tenant de tema en backend queda para evolucion posterior.
- Ninguna regla de negocio se mueve al frontend.
- Los formularios deben usar etiquetas de negocio entendibles para recepcion y administracion.
