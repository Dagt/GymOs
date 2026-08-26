# Paginación de listados operativos

Revisión: 26 de agosto de 2026. Rama: `codex/fix-list-pagination`.
Implementación: `cab3e28`. Cierre de deuda: `INV-001`.

## Hallazgos del corte inicial

- Socios consumía una lista limitada a 50 y mostraba su longitud como total,
  sin un control para pedir la página siguiente.
- Inventario tenía consultas paginadas, pero la UI no recorría todas las
  páginas; filtraba movimientos después de descargarlos y recortaba el
  historial de conteos. Transferencias/conteos conservaban listas limitadas.
- Catálogo ya tenía paginación, pero la búsqueda no reiniciaba la página. El
  scroll interno no sustituía la necesidad de navegar por todas las páginas.
- POS pedía sólo la primera página de 50; ejercicios, hasta 100 con filtrado
  local. Los selectores reutilizaban esas listas parciales.

## Comportamiento visible

- Socios, catálogo, existencias, movimientos, transferencias, conteos, POS y
  ejercicios muestran el total filtrado del servidor, no `items.length` como
  si fuera el total de la colección.
- Los controles están antes del listado: rango visible, anterior/siguiente y
  25/50/100 registros por página, comenzando con **100**. Las alertas también
  comienzan con 100. El valor inicial se centraliza en `DEFAULT_PAGE_SIZE`;
  una selección explícita de otro tamaño, incluida la URL de catálogo, se respeta.
  Son compactos y no heredan las alturas de los formularios; sin recuadro exterior.
- «Socios registrados» conserva el total sin filtros de la sucursal. El rango
  del paginador corresponde a los resultados de búsqueda, no al total del padrón.
  Ninguno de los totales se calcula con la longitud de la página visible.
- Los listados tienen una altura máxima y scroll interno con barra oculta,
  según la preferencia operativa del piloto. Admiten rueda, touch y teclado;
  reciben foco y tienen nombre accesible. No alargan la página con 100 filas.
- La paginación permanece fuera del área desplazable. El scroll recorre sólo
  la página actual; **Siguiente** consulta la siguiente página del servidor.
  Al cambiar de página se reinicia el desplazamiento del listado. Se conserva
  el desplazamiento horizontal de tablas anchas y de formularios laterales.
- Cambiar búsqueda, filtros o tamaño vuelve a la primera página. Si al
  actualizar/archivar desaparece la última página, se consulta la última válida.
  Las respuestas de una búsqueda anterior no reemplazan la búsqueda vigente.
- En catálogo, filtros y página se reflejan en la URL. En las otras pantallas
  el estado se conserva durante la estancia en la pantalla, no entre recargas.
- Cambiar de página en POS no vacía el carrito. El lector de código de barras
  conserva la consulta exacta fuera de la página visible.
- Los selectores de inventario buscan en servidor y tienen sus propias páginas
  de 100 opciones con scroll interno. El constructor de rutinas busca ejercicios
  independientemente del catálogo visible; ofrece hasta 25 sugerencias y pide afinar la búsqueda
  cuando existen más coincidencias.
- Los indicadores de conteos/transferencias activos consultan su propio filtro
  `activeOnly`; no se calculan a partir de una página del historial.
- El historial de asignaciones, su reporte y los historiales paginados de pagos
  y automatizaciones también solicitan 100 por defecto. Las consultas de sólo
  totales conservan `size=1`; no son páginas visibles. No se modifican los límites
  de los autocompletados ni los contratos por defecto del backend.
- Los historiales de pagos conservan altura limitada; pulsar su pestaña ya
  seleccionada no borra los registros ni deja la vista cargando indefinidamente.
- La lista de problemas de automatizaciones también mantiene scroll interno
  con barra oculta al cargar 100 registros.
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

## Compatibilidad entre frontend y backend

`GET /system/info` anuncia `capabilities: ["operational-pagination-v1"]`.
Socios y las consultas nuevas de inventario verifican esta capacidad antes de
usarlas; un backend anterior produce una instrucción explícita de actualización,
no un total parcial ni una falsa lista vacía. El hook paginado también rechaza
respuestas legacy o totales incoherentes. Un error de socios/inventario muestra
que el total no está disponible y permite reintentar.

Las capturas del piloto muestran `500` en `/clients/search` y en
`/inventory/physical-counts/search`. Un backend anterior puede interpretar
`search` como un UUID o ignorar el filtro `lowStockOnly`; esto es compatible con
los síntomas, pero no se dispone del log de esa instancia para atribuirles una
causa definitiva. Las rutas actuales y sus consultas PostgreSQL pasan pruebas.
Los parámetros y cuerpos mal formados ahora devuelven `400 VALIDATION_ERROR`
sin exponer los valores rechazados.

Para probar, reiniciar/desplegar **backend y frontend del mismo corte** y
comprobar `NEXT_PUBLIC_API_BASE_URL`; no basta con actualizar sólo el navegador.

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

Resultado histórico del cierre inicial de paginación (`cab3e28`, 25/08/2026):

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

La validación del endurecimiento posterior, scroll oculto y compatibilidad está
en [cierre operativo](operational-hardening.md). No confundir la evidencia del
corte inicial anterior con el código posterior.

## Validación del ajuste de 100 registros y equipo (26/08/2026)

- `npm test`: 113 pruebas aprobadas.
- `npm run test:e2e -- --workers=2`: suite completa de 81 recorridos aprobados.
  Después del último ajuste de scroll en automatizaciones se ejecutó
  `npm run test:e2e -- e2e/notifications.spec.ts --workers=2`: 2 aprobados,
  incluido el nuevo caso con 100 problemas visibles en un panel acotado.
- `npm run typecheck`, `npm run lint` y lint focalizado de los últimos cambios:
  correctos.
- `npm run build` y `npm run cf:build`: correctos. El último build de Cloudflare
  incluye también una compilación Next.js/TypeScript del ajuste final.
- Revisión visual de equipo y recuperación en escritorio/móvil; layout probado
  en anchos de 390, 768, 1024 y 1440 px. Se conservan permisos y entrega manual
  de recuperación ante fallos de correo.

Este ajuste es de frontend: sin migraciones ni cambios de backend. No se
repitieron suites PostgreSQL en este corte ni se enviaron correos o cobros reales.
OpenNext conserva la advertencia de soporte parcial de Windows; los builds
no constituyen un despliegue ni una prueba del runtime productivo.
