# ADR-004 — Marca por sucursal con fallback de organización

**Estado:** PROPOSED (29/10/2026). **Issue:** [#51](https://github.com/Dagt/GymOs/issues/51). **OQ:** OQ-006.

## Contexto
Un mismo tenant puede operar Centro y Tecnológica con marcas distintas, compartiendo socios, catálogo, planes y beneficios; el branding de organizaciones actualmente se resuelve desde `organization`. V26 tenía columnas de sucursal retiradas en V27. No recrearlas sin migración/contratos consistentes.

## Diseño propuesto
Entidad de perfil de sede con nombre visible, logo, principal/acento/superficie, remitente y reply-to permitidos; cada propiedad tiene fallback explícito a organización. El contexto operativo (branchId autorizado) determina la marca efectiva. Una marca jamás cambia IDs, datos financieros, propiedad tenant ni permisos.

## Alternativas
A. Conservar solo marca organización; no cumple cliente con dos marcas.
B. Overrides por sucursal con fallback atómico y contrato de marca resuelta; sugerida para evaluación.
C. Crear tenants distintos e interoperabilidad; rechazable preliminarmente por romper historial, ownership y derechos compartidos.

## Áreas afectadas
Shell y selector de sede, kiosco y terminal, email ZeptoMail (From allowlisted + Reply-To), recibos, PDF coaches, logo en MinIO y enlaces públicos. Caches deben invalidarse al cambiar sede; logo viejo se limpia con cola durable. Validar contraste WCAG de colores por sede, fallback seguro para logos ausentes.

## Criterios
Dos sedes un tenant, identidad visual diferente sin doble registro de socio, correos PDF/recibos de marca de operación, rollback de overrides, pruebas HTTP/PG, E2E cambio de sede y aislamiento con segundo tenant.

**Decisión:** pendiente de aceptación. No introducir duplicación de beneficio/plan por marca.