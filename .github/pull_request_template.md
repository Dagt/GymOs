## Issue, épica y deuda
- Closes #
- Épica #
- ID deuda / FR / BR / NFR:

## Situación anterior y resultado esperado

## Implementación y arquitectura
- Módulos y límites
- Contratos API / compatibilidad
- Migración Flyway (nueva versión / compatibilidad / rollback)

## Seguridad e invariantes
- Tenant / sucursal / rol / ownership
- Datos sensibles, secretos y auditoría
- Reintentos / idempotencia / concurrencia / errores

## UX si aplica
- Rutas/roles/estados revisados
- Capturas antes/después (390, 768, 1440, kiosco cuando aplique)
- Focus, accesibilidad, mensajes de estado, privacidad

## Validación del HEAD final
| Suite | Comando / SHA | Resultado | Evidencia | Limitación |
|---|---|---|---|---|
| Unitarios backend | | | | |
| PostgreSQL/migraciones | | | | |
| Frontend test/lint/typecheck | | | | |
| Build/Cloudflare | | | | |
| Playwright E2E | | | | |
| Proveedor sandbox/hardware | | | | |
| Seguridad/dependencias | | | | |

## Documentación cambiada
- [ ] Estado (`Documentación/13`)
- [ ] Deuda (`Documentación/28`)
- [ ] Ficha modular y contratos
- [ ] MVP/backlog/roadmap y changelog si impactan
- [ ] Runbooks y QA/matriz de evidencia

## Riesgos y aceptación
- Estado: Draft / Ready for Review / HOLD
- Restricciones de CI/billing/logs/hardware:
- Plan de recuperación, impacto en producción y pasos manuales:

**No fusionar ni desplegar automáticamente.** No inferir PASS de un CI fallido, incompleto o no ejecutado.
