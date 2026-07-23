# 17. Prompt maestro para implementar catálogo e inventario

Estado: `READY_TO_USE`  
Fecha: 23 de julio de 2026

## Instrucciones de uso

Este prompt debe entregarse a la inteligencia artificial responsable de implementar la mejora de catálogo e inventario. Debe acompañarse del acceso al repositorio completo y del documento:

```text
Documentación/16-plan-accion-catalogo-inventario.md
```

La inteligencia artificial debe trabajar sobre una rama específica para la implementación, conservar avances verificables y no declarar una fase terminada sin ejecutar sus validaciones.

---

# PROMPT MAESTRO

Actúa como arquitecto de software senior, desarrollador backend senior en Java y Spring Boot, desarrollador frontend senior en Next.js, React y TypeScript, especialista en bases de datos PostgreSQL, pruebas automatizadas, Spring Modulith, arquitectura modular y diseño de producto SaaS.

Debes implementar integralmente la mejora de los módulos de catálogo e inventario de GymOS descrita en:

```text
Documentación/16-plan-accion-catalogo-inventario.md
```

Tu trabajo debe abarcar backend, frontend, base de datos, migraciones, contratos, permisos, auditoría, pruebas, documentación e integración con los módulos consumidores.

## 1. Objetivo principal

Completa todos los puntos pendientes del catálogo identificados en la revisión previa:

1. edición de productos comunes;
2. activación, desactivación y archivado lógico;
3. control opcional de inventario por producto;
4. representación consistente de los conceptos vendibles;
5. categorías, subcategorías, etiquetas, búsqueda y filtros;
6. preparación para variantes y presentaciones;
7. código de barras;
8. información comercial adicional;
9. eliminación del acoplamiento entre API/aplicación y modelos de infraestructura;
10. rediseño completo de la sección de catálogo del frontend para hacerla más completa, dinámica, rápida y funcional.

El resultado debe estar preparado para un piloto real, no ser únicamente una demostración visual.

## 2. Regla central: calidad antes que velocidad

No implementes soluciones rápidas que generen deuda técnica inmediata.

Todo el código debe ser:

- eficiente;
- eficaz;
- legible;
- modular;
- cohesivo;
- mantenible;
- comprobable;
- extensible;
- seguro para multi-tenant;
- consistente con la arquitectura existente.

No concentres lógica para “terminar más rápido”. Una implementación no se considera completa si funciona pero obliga a una refactorización evidente en la siguiente fase.

## 3. Prohibición de archivos monolíticos

Está estrictamente prohibido crear o ampliar archivos que acumulen responsabilidades heterogéneas.

Antes de escribir código, identifica la responsabilidad exacta de cada clase, componente, hook, repositorio y archivo.

Debes dividir preventivamente:

- controladores extensos;
- servicios con múltiples casos de uso;
- repositorios con consultas y comandos heterogéneos;
- páginas frontend que contengan formularios, filtros, consultas, mutaciones y tablas en el mismo archivo;
- clientes API de dominio demasiado amplios;
- archivos de tipos que mezclen dominios;
- utilidades genéricas creadas sólo para esconder complejidad;
- componentes con demasiadas props o demasiadas ramas condicionales.

### Umbral de revisión

Cuando un archivo se acerque a 250–300 líneas, debes detenerte y revisar si puede dividirse. No esperes a que sea inmanejable.

El límite de líneas no sustituye el criterio arquitectónico. Debes separar antes cualquier clase o componente que tenga más de una razón clara para cambiar.

No crees un archivo grande con la intención de “refactorizar después”. La modularización debe formar parte de la primera implementación.

## 4. Flujo de trabajo obligatorio

Trabaja por fases pequeñas y verificables.

### Antes de modificar código

1. Lee el PRD, roadmap, contratos, casos de uso, decisiones arquitectónicas y documentación de estado.
2. Revisa por completo los módulos actuales `catalog`, `inventory`, `sales`, `membership` y el cliente frontend relacionado.
3. Localiza migraciones, permisos, auditoría, pruebas y endpoints existentes.
4. Identifica dependencias directas entre módulos y acceso cruzado a tablas.
5. Crea una lista de comportamiento que debe conservarse.
6. Ejecuta las pruebas actuales para establecer una línea base.
7. No asumas que un archivo o comportamiento está incompleto sin verificarlo en el código.

### Durante la implementación

1. Implementa una fase coherente.
2. Ejecuta las pruebas relevantes.
3. Corrige fallos antes de continuar.
4. Revisa complejidad y tamaño de archivos.
5. Actualiza documentación técnica de la fase.
6. Conserva commits pequeños y descriptivos.

### Al finalizar

1. Ejecuta toda la suite backend.
2. Ejecuta lint, typecheck y build del frontend.
3. Ejecuta pruebas de arquitectura.
4. Ejecuta pruebas de integración y multi-tenant.
5. Ejecuta validación Docker y smoke tests.
6. Revisa migraciones sobre una base con datos existentes.
7. Compara el resultado punto por punto con el plan de acción.
8. Documenta cualquier desviación, decisión o pendiente real.

## 5. Reglas de arquitectura backend

### 5.1 Capas

Respeta una estructura equivalente a:

```text
catalog/
  api/
  application/
    command/
    query/
    port/
    service/
  domain/
  infrastructure/
    jdbc/
    storage/
```

### 5.2 Dependencias permitidas

- `api` puede depender de `application`.
- `application` puede depender de `domain` y de puertos.
- `infrastructure` implementa puertos.
- `domain` no depende de Spring, JDBC, HTTP ni infraestructura.

### 5.3 Dependencias prohibidas

- `application` no puede usar `JdbcClient`.
- `api` no puede devolver records definidos dentro de repositorios.
- `api` no puede importar adaptadores JDBC.
- otros módulos no pueden importar clases internas de infraestructura de catálogo.
- un servicio de aplicación no puede contener SQL.
- un repositorio no puede decidir reglas de negocio de transición de estado.

### 5.4 Modelos de salida

Crea modelos de aplicación claros, por ejemplo:

```text
ProductSummaryView
ProductDetailView
PlanView
CategoryView
CatalogPageView
```

No reutilices entidades JDBC como respuesta HTTP.

### 5.5 Casos de uso

Separa casos de uso con lógica significativa:

```text
CreateProductService
UpdateProductService
ActivateProductService
DeactivateProductService
ArchiveProductService
GetProductService
SearchProductsService
ManageCategoryService
UploadProductImageService
```

No es obligatorio crear una clase por cada método trivial, pero sí separar responsabilidades y razones de cambio.

### 5.6 Repositorios y puertos

Prefiere contratos especializados:

```text
CatalogProductQueryPort
CatalogProductCommandPort
CatalogCategoryQueryPort
CatalogCategoryCommandPort
CatalogImageStoragePort
CatalogDependencyQueryPort
```

No crees una interfaz gigante que concentre todo el módulo.

### 5.7 Transacciones

- Coloca límites transaccionales en aplicación.
- Las operaciones que modifican producto y relaciones deben ser atómicas.
- La carga de imagen debe manejar compensación si falla persistencia o almacenamiento.
- No dejes registros parciales.

## 6. Reglas de base de datos

### 6.1 Flyway

Todo cambio estructural debe usar una nueva migración Flyway.

No modifiques migraciones ya aplicadas.

### 6.2 Datos existentes

Diseña backfill para instalaciones existentes:

- productos físicos existentes deben conservar comportamiento;
- planes existentes no deben aparecer como productos ordinarios;
- ventas históricas deben mantener referencias;
- inventario existente no debe perder cantidades o movimientos.

### 6.3 Campos mínimos

Implementa o decide explícitamente:

```text
inventory_tracked
barcode
brand
presentation
unit_of_measure
display_order
image_object_key o referencia equivalente
category
status/archivado
```

### 6.4 Restricciones e índices

Añade restricciones e índices para:

- SKU por organización;
- código de barras por organización;
- estado y tipo;
- categoría;
- búsqueda por nombre;
- relaciones de etiquetas;
- productos no archivados.

Verifica que los índices respondan a consultas reales. No agregues índices indiscriminadamente.

### 6.5 Soft delete

El archivado debe conservar historial y referencias.

No hagas eliminación física de productos utilizados.

## 7. Reglas funcionales obligatorias

### 7.1 Edición

Implementa detalle y actualización de productos.

Valida:

- organización;
- SKU;
- código de barras;
- tipo;
- precio;
- moneda;
- estado;
- inventario;
- categoría;
- dependencias.

### 7.2 Estados

Implementa activación, desactivación y archivado como casos de uso explícitos.

Un producto:

- activo puede venderse;
- inactivo puede administrarse pero no venderse;
- archivado se conserva sólo para historial y administración autorizada.

### 7.3 Inventario opcional

Sólo productos físicos pueden activar seguimiento de inventario.

El inventario y POS deben respetar `inventoryTracked`.

No desactives seguimiento en productos con historial sin una política segura y una confirmación explícita.

### 7.4 Categorías y etiquetas

Implementa categorías por organización, con estado y orden.

Permite filtros y asignación.

Evita mezclar categorías comerciales con reglas operativas de reservas, membresías o servicios.

### 7.5 Conceptos vendibles

Define claramente cómo se representan:

- producto físico;
- servicio;
- plan de membresía;
- visita;
- reserva;
- rutina;
- consulta profesional.

No agregues tipos rígidos innecesarios si una categoría o subtipo resuelve mejor el problema.

### 7.6 Variantes

No construyas un sistema completo de variantes si no es necesario para el piloto.

Prepara el modelo mediante campos de marca, presentación y unidad, y documenta la futura separación `Product`/`ProductVariant`.

No bloquees esa migración futura.

### 7.7 Código de barras

- opcional;
- único por organización cuando exista;
- distinto del SKU;
- buscable desde catálogo y POS;
- compatible con lectores que emulan teclado.

### 7.8 Imágenes

Usa MinIO mediante un puerto.

No guardes binarios en PostgreSQL.

Valida tipo, tamaño y nombre.

Evita archivos huérfanos.

## 8. Reglas del frontend

### 8.1 Objetivo

Transforma la sección de catálogo en un centro de administración comercial completo, dinámico y funcional.

No te limites a cambiar estilos.

### 8.2 Estructura

La página debe incluir:

- encabezado con acciones;
- métricas resumidas;
- búsqueda;
- filtros;
- ordenamiento;
- filtros activos;
- tabla administrativa;
- vista de tarjetas sólo si agrega utilidad;
- paginación;
- estados vacíos;
- detalle;
- edición;
- acciones de estado;
- archivado;
- categorías;
- imágenes;
- indicadores de inventario.

### 8.3 Organización de archivos

Usa una estructura por feature similar a:

```text
features/catalog/
  api/
  components/
  hooks/
  forms/
  tables/
  filters/
  mappers/
  types/
  validation/
```

`app/catalog/page.tsx` debe ser un ensamblador pequeño.

### 8.4 Componentes

Divide al menos por responsabilidad:

```text
CatalogPageHeader
CatalogSummaryCards
CatalogToolbar
CatalogSearchInput
CatalogActiveFilters
CatalogProductTable
CatalogProductCard
CatalogProductActions
CatalogProductForm
CatalogProductDrawer
CatalogArchiveDialog
CatalogCategoryManager
CatalogImageUploader
CatalogPagination
```

Reutiliza componentes compartidos de GymOS cuando sean adecuados.

No dupliques `AppDrawer`, botones, badges o patrones de formulario.

### 8.5 Estado y datos

Crea hooks especializados:

```text
useCatalogProducts
useCatalogProduct
useCatalogFilters
useCreateCatalogProduct
useUpdateCatalogProduct
useChangeProductStatus
useArchiveProduct
useCatalogCategories
useCatalogImageUpload
```

No uses un solo hook que controle toda la pantalla.

### 8.6 Filtros

Los filtros deben persistir en URL.

Incluye:

- búsqueda por nombre, SKU y código;
- tipo;
- categoría;
- estado;
- control de inventario;
- ordenamiento;
- página y tamaño.

Usa debounce para búsqueda remota.

### 8.7 Rendimiento

- no cargues todo el catálogo;
- usa paginación backend;
- no vuelvas a descargar datos no afectados;
- evita renderizados innecesarios;
- optimiza imágenes;
- no hagas una petición por fila;
- evita N+1 en backend y frontend.

### 8.8 Experiencia

Implementa:

- skeletons;
- errores recuperables;
- estados vacíos contextuales;
- confirmaciones;
- mensajes de éxito;
- foco correcto;
- navegación por teclado;
- diseño responsive;
- permisos visibles sin mostrar acciones no autorizadas.

## 9. Integración con módulos existentes

Revisa y actualiza:

- POS;
- ventas;
- inventario;
- membresías;
- visitas;
- reservas;
- servicios profesionales;
- onboarding y seed.

### Reglas

- productos archivados o inactivos no aparecen en ventas nuevas;
- `MEMBERSHIP_PLAN` no aparece como producto común;
- inventario sólo incluye productos controlados;
- ventas descuentan únicamente productos físicos controlados;
- búsqueda por código funciona en POS;
- historial conserva nombres y referencias;
- ningún módulo debe acceder directamente a adaptadores internos de catálogo.

## 10. Seguridad y multi-tenant

Todas las consultas y comandos deben filtrar por `organizationId`.

Cuando aplique disponibilidad por sucursal, usa también `branchId`.

Prueba explícitamente:

- acceso cruzado entre organizaciones;
- edición de producto ajeno;
- categoría ajena;
- código de barras repetido en otra organización permitido;
- código repetido dentro de la misma organización rechazado;
- permisos por rol.

Nunca confíes en IDs recibidos sin validar pertenencia.

## 11. Permisos

Implementa o confirma permisos equivalentes a:

```text
catalog.read
catalog.create
catalog.update
catalog.status.manage
catalog.archive
catalog.category.manage
catalog.image.manage
catalog.cost.read
```

No amplíes permisos de recepción o coach por comodidad.

Actualiza migraciones, seed, documentación y pruebas de autorización.

## 12. Auditoría

Registra como mínimo:

```text
PRODUCT_CREATED
PRODUCT_UPDATED
PRODUCT_ACTIVATED
PRODUCT_DEACTIVATED
PRODUCT_ARCHIVED
PRODUCT_IMAGE_UPDATED
PRODUCT_IMAGE_REMOVED
PRODUCT_CATEGORY_CHANGED
CATEGORY_CREATED
CATEGORY_UPDATED
CATEGORY_ARCHIVED
```

Para actualizaciones conserva los campos modificados sin exponer datos sensibles.

Mantén correlation ID.

## 13. Manejo de errores

Usa códigos de negocio estables y específicos, por ejemplo:

```text
PRODUCT_NOT_FOUND
PRODUCT_SKU_ALREADY_EXISTS
PRODUCT_BARCODE_ALREADY_EXISTS
PRODUCT_TYPE_CHANGE_NOT_ALLOWED
PRODUCT_ARCHIVE_NOT_ALLOWED
PRODUCT_INVENTORY_TRACKING_CHANGE_NOT_ALLOWED
CATEGORY_NOT_FOUND
CATEGORY_ALREADY_EXISTS
PRODUCT_IMAGE_INVALID
```

No devuelvas errores genéricos cuando exista una causa de negocio conocida.

El frontend debe traducirlos a mensajes claros.

## 14. Pruebas obligatorias

### 14.1 Backend unitario

Cubre:

- normalización;
- validaciones;
- transiciones;
- archivado;
- inventario opcional;
- dependencias;
- categorías;
- código de barras;
- permisos.

### 14.2 Backend integración

Cubre:

- alta;
- detalle;
- edición;
- activar/desactivar;
- archivar;
- paginación;
- búsqueda;
- filtros;
- categorías;
- imágenes;
- aislamiento multi-tenant;
- integración POS/inventario.

### 14.3 Arquitectura

Añade pruebas que impidan:

- JDBC en aplicación;
- infraestructura en API;
- imports internos entre módulos;
- repositorios fachada con SQL cuando deban delegar;
- ciclos de dependencia.

### 14.4 Frontend

Cubre:

- búsqueda y debounce;
- filtros en URL;
- paginación;
- formularios;
- edición;
- conflictos;
- archivado;
- permisos;
- estados vacíos;
- error;
- accesibilidad básica.

### 14.5 End-to-end

Ejecuta un flujo real:

1. crear producto físico;
2. activar inventario;
3. asignar categoría;
4. subir imagen;
5. buscar por SKU y código;
6. editar;
7. vender;
8. comprobar descuento de inventario;
9. desactivar;
10. comprobar exclusión en POS;
11. archivar;
12. comprobar conservación histórica.

## 15. Validaciones técnicas finales

Debes ejecutar, según el entorno del repositorio:

```text
backend:
  clean test
  architecture tests
  integration tests

frontend:
  lint
  typecheck
  test
  build

infra:
  docker compose config
  docker compose build
  smoke tests
```

No declares éxito si una validación no fue ejecutada. Indica con honestidad cualquier bloqueo del entorno.

## 16. Documentación obligatoria

Actualiza:

- PRD si cambia alcance;
- roadmap;
- contratos HTTP;
- casos de uso;
- modelo de datos;
- permisos;
- auditoría;
- estado de implementación;
- decisiones arquitectónicas;
- instrucciones de uso del catálogo.

La documentación debe describir lo realmente implementado, no lo deseado.

## 17. Disciplina de commits

Realiza commits pequeños y coherentes, por ejemplo:

```text
feat(catalog): add product metadata migration
refactor(catalog): introduce application query ports
feat(catalog): add product update use case
feat(catalog): add categories and paginated search
feat(frontend): build catalog filters and table
feat(frontend): add catalog product editor
feat(inventory): honor inventory tracking flag
test(catalog): cover tenant isolation and archive rules
docs: update catalog implementation status
```

No mezcles migración, refactor arquitectónico, frontend completo y documentación en un solo commit.

## 18. Revisión de complejidad obligatoria

Después de cada fase, produce una revisión interna con:

- archivos creados;
- archivos modificados;
- líneas aproximadas;
- responsabilidad de cada archivo;
- clases o componentes que deban dividirse;
- duplicación detectada;
- acoplamiento detectado;
- consultas potencialmente costosas;
- deuda técnica introducida.

Si detectas que un archivo comienza a ser extenso o multifuncional, divídelo antes de continuar.

No pospongas esta revisión hasta el final.

## 19. Criterios para rechazar tu propia implementación

Debes considerar incompleto o incorrecto tu propio trabajo si ocurre cualquiera de estos casos:

- una página contiene toda la lógica del catálogo;
- un servicio contiene alta, edición, estado, categorías e imágenes;
- un repositorio concentra todas las consultas y comandos;
- API devuelve modelos de infraestructura;
- aplicación usa JDBC;
- se carga todo el catálogo sin paginación;
- filtros sólo existen en frontend y no en backend;
- inventario ignora `inventoryTracked`;
- archivado rompe historial;
- no existe prueba multi-tenant;
- no existe auditoría;
- no se actualizaron contratos y documentación;
- se agregaron archivos extensos con la promesa de refactorizarlos después;
- se duplicaron formularios o clientes API;
- las pruebas existentes dejaron de pasar.

## 20. Formato de reporte de avance

Al finalizar cada fase, informa:

```text
Fase:
Objetivo:
Cambios realizados:
Archivos principales:
Decisiones:
Pruebas ejecutadas:
Resultados:
Riesgos o pendientes:
Siguiente fase:
```

No uses afirmaciones vagas como “se mejoró la arquitectura”. Explica qué dependencia fue eliminada, qué puerto se creó, qué archivo se dividió y qué prueba lo protege.

## 21. Definición final de terminado

No declares el trabajo terminado hasta confirmar:

- edición completa;
- estados y archivado;
- categorías y etiquetas;
- búsqueda, filtros, ordenamiento y paginación;
- inventario opcional;
- código de barras;
- información comercial prioritaria;
- imágenes mediante MinIO;
- preparación de variantes documentada;
- frontend completo, responsive y accesible;
- integración con POS, ventas, membresías e inventario;
- API y aplicación desacopladas de infraestructura;
- permisos y auditoría;
- migraciones seguras;
- pruebas verdes;
- documentación actualizada;
- revisión de tamaño y responsabilidad de todos los archivos nuevos o modificados.

## 22. Instrucción final

Realiza la implementación completa por fases, tomando decisiones conservadoras y documentadas. Prioriza integridad de datos, claridad arquitectónica, rendimiento, experiencia operativa y mantenibilidad.

No produzcas código masivo en archivos únicos. No sacrifiques modularidad para avanzar rápido. No dejes para una refactorización futura separaciones que ya son evidentes durante la implementación.

Cada pieza de código debe tener una responsabilidad concreta, pruebas proporcionales a su riesgo y una ubicación coherente dentro del módulo.

Cuando existan varias alternativas válidas, elige la solución más simple que cumpla los requisitos actuales sin cerrar la evolución futura de GymOS.