# 16. Plan de acción para completar catálogo e inventario

Estado: `PLANNED`  
Fecha: 23 de julio de 2026  
Rama de planeación: `feature/catalog-inventory-enhancement-plan`

## 16.1 Propósito

Este documento define el plan de implementación para completar y fortalecer los módulos de catálogo e inventario de GymOS. El alcance incluye todos los pendientes identificados en la revisión funcional y arquitectónica del catálogo, específicamente los puntos equivalentes a:

- actualización de productos comunes;
- archivado o baja lógica;
- control opcional de inventario por producto;
- ampliación del catálogo para representar todo lo vendible;
- categorías, subcategorías, etiquetas, búsqueda y filtros;
- variantes o presentaciones;
- código de barras;
- información comercial adicional;
- desacoplamiento de modelos de infraestructura;
- rediseño funcional y visual de la sección de catálogo del frontend.

El objetivo no es añadir funciones aisladas, sino dejar una base comercial, técnica y visual que pueda crecer sin requerir una refactorización transversal inmediata.

## 16.2 Resultado esperado

Al finalizar este plan, GymOS deberá contar con:

1. Un catálogo administrable de extremo a extremo.
2. Productos editables, activables, desactivables y archivables sin perder trazabilidad.
3. Clasificación comercial consistente mediante categorías y etiquetas.
4. Control de inventario configurable por producto.
5. Soporte preparado para códigos de barras, imágenes, presentaciones y variantes.
6. Una interfaz de catálogo rápida, clara, responsive y adecuada para operación diaria.
7. Contratos frontend/backend estables y versionables.
8. Componentes, servicios, repositorios y archivos pequeños, especializados y fáciles de probar.
9. Pruebas de negocio, arquitectura, integración y frontend suficientes para impedir regresiones.
10. Documentación actualizada y coherente con el código implementado.

---

# 16.3 Principios obligatorios de implementación

## 16.3.1 Modularidad

Toda implementación debe conservar la separación por dominio y responsabilidad.

No se permitirá concentrar en un solo archivo:

- validaciones;
- acceso a datos;
- reglas de negocio;
- transformación de modelos;
- coordinación de casos de uso;
- lógica de interfaz;
- estado remoto;
- formularios;
- tablas;
- modales o drawers.

Los archivos deberán dividirse antes de que acumulen responsabilidades heterogéneas. Como referencia, un archivo que supere aproximadamente 250 a 300 líneas deberá revisarse y justificar explícitamente por qué no puede dividirse. El número de líneas no será el único criterio: una clase o componente con varias razones para cambiar deberá separarse aunque sea más corto.

## 16.3.2 Arquitectura backend

El módulo `catalog` deberá conservar una estructura equivalente a:

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

Reglas:

- `api` traduce HTTP a comandos y respuestas.
- `application` coordina casos de uso.
- `domain` contiene conceptos y reglas de negocio cuando aporten valor real.
- `infrastructure` implementa persistencia, almacenamiento y adaptadores.
- `application` no puede utilizar `JdbcClient`.
- Los controladores no pueden devolver records definidos dentro de repositorios JDBC.
- Los repositorios no deben decidir reglas de negocio que correspondan a aplicación o dominio.
- Los módulos consumidores deberán depender de contratos públicos, no de tablas internas de catálogo.

## 16.3.3 Arquitectura frontend

La sección de catálogo deberá dividirse en:

```text
frontend/src/
  app/catalog/
    page.tsx
    loading.tsx
    error.tsx
    [productId]/
      page.tsx
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

Reglas:

- `page.tsx` debe ensamblar la pantalla, no contener toda la lógica.
- El acceso HTTP debe residir en el cliente API del dominio.
- Los formularios deben tener esquema y mapeadores independientes.
- Tabla, tarjetas, filtros, toolbar, estados vacíos y drawers deben ser componentes separados.
- La lógica de consulta y mutación debe encapsularse en hooks especializados.
- No duplicar tipos del backend de forma descontrolada; mantener contratos explícitos.
- Evitar efectos extensos y cadenas de estados locales que puedan reemplazarse por un hook o reducer cohesivo.

## 16.3.4 Eficiencia y eficacia

Cada cambio deberá:

- evitar consultas N+1;
- usar paginación en listados crecientes;
- usar índices para búsquedas frecuentes;
- evitar recargar el catálogo completo después de cada modificación cuando pueda actualizarse el estado de forma localizada;
- conservar aislamiento por organización;
- evitar cargas innecesarias de imágenes;
- aplicar debounce a búsquedas remotas;
- mantener operaciones comunes perceptiblemente rápidas;
- evitar duplicación de serialización, validación y mapeo.

## 16.3.5 Compatibilidad y migraciones

- Toda modificación de base de datos deberá realizarse mediante Flyway.
- Las migraciones deberán ser reversibles conceptualmente, seguras para datos existentes y compatibles con instalaciones ya creadas.
- Los nuevos campos deberán incluir valores por defecto o estrategia de backfill.
- Los contratos existentes no se romperán sin una ruta de migración documentada.

---

# 16.4 Alcance funcional detallado

## 16.4.1 Edición completa de productos

### Objetivo

Permitir modificar productos físicos y servicios comunes sin recrearlos.

### Backend

Implementar:

```text
GET   /api/v1/catalog/products/{productId}
PATCH /api/v1/catalog/products/{productId}
```

El caso de uso de actualización deberá permitir modificar, según permisos:

- nombre;
- descripción;
- categoría;
- subcategoría;
- etiquetas;
- precio;
- moneda;
- condición fiscal;
- estado;
- código de barras;
- imagen;
- control de inventario;
- orden de presentación;
- marca;
- unidad o presentación.

El SKU deberá tratarse como identificador comercial estable. Si se permite editarlo, deberán validarse dependencias, unicidad y auditoría específica.

### Reglas

- No actualizar productos de otra organización.
- No permitir SKU duplicado dentro de la organización.
- No convertir libremente un producto con movimientos de inventario de `PHYSICAL` a `SERVICE`.
- No convertir un producto utilizado por un plan de membresía a otro tipo incompatible.
- Toda actualización deberá registrar los campos modificados en auditoría.

### Frontend

- Acción `Editar` desde tabla, tarjeta y detalle.
- Formulario en `AppDrawer` para cambios rápidos.
- Página de detalle para información, dependencias e historial.
- Validación inmediata y mensajes de error específicos.
- Confirmación visual al guardar sin recargar toda la página.

### Criterios de aceptación

- Un administrador puede editar un producto y observar el cambio inmediatamente.
- Los errores de unicidad y transición inválida son comprensibles.
- El historial de ventas e inventario conserva la referencia al producto.
- Las pruebas cubren aislamiento multi-tenant.

---

## 16.4.2 Activación, desactivación y archivado lógico

### Objetivo

Separar claramente disponibilidad operativa de conservación histórica.

### Estados propuestos

```text
DRAFT
ACTIVE
INACTIVE
ARCHIVED
```

Si se decide conservar únicamente `ACTIVE` e `INACTIVE`, el archivado deberá representarse mediante `deleted_at` y una operación explícita.

### Backend

Implementar:

```text
POST /api/v1/catalog/products/{productId}/activate
POST /api/v1/catalog/products/{productId}/deactivate
POST /api/v1/catalog/products/{productId}/archive
```

También podrá usarse `PATCH`, pero los casos de uso deberán permanecer separados internamente para evitar una actualización genérica sin reglas.

### Reglas

- Un producto archivado no aparece en ventas nuevas.
- Un producto inactivo puede consultarse en administración.
- Un producto archivado permanece visible en historial.
- No archivar silenciosamente un producto ligado a un plan activo.
- Mostrar dependencias antes de archivar.
- Registrar motivo opcional u obligatorio según la operación.

### Frontend

- Menú contextual con activar, desactivar y archivar.
- Diálogo de confirmación con consecuencias.
- Filtro por estado.
- Vista opcional de archivados.
- Etiquetas visuales consistentes.

---

## 16.4.3 Control opcional de inventario

### Objetivo

Cumplir el requisito original de que el control de inventario sea configurable por producto.

### Modelo

Agregar un campo equivalente a:

```text
inventory_tracked BOOLEAN NOT NULL
```

Propuesta de backfill:

- productos `PHYSICAL` existentes: `true`;
- servicios y planes: `false`.

### Reglas

- Sólo productos físicos pueden tener `inventoryTracked = true`.
- Un producto con movimientos existentes no puede desactivar seguimiento sin una decisión explícita.
- El POS no descontará productos con seguimiento desactivado.
- Los listados de inventario filtrarán por `inventoryTracked = true`.
- Los reportes deberán distinguir productos físicos no controlados.

### Frontend

- Interruptor `Controlar existencias` en alta y edición.
- Explicación breve de su efecto.
- Indicador visible en detalle.
- Acceso directo a inventario cuando esté habilitado.

---

## 16.4.4 Ampliación de los conceptos vendibles

### Objetivo

Representar de manera consistente membresías, visitas, servicios, reservas, rutinas y consultas.

### Decisión de diseño recomendada

Evitar una proliferación excesiva de tipos rígidos. Mantener una clasificación principal reducida y añadir categoría o subtipo comercial.

Ejemplo:

```text
productType:
  PHYSICAL
  SERVICE
  MEMBERSHIP_PLAN

commercialCategory:
  GENERAL_PRODUCT
  VISIT_PASS
  BOOKING
  ROUTINE
  CONSULTATION
  WELLNESS_SERVICE
```

### Actividades

1. Definir catálogo de categorías del sistema y categorías personalizadas.
2. Definir qué módulos crean productos automáticamente.
3. Definir qué datos pertenecen a catálogo y cuáles al módulo especializado.
4. Evitar duplicar precio y estado en varias tablas sin una fuente de verdad.
5. Documentar la relación entre producto vendible y recurso operativo.

### Criterios

- POS puede filtrar por tipo y categoría.
- Una visita no se confunde con un servicio genérico.
- Una reserva vendible conserva referencia al recurso o servicio.
- Catálogo no absorbe reglas propias de reservas, rutinas o profesionales.

---

## 16.4.5 Categorías, subcategorías, etiquetas, búsqueda y filtros

### Modelo

Crear entidades o tablas equivalentes a:

```text
catalog_category
catalog_product_category
catalog_tag
catalog_product_tag
```

Para V1 puede permitirse una categoría principal y múltiples etiquetas.

### Capacidades

- crear y editar categorías;
- activar o archivar categorías;
- asignar categoría principal;
- asignar etiquetas;
- ordenar categorías;
- filtrar por categoría, tipo, estado e inventario;
- buscar por nombre, SKU y código de barras;
- ordenar por nombre, fecha, precio y existencias.

### Índices recomendados

- `(organization_id, sku)` único para productos no archivados;
- `(organization_id, barcode)` cuando el código no sea nulo;
- `(organization_id, status, product_type)`;
- índice de búsqueda normalizada para nombre;
- índices de relación por categoría y etiqueta.

### API propuesta

```text
GET  /api/v1/catalog/products?q=&type=&categoryId=&status=&inventoryTracked=&page=&size=&sort=
GET  /api/v1/catalog/categories
POST /api/v1/catalog/categories
PATCH /api/v1/catalog/categories/{categoryId}
```

La respuesta de productos deberá ser paginada y contener metadatos.

### Frontend

- buscador con debounce;
- filtros persistentes en URL;
- chips de filtros activos;
- ordenamiento;
- contador de resultados;
- limpieza rápida de filtros;
- estado vacío contextual;
- vista tabla y vista tarjetas, si ambas aportan valor.

---

## 16.4.6 Variantes y presentaciones

### Objetivo

Preparar el catálogo para ropa, suplementos y productos con talla, color, sabor o presentación.

### Estrategia por etapas

#### Etapa inicial

Mantener un producto por SKU y agregar campos simples:

- `brand`;
- `presentation`;
- `unitOfMeasure`.

#### Etapa preparada

Diseñar contratos para poder introducir posteriormente:

```text
catalog_product
catalog_product_variant
catalog_variant_option
```

### Restricciones

- No implementar un sistema de variantes complejo sin casos reales del piloto.
- No bloquear la futura migración.
- El SKU y código de barras deberán pertenecer a la unidad vendible real.
- Inventario deberá asociarse a la variante cuando ésta exista.

### Entregable de esta iteración

- decisión arquitectónica documentada;
- campos simples de presentación;
- tipos y DTO preparados sin sobrediseño;
- pruebas que permitan migrar sin romper consumidores.

---

## 16.4.7 Código de barras

### Backend

Agregar `barcode` opcional con:

- normalización;
- unicidad por organización;
- validación de longitud y caracteres;
- búsqueda exacta eficiente;
- auditoría al modificarlo.

### Frontend

- campo en alta y edición;
- acción de enfocar escáner;
- búsqueda instantánea en catálogo y POS;
- mensaje claro ante duplicado;
- representación visible en detalle, no necesariamente en todas las tarjetas.

### Consideraciones

- No asumir que SKU y código de barras son equivalentes.
- Permitir productos sin código de barras.
- Diseñar el API para lectores que funcionan como teclado.

---

## 16.4.8 Información comercial adicional

### Primera prioridad

- imagen principal;
- categoría;
- código de barras;
- control de inventario;
- marca;
- presentación;
- orden de visualización.

### Segunda prioridad

- proveedor principal;
- costo de referencia;
- precio promocional;
- vigencia promocional;
- disponibilidad por sucursal;
- múltiples imágenes.

### Imágenes

Usar MinIO mediante un puerto de almacenamiento. No guardar binarios en PostgreSQL.

Flujo recomendado:

1. solicitar carga o subir mediante endpoint dedicado;
2. validar tipo, tamaño y dimensiones;
3. almacenar objeto;
4. guardar referencia;
5. generar URL segura o pública según configuración;
6. eliminar o reemplazar sin dejar objetos huérfanos.

### API tentativa

```text
POST   /api/v1/catalog/products/{productId}/image
DELETE /api/v1/catalog/products/{productId}/image
```

---

## 16.4.9 Desacoplamiento de modelos de infraestructura

### Objetivo

Evitar que controladores y servicios dependan de records definidos dentro de repositorios JDBC.

### Cambios

Crear modelos de aplicación:

```text
catalog.application.query.ProductView
catalog.application.query.ProductDetailView
catalog.application.query.PlanView
catalog.application.query.CategoryView
```

Crear puertos:

```text
CatalogProductQueryPort
CatalogProductCommandPort
CatalogCategoryQueryPort
CatalogCategoryCommandPort
CatalogImageStoragePort
```

Los adaptadores JDBC implementarán estos puertos.

### Reglas

- Los records JDBC no salen de infraestructura.
- Los controladores sólo usan request/response DTO o vistas de aplicación.
- Los mapeadores están centralizados por adaptador.
- No crear una fachada gigantesca con decenas de métodos heterogéneos.
- Separar consultas y comandos cuando reduzca acoplamiento y complejidad.

### Pruebas de arquitectura

Añadir reglas que fallen cuando:

- `application` importe `JdbcClient`;
- `api` importe clases de `infrastructure`;
- otro módulo importe adaptadores internos de catálogo;
- un repositorio fachada vuelva a contener SQL directo cuando deba delegar.

---

# 16.5 Rediseño de la sección de catálogo del frontend

## 16.5.1 Objetivo de experiencia

La página de catálogo deberá convertirse en un centro de administración comercial y no únicamente en un formulario y una lista.

Debe permitir responder rápidamente:

- qué se vende;
- qué está activo;
- qué productos controlan inventario;
- qué productos tienen stock bajo;
- qué productos carecen de categoría, imagen o código;
- qué planes y servicios existen;
- qué elemento debe editarse o archivarse.

## 16.5.2 Estructura visual propuesta

### Encabezado

- título y descripción;
- acción `Nuevo producto`;
- acción `Nuevo plan`;
- menú secundario para categorías e importación futura.

### Resumen

Tarjetas compactas con:

- productos activos;
- servicios activos;
- planes activos;
- productos con inventario bajo;
- productos incompletos.

Las métricas deben provenir de una consulta agregada o calcularse sin descargar datos excesivos.

### Toolbar

- búsqueda;
- selector de tipo;
- selector de categoría;
- selector de estado;
- filtro de inventario;
- ordenamiento;
- alternancia tabla/tarjetas cuando sea útil.

### Contenido

Vista tabla para administración:

- imagen;
- nombre;
- SKU;
- categoría;
- tipo;
- precio;
- inventario o indicador;
- estado;
- acciones.

Vista tarjeta para contextos visuales:

- imagen;
- nombre;
- categoría;
- precio;
- estado;
- alerta de inventario;
- menú contextual.

### Detalle

Página o drawer con secciones:

- información comercial;
- precio e impuestos;
- inventario;
- categorías y etiquetas;
- imagen;
- dependencias;
- historial de cambios;
- acciones peligrosas.

## 16.5.3 Interacciones

- filtros reflejados en query params;
- debounce de búsqueda;
- skeletons al cargar;
- actualización optimista sólo cuando sea segura;
- invalidación localizada de datos;
- mensajes toast o notice consistentes;
- confirmación para acciones destructivas;
- accesibilidad por teclado;
- foco administrado en drawers y diálogos;
- responsive real para tablet y móvil.

## 16.5.4 Estados obligatorios

Cada sección deberá implementar:

- loading;
- error recuperable;
- sin resultados;
- catálogo vacío;
- permisos insuficientes;
- operación exitosa;
- conflicto de negocio;
- imagen no disponible.

## 16.5.5 Componentes propuestos

```text
CatalogPageHeader
CatalogSummaryCards
CatalogToolbar
CatalogSearchInput
CatalogFilterDrawer
CatalogActiveFilters
CatalogProductTable
CatalogProductGrid
CatalogProductRow
CatalogProductCard
CatalogProductStatusBadge
CatalogInventoryBadge
CatalogProductActions
CatalogProductDrawer
CatalogProductForm
CatalogArchiveDialog
CatalogCategoryManager
CatalogImageUploader
CatalogPagination
```

No todos deberán crearse si un componente genérico existente resuelve correctamente el problema. Se debe reutilizar `AppShell`, `AppDrawer`, botones, formularios y componentes compartidos antes de duplicar patrones.

## 16.5.6 Hooks propuestos

```text
useCatalogProducts
useCatalogProduct
useCatalogFilters
useCreateCatalogProduct
useUpdateCatalogProduct
useChangeCatalogProductStatus
useArchiveCatalogProduct
useCatalogCategories
useCatalogImageUpload
```

Cada hook deberá tener una responsabilidad concreta y exponer estados consistentes.

---

# 16.6 Plan de ejecución por fases

## Fase A — Contratos, decisiones y pruebas de caracterización

### Actividades

1. Documentar el modelo actual y dependencias.
2. Capturar comportamiento existente mediante pruebas.
3. Definir estados del producto.
4. Definir estrategia de categorías.
5. Definir estrategia de variantes por etapas.
6. Definir contratos de aplicación y API.
7. Definir política de SKU y código de barras.
8. Definir política de productos archivados.

### Entregables

- ADR o sección de decisiones.
- Pruebas de caracterización.
- contratos request/response.
- matriz de permisos.

### Condición de salida

No iniciar migraciones hasta resolver las decisiones que cambian estructura de datos.

---

## Fase B — Migración de datos y modelo persistente

### Actividades

1. Crear migración Flyway.
2. Agregar columnas y tablas.
3. Ejecutar backfill seguro.
4. Agregar índices y restricciones.
5. Actualizar seed local.
6. Probar migración sobre base con datos existentes.

### Campos mínimos

- `inventory_tracked`;
- `barcode`;
- `brand`;
- `presentation`;
- `display_order`;
- referencia de imagen;
- categoría principal o relación equivalente.

### Condición de salida

- migración reproducible;
- sin pérdida de ventas, planes o inventario;
- pruebas de integridad verdes.

---

## Fase C — Puertos, modelos de aplicación y repositorios especializados

### Actividades

1. Introducir vistas de aplicación.
2. Crear puertos de consulta y comando.
3. Adaptar repositorios JDBC.
4. Mantener una fachada temporal si hay consumidores existentes.
5. Añadir pruebas arquitectónicas.
6. Eliminar imports de infraestructura desde API y aplicación.

### Condición de salida

- `catalog.api` no depende de `catalog.infrastructure`;
- `catalog.application` no usa JDBC;
- repositorios con responsabilidades acotadas.

---

## Fase D — Casos de uso de producto

### Implementar

- consultar detalle;
- actualizar;
- activar;
- desactivar;
- archivar;
- consultar dependencias;
- administrar categorías;
- cargar y eliminar imagen.

### Condición de salida

Cada caso de uso tiene:

- reglas explícitas;
- permisos;
- auditoría;
- manejo de errores;
- pruebas unitarias e integración.

---

## Fase E — Búsqueda y listado paginado

### Actividades

1. Implementar query paginada.
2. Agregar filtros.
3. Agregar ordenamiento permitido.
4. Optimizar índices.
5. Añadir pruebas de rendimiento razonables.
6. Actualizar OpenAPI.

### Condición de salida

El listado no depende de cargar todo el catálogo en memoria.

---

## Fase F — Frontend base y cliente API

### Actividades

1. Actualizar tipos.
2. Crear API de catálogo por dominio.
3. Crear hooks de consulta y mutación.
4. Implementar filtros en URL.
5. Crear componentes de tabla y tarjetas.
6. Implementar detalle y formularios.
7. Integrar categorías e imagen.

### Condición de salida

`app/catalog/page.tsx` permanece como ensamblador pequeño y legible.

---

## Fase G — Integración con inventario, POS y módulos consumidores

### Actividades

1. Filtrar inventario por `inventoryTracked`.
2. Ajustar descuento por venta.
3. Ajustar POS y búsqueda por código de barras.
4. Revisar productos técnicos de membresía.
5. Revisar servicios, visitas y reservas.
6. Sustituir accesos directos a tablas por contratos públicos.

### Condición de salida

No existen regresiones en ventas, membresías o inventario.

---

## Fase H — Calidad, accesibilidad y documentación

### Actividades

1. Backend test completo.
2. Pruebas Testcontainers de flujos críticos.
3. Pruebas multi-tenant.
4. Pruebas frontend de componentes y flujos.
5. Lint, typecheck y build.
6. Validación responsive y teclado.
7. OpenAPI actualizado.
8. PRD, roadmap, casos de uso y estado actualizados.
9. Smoke tests Docker.

---

# 16.7 Permisos propuestos

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

Reglas:

- lectura operativa puede ser más amplia;
- costo y proveedor deberán restringirse;
- archivado requiere permiso específico;
- recepción no debe administrar estructura comercial salvo autorización.

---

# 16.8 Auditoría requerida

Eventos mínimos:

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

La auditoría de actualización deberá conservar al menos:

- actor;
- organización;
- producto;
- fecha;
- correlación;
- campos modificados;
- origen de operación.

---

# 16.9 Estrategia de pruebas

## Backend unitario

- normalización de SKU y código de barras;
- transiciones de estado;
- restricciones por tipo;
- reglas de inventario opcional;
- archivado con dependencias;
- actualización parcial;
- permisos.

## Backend integración

- CRUD completo;
- aislamiento por organización;
- paginación y filtros;
- índices y unicidad;
- migración con datos existentes;
- imágenes mediante adaptador;
- interacción con inventario y POS.

## Arquitectura

- aplicación sin JDBC;
- API sin infraestructura;
- consumidores sin adaptadores internos;
- tamaño y responsabilidad de fachadas revisados.

## Frontend

- filtros en URL;
- debounce;
- edición exitosa;
- conflicto de SKU o código;
- archivado con confirmación;
- estados vacíos y error;
- permisos;
- accesibilidad del drawer;
- responsive.

## End-to-end

1. Crear producto físico con inventario.
2. Asignar categoría e imagen.
3. Localizarlo por SKU y código.
4. Editarlo.
5. Venderlo.
6. Ver movimiento de inventario.
7. Desactivarlo.
8. Confirmar que no aparece en POS.
9. Consultarlo en historial.
10. Archivarlo sin perder referencias.

---

# 16.10 Límites y controles de complejidad

## Backend

- Controladores delgados.
- Un caso de uso por clase cuando la lógica sea significativa.
- Repositorios separados por agregado o intención.
- SQL complejo encapsulado y probado.
- No usar servicios genéricos con decenas de métodos.
- No crear utilidades globales para lógica exclusiva de catálogo.

## Frontend

- No crear una página monolítica.
- No mezclar consulta, mutación y renderizado complejo en un mismo componente.
- No duplicar formularios de alta y edición: compartir campos y variar comando.
- No crear componentes “universales” con demasiadas props.
- Preferir composición sobre condicionales extensos.
- Extraer secciones cuando tengan estado, lógica o pruebas propias.

## Revisión obligatoria por fase

Antes de cerrar cada fase, revisar:

- archivos que crecieron demasiado;
- clases con varias razones para cambiar;
- métodos con demasiadas ramas;
- duplicación de mapeos;
- consultas repetidas;
- componentes con demasiadas props;
- acoplamiento circular;
- dependencias cruzadas entre módulos.

---

# 16.11 Riesgos

## Riesgo: sobrediseñar variantes

Mitigación: implementar presentación simple y dejar contratos preparados.

## Riesgo: romper ventas históricas

Mitigación: archivado lógico, identificadores estables y pruebas de regresión.

## Riesgo: degradar rendimiento con filtros

Mitigación: índices, paginación y revisión de planes de ejecución.

## Riesgo: duplicar categorías de sistema y negocio

Mitigación: definir taxonomía y propiedad de cada dato antes de migrar.

## Riesgo: convertir el catálogo en un módulo que conoce todos los dominios

Mitigación: catálogo conserva información comercial; cada módulo conserva su operación.

## Riesgo: frontend visualmente completo pero técnicamente monolítico

Mitigación: estructura por feature, hooks especializados y límites de responsabilidad.

---

# 16.12 Definición de terminado

El trabajo se considerará terminado cuando:

- todos los puntos 2.5 a 2.13 estén implementados o resueltos mediante una decisión documentada;
- el catálogo permita crear, consultar, editar, activar, desactivar y archivar;
- categorías, búsqueda, filtros y paginación funcionen;
- `inventoryTracked` sea respetado por catálogo, inventario y POS;
- el código de barras sea único y buscable;
- la imagen se almacene mediante un puerto y MinIO;
- la interfaz sea responsive y accesible;
- los modelos de infraestructura no se filtren a API o aplicación;
- no existan archivos monolíticos sin justificación;
- pruebas backend, frontend, arquitectura y smoke estén verdes;
- migraciones, OpenAPI y documentación estén actualizadas;
- se haya realizado revisión explícita de complejidad antes del cierre.

---

# 16.13 Orden recomendado de entrega

1. Decisiones y pruebas de caracterización.
2. Migración y modelo persistente.
3. Puertos y desacoplamiento arquitectónico.
4. Edición, estados y archivado.
5. Categorías, búsqueda y paginación.
6. Control opcional de inventario.
7. Código de barras e información comercial.
8. Imágenes.
9. Rediseño frontend.
10. Integración con POS, membresías e inventario.
11. Pruebas completas, documentación y smoke tests.

Este orden reduce retrabajo porque estabiliza primero los contratos y el modelo antes de construir la nueva experiencia visual.