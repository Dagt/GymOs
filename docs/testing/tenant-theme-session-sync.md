# Tema del tenant y sesión activa — PR #74

Rama única: `codex/reception-memberships-visual-polish`. SHA inicial: `e951764ae651ed935c0753a97a0cf36d5b642c35`. Se actualizaron referencias remotas y se comprobó el PR existente. El SHA final se registra en el PR. Sin nueva rama ni PR, merge o despliegue.

## Reproducción antes de editar producción

Se agregó primero una prueba Playwright con restauración retrasada 500 ms y carga directa de `/inventory`. El panel terminó de cargar pero el tema permaneció en `#093D3A`; se esperaba el `#173c2a` de la sesión simulada. La prueba falló tras esperar diez segundos sin navegar. Esa secuencia confirma el fallo, no sólo una hipótesis estática. La prueba final utiliza una paleta púrpura/amarilla más distinguible y repite la recarga completa.

## Causa raíz confirmada

TenantThemeProvider dependía únicamente de pathname. Su efecto leía una sesión nula mientras SessionRouteGuard esperaba restoreSession. La restauración emitía `gymos.session-refreshed`, pero el proveedor no lo escuchaba; hasta navegar se conservaba el valor predeterminado.

Se confirmaron otros dos huecos: storeCurrentUser no notificaba cambios, y guardar colores en Configuración sólo actualizaba el estado de esa página. Además, refreshMissingTheme escribía el perfil antes de comprobar cancelled, de modo que una respuesta vieja podía modificar la sesión aunque ya no se aplicara directamente al DOM.

## Corrección

- TenantThemeProvider escucha el evento existente `gymos.session-refreshed` y conserva la sincronización por ruta. useLayoutEffect instala la suscripción y sincroniza antes de pintar. La restauración aplica el tema en el evento de storeSession, antes de que el guard cambie a ready.
- Se revisaron layout, SessionRouteGuard, restauración de tokens, login, refresh, selección de organización/sucursal y soporte. Sus flujos ya pasan por storeSession; no se añade otro bus ni se cambia su autorización.
- storeCurrentUser notifica usando el mismo evento y admite un snapshot esperado. Rechaza perfiles de otro usuario, scope, tenant, sucursal o modo soporte, y respuestas cuyo snapshot ya no sea el activo. clearSession notifica la limpieza una vez.
- La recuperación de un perfil incompleto conserva el snapshot, deduplica solicitudes en vuelo por contexto y marca los perfiles ya consultados. Las respuestas incompletas no causan loops; el 401 de perfil queda limitado al mecanismo existente de refresh. Una respuesta tardía nunca se aplica a una sesión nueva. Si el contexto se invalida, el tema vuelve a plataforma y un cambio de perfil dentro del mismo contexto de acceso no lo reactiva.
- Guardar datos/colores publica los valores confirmados en la sesión. Si el token se renovó durante el guardado en el mismo contexto, se consulta el perfil autoritativo actual antes de publicarlo; si cambió el contexto, se descarta la aplicación local de la respuesta anterior. El logo comprueba también el contexto.
- Se evitan escrituras de tokens CSS si su valor no cambió. No se modifican paletas, tipografía, tokens, componentes visuales ni globals.css.

## Carga inicial y aislamiento

No se añade caché visual, localStorage, cookies de tema ni credenciales persistidas. El origen del tema es exclusivamente la sesión activa confirmada. Mientras se restaura se utiliza el estado de carga existente; no se muestra el panel operativo antes de sincronizar el tema. La prueba observa las mutaciones del DOM y comprueba que Inventario sólo aparezca con la paleta personalizada, nunca con la predeterminada. Si no hay sesión o se está en plataforma, se conservan los valores GymFort. La consulta de tema no bloquea la UI si falla o no está disponible.

## Pruebas

- Ocho regresiones unitarias de publicación del evento y descarte de respuestas antiguas: usuario, tenant, sucursal, plataforma, soporte, perfil más reciente y token renovado; limpieza de sesión y notificación sin duplicación para la misma referencia.
- Ocho recorridos E2E: carga directa retrasada y recarga; login/navegación/refresh/sucursal/logout; guardado de colores y perfil viejo; un único perfil y listener tras navegar varias veces; plataforma/soporte y salida; perfil 401 sin loop; cambio a otro gimnasio con el perfil anterior deliberadamente pendiente; guardado tardío después de invalidar el contexto.
- Respuestas de API simuladas, datos sintéticos. Se verifican conteos de solicitudes y listeners además del color del DOM. No se simula un proveedor conectado ni se modifica backend.

Los primeros intentos de las pruebas nuevas detectaron selectores de menú cerrado y fixtures sin permiso organization.update/RFC requerido. Se corrigieron esas fixtures y esperas de navegación; no se declaran aprobadas esas ejecuciones fallidas. La reproducción inicial previa al fix es deliberadamente roja.

Entorno: Windows, Node 24.13.1, npm 11.8.0, Next 16.4.5, Playwright 1.61.1/Chromium. CI remoto y aceptación visual pendientes. Las advertencias existentes de OpenNext en Windows y del launcher standalone no equivalen a validación desplegada. Sin pruebas backend/PostgreSQL, hardware o proveedor conectado en esta corrección de frontend.

## Resultados locales

- `npm.cmd test`: 126/126 (118 existentes y 8 nuevas).
- `npm.cmd run lint -- --ignore-pattern test-results/**`: PASS.
- `npm.cmd run typecheck`: PASS.
- `npm.cmd run test:e2e -- --workers=3 --timeout=60000`: 138/138 en tres minutos. Incluye toda la regresión UX, cobros/Point, inventario, catálogo, miembros, reservas, roles y kiosco.
- Comprobación adicional final de invalidación mientras termina un guardado: 1/1. El color vuelve a plataforma y permanece así aunque llegue esa respuesta.
- `npm.cmd run cf:build`: PASS; compila Next.js con webpack y el worker OpenNext.

La corrida completa se ejecutó antes del último endurecimiento del contexto invalidado. Esa ruta se comprobó separadamente y los recorridos seleccionados finales se repiten sobre producción antes de publicar las capturas. Los siete casos previos también pasaron en producción antes de añadir esa comprobación.

## Evidencia final del build de producción

La repetición final seleccionada terminó **12/12 PASS en 21.8 s**: ocho escenarios de tema, los tres recorridos UX de inventario/transferencia/catálogo y la contratación completa. Comando: `npm.cmd run test:e2e -- --config playwright.polish.config.ts e2e/tenant-theme.spec.ts e2e/ux-consistency.spec.ts e2e/console-routing.spec.ts --grep` seguido de los nombres de esos doce casos. Servidor next start 3127; configuración temporal eliminada al finalizar. Lint, typecheck y compilación se repitieron con el código final y terminaron correctamente.

- [Carga directa después de restauración retrasada](evidence/tenant-theme/tema-carga-directa.png).
- [Recarga completa](evidence/tenant-theme/tema-recarga.png).
- [Actualización de paleta sin navegar](evidence/tenant-theme/tema-actualizado-sin-navegar.png).

Las capturas de las mejoras UX de commercial-consistency y reception-polish se regeneraron también con el tema de la sesión realmente aplicado. Los colores de cada fixture son distintos y pertenecen a sus contextos de prueba; no son una nueva paleta de producto.

Archivos de producción: `frontend/src/components/TenantThemeProvider.tsx`, `frontend/src/lib/api/session.ts`, `frontend/src/app/settings/organization/page.tsx`. Pruebas: `frontend/src/lib/session-theme.test.ts`, `frontend/e2e/tenant-theme.spec.ts` y `frontend/e2e/theme-fixtures.ts`. El resto del diff de esta continuación es documentación y evidencia.
