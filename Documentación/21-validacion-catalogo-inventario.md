# 21. Validación de catálogo e inventario

Estado: `VALIDATED_WITH_RESIDUAL_SCOPE`  
Fecha: 23 de julio de 2026  
Rama: `feature/catalog-inventory-enhancement-plan`

Este documento registra las validaciones ejecutadas después del hardening descrito en `18-implementacion-catalogo-inventario.md` y `19-registro-avance-catalogo-inventario.md`.

> Esta evidencia sustituye la advertencia histórica de la sección 18.11 que indicaba que las suites todavía no habían sido ejecutadas. El documento 18 deberá conservarse sincronizado en la siguiente revisión editorial, pero el resultado vigente es el registrado aquí.

## 21.1 Entorno

Se creó un checkout limpio de la rama remota y se ejecutaron las validaciones sobre Linux con:

- JDK y Gradle Wrapper del repositorio;
- Node.js y `npm ci`;
- Chromium instalado por Playwright;
- Docker Compose;
- PostgreSQL real del compose;
- MinIO real del compose;
- backend y frontend construidos desde la rama.

## 21.2 Backend

Comando:

```text
cd backend
./gradlew clean test
```

Resultado: `GREEN`.

La suite incluyó:

- pruebas unitarias de catálogo;
- permisos y redacción de costos;
- rechazo de planes técnicos en el flujo genérico;
- bloqueo optimista;
- pruebas de imágenes y compensación;
- pruebas PostgreSQL/Testcontainers de catálogo;
- integridad multi-tenant;
- slug reutilizable después del archivado;
- paginación y aislamiento de inventario;
- integración de eventos de venta y reembolso;
- pruebas de arquitectura.

## 21.3 Frontend

Instalación:

```text
cd frontend
npm ci
```

Validaciones:

```text
npm run test
npm run lint
npm run typecheck
npm run build
```

Resultado: `GREEN` en los cuatro comandos.

## 21.4 Playwright

Preparación y ejecución:

```text
npx playwright install chromium
npm run test:e2e
```

Resultado: `GREEN`.

La ejecución incluyó `frontend/e2e/catalog-inventory.spec.ts`, que protege:

- filtros persistidos en URL;
- versión enviada en edición;
- ocultamiento de costos sin permiso;
- ocultamiento de acciones de imagen sin permiso;
- búsqueda paginada del POS;
- ausencia de descarga del catálogo completo en POS.

## 21.5 Docker Compose

Comandos:

```text
docker compose -f infra/docker-compose.yml config
docker compose -f infra/docker-compose.yml build
docker compose -f infra/docker-compose.yml up -d
```

Resultado: `GREEN`.

## 21.6 Salud de servicios

Se verificaron:

```text
GET http://localhost:8080/actuator/health
GET http://localhost:3000/login
GET http://localhost:9000/minio/health/live
```

Resultado:

- backend: disponible y saludable;
- frontend: HTTP 200;
- MinIO: disponible.

## 21.7 Smoke autenticado de lectura

Con un usuario piloto con permisos suficientes se consultaron contra la pila real:

```text
GET /api/v1/catalog/products/search?page=0&size=5
GET /api/v1/catalog/products/summary
GET /api/v1/catalog/categories?includeInactive=true
GET /api/v1/inventory/search?page=0&size=5
GET /api/v1/inventory/movements/search?page=0&size=5
```

Resultado: `GREEN`.

## 21.8 Smoke real de MinIO

Flujo ejecutado:

1. crear producto temporal;
2. cargar primera imagen PNG;
3. sustituir por una segunda imagen;
4. eliminar la imagen;
5. archivar el producto temporal;
6. consultar `catalog_storage_cleanup`;
7. esperar al worker programado.

Resultado:

- ambas cargas devolvieron claves distintas;
- `image_object_key` quedó nulo después de eliminar;
- las dos claves anteriores fueron registradas en la cola;
- el worker marcó al menos dos tareas como `COMPLETED`;
- MinIO no produjo errores durante el proceso.

## 21.9 Smoke integrado catálogo–inventario–ventas–reembolso

Flujo ejecutado:

1. crear producto físico activo con `inventoryTracked=true`;
2. registrar movimiento inicial de cinco unidades;
3. confirmar existencia `5`;
4. crear venta pagada de dos unidades;
5. confirmar descuento automático a `3`;
6. registrar reembolso total;
7. confirmar reposición automática a `5`;
8. archivar el producto temporal.

Resultado: `GREEN`.

## 21.10 Automatización incorporada

Se agregó:

```text
scripts/smoke_catalog_inventory.py
```

Uso:

```text
export GYMOS_SMOKE_USERNAME='usuario'
export GYMOS_SMOKE_PASSWORD='contraseña'
python scripts/smoke_catalog_inventory.py
```

Variable opcional:

```text
GYMOS_BASE_URL=http://localhost:8080/api/v1
```

El script valida:

- autenticación;
- creación de producto;
- carga, sustitución y eliminación de imagen;
- inventario inicial;
- venta pagada;
- descuento;
- reembolso;
- reposición;
- archivado del dato temporal.

La ejecución del script sobre la pila local terminó con estado `OK`.

## 21.11 Resultado consolidado

| Validación | Resultado |
|---|---|
| Backend `clean test` | GREEN |
| Frontend tests | GREEN |
| ESLint | GREEN |
| TypeScript | GREEN |
| Next.js build | GREEN |
| Playwright | GREEN |
| Docker config | GREEN |
| Docker build | GREEN |
| Docker up | GREEN |
| Backend health | GREEN |
| Frontend HTTP | GREEN |
| MinIO health | GREEN |
| Smoke lectura autenticada | GREEN |
| Smoke MinIO | GREEN |
| Smoke venta e inventario | GREEN |
| Smoke reproducible | GREEN |

## 21.12 Alcance que continúa pendiente

Las validaciones verdes no convierten automáticamente todo el plan en `DONE`. Continúan fuera del alcance cerrado:

### Devoluciones detalladas

- devolución por línea;
- cantidad por producto;
- decisión `restock`;
- productos dañados o no retornables;
- reversión de una devolución.

### Rendimiento de alto volumen

- conjunto de datos representativo;
- `EXPLAIN ANALYZE` documentado;
- decisión sobre `pg_trgm`;
- pruebas de carga concurrente.

### Operación de imágenes

- endpoint o URL firmada para mostrar la imagen almacenada;
- política de retención y purga de tareas `COMPLETED`;
- métricas y alertas del worker de limpieza.

### Compatibilidad

- deprecación formal de endpoints legacy no paginados;
- confirmación de que no existen consumidores externos antes de eliminarlos.

## 21.13 Criterio de estado

Con esta evidencia, el bloque puede pasar de `IN_PROGRESS_HARDENING` a:

```text
VALIDATED_WITH_RESIDUAL_SCOPE
```

No debe marcarse `DONE` hasta resolver o aceptar formalmente la deuda residual de la sección 21.12.
