# 25. Registro General de Hallazgos, Auditoría y Deuda Técnica de la Plataforma GymOS

Estado: VIGENTE / AUDITADO Y ACTUALIZADO  
Fecha de revisión: 25 de julio de 2026  
Módulos auditados: `wellness`, `bookings`, `inventory`, `catalog`, `sales`, `memberships`, `access`, `identity`, `security`  

---

## 1. Resumen Ejecutivo de la Plataforma

GymOS cuenta con una base de código estabilizada y modular en Java 25 (Spring Boot 4.1.0) y Next.js 16 (TypeScript / TailwindCSS), desplegada sobre contenedores Docker (`postgres:18-alpine`, `redis:8-alpine`, `minio`).

### Estado de Validación Local:
- **Backend:** Compilación Java 25 y ejecución de pruebas unitarias/modulares exitosas (`BUILD SUCCESSFUL`).
- **Frontend:** TypeScript sin errores (`npx tsc --noEmit` con 0 errores), 28/28 pruebas unitarias pasando.
- **Base de Datos:** Migraciones Flyway al día hasta `V46__wellness_role_permissions.sql`.

---

## 2. Hallazgos y Deudas Técnicas RESUELTAS (Cerradas)

### 2.1 Módulo Wellness (Servicios de Bienestar) [CERRADO]
- **Error SQL 500 en `/wellness/appointments`:** Corregido el `JOIN` a la relación `person cp` para obtener nombre completo y contacto del socio (`cp.first_name || ' ' || cp.last_name`).
- **Permisos de Seguridad por Rol (`V46`):** Creada la migración `V46__wellness_role_permissions.sql` que otorga `professional.manage` a Gerente y `professional.read` a Recepción. Recepción opera en modo lectura para el directorio y sólo agenda/confirma citas.
- **Duplicación de Bloques de Disponibilidad:** Incorporada la purga previa de horarios por día al guardar y la deduplicación en servidor por `startsAt` en `WellnessService.java`.
- **Eliminación en Cascada de Especialistas:** Creado endpoint `@DeleteMapping("/api/v1/professionals/{id}")` respaldado por borrado suave en cascada (`deleted_at = NOW()`) de perfil, servicios, disponibilidad y citas activas.
- **Zona Horaria y Métrica "Hoy":** Sustituida la conversión UTC `toISOString()` por `toDateInput(new Date())` para preservar la fecha local real (Sábado 25 de Julio).
- **Simplificación UX a 2 Pestañas:** Unificada la consola en 2 pestañas (`Agenda & Solicitudes` y `Equipo Profesional`), derivando automáticamente el servicio de consulta según la especialidad y con formulario modal de 2 columnas.
- **Calendario Visual & Coordinación Externa:** Vista por defecto en `Vista Mes`, mostrando badges y datos del socio sin clic previo, e incorporando el Modal de Coordinación Externa para fijar fecha/hora acordadas.

### 2.2 Arquitectura Backend (`ARCH-001`) [CERRADO]
- **Desacoplamiento de `JdbcClient` en Servicios de Aplicación:** Se eliminó la dependencia directa de `JdbcClient` en `SalesService`, `ClientService`, `CatalogService`, `OrganizationService`, `OnboardingService` y `WellnessService`, migrándolos a puertos y repositorios en la capa de infraestructura. Solo `AuditService` conserva acceso directo por diseño.

### 2.3 Módulo Reservas (`RF-001`, `RF-002`, `RF-003`) [CERRADO]
- **Eliminación de Manipulación del DOM:** Se removieron `BookingsNotesEnhancer` y `BookingsRoleLayoutEnhancer` (que usaban `MutationObserver`).
- **Refactorización de Página Monolítica:** Se dividió `bookings/page.tsx` en controlador (`useBookingsController.ts`), componentes (`components.tsx`) y formularios (`forms.tsx`) usando estado nativo React.

### 2.4 Módulo Inventarios (`INV-001`, `INV-002`) [CERRADO]
- **Permisos de Gerente:** Acotada la regla `PUT` en `SecurityConfig` para permitir la consulta `GET /branches/all`.
- **Integridad de Stock:** Publicación automática de productos al recibir transferencias (`catalog.attachBranch`), validación preventiva de stock negativo y política configurable por sucursal (`allow_negative_stock`).

---

## 3. Matriz de Deuda Técnica Pendiente y Roadmap de Producción

### P0 — Críticos / Bloqueantes para Producción

| ID | Módulo | Descripción / Riesgo | Solución Requerida | Estado |
| :--- | :--- | :--- | :--- | :--- |
| **P0.1** | Security / Auth | **Refresh Token en localStorage:** Exposición del token en almacenamiento local frente a vulnerabilidades XSS. | Migrar a cookies `HttpOnly`, `Secure` y `SameSite=Strict` con rotación por familia. | PENDIENTE |
| **P0.2** | Config / Infra | **Secretos en Defaults de Desarrollo:** Inseguridad si se despliega en producción con valores por defecto. | Validar variables de entorno en el arranque para perfil `production` (exigir JWT secret >= 256 bits). | PENDIENTE |
| **P0.3** | Multi-Tenancy | **Aislamiento Multi-Tenant Negativo:** Poca evidencia automatizada probando peticiones cruzadas con UUIDs de otros gimnasios. | Implementar suite `MultiTenantIsolationTests` con Testcontainers asegurando respuestas `404/403` semánticas. | PENDIENTE |

---

### P1 — Deuda Funcional y Arquitectónica Importante

| ID | Módulo | Descripción / Riesgo | Solución Requerida | Estado |
| :--- | :--- | :--- | :--- | :--- |
| **P1.1** | Wellness / Notif | **Avisos Automáticos para Especialistas Externos:** La solicitud requiere que recepción llame o envíe WhatsApp manualmente. | Conectar evento `WELLNESS_APPOINTMENT_CREATED` al despachador de correo/SMS para notificar al especialista externo. | PENDIENTE |
| **P1.2** | Wellness / POS | **Cobro de Consultas en Caja:** Las citas completadas muestran honorarios pero no generan partida automática en caja. | Permitir liquidar o generar la venta del servicio de bienestar directamente en la consola POS (`/pos`). | PENDIENTE |
| **P1.3** | Memberships | **Semántica de Término de Membresía:** Ambigüedad sobre si `ends_on` es inclusivo o exclusivo por zona horaria de sucursal. | Estandarizar la evaluación de vencimiento a las 23:59:59 del día de término en hora local de la sucursal. | PENDIENTE |
| **P1.4** | Inventory | **Paginación Parametrizada:** Consultas de transferencias y conteos físicos limitadas a `LIMIT 50`. | Incorporar parámetros `page` y `size` en los repositorios JDBC de inventarios. | PENDIENTE |

---

### P2 — Experiencia de Usuario y Operación Futura

| ID | Módulo | Descripción / Riesgo | Solución Requerida | Estado |
| :--- | :--- | :--- | :--- | :--- |
| **P2.1** | Mobile API | **Endpoints para App Móvil de Socios:** Ausencia de API pública/cliente para auto-servicio de citas desde celular. | Diseñar e implementar endpoints de lectura de directorio y solicitud de citas desde la app móvil del socio. | PENDIENTE |
| **P2.2** | QA / CI | **Pruebas Headless E2E en CI:** Falta ejecutar Playwright de forma automatizada en el pipeline de GitHub Actions. | Habilitar el job de Playwright en `ci.yml` para validar flujos de login, POS, reservas y wellness. | PENDIENTE |
| **P2.3** | Operations | **Rigor de Backup & Restore Probad:** Existen scripts `.ps1` pero falta ejecutarlos en entorno limpio de pruebas. | Ejecutar prueba formal de restauración de PostgreSQL y documentar la evidencia en `docs/operations/restore.md`. | PENDIENTE |

---

## 4. Conclusión y Recomendación

La plataforma **GymOS** se encuentra en estado **ESTABILIZADO** y con sus módulos principales (`Ventas`, `Inventario`, `Catálogo`, `Reservas` y `Wellness`) operando con patrones limpios, código tipo-seguro y arquitectura desacoplada.

Para alcanzar el nivel **PILOT_READY / PRODUCTION_READY**, la prioridad debe enfocarse en resolver los puntos **P0.1** (Refresh token en cookie `HttpOnly`) y **P0.3** (Suite automatizada de aislamiento multi-tenant).
