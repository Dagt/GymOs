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

## 8.10 Diseño visual

- Interfaz limpia.
- Tipografía legible.
- Botones grandes en recepción.
- Modo kiosco con controles táctiles grandes.
- Estados por iconos y etiquetas.
- No depender exclusivamente del color.
- Accesibilidad mínima WCAG AA.
