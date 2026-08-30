# Perfil de capacidades HikCentral

El Connector no fija rutas inventadas para eventos, recursos ni apertura remota. Hikvision distribuye la documentación OpenAPI que corresponde a la versión instalada de HikCentral Professional; el perfil traduce ese contrato local al modelo estable de GymFort.

## Qué ya no requiere recompilar

- ruta OpenAPI de búsqueda de personas;
- ruta y forma de paginación de recursos/puertas;
- ruta de eventos de acceso;
- campos JSON de persona, dispositivo, fecha y decisión;
- normalización de valores de decisión y formatos de fecha;
- ruta y cuerpo para apertura remota;
- referencia de operación devuelta por HikCentral.

El archivo `config/hikcentral-profile.example.json` es una plantilla deliberadamente no ejecutable. Los segmentos `REPLACE_FROM_INSTALLED_OPENAPI` se sustituyen usando la documentación incluida en la instalación del gimnasio. No deben copiarse como endpoints reales.

## Variables disponibles

En búsquedas: `${cursor}`, `${limit}` y `${now}`. En apertura: `${externalDeviceRef}`, `${externalEventId}` e `${idempotencyKey}`.

Los valores que sólo contienen un entero se serializan como número JSON; los demás como texto. Todos los `path` deben iniciar con `/artemis/`, y los campos se expresan como JSON Pointer relativo al elemento de la lista.

`paginationMode` admite `NEXT_CURSOR` (valor predeterminado) y `PAGE_NUMBER`. En `NEXT_CURSOR`, configura `nextCursorPointer` y opcionalmente `hasMorePointer`; si HikCentral afirma que hay más resultados sin avanzar el cursor, el Connector falla de forma controlada en vez de repetir indefinidamente. En `PAGE_NUMBER`, configura preferentemente `totalPointer`; si el contrato sólo expone una bandera puede usarse `hasMorePointer`, y como último recurso se infiere por el tamaño de página.

`valueMappings` traduce valores propios del paquete (por ejemplo `1`/`0`) a los valores estables `GRANTED`/`DENIED`. `timestampFormat` admite `ISO_OFFSET_DATE_TIME`, `ISO_INSTANT`, `ISO_LOCAL_DATE_TIME`, `ISO_ZONED_DATE_TIME`, `EPOCH_MILLIS` y `EPOCH_SECONDS`. Para una fecha local sin offset es obligatorio declarar `timestampZone`, por ejemplo `America/Mexico_City`.

## Modos por dispositivo

- `PROVIDER_MANAGED`: HikCentral decide y abre localmente; GymFort recibe el evento para auditoría. No se descuentan paquetes a partir de esa notificación.
- `GYMFORT_ONLINE`: el evento se envía a GymFort, GymFort evalúa membresía/paquete, y el Connector ejecuta `remoteOpen` sólo cuando la decisión es `GRANTED`. Este modo es obligatorio para aplicar paquetes de visitas en tiempo real.

Un beneficio usado por un punto `GYMFORT_ONLINE` no se sincroniza como Access Level autónomo para esa conexión: hacerlo permitiría que HikCentral abriera antes de consultar a GymFort. El backend también impide mezclar `PROVIDER_MANAGED` y `GYMFORT_ONLINE` para el mismo beneficio. En HikCentral, la persona puede permanecer enrolada para reconocimiento, pero el Access Level que abre una puerta en línea no debe otorgarle apertura local; este aislamiento forma parte obligatoria de la validación en sitio.

No habilites `GYMFORT_ONLINE` hasta que `doctor` muestre `EVENT_POLLING` y `REMOTE_OPEN`, el dispositivo descubierto esté vinculado a un punto GymFort y una prueba controlada confirme la semántica del evento del equipo.

El Connector reporta esas capacidades en cada heartbeat. El backend impide guardar `GYMFORT_ONLINE` mientras la conexión no haya negociado ambas; esta protección también aplica si alguien intenta saltarse la interfaz y llamar directamente a la API.

GymFort rechaza eventos históricos en modo online. La ventana predeterminada es de dos minutos (`GYMOS_CONNECTOR_ONLINE_MAX_EVENT_AGE` en el backend). Esto evita que el primer sondeo, una cola atrasada o un reinicio abra una puerta por un reconocimiento ocurrido tiempo atrás. Una apertura ya confirmada tampoco puede degradarse posteriormente mediante un acuse contradictorio.

## Seguridad

El perfil no contiene App Secret. El secreto continúa en DPAPI. TLS sigue requiriendo una cadena confiable o el certificado explícito configurado; no existe opción para desactivar la validación.
