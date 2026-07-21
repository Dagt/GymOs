# Entrega para validación local

## Propósito

Este documento separa el trabajo ya realizado en la rama `feature/code-simplification-rebasion-and-refactor` de los cambios críticos que no deben aplicarse hasta tener una línea base local ejecutable.

## Comandos requeridos

### Backend

```bash
cd backend
./gradlew test --no-daemon
```

### Frontend

```bash
cd frontend
npm ci
npm test
npm run lint
npm run typecheck
npm run build
```

### Infraestructura

```bash
docker compose -f infra/docker-compose.yml config
docker compose -f infra/docker-compose.yml build
```

## Cómo reportar cada fallo

Para cada comando fallido conservar:

1. comando exacto;
2. primer error completo;
3. archivo y línea;
4. nombre de prueba o regla;
5. si también ocurre en `main`;
6. clasificación provisional: `PREEXISTING`, `INTRODUCED` o `UNKNOWN`.

No corregir varios fallos distintos en un mismo commit.

## Cambios que necesitan validación inmediata

### Frontend

- utilidades puras de fecha y hora;
- parsing y formateo de fecha mexicana del kiosco;
- campo de nacimiento integrado directamente en React;
- eliminación del enhancer y layout exclusivo del kiosco;
- pruebas Node TypeScript;
- `allowImportingTsExtensions` para typecheck;
- utilidades puras de reservas, calendario y horarios.

### Backend

- pruebas de JWT y manipulación de tokens;
- rotación de refresh tokens;
- conservación de contexto de organización, sucursal y soporte;
- propiedad del refresh token durante logout;
- validación de organizaciones activas al seleccionar y renovar sesión;
- restricción HTTP de creación de tenants a `platform.admin`;
- pruebas negativas de aislamiento de organizaciones, sucursales, membresías, pagos y reembolsos;
- idempotencia secuencial de eventos de membresía;
- allowlist exacta de `JdbcClient` en application.

## Cambios críticos deliberadamente bloqueados

No aplicar hasta ejecutar la suite y revisar el esquema PostgreSQL real:

- cambiar `anyRequest().authenticated()` a `denyAll()`;
- cambiar el alcance de detalle de ventas entre sucursales;
- centralizar y modificar `validatePayment` y `rejectPayment`;
- cambiar SQL o contratos de pagos y reembolsos;
- introducir bloqueo para reembolsos concurrentes;
- añadir restricciones únicas nuevas en `sale`, `payment`, `membership` o `processed_domain_event`;
- cambiar la generación obligatoria de llaves de idempotencia;
- extraer `JdbcClient` de servicios application de alta superficie;
- modificar transacciones de venta, pago y activación de membresía;
- eliminar los enhancers de reservas antes de validar equivalencia visual directa.

## Escenarios manuales mínimos

### Kiosco

- captura válida `DD/MM/AAAA`;
- fecha imposible;
- año fuera del rango permitido;
- socio encontrado y no encontrado;
- cierre de sesión limpia la fecha;
- valor enviado al backend conserva `AAAA-MM-DD`.

### Autenticación

- login normal;
- selección de sucursal;
- refresh conserva tenant y sucursal;
- refresh anterior no puede reutilizarse;
- logout propio invalida la sesión;
- logout con token ajeno no elimina la sesión ajena;
- organización suspendida no puede seleccionarse ni renovarse en soporte.

### Ventas y membresías

- venta con precio proveniente del catálogo;
- pago pendiente, validado y rechazado;
- reembolso parcial;
- renovación de membresía;
- cambio de plan;
- reintento con misma llave y mismo payload;
- reutilización con payload diferente debe fallar.

## Criterio para continuar cambios críticos

Se puede desbloquear un bloque cuando:

1. la suite relevante compila;
2. los fallos introducidos están corregidos;
3. el comportamiento actual está cubierto por una prueba;
4. la decisión de alcance o contrato está documentada;
5. el cambio puede hacerse en un commit pequeño y reversible.

## Política de PR

No abrir ni reabrir un pull request hasta que el usuario termine la validación local y autorice expresamente crear el PR.
