# Operación de sesiones de personal

El diseño se documenta en [ADR-001](../../Documentación/adr/ADR-001-sesiones-refresh-y-csrf.md). Las pruebas conectadas del repositorio usan instancias efímeras y datos sintéticos. La aprobación de staging requiere evidencias del entorno real.

## Validación en staging

1. Confirmar que la web y la API usan HTTPS dentro del mismo sitio y que `GYMOS_CORS_ALLOWED_ORIGINS` contiene los orígenes web exactos. Confirmar el secreto JWT fuerte, el acceso privado a Redis y los permisos para ejecutar los scripts de sesión. En el navegador, verificar que la cookie no tiene `Domain` y lleva `Secure`, `HttpOnly`, `SameSite=Strict` y `Path=/`. Registrar atributos sin el valor.
2. Iniciar sesión con dueño, gerente, recepción, coach y plataforma de prueba. Recargar. Abrir otra sesión independiente y verificar que cerrar una conserva la otra. Probar cambio de sucursal autorizado y denegado, pérdida de una asignación, recuperación de contraseña y retiro de un rol.
3. Iniciar soporte con motivo. Cambiar sede dentro del mismo tenant, salir antes de caducar y confirmar que el JWT anterior deja de funcionar. Repetir dejando vencer la sesión. El operador debe autenticarse de nuevo.
4. Abrir kiosco desde Recepción y verificar que la pestaña del socio no puede restaurar la sesión de personal al navegar a una ruta privada. Un dispositivo público debe usar perfil de navegador separado y restricciones de navegación. La apertura en otra pestaña no protege un navegador que permite al público cambiar a la pestaña de Recepción.
5. Interrumpir la red durante el refresh y comprobar que el mismo intento se recupera dentro de 30 segundos. Durante una caída de Redis las peticiones protegidas deben fallar sin ejecutar operaciones y el cliente debe permitir reintentar. Confirmar el comportamiento después de reinicio o pérdida del registro de familia. Revisar latencia de Redis, PostgreSQL y errores 5xx.
6. Confirmar que proxy, APM, servidor y frontend no registran `Cookie`, `Set-Cookie`, `Authorization`, contraseñas ni cuerpos de autenticación. Los eventos de auditoría conservan actor, contexto y correlación. Registrar resultados sin credenciales y obtener aprobación del operador responsable.

## Corte y recuperación

- Publicar artefactos de frontend y backend coordinadamente en la ventana aprobada. El primer acceso con la versión nueva requiere login y limpia el almacenamiento legado. El refresh tiene un límite absoluto de siete días configurable. El JWT de acceso usa 15 minutos por defecto para todo el personal. Fuera de los perfiles `local` y `test`, Spring rechaza el secreto de ejemplo y valores de menos de 32 caracteres.
- Redis guarda el contexto y la generación en `gymos:session:v3:`. La pérdida de una familia exige iniciar sesión otra vez. Para forzar cierre de todas las sesiones de un usuario, usar una operación soportada de cuenta o incrementar su `credential_version` mediante el procedimiento controlado de incidente. Si se cambian concesiones directamente en PostgreSQL, incrementar la versión de credenciales de los usuarios afectados en la misma transacción.
- Si se requiere rollback, restaurar ambos artefactos coordinadamente, drenar tráfico y purgar las claves `gymos:refresh:v2:` con `SCAN` y eliminación por lotes limitada a ese prefijo. Verificar el namespace en el entorno objetivo antes de borrar. Las sesiones de v2 requieren volver a autenticarse. Mantener el secreto fuera de los logs y revisar la recuperación en staging antes de la ventana piloto.
- La comprobación externa Workers Builds de ramas secundarias se registra por SHA aparte del build interno OpenNext. El flujo de producción y su configuración permanecen en `main`.

## Reproducción

- Backend desde `backend` con Java 25 y Docker. Ejecutar `./gradlew test postgresIntegrationTest --no-daemon --console=plain`.
- Navegador conectado en Linux desde la raíz. Instalar las dependencias del frontend y Chromium de Playwright. Ejecutar `bash scripts/test-auth-browser.sh`. La prueba asigna puertos locales dinámicamente para PostgreSQL, Redis, Spring y Next.
- La suite general Playwright intercepta servicios en flujos de negocio. El escenario de sesión conectado tiene una configuración separada y se ejecuta en un paso explícito de CI.
