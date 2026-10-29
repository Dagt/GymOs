# ADR-001 — Estrategia de sesión Web y protección del refresh token

**Estado:** PROPOSED (29/10/2026). **Issue:** [#28](https://github.com/Dagt/GymOs/issues/28). **OQ:** OQ-002. **NFR:** 001–005, 020.

## Evidencia
`frontend/src/lib/api/auth.ts` guarda `gymos.accessToken`, `gymos.refreshToken` y usuario en sessionStorage y migra valores desde localStorage. `frontend/src/lib/api/client.ts` llama `/auth/refresh` con refreshToken por JSON, y serializa peticiones concurrentes. Eso permite acceso a tokens si un script malicioso ejecuta JS en ese origen; no constituye por sí mismo prueba de XSS o cuenta comprometida.

## Alternativas
A. Conservar sessionStorage y reforzar CSP, sanitización, rotación, detección de reuse, expiración y gestión de sesiones. Es menos invasivo pero conserva exposición a JS.
B. Backend-for-Frontend o proxy para manejar refresh en cookie Secure/HttpOnly/SameSite, token de acceso de vida corta en memoria y CSRF para acciones con cookie. Requiere contrato y despliegue coordinado.
C. Gestión de sesión server-side completa en fachada web, evitando tokens en JS; mayor superficie de infraestructura.

## Propuesta de evaluación
Validar compatibilidad con Cloudflare/OpenNext, login PLATFORM/TENANT, soporte temporal, kiosk, checkout externos y rutas públicas. Elegir tras threat modeling y prueba de CSRF/refresh concurrente. No introducir cookie sin CSRF ni romper sesiones históricas silenciosamente.

## Criterios de aceptación
Logout revoca, reuse bloquea, cambio de contexto conserva scope, no se filtran tokens a logs/URLs, no se pierde sesión por retry concurrente, caducidad apropiada, E2E de callback y soporte. Evidencia con SHA + tests negativos.

**Decisión:** pendiente de Product Owner y revisión seguridad; Codex no implementa migración irreversible antes de aceptar ADR.