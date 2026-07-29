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
Puede administrar su perfil, consultar o ampliar el catálogo de ejercicios, crear y versionar rutinas, asignarlas a socios dentro de su alcance y descargar el PDF imprimible. No registra cobros de rutinas en GymOS.

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

Los pagos cubiertos por este apartado corresponden a productos y servicios comercializados por el gimnasio. Los acuerdos o cobros en efectivo entre coach y socio por una rutina se realizan fuera de GymOS y no generan ventas, pagos, comisiones, ingresos ni movimientos de caja en la plataforma.

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
- Servicios comercializados por el gimnasio.
- Consultas.
- Operaciones rápidas.

Las rutinas creadas o asignadas por coaches no son artículos del POS en V1.

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
- Perfil de coach vinculado con la identidad interna existente.
- Alcance de coach por organización y sucursal.
- Catálogo inicial amplio de ejercicios.
- Búsqueda por nombre, alias, grupo muscular, patrón de movimiento, tipo y equipamiento.
- Alta de ejercicios faltantes por organización o coach, con detección de duplicados y archivado lógico.
- Rutinas estructuradas por días o sesiones, bloques y ejercicios ordenados.
- Prescripción flexible para series, repeticiones, carga, intensidad, descanso, tempo, duración, distancia, pasos y notas libres.
- Soporte para calentamiento, movilidad, fuerza, cardio, core, superseries, circuitos, ejercicios opcionales y estiramiento.
- Borradores, publicación y versionado inmutable.
- Asignación de una versión específica a un socio elegible.
- Historial de asignaciones y reemplazos sin alterar versiones anteriores.
- Generación y descarga de PDF imprimible para entrega física al socio.
- Métricas semanales de asignaciones por coach, sucursal y socio.
- Socios únicos atendidos, primera rutina, reasignaciones y coaches sin actividad.
- Exportación del detalle para supervisión.

Reglas de alcance:

- La creación o asignación de una rutina no genera una venta ni un pago.
- El efectivo cobrado directamente por el coach no se registra en GymOS.
- Las métricas miden involucramiento operativo, no ingresos ni comisiones.
- La aplicación móvil o portal remoto para que el socio consulte su rutina queda fuera de V1.
- El dominio y los contratos deben quedar preparados para que una futura app consuma la versión asignada.
- El alcance y estado rector del módulo está en `modulos/14-coaching.md`.

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
- Desempeño de coaches medido por asignaciones de rutinas y socios atendidos.
- Ingresos por categoría, excluyendo rutinas externas de coaches.

## 2.4 Exclusiones V1

- App móvil o portal remoto completo para clientes.
- Visualización remota de rutinas por el socio.
- Pasarela de pagos obligatoria en producción.
- Cobro de rutinas de coaches por POS o pasarela.
- Registro del efectivo recibido por el coach.
- Comisiones, nómina o liquidaciones de coaches.
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
- Generación de PDF estable, legible y reproducible desde la versión asignada.
- Búsqueda paginada y extensible del catálogo de ejercicios.
