# 1. Constitución del Proyecto

## 1.1 Propósito

Esta constitución es la referencia oficial y vinculante para el diseño, desarrollo, prueba y mantenimiento de GymFort (repositorio técnico GymOS). Cuando exista contradicción entre el código, una implementación generada por IA y este documento, prevalece esta constitución hasta que se apruebe un cambio formal.

## 1.2 Misión

Desarrollar una plataforma SaaS de clase empresarial que permita a gimnasios y centros de bienestar administrar su operación completa en un único sistema, reduciendo carga operativa, minimizando errores administrativos y facilitando la toma de decisiones mediante datos y automatización.

## 1.3 Visión

Convertir GymFort en el sistema operativo de referencia para negocios de entrenamiento y bienestar, integrando personas, servicios, recursos, membresías, pagos, accesos, reservas, profesionales y analítica en una sola plataforma extensible.

GymFort debe nacer como producto comercializable, no como una implementación a medida para un solo gimnasio. La primera versión debe poder operar gimnasios independientes y dejar soporte real para cadenas con varias sucursales desde el modelo, la seguridad, la configuración y los datos.

## 1.4 Objetivos estratégicos

- Centralizar la información en una sola fuente de verdad.
- Reducir tiempos operativos en recepción y administración.
- Automatizar tareas repetitivas.
- Eliminar dependencias de Excel, mensajes dispersos e Instagram/WhatsApp como canal informal de comprobantes.
- Permitir crecimiento modular sin reescrituras masivas.
- Permitir venta a gimnasios independientes y cadenas con varias sucursales.
- Mantener una experiencia consistente para recepción, administración y futuras aplicaciones de cliente.
- Habilitar autoservicio dentro del gimnasio mediante kioscos o pantallas compartidas.
- Facilitar desarrollo colaborativo entre humanos y modelos de IA.

## 1.5 Filosofía de producto

### Configuración antes que programación
La plataforma debe adaptarse mediante parámetros, reglas y catálogos. Los cambios de código deben reservarse para nuevas capacidades reales.

### Modelar conceptos, no casos particulares
No se programan “saunas” o “tinas” como entidades rígidas. Se modelan recursos reservables. No se programan tres tipos fijos de membresía. Se modelan planes configurables.

### Una sola fuente de verdad
Cada dato tiene un módulo propietario. Ningún otro módulo debe duplicarlo.

### Experiencia antes que complejidad
Las operaciones frecuentes deben ser rápidas y comprensibles. La plataforma debe reducir trabajo, no agregarlo.

### Todo es auditable
Las acciones críticas deben registrar quién, cuándo, qué cambió, desde dónde y por qué.

### Automatización como capacidad nativa
Los eventos de negocio deben poder disparar notificaciones, tareas o acciones sin cambios de código.

### Extensibilidad controlada
La arquitectura debe permitir agregar módulos y proveedores sin romper los existentes.

### Producto vendible desde el inicio
Cada hito debe acercar el sistema a una versión operable por un gimnasio real. Las decisiones técnicas deben priorizar una base robusta, mantenible y comercializable, evitando prototipos desechables.

## 1.6 Principios de arquitectura

- API First.
- Monolito modular.
- Domain-Driven Design.
- Arquitectura hexagonal.
- Comunicación entre módulos mediante contratos y eventos.
- Integraciones mediante puertos y adaptadores.
- Alta cohesión y bajo acoplamiento.
- Idempotencia en pagos, renovaciones y consumo de eventos.
- Observabilidad desde el inicio.
- Seguridad por diseño.
- Multi-organización y multisucursal desde la base del modelo.
- Modularidad comercial: las capacidades pueden habilitarse o licenciarse por módulo.

## 1.7 Principios de desarrollo asistido por IA

- Ninguna IA decide arquitectura por su cuenta.
- Ningún módulo se desarrolla sin especificación previa.
- Las IAs deben leer esta documentación antes de generar código.
- Cada cambio debe producir pruebas.
- Toda salida generada por IA debe ser revisada.
- Se debe trabajar módulo por módulo.
- Se debe mantener documentación viva por módulo.
- Ninguna IA debe introducir dependencias nuevas sin justificarlo.
- Ninguna IA debe modificar contratos públicos sin registrar el cambio.

## 1.8 Stack base

Backend:
- Java 25.
- Spring Boot.
- Spring Modulith.
- Spring Security.
- Spring Data JPA / Hibernate.
- Flyway.
- PostgreSQL.
- Redis.
- OpenAPI.

Frontend:
- Next.js.
- React.
- TypeScript.
- Tailwind CSS.
- Componentes accesibles y consistentes.

Infraestructura:
- Docker.
- Reverse proxy.
- Almacenamiento compatible con S3.
- CI/CD automatizado.
- Despliegue inicial en VPS o cloud sin dependencia fuerte de un vendor.

## 1.9 Decisiones fundacionales confirmadas

- El repositorio recomendado es un monorepo con `backend/`, `frontend/`, `infra/` y `Documentación/`.
- La primera versión debe incluir frontend web desde el primer hito útil.
- La autenticación debe ser real desde el inicio: JWT de corta duración, refresh tokens, roles y permisos.
- El sistema debe modelar `Organization` y `Branch` desde la base para permitir venta a gimnasios independientes y cadenas.
- V1 admite efectivo, transferencia y terminal externa con validación manual,
  además de proveedores integrados. Stripe Checkout/Setup atiende el kiosco y
  Mercado Pago Point atiende recepción cuando el gimnasio los vincula.
- Ningún retorno del navegador confirma un pago: los efectos comerciales se
  aplican sólo con webhook firmado, consulta autoritativa e idempotencia.
- El almacenamiento local recomendado para fotos y comprobantes es S3-compatible mediante MinIO en Docker Compose.
- El kiosco de autoservicio forma parte de V1 como pantalla interna del gimnasio,
  con apertura exclusiva de Recepción, sesiones temporales, permisos
  restringidos, privacidad y auditoría.
