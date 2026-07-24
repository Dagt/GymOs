# 24. Registro de hallazgos y deuda técnica posterior al cierre de catálogo

Estado: BASELINE PARA SIGUIENTES ITERACIONES  
Fecha de revisión: 24 de julio de 2026  
Rama revisada: `feature/catalog-inventory-enhancement-plan`  
PR de cierre: `#5`  

## 1. Objetivo

Este documento registra el estado técnico real después de la mejora integral de catálogo, productos por sucursal, planes, categorías, sesiones, alta de socios, devoluciones e inventario básico.

No representa una lista de defectos que impida usar la plataforma. Distingue entre:

- problemas corregidos antes del cierre;
- decisiones de negocio vigentes;
- riesgos que deben vigilarse;
- deuda técnica que debe resolverse por prioridad;
- trabajo recomendado para la siguiente rama dedicada a inventarios.

## 2. Alcance y método de revisión

La revisión incluyó:

- contratos REST y reglas de autorización en `SecurityConfig`;
- emisión, renovación y almacenamiento de sesión;
- aislamiento por organización y sucursal;
- creación, edición, archivado y búsqueda de catálogo;
- planes y activación de membresías por pago;
- alta de socios con membresía inicial;
- movimientos automáticos de inventario por venta y devolución;
- pruebas de arquitectura, aplicación, persistencia y frontend;
- flujo de CI de backend, frontend y Docker;
- comparación de la rama contra `main`.

La rama de cierre contiene un cambio de gran tamaño: más de 190 archivos y cerca de 400 commits acumulados. El merge debe realizarse preferentemente mediante `squash` para no trasladar a `main` el historial experimental completo.

## 3. Correcciones cerradas en la iteración final

### 3.1 Borradores de datos del gimnasio

Hallazgo:

- la tarjeta de resumen de la organización consumía el mismo objeto que el formulario del drawer;
- cada pulsación modificaba el resumen visible aunque no se hubiera guardado;
- cerrar el drawer no restauraba necesariamente el último estado persistido.

Corrección:

- el resumen usa exclusivamente el objeto persistido recibido del backend;
- al abrir el drawer se crea un borrador a partir del estado persistido;
- al cancelar se descarta el borrador;
- el mismo patrón se aplicó a edición y alta de sucursales.

### 3.2 Duración visible de membresías

Hallazgo:

- la fecha se calculaba con `durationDays - 1`;
- un plan iniciado el 22 de julio con duración 30 mostraba vencimiento el 20 de agosto;
- esto contradice la expectativa operativa de “vence 30 días después”.

Corrección:

- la fecha de vencimiento se calcula como `startsOn + durationDays`;
- se añadieron pruebas para alta y renovación;
- una membresía iniciada el 22 de julio por 30 días muestra vencimiento el 21 de agosto.

Deuda residual:

- la plataforma usa fechas sin hora y combina conceptos de “último día válido” y “momento de expiración”;
- debe estandarizarse formalmente si `ends_on` es inclusivo o exclusivo en todos los módulos.

### 3.3 Sesiones simultáneas

Hallazgo:

- las credenciales estaban en `localStorage`;
- dos cuentas abiertas en pestañas del mismo navegador compartían y sobrescribían access token, refresh token y usuario;
- el problema se manifestaba como 401 en endpoints no relacionados, por ejemplo `/organizations/current`.

Corrección:

- cada pestaña usa `sessionStorage`;
- cada sesión mantiene su propio access token y refresh token rotativo;
- una pestaña de dueño y otra de gerente pueden coexistir sin contaminarse;
- recepción recibe un access token de 24 horas;
- perfiles administrativos conservan el token corto y la renovación mediante refresh token.

### 3.4 Catálogo y jerarquía visual

Cerrado:

- productos y planes visibles sólo en la sucursal activa;
- categorías compartidas por organización;
- stock y punto de reorden por sucursal;
- categorías en columnas de altura independiente;
- productos en tabla compacta con scroll interno;
- resumen compacto;
- filtros visibles por defecto con controles reducidos;
- formularios de producto y plan simplificados.

## 4. Decisiones de negocio vigentes

### 4.1 Gerente y plan inicial

El flujo “crear socio con plan inicial” crea realmente:

1. el socio;
2. una venta;
3. un pago;
4. la activación de la membresía cuando el pago se completa.

Por lo tanto, el gerente sólo puede seleccionar y cobrar un plan inicial cuando tiene `sale.create` y acceso al catálogo.

La restricción actual es correcta desde el principio de mínimo privilegio: administrar personal o consultar socios no debe otorgar implícitamente funciones de caja.

Mejora futura recomendada:

- separar `membership.assign`, `membership.activate`, `sale.create` y `payment.collect`;
- permitir al gerente generar una membresía pendiente de pago sin registrar efectivo;
- permitir que recepción o caja complete posteriormente el cobro;
- registrar auditoría de quién asignó, quién cobró y quién validó.

No se recomienda conceder `sale.create` al gerente sólo para evitar esta restricción.

### 4.2 Categorías compartidas

Se mantiene que las categorías son maestras para todo el gimnasio. Esto evita duplicar “Bebidas”, “Suplementos” o “Accesorios” por sucursal.

Productos, planes, precios operativos e inventario deben evolucionar independientemente por sucursal.

## 5. Deuda técnica priorizada

## P0 — Riesgos que deben resolverse antes de escalar producción

### P0.1 Modelo uniforme de autorización

Situación:

- las reglas existen en migraciones SQL, `SecurityConfig`, frontend, modelo de consola y lógica de negocio;
- la duplicación ya produjo diferencias entre enlaces visibles y endpoints permitidos;
- algunos componentes infieren capacidades por nombre de rol o combinaciones de permisos.

Acción:

- crear una matriz versionada de perfiles y capacidades;
- utilizar códigos estables de rol, no coincidencias de texto como “gerente”, “owner” o “recepción”;
- añadir pruebas de contrato por endpoint y rol;
- centralizar helpers de autorización del frontend;
- impedir que la UI use una condición distinta a la API.

Criterio de cierre:

- una suite parametrizada demuestra los permisos de administrador, dueño, gerente, recepción y coach sobre todas las rutas sensibles.

### P0.2 Almacenamiento seguro de refresh token

Situación:

- `sessionStorage` corrige la contaminación entre pestañas;
- access token y refresh token siguen siendo accesibles desde JavaScript;
- un XSS podría extraerlos.

Acción:

- migrar refresh token a cookie `HttpOnly`, `Secure` y `SameSite`;
- mantener access token sólo en memoria cuando sea posible;
- implementar familia de refresh tokens y detección de reutilización;
- añadir revocación por dispositivo y cierre remoto de sesión.

### P0.3 Consistencia entre venta e inventario

Situación:

- el inventario se descuenta mediante evento posterior a la venta;
- dos ventas concurrentes pueden aprobarse antes de que el stock haya sido descontado;
- la política de stock negativo no está formalizada.

Acción:

- definir si la venta bloquea, reserva o permite stock negativo;
- realizar verificación y decremento atómico bajo lock de la fila de stock;
- establecer reintentos e idempotencia del consumidor;
- exponer estado “venta registrada, inventario pendiente” cuando exista asincronía.

Criterio de cierre:

- pruebas concurrentes demuestran que no se venden más unidades que las permitidas por la política configurada.

### P0.4 Migraciones con datos reales

Situación:

- `V41`, `V42` y `V43` contienen backfills y reparaciones de permisos;
- Flyway tiene `out-of-order` habilitado globalmente;
- CI valida una base limpia, no una copia anonimizada de una base existente.

Acción:

- ejecutar migración sobre snapshot anonimizado;
- validar conteos antes/después;
- deshabilitar `out-of-order` en producción salvo procedimiento excepcional;
- documentar rollback o forward-fix por migración;
- agregar smoke de permisos después de Flyway.

## P1 — Deuda funcional y arquitectónica importante

### P1.1 Semántica de fechas de membresía

Problema:

- `starts_on`, `ends_on` y `grace_ends_on` son `LocalDate`;
- acceso, reservas, reportes y UI deben interpretar el mismo límite;
- actualmente se usa una zona global `America/Mexico_City`, aunque cada organización y sucursal almacenan zona horaria.

Acción:

- elegir una semántica única:
  - `valid_through` inclusivo; o
  - `expires_on` exclusivo;
- usar la zona horaria de la sucursal u organización;
- cubrir cambio de horario, compra cerca de medianoche, renovación y gracia;
- compartir una política pública de elegibilidad entre membresías, acceso y reservas.

### P1.2 Puertos de aplicación en módulos restantes

Situación:

- catálogo e inventario tienen puertos explícitos;
- otros servicios de aplicación todavía dependen de repositorios de `infrastructure` de su propio módulo;
- `MembershipActivationWindowCalculator` y varios servicios de ventas son ejemplos.

Acción:

- extender el patrón `application.port` a membresías, ventas, acceso, reservas y organizaciones;
- prohibir gradualmente `application -> infrastructure` también dentro del mismo módulo;
- añadir reglas ArchUnit por módulo.

### P1.3 Producto maestro versus oferta por sucursal

Situación:

- `catalog_product_branch` define disponibilidad;
- nombre, SKU y precio continúan en el producto maestro;
- no existe aún una entidad formal para precio, estado comercial, proveedor o costo específicos por sucursal.

Riesgo:

- dos sucursales pueden necesitar el mismo producto y SKU con precios o proveedores distintos;
- duplicar el producto puede chocar con unicidad organizacional del SKU y fragmentar reportes.

Acción:

- definir `catalog_branch_offer` o equivalente con:
  - precio;
  - estado;
  - costo y proveedor local;
  - impuestos/promoción;
  - vigencia;
- mantener identidad, código de barras y descripción en el producto maestro.

### P1.4 Permisos de membresía separados de caja

Acción:

- introducir permisos específicos para proponer, asignar, activar, cobrar y validar membresías;
- soportar membresía pendiente de pago;
- evitar que la única forma de asignar un plan sea registrar una venta completa.

### P1.5 Reembolsos financieros completos

Situación:

- ya existen devoluciones itemizadas y reposición selectiva;
- falta formalizar prorrateo de descuentos, impuestos y comisiones por línea;
- el estado interno no implica necesariamente devolución en una pasarela externa.

Acción:

- asignar importe reembolsable por línea;
- reconciliar contra proveedor de pago;
- distinguir solicitado, procesando, procesado, rechazado y conciliado;
- generar reportes de diferencias.

### P1.6 Ciclo operativo de archivos en MinIO

Situación:

- existe cola de limpieza para imágenes;
- faltan métricas, alertas y recuperación operacional.

Acción:

- contabilizar trabajos pendientes y fallidos;
- reintentos con backoff;
- dead-letter y reproceso manual;
- detección de objetos huérfanos;
- políticas de respaldo y retención.

### P1.7 Estado de sesión centralizado en frontend

Situación:

- las páginas suelen capturar una copia de `StoredSession` en estado local;
- `apiFetch` puede renovar tokens, pero el árbol de React no siempre recibe inmediatamente el usuario renovado;
- permisos, sucursal o modo soporte pueden quedar visualmente obsoletos hasta recargar.

Acción:

- crear `AuthProvider` o store central;
- exponer sesión reactiva y eventos de cambio;
- centralizar logout y expiración;
- eliminar helpers locales repetidos de permisos.

## P2 — Mantenibilidad, experiencia y operación

### P2.1 Servicios y componentes grandes

Hallazgos:

- `CatalogProductService` supera ampliamente el tamaño recomendado y reúne validación, mutación, auditoría, normalización y descripción de cambios;
- algunos controladores/hooks del frontend concentran carga, permisos, formularios, imágenes y mensajes.

Acción:

- extraer validadores, normalizadores y política de cambios;
- separar comandos de alta, actualización, estado y archivado;
- establecer umbral orientativo de 250–300 líneas con revisión obligatoria.

### P2.2 Campos comerciales ocultos pero persistentes

Situación:

- promociones y subtipos históricos permanecen en modelo y API aunque el flujo diario los oculta;
- pueden convertirse en campos muertos o inconsistentes.

Acción:

- decidir si se implementarán integralmente;
- de lo contrario, deprecar y retirar mediante migración;
- no conservar funcionalidades parciales sin consumidor claro.

### P2.3 Búsqueda y escalabilidad

Acción:

- medir consultas con volúmenes reales;
- añadir trigramas o full-text cuando corresponda;
- revisar índices por organización, sucursal, estado y búsqueda;
- establecer límites de página y orden estable;
- probar 10 mil, 100 mil y 1 millón de movimientos de inventario.

### P2.4 Accesibilidad del scroll oculto

Situación:

- el scroll interno oculto mejora densidad visual;
- reduce la señal visual de que existe más contenido.

Acción:

- mantener navegación por teclado;
- añadir sombras/fades de continuidad;
- verificar lectores de pantalla y foco;
- permitir barra visible en preferencias de accesibilidad.

### P2.5 Observabilidad

Falta consolidar:

- métricas por operación de negocio;
- errores 401/403 por endpoint y rol;
- retraso de consumidores de eventos;
- inventario inconsistente;
- cola de limpieza;
- tasa de reintento y conflictos optimistas;
- alertas de stock bajo enviadas, no sólo calculadas.

### P2.6 Seguridad operativa

Pendientes:

- rate limiting de login y refresh;
- alertas por intentos fallidos;
- cierre automático por inactividad para perfiles sensibles;
- bloqueo de pantalla para recepción sin destruir la sesión de jornada;
- secretos obligatorios en producción;
- revisión de CORS por ambiente;
- pinning de imágenes Docker en lugar de etiquetas flotantes.

### P2.7 Documentación y contratos

Acción:

- declarar un documento canónico por módulo;
- eliminar endpoints antiguos o marcarlos como deprecated;
- actualizar diagramas de contexto por sucursal;
- sincronizar estado `PLANNED`, `IMPLEMENTATION_COMPLETE` y cierre real;
- generar OpenAPI verificado en CI.

## 6. Cobertura de pruebas pendiente

Aun con CI verde, se recomiendan las siguientes pruebas:

1. E2E real con backend y PostgreSQL, no sólo mocks de API.
2. Dos sesiones distintas en pestañas o contextos de navegador simultáneos.
3. Venta concurrente del último artículo disponible.
4. Venta seguida de devolución parcial con y sin reposición.
5. Migración desde una base con productos y ventas históricos en varias sucursales.
6. Compra de membresía antes y después de medianoche en zonas diferentes.
7. Renovación anticipada, tardía y durante periodo de gracia.
8. Cambio de plan con membresía vigente.
9. Matriz completa de permisos por perfil.
10. Pruebas de carga para catálogo e historial de movimientos.

## 7. Plan recomendado para la nueva rama de inventarios

Nombre sugerido:

`feature/inventory-operations-hardening`

### Fase I — Integridad y concurrencia

- definir política de stock negativo;
- lock atómico al descontar;
- idempotencia de movimientos manuales;
- validación de pertenencia producto-sucursal;
- pruebas concurrentes;
- reconciliación venta/movimiento.

### Fase II — Operación diaria

- entradas por compra o recepción;
- salidas por merma, consumo interno y ajuste;
- transferencias entre sucursales;
- motivos configurables;
- folios y evidencia;
- autorización de ajustes sensibles.

### Fase III — Conteos físicos

- sesiones de conteo;
- captura parcial;
- diferencias esperadas versus físicas;
- aprobación y generación de ajuste;
- auditoría por usuario y sucursal.

### Fase IV — Costos y reabastecimiento

- costo promedio ponderado;
- costo por recepción;
- proveedor por sucursal;
- punto de reorden;
- cantidad sugerida;
- alertas y notificaciones;
- reporte de valuación.

### Fase V — Escenarios avanzados

- lotes y caducidad;
- unidades de compra y venta;
- inventario reservado;
- devoluciones a proveedor;
- productos compuestos o recetas, si el negocio los requiere.

## 8. Criterios para considerar inventario listo

- ninguna operación puede afectar otra sucursal;
- cada cambio produce un movimiento inmutable;
- el saldo se puede reconstruir desde el ledger;
- no hay doble descuento por reintento;
- concurrencia cubierta por pruebas;
- ajustes sensibles requieren permiso y auditoría;
- stock bajo produce una acción operativa verificable;
- reportes y POS muestran el mismo saldo;
- CI, migraciones y smoke real quedan verdes.

## 9. Recomendaciones de proceso

- ramas pequeñas y enfocadas;
- PR de menos de 30 archivos cuando sea posible;
- squash merge para ramas experimentales extensas;
- CI requerido antes de merge;
- smoke manual documentado para cambios de permisos y sucursal;
- ADR para decisiones de fechas, inventario negativo y producto maestro;
- no mezclar refactor transversal con funcionalidad nueva salvo necesidad demostrada.

## 10. Estado final del bloque catálogo

El bloque puede cerrarse funcionalmente con estas condiciones:

- CI completo verde;
- smoke manual de edición de gimnasio, fecha de membresía, filtros visibles y sesiones por pestaña;
- PR fusionado mediante squash;
- nueva rama de inventario creada desde el `main` resultante.

La deuda registrada en este documento no debe reabrir la rama de catálogo salvo que aparezca un defecto crítico. Los siguientes cambios deben tratarse en ramas dedicadas, empezando por integridad y operación de inventarios.
