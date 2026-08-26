# Paginación de listados operativos

Revisión: 25 de agosto de 2026. Rama: `codex/fix-list-pagination`.
Implementación: `cab3e28`. Cierre de deuda: `INV-001`.

## Hallazgos del corte inicial

- Socios consumía una lista limitada a 50 y mostraba su longitud como total,
  sin un control para pedir la página siguiente.
- Inventario tenía consultas paginadas, pero la UI no recorría todas las
  páginas; filtraba movimientos después de descargarlos y recortaba el
  historial de conteos. Transferencias/conteos conservaban listas limitadas.
- Catálogo ya tenía paginación, pero la búsqueda no reiniciaba la página y la
  tabla añadía un scroll interno con barra oculta.
- POS pedía sólo la primera página de 50; ejercicios, hasta 100 con filtrado
  local. Los selectores reutilizaban esas listas parciales.

## Comportamiento visible

- Socios, catálogo, existencias, movimientos, transferencias, conteos, POS y
  ejercicios muestran el total filtrado del servidor, no `items.length` como
  si fuera el total de la colección.
- Los controles están antes del listado: rango visible, anterior/siguiente y
  25/50/100 registros por página. Las alertas de inventario usan 10 por página.
- El scroll de la página sólo recorre los registros de esa página; no carga
  páginas siguientes ni existe un scroll vertical oculto dentro del listado.
  Se conserva el desplazamiento horizontal de tablas anchas y el scroll de
  formularios laterales cuando es necesario.
- Cambiar búsqueda, filtros o tamaño vuelve a la primera página. Si al
  actualizar/archivar desaparece la última página, se consulta la última válida.
  Las respuestas de una búsqueda anterior no reemplazan la búsqueda vigente.
- En catálogo, filtros y página se reflejan en la URL. En las otras pantallas
  el estado se conserva durante la estancia en la pantalla, no entre recargas.
- Cambiar de página en POS no vacía el carrito. El lector de código de barras
  conserva la consulta exacta fuera de la página visible.
- Los selectores de inventario buscan en servidor y tienen sus propias páginas
  de 10 opciones. El constructor de rutinas busca ejercicios independientemente
  del catálogo visible; ofrece hasta 25 sugerencias y pide afinar la búsqueda
  cuando existen más coincidencias.
- Los indicadores de conteos/transferencias activos consultan su propio filtro
  `activeOnly`; no se calculan a partir de una página del historial.
- **Exportar página CSV** en inventario incluye únicamente las filas visibles.
  El nombre del archivo incluye el número de página. No es una exportación
  masiva de todos los registros.

## Contratos

Todas las rutas siguientes se encuentran bajo `/api/v1`. Requieren la sesión y
los permisos humanos existentes; el tenant y la sucursal proceden del contexto
autenticado, no de un parámetro libre enviado por el frontend.

| Listado | Ruta | Filtros relevantes |
|---|---|---|
| Socios | `GET /clients/search` | `query`, `status` |
| Catálogo/POS | `GET /catalog/products/search` | Filtros de catálogo existentes |
| Existencias/alertas | `GET /inventory/search` | `q`, `sort`, `lowStockOnly` |
| Movimientos | `GET /inventory/movements/search` | `q`, `type`, `productId` |
| Conteos | `GET /inventory/physical-counts/search` | `activeOnly` |
| Transferencias | `GET /inventory/transfers/search` | `activeOnly` |
| Ejercicios | `GET /exercises` | `query` y filtros de catálogo de ejercicios existentes |

Respuesta: `{ items, page, size, totalElements, totalPages }`. `page` es base
cero, `size` se acota a 1–100 y el total corresponde a los mismos filtros que
los registros. La UI presenta las páginas en base uno. Las consultas nuevas
usan `LIMIT/OFFSET` y un desempate por ID para evitar orden ambiguo entre
registros con igual nombre o fecha.

La búsqueda de movimientos abarca nombre, SKU y motivo; la de ejercicios,
nombre, alias, músculo principal y equipo. Se filtra antes de `LIMIT/OFFSET`,
no sobre la página ya descargada.

Se conservan los endpoints de listas legacy, incluido `GET /clients`, para
selectores existentes. Su límite no representa el total de socios. Las
pantallas principales usan el contrato paginado; no se descarga la colección
completa para simular paginación en el navegador.

## Alcance y límites

- Sin migraciones, cambios de permisos ni cambios al ciclo de inventario,
  membresías o invitaciones.
- Se conserva la visibilidad multi-sucursal existente de socios, incluyendo
  membresías con acceso multi-sucursal. La paginación no concede acceso nuevo.
- La paginación por offset no es una fotografía inmutable: altas, bajas o
  cambios concurrentes de orden pueden mover filas entre consultas. Actualizar
  vuelve a consultar los totales actuales; no se garantiza un snapshot para
  exportaciones masivas.
- Las pruebas con 127 registros demuestran navegación y límites, no capacidad
  bajo carga productiva masiva. Siguen vigentes las deudas de concurrencia,
  privacidad, archivos y feedback por partida que no pertenecen a este cierre.

## Validación reproducible

Desde `backend` (Docker disponible):

```powershell
.\gradlew.bat test postgresIntegrationTest --no-daemon --console=plain
```

Cobertura dedicada: `ClientPaginationControllerTests`,
`ClientPaginationServiceTests`, `ClientPaginationIntegrationTests`,
`InventoryPaginationIntegrationTests`, `ExercisePaginationIntegrationTests`,
`InventoryControllerTests` y `SecurityAuthorizationTests`.

Desde `frontend`:

```powershell
npm test
npm run typecheck
npm run lint
npm run test:e2e -- --workers=2
npm run build
npm run cf:build
```

`e2e/pagination.spec.ts` usa 127 registros sintéticos y verifica últimas páginas,
búsquedas fuera de los primeros 50, cambios de tamaño, filtros, respuestas fuera
de orden, vacíos, archivado, CSV acotado, carrito, selectores y vista móvil.
PostgreSQL comprueba el recorrido sin duplicados sobre datos estáticos, totales,
filtros, aislamiento por tenant/sucursal y límites del contrato.

Resultado local del 25/08/2026:

| Validación | Resultado |
|---|---|
| Backend `test` | 431 pruebas; 0 fallos, 0 omitidas |
| `postgresIntegrationTest` | 70 pruebas; 0 fallos, 0 omitidas; incluye 11 de migraciones |
| Frontend `npm test` | 104 pruebas; 0 fallos, 0 omitidas |
| Playwright completo | 56 recorridos; 0 fallos; 12 escenarios nuevos de paginación |
| `typecheck`, `lint`, `build` | Exitosos |
| `npm run cf:build` | Bundle OpenNext/Cloudflare generado correctamente |
| `npm audit --audit-level=high` | 0 vulnerabilidades |

Se actualizaron fixtures antiguos de sesión para incluir `accountScope=TENANT`
y el recorrido de automatizaciones para usar el asistente vigente, sin cambiar
la autenticación ni la funcionalidad de esos módulos. La revisión visual usa
datos sintéticos en escritorio y móvil; no se enviaron correos ni se hicieron
cobros reales. `stripeTest` no forma parte de esta ejecución.

OpenNext emitió su advertencia de compatibilidad con Windows, pero terminó el
bundle sin errores. Generar los builds no despliega estos cambios a producción.
