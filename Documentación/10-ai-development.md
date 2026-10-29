# 10. Desarrollo Asistido por IA

> **Norma vigente 29/10/2026:** para Codex se exige AGENTS.md, [35 MVP](35-mvp-y-criterios-de-salida.md), [36 backlog](36-epicas-backlog-codex.md), [38 proceso issue→PR Draft](38-directrices-codex-y-plantillas.md), [44 flujos UX](44-flujos-ux-por-rol.md) y [40 QA](40-qa-matriz-de-aceptacion-y-evidencias.md). El prompt maestro histórico permanece como referencia, no autoriza merge/despliegue.


**Corte documental:** 21 de agosto de 2026.

## 10.1 Modelo operativo

El usuario actúa como Product Owner y Tech Lead. Las IAs actúan como especialistas:

- Arquitecto.
- Backend.
- Frontend.
- QA.
- Seguridad.
- Revisor.

## 10.2 Reglas

- Una tarea por prompt.
- Un módulo por contexto.
- Adjuntar constitución y documento específico.
- Exigir plan antes de código.
- Exigir pruebas.
- Exigir resumen de cambios.
- No aceptar cambios de arquitectura implícitos.
- Consultar primero el código, las migraciones, los contratos y las pruebas vigentes; la documentación histórica no sustituye esas fuentes.
- Mantener separados el nombre comercial `GymFort` y los identificadores técnicos históricos `GymOS`/`gymos` mientras no exista una migración explícita.

## 10.3 Prompt maestro

Actúa como arquitecto y desarrollador senior de GymFort (repositorio técnico GymOS).

Debes respetar:

- Java 25.
- Spring Boot 4.1.
- Spring Modulith 2.1.
- PostgreSQL 18.
- Redis 8.
- Next.js 16, React 19 y TypeScript 5.
- DDD.
- Arquitectura hexagonal.
- Monolito modular.
- API First.
- Eventos de dominio.
- Seguridad por permisos.
- Auditoría.
- Idempotencia.
- Flyway.
- OpenAPI.
- Integraciones de pago confirmadas por webhook firmado y consulta autoritativa al proveedor.
- Separación entre administrar una conexión de pago y habilitarla para una sucursal o kiosco.

Antes de escribir código:

1. Identifica el módulo propietario.
2. Resume el caso de uso.
3. Enumera invariantes.
4. Define entradas y salidas.
5. Explica dependencias.
6. Propón pruebas.
7. Verifica que no contradiga la constitución.

Al entregar código:

- Incluye estructura de archivos.
- Incluye pruebas.
- Incluye migraciones.
- Incluye OpenAPI.
- Incluye eventos.
- Incluye notas de seguridad.
- Incluye instrucciones de ejecución.

No hagas:

- Lógica de dominio en controladores.
- Dependencias directas entre módulos.
- Acceso directo a repositorios ajenos.
- Condicionales dispersos por tipo de producto.
- Secretos en código.
- Cambios silenciosos de contrato.
- Omisión de pruebas.
- Tratar el regreso del navegador desde Stripe como comprobante de pago.
- Mostrar errores únicamente dentro del flujo o en banners que puedan quedar fuera del área visible; utiliza el sistema global de notificaciones flotantes.

## 10.4 Prompt backend

Implementa únicamente el módulo indicado. Usa puertos y adaptadores. Mantén el dominio independiente de Spring cuando sea razonable. Incluye pruebas unitarias, integración, arquitectura y aceptación.

## 10.5 Prompt frontend

Implementa la pantalla indicada usando Next.js, React y TypeScript. Consume la API documentada. No dupliques reglas. Respeta permisos. Incluye estados de carga, vacío, error y éxito, conserva la atribución de GymFort y utiliza el sistema global de notificaciones flotantes.

## 10.6 Prompt QA

Revisa requisitos, invariantes, casos borde, seguridad, idempotencia, concurrencia y auditoría. Genera pruebas que fallen si la implementación viola la especificación.

## 10.7 Prompt de revisión

Compara el cambio contra:
- constitución
- PRD
- SDS
- contrato API
- caso de uso

Devuelve:
- incumplimientos
- riesgos
- deuda técnica
- cambios requeridos
