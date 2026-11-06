# ADR-001 — Sesiones web y protección del refresh

**Estado:** Propuesto para revisión del PR. [#28](https://github.com/Dagt/GymOs/issues/28).

## Contexto

En `4a585dc711202f99e4e808c70fb7e41e7df4ea91` Spring autentica con BCrypt, emite JWT y valida la versión de credenciales, cuenta, tenant y sede en PostgreSQL. Redis guarda refresh en claro y la rotación v2 emplea varias operaciones independientes. El navegador conserva ambos tokens en almacenamiento accesible a JavaScript y el botón de salida borra únicamente el estado local. No hay integración Firebase de identidad en la rama auditada. Las credenciales de kiosco, conectores y proveedores son protocolos separados.

## Decisión propuesta

Conservar el JWT Bearer de acceso, de 15 minutos por defecto para todos los roles, sólo en memoria del navegador. Spring entrega el refresh en una cookie host-only `__Host-gymfort-refresh-{selector}`, `HttpOnly; Secure; SameSite=Strict; Path=/`. El selector aleatorio identifica la cookie de la pestaña y se guarda en sessionStorage. Por sí solo no autentica. Una pestaña nueva de kiosco elimina el selector heredado para mantener el aislamiento de recepción. Frontend y API deben compartir sitio y esquema HTTPS en staging y producción. En desarrollo se usa localhost en ambos orígenes. Los endpoints de sesión validan el `Origin` exacto contra la lista CORS y una cabecera no simple, además de comprobar JWT y cookie para los cambios de contexto. Las demás API privadas sólo autentican por Bearer.

Redis guarda una familia por login con límite absoluto de siete días por defecto. El refresh se deriva de un identificador aleatorio, una generación y una MAC separada del JWT por dominio. Redis sólo recibe metadatos de la familia. Un script atómico rota la generación, reconoce el reintento de la misma operación durante una ventana breve y revoca la familia si se vuelve a presentar una credencial retirada fuera de esa ventana. Una solicitud concurrente con otro identificador recibe un conflicto durante la ventana y queda sin sucesor. Los accesos previos se invalidan al revocar la familia o cambiar de contexto. PostgreSQL conserva la autoridad sobre cuenta, versión de credenciales, organización, asignación a sede y concesiones vigentes.

El cambio de sede o modo rota el refresh y cambia la revisión de contexto. Se conserva el vencimiento original al cambiar de sede durante soporte. Al caducar el soporte, la familia deja de ser válida y la consola requiere autenticarse de nuevo. Para volver antes del vencimiento, el operador usa la salida explícita de soporte con JWT y cookie. El navegador coordina los refresh entre pestañas y evita reejecutar una mutación en otro contexto.

## Alternativas evaluadas

- Mantener tokens en sessionStorage preservaría clientes antiguos, pero un XSS podría extraer ambos tokens.
- Un BFF completo permitiría ocultar también el JWT. Requeriría intermediar todas las API, descargas y subidas y ampliar el alcance de este cambio.
- Una cookie en la API mantiene las rutas y los Bearer existentes. La arquitectura exige el mismo sitio HTTPS y una lista explícita de orígenes. Un XSS activo en un origen autorizado todavía puede actuar como el usuario mientras se ejecuta, aunque no pueda leer el refresh.

## Corte y rollback

Coordinar el despliegue de backend y frontend. El formato v3 y la comprobación de familia rechazan JWT y refresh de v2. Las sesiones anteriores requieren iniciar sesión una vez. El frontend elimina las claves legadas sin enviarlas al servidor. No hay migración Flyway. El rollback revierte ambos artefactos juntos y requiere renovar las sesiones. Antes de restaurar v2, purgar sus claves de sesión para no reactivar credenciales antiguas. Mantener separados los namespaces y verificar el procedimiento en staging.
