# 2. Product Requirements Document

## 2.1 Objetivo del producto

GymOS debe permitir que un gimnasio opere el 100% de su actividad interna en una sola herramienta web, sin depender de hojas de cálculo, mensajes informales o sistemas desconectados.

El producto debe ser vendible desde su primera versión operativa. Aunque los primeros clientes objetivo sean gimnasios específicos, GymOS debe diseñarse como SaaS reutilizable para gimnasios independientes y cadenas con varias sucursales.

## 2.2 Usuarios

### Administrador del sistema
Acceso total a configuración, seguridad, integraciones y auditoría.

### Administrador de plataforma
Rol interno del proveedor de GymOS. Puede administrar organizaciones cliente, planes comerciales, parámetros globales, soporte técnico y operaciones de mantenimiento autorizadas.

### Propietario / Gerente
Acceso a métricas, reportes, configuración de negocio y supervisión.

### Gerente de sucursal
Acceso operativo y reportes de una sucursal específica, según permisos asignados por la organización.

### Recepcionista
Usuario operativo principal. Debe poder registrar clientes, cobrar, renovar, vender, reservar y resolver excepciones con velocidad.

### Coach
Puede gestionar rutinas, asignaciones y seguimiento de clientes según permisos.

### Profesional de bienestar
Puede administrar servicios, disponibilidad y citas.

### Cliente
Actor externo contemplado desde el diseño. Puede interactuar con GymOS desde un kiosco o pantalla de autoservicio dentro del gimnasio. La app móvil o portal remoto completo no se construye en V1.

### Cliente en kiosco
Cliente usando una pantalla compartida dentro del gimnasio. Puede consultar información limitada, iniciar pagos, renovar o solicitar cambio de membresía y actualizar datos permitidos mediante una sesión temporal y restringida.

## 2.3 Alcance funcional V1

### Personas y clientes
- Alta de personas.
- Registro de clientes.
- Foto.
- Nombre completo.
- Fecha de nacimiento.
- Teléfono.
- Correo.
- Contacto de emergencia.
- Estados comerciales.
- Historial.

### Usuarios y seguridad
- Autenticación.
- JWT de corta duración y refresh tokens revocables.
- Recuperación de acceso.
- Roles.
- Permisos configurables.
- Contexto de organización y sucursal.
- Auditoría.
- Bloqueo y desactivación.

### Organizaciones y sucursales
- Alta de organización cliente.
- Configuración básica de organización.
- Alta de sucursales.
- Usuarios asignables a una o varias sucursales.
- Operaciones filtradas por organización y sucursal cuando aplique.
- Preparación para cadenas con varias sucursales desde V1.

### Planes y membresías
- Planes configurables.
- Normal.
- Premium.
- Estudiante.
- Visita.
- Duraciones mensuales, trimestrales, semestrales y anuales.
- Renovación anticipada acumulativa.
- Sin congelamiento.
- Sin pagos parciales.
- Renovación automática futura.
- Reglas de acceso por plan.

### Beneficios y descuentos
- Estudiante.
- Adulto mayor.
- Convenios.
- Promociones temporales.
- Evidencia requerida.
- Vigencia.
- Revalidación periódica.

### Pagos
- Efectivo.
- Transferencia.
- Tarjeta.
- Registro de comprobantes.
- Validación manual de transferencias.
- Procesamiento interno y manual en V1.
- Inicio de pagos desde kiosco de autoservicio.
- Integración futura con pasarela.
- Recibos y notificaciones.

### Kiosco de autoservicio
- Pantalla interna para uso de clientes dentro del gimnasio.
- Identificación segura del cliente mediante mecanismo configurable.
- Consulta de estado de membresía.
- Consulta de pagos pendientes.
- Renovación de membresía.
- Solicitud de cambio de plan.
- Inicio de pago interno/manual.
- Carga o captura de comprobante cuando aplique.
- Actualización de datos personales permitidos.
- Sesiones temporales con cierre automático.
- Auditoría de acciones realizadas desde kiosco.

### Punto de venta
- Membresías.
- Visitas.
- Productos físicos.
- Dosis o consumos simples sin control fino de gramos.
- Reservas.
- Servicios.
- Rutinas.
- Consultas.
- Operaciones rápidas.

### Inventario
- Control opcional por producto.
- Productos empacados.
- Entradas.
- Salidas.
- Ventas.
- Ajustes.
- Mermas.
- Umbrales.
- Auditoría.

### Reservas
- Recursos reservables.
- Sauna.
- Tina fría.
- Clases.
- Consultorios.
- Capacidad.
- Horarios.
- Duración.
- Anticipación.
- Cancelación.
- No traslapes.
- Validación de derecho a reservar.

### Control de acceso
- Torniquetes.
- Reconocimiento facial.
- Acceso manual.
- Acceso temporal.
- Registro de intentos.
- Motivos de rechazo.
- Acceso múltiple libre por defecto.
- Restricciones futuras configurables.
- Independencia de proveedor.

### Entrenadores y rutinas
- Perfil de coach.
- Rutinas estructuradas.
- Versionado.
- Asignación a clientes.
- Venta de rutinas.
- Métricas por coach.

### Profesionales de bienestar
- Quiropráctico.
- Nutriólogo.
- Fisioterapeuta.
- Servicios.
- Agenda.
- Reservas.
- Cobros.

### Notificaciones
- Correo.
- WhatsApp.
- Push futuro.
- Pago recibido.
- Membresía próxima a vencer.
- Membresía vencida.
- Reserva confirmada.
- Reserva cancelada.

### Automatizaciones
- Evento.
- Condición.
- Acción.
- Reglas configurables.
- Reintentos.
- Historial.

### Reportes
- Ventas diarias y mensuales.
- Nuevas membresías.
- Renovaciones.
- Clientes por vencer.
- Membresías vencidas.
- Accesos por horario.
- Uso de recursos.
- Inventario bajo.
- Desempeño de coaches.
- Ingresos por categoría.

## 2.4 Exclusiones V1

- App móvil o portal remoto completo para clientes.
- Pasarela de pagos obligatoria en producción.
- Microservicios.
- Control de gramos exactos por scoop.
- Contabilidad completa.
- Nómina.
- CRM externo.
- Expediente clínico completo.

## 2.5 Requisitos no funcionales

- Operaciones comunes con respuesta perceptible inferior a un segundo en condiciones normales.
- Diseño responsive.
- Modo kiosco optimizado para pantalla táctil compartida.
- Disponibilidad continua.
- Seguridad y auditoría.
- Trazabilidad.
- Aislamiento estricto por organización.
- Soporte de contexto de sucursal desde el modelo y permisos.
- Migraciones controladas.
- Backups.
- Escalabilidad horizontal futura.
