# Descubrimiento del sitio piloto — HikCentral / Hikvision (GymFort)

**Corte documental:** 2026-11-09. **Estado:** guía de levantamiento; **no** certificación de equipo.  
**Épica:** #24; **issues:** #38 recuperación, #39 validación física, #40 servicio Windows, #41 consistencia documental.  
**Rama experimental:** `feature/local-access-connector` SHA `8955cdb7682376bd66fbc733610e84d24ebfe9c9`; no pertenece a `main` al corte.  
**Principio:** primero lectura/diagnóstico, después sincronización limitada con persona de prueba, y finalmente pruebas físicas supervisadas.

## 0. Consentimiento y seguridad operativa

Solicitar autorización del propietario y del responsable de TI/accesos para revisar la PC y el sistema. Registrar quién autoriza y una ventana de pruebas. No cambiar parámetros, asignaciones, políticas, cableado, apertura remota ni relés durante levantamiento. No detener HikCentral. Nunca interrumpir el mecanismo de salida de emergencia; coordinar cualquier prueba de paso físico con personal presente.

No guardar ni subir a GitHub claves, App Secret, contraseñas, token, certificados con llave privada, biometría, imágenes faciales, pantallas con datos personales, listas de socios ni direcciones IP públicas. Los datos de red internos, credenciales y certificados públicos se entregan sólo por canal privado/controlado al operador técnico. Capturas para issues deben censurarse. GymFort sólo requiere IDs opacos para enlazar personas.

## 1. Inventario que debe entregar el gimnasio

| Campo | Cómo obtenerlo sin afectar el sistema | Registro esperado |
| --- | --- | --- |
| Sucursal y puntos de paso | Recorrido físico acompañado de recepción | Centro/Tecnológica; entrada/salida, torniquete y puertas asociadas |
| Marca/modelo torniquete | Etiqueta exterior, factura o manual; foto sin rostros | Fabricante y modelo exactos; sentido de apertura |
| Marca/modelo terminal facial | Etiqueta del lector (no confundir con el torniquete), menú Información/About o inventario | Modelo, versión firmware, número de lectores |
| Controlador/placa de acceso | Revisar con técnico el gabinete **sin abrirlo ni manipular cableado por cuenta propia** | Fabricante, modelo y cómo se comunica con terminal/torniquete |
| HikCentral Professional (HCP) | En la PC de administración, abrir cliente/consola → menú Help/About / Información de versión (nombre puede variar) | Edición, versión y build completos; capturar pantalla sin usuarios |
| Licencias/servicios | Administrador HCP → License/Activation/System o pedirlo al proveedor (menú depende de versión) | Licencias de Access Control y posibilidad de OpenAPI/integraciones; vigencia |
| Topología LAN | Personal TI: identificar servidor HCP y PC Windows candidata; comprobar que la PC llegue a HCP sin cambios de firewall | HCP on-premise o remoto; PC de instalación; acceso LAN; no publicar IP/host en Git |
| Política de continuidad | Preguntar quién abre hoy cuando falla Internet, HCP o la PC; observar procedimiento documentado | Responsable, mecanismo de salida segura y procedimiento ante falla |
| Identificación actual | Preguntar si usan rostro, tarjeta, PIN u otro; observar sin capturar biometría | Tipo de lector y enrolamiento; si usan HikCentral para altas y bajas |
| Accesos actuales | Administrador HCP → Access Control → Access Levels / Doors / Persons (menús varían) | Nombres e IDs opacos de niveles, puertas y grupo de prueba; propiedad de permisos |
| Restricciones y horarios | Administrador HCP → calendarios/horarios de acceso y políticas | Horarios, días, anti-passback, excepciones y restricciones vigentes |

La UI depende de la versión; si una ruta no aparece, detenerse y pedir al integrador/manufacturer el manual específico, no adivinar.

## 2. OpenAPI, permisos y compatibilidad (con el integrador autorizado)

1. Confirmar por pantalla o documentación oficial **versión exacta de HCP** y edición.
2. Solicitar **paquete OpenAPI/SDK oficial que corresponda exactamente a esa versión** (portal Hikvision TPP o distribuidor autorizado). Registrar título y versión del paquete, no compartir materiales sujetos a licencia en repositorio abierto.
3. Verificar si la instalación tiene OpenAPI habilitada y si la licencia permite integración de terceros; anotar error/costo de licencia adicional si existe.
4. Solicitar creación de una **cuenta/app de integración de mínimo privilegio** con `App Key` y `App Secret`, sólo cuando el administrador autorice la prueba. **No enviarlos por chat, correo sin cifrar o GitHub**: el técnico los ingresará directamente en el almacén seguro DPAPI del Connector local.
5. Pedir URL **HTTPS interna de OpenAPI**, puerto, hostname/certificado X.509 público si usa CA privada y prueba de DNS/puerto desde PC del gimnasio. Registrar hostname/URL sólo en configuración local segura.
6. Pedir evidencia oficial de capacidades y contratos para: consultar versión; listar personas, puertas y niveles; asignar/quitar nivel; consultar/suscribir eventos; identificación de evento único; comando de apertura remota y su respuesta; paginación, rate limits y zonas horarias.
7. Confirmar particularmente **si el evento se genera antes o después de apertura local**, y si es técnicamente posible impedir apertura autónoma antes de decidir con GymFort. Sin esta prueba, **NO** habilitar `GYMFORT_ONLINE`.
8. Obtener IDs de una persona **de prueba y consentida**, un dispositivo y un Access Level exclusivo del piloto. No reutilizar o revocar derechos de socios reales durante la primera prueba.

## 3. Recolección y verificación — formato de acta

Registrar en documento privado de levantamiento: fecha/hora; sucursal; responsable que autorizó; técnico presente; HCP edición/version/build; licencia/OpenAPI sí/no/desconocido; paquete oficial y versión; modelo/firmware de lector; modelo controlador; modelo torniquete; topología de la red (sin credenciales ni host público en Git); IDs opacos del **entorno de prueba**; esquema de permisos/horarios; método de salida de emergencia; política de operación sin Internet; proveedor de soporte; documentación entregada; pendientes y evidencias desidentificadas.

Clasificar cada dato como **CONFIRMADO POR CAPTURA/MANUAL**, **REPORTADO**, **DESCONOCIDO** o **NO DISPONIBLE**. Separar edición/versión instalada de ejemplos de OpenAPI publicados en Internet.

## 4. Camino de prueba segura (issue #39)

**Fase A — Solo diagnóstico (sin mutar hardware).** Con #38 integrado o ejecutado en entorno aislado, validar certificado/TLS, autenticación OpenAPI de mínimo privilegio, consulta de versión, lectura de personas/puertas/niveles, diagnósticos `doctor`, heartbeat y health. Si falla, detener.

**Fase B — Permisos gestionados por proveedor (`PROVIDER_MANAGED`).** Con cuenta/persona y punto exclusivos de prueba, enlazar ID exacto y sincronizar solo un Access Level **propiedad de GymFort**. Comprobar alta, renovación, revocación, cambios de plan, eventos e idempotencia. Nunca retirar accesos ajenos al alcance del piloto. Verificar que HCP decide localmente y continúa con permisos conocidos durante desconexión según la política autorizada.

**Fase C — Autorización por GymFort (`GYMFORT_ONLINE`, opcional y bloqueada inicialmente).** Requiere evidencia del evento **preapertura**, capacidad `EVENT_POLLING`/equivalente con latencia aceptable, `REMOTE_OPEN`, aislamiento de derechos locales y ensayo sin relé activo. Probar denegación, balance de visitas, timeout/resultado incierto, reconexión, evento histórico y no repetición de apertura antes de conectar relé. Si no se cumple, mantener modo proveedor y **no prometer consumo físico de paquetes**.

**Fase D — Aceptación supervisada.** Seguir `connector/docs/hikcentral-site-acceptance-matrix.md` y `connector/docs/TEST-MATRIX.md` de la rama experimental, adaptándolas a la topología real; documentar cada PASS/FAIL/SKIP con evidencia y firma del responsable. El código que pasa tests con SIMULATOR **no** equivale a prueba en sitio.

## 5. Puertas de salida y bloqueo

**Se puede avanzar con #38 y simulador sin datos del gimnasio.** El siguiente bloqueo para SITE es conocer versión/licencia OpenAPI, hardware y autorización de pruebas. No exigir App Secret en Git ni para análisis de PR. No habilitar el modo en línea si no hay evento previo y actuador remoto probado. No instalar servicios persistentes/firma en producción sin completar #40. En ningún escenario una falla cloud por sí sola debe ordenar apertura.

## 6. Matriz de fuentes de verdad

- `main`: contrato Cloud, APIs, modelo físico, reglas y consola de administración; **no** ejecutable `connector/` integrado al corte.
- `feature/local-access-connector`: código de Connector, SIMULATOR, driver HikCentral y documentación técnica de piloto, **sin aprobación SITE**.
- `Documentación/adr/ADR-003-acceso-fisico-hikcentral-fail-closed.md`: decisión **PROPOSED**, no aprobación de modo productivo.
- Issues #38–#41: ejecución pendiente y criterios de aceptación; prevalece código verificado con SHA y evidencia sobre afirmaciones documentales históricas.
