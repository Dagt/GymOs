# 10. Desarrollo Asistido por IA

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

## 10.3 Prompt maestro

Actúa como arquitecto y desarrollador senior de GymOS.

Debes respetar:

- Java 25.
- Spring Boot.
- Spring Modulith.
- PostgreSQL.
- Redis.
- Next.js y TypeScript.
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

## 10.4 Prompt backend

Implementa únicamente el módulo indicado. Usa puertos y adaptadores. Mantén el dominio independiente de Spring cuando sea razonable. Incluye pruebas unitarias, integración, arquitectura y aceptación.

## 10.5 Prompt frontend

Implementa la pantalla indicada usando Next.js y TypeScript. Consume la API documentada. No dupliques reglas. Respeta permisos. Incluye estados de carga, vacío, error y éxito.

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
