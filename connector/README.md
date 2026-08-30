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
  Access Levels administrados por GymFort;
- descubrimiento neutral de personas y dispositivos con vinculación explícita;
- ingestión durable de eventos, autorización GymFort y apertura remota auditada;
- dos modos por punto: proveedor administrado o validación GymFort en línea;
- perfil declarativo HikCentral para adaptar rutas/campos OpenAPI sin recompilar.

Diseño y aceptación:

- [Arquitectura y garantías](docs/architecture.md)
- [Matriz de aceptación HikCentral](docs/hikcentral-site-acceptance-matrix.md)
- [Matriz integral Centro/Tecnológica](docs/TEST-MATRIX.md)
- [Perfil OpenAPI configurable](docs/HIKCENTRAL-CAPABILITY-PROFILE.md)
- [Instalación piloto en Windows](docs/windows-pilot-installation.md)

El flujo completo está automatizado con el simulador. Todavía no es una
integración certificada para producción física: deben completarse el perfil
con la documentación OpenAPI de la instalación, las pruebas con HikCentral y
el torniquete reales, el enrolamiento facial local y la firma Authenticode.

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
backend\gradlew.bat -p connector pilotChecksum
```

`pilotChecksum` produce una imagen Windows autocontenida con su propio Java,
scripts de instalación y un SHA-256 para verificar la copia. Consulta el
runbook antes de llevarla al gimnasio.

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
`hikcentral.appKey` y, cuando existan permisos autónomos, la lista cerrada
`hikcentral.managedAccessLevelRefs`. La lista puede quedar vacía en una
instalación exclusivamente `GYMFORT_ONLINE`.
`hikcentral.baseUrl` exige HTTPS. Si el servidor usa una CA local, exporta su
certificado X.509 y configura `hikcentral.trustedCertificatePath`; esto agrega
esa confianza sin reemplazar las autoridades públicas ni desactivar la
verificación del nombre del servidor.
Para descubrimiento, eventos y apertura remota configura además
`hikcentral.capabilityProfilePath`; consulta la guía del perfil. Sin ese archivo
el modo estático de Access Levels continúa funcionando, pero `GYMFORT_ONLINE`
no puede habilitarse. El Connector reporta sus capacidades en el heartbeat y
el backend exige `EVENT_POLLING` y `REMOTE_OPEN` antes de aceptar ese modo.
Guarda el App Secret desde una consola interactiva:

```powershell
...\gymfort-local-connector.bat --config connector.properties set-secret hikcentral.appSecret
```

El driver sólo agrega o retira al socio de los Access Levels declarados como
administrados. Nunca elimina permisos que estén fuera de ese conjunto. Una
lista vacía desactiva esa sincronización sin afectar descubrimiento, eventos o
apertura remota.
