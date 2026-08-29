# GymFort Local Connector

Proceso local y provider-neutral que entrega el estado de acceso calculado por
GymFort Cloud a un proveedor físico. Es un componente desplegable independiente
del backend: no se conecta a PostgreSQL y no contiene reglas comerciales.

Este primer incremento incluye:

- cliente del contrato `/api/v1/physical-connectors`;
- procesamiento durable de jobs con idempotencia SQLite;
- renovación periódica de leases y reanudación tras una caída;
- secretos protegidos con DPAPI en Windows;
- driver `SIMULATOR` para pruebas sin hardware;
- contrato neutral `AccessProviderDriver`;
- base del driver `HIKCENTRAL` con diagnóstico OpenAPI y asignación exacta de
  Access Levels administrados por GymFort.

Diseño y aceptación:

- [Arquitectura y garantías](docs/architecture.md)
- [Matriz de aceptación HikCentral](docs/hikcentral-site-acceptance-matrix.md)

No es todavía una integración certificada para producción: faltan validación
contra una instalación real, eventos HikCentral, enrolamiento asistido y el
modo de autorización local para paquetes de visitas medidos.

## Compilar y probar

El repositorio reutiliza el wrapper Gradle del backend:

```powershell
..\backend\gradlew.bat -p . test
..\backend\gradlew.bat -p . installDist
```

Desde la raíz:

```powershell
backend\gradlew.bat -p connector test
backend\gradlew.bat -p connector installDist
```

## Desarrollo con simulador

1. Copia `connector.example.properties` como `connector.properties`.
2. Configura y activa una conexión física en GymFort.
3. Activa el proceso:

```powershell
connector\build\install\gymfort-local-connector\bin\gymfort-local-connector.bat `
  --config connector\connector.properties activate CODIGO_DE_UN_SOLO_USO
```

4. Ejecuta el diagnóstico y después el worker:

```powershell
connector\build\install\gymfort-local-connector\bin\gymfort-local-connector.bat `
  --config connector\connector.properties doctor

connector\build\install\gymfort-local-connector\bin\gymfort-local-connector.bat `
  --config connector\connector.properties run
```

El secreto de GymFort se cifra con DPAPI y sólo puede descifrarse bajo la misma
cuenta de Windows. En sistemas no Windows debe inyectarse mediante
`GYMFORT_CONNECTOR_SECRET`; no se ofrece almacenamiento plano como fallback.
Los logs rotan por tamaño/día durante 14 días (máximo agregado 200 MB). El
directorio predeterminado es `runtime/logs` y puede cambiarse con
`GYMFORT_CONNECTOR_LOG_DIR`.

## HikCentral

Configura `connector.provider=HIKCENTRAL`, `hikcentral.baseUrl`,
`hikcentral.appKey` y la lista cerrada `hikcentral.managedAccessLevelRefs`.
Guarda el App Secret desde una consola interactiva:

```powershell
...\gymfort-local-connector.bat --config connector.properties set-secret hikcentral.appSecret
```

El driver sólo agrega o retira al socio de los Access Levels declarados como
administrados. Nunca elimina permisos que estén fuera de ese conjunto.
