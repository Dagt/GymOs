# Prueba local rápida en el gimnasio

Esta es la ruta recomendada cuando GymFort corre en una laptop Windows dentro
de la red del gimnasio. El Connector se instala en esa misma laptop y se
comunica con GymFort por `localhost`; no hace falta publicar GymFort en Internet
para la primera prueba.

## Lo único que debes conseguir antes

Del administrador de HikCentral:

1. URL HTTPS de **OpenAPI** accesible desde la laptop. No uses `127.0.0.1` salvo
   que HikCentral también esté instalado en esa misma laptop.
2. App Key.
3. App Secret.
4. Para probar sincronización real, IDs de los Access Levels de prueba.

No necesitas todavía el perfil avanzado de eventos/apertura remota. La primera
instalación usa modo `BASIC`: valida conexión y sincronización de membresías sin
cambiar el torniquete a autorización GymFort en línea.

## Antes de abrir el asistente

1. Inicia el backend GymFort en `http://127.0.0.1:8080`.
2. Inicia la interfaz GymFort en `http://127.0.0.1:3000`.
3. Extrae el ZIP del Connector; no lo ejecutes dentro del ZIP.
4. Haz doble clic en `INICIAR-PRUEBA-LOCAL.cmd`.

## Qué hace el asistente

1. Detecta backend e interfaz local.
2. Abre GymFort en `Accesos > Configuración física`.
3. Te indica crear una conexión HikCentral con ID `acceso-centro-01` y generar
   el código de activación.
4. Te pide URL y App Key de HikCentral.
5. Comprueba que el host/puerto respondan.
6. Puede capturar el certificado público presentado por HikCentral, muestra
   sujeto, emisor, vigencia y SHA-256, y sólo lo acepta después de tu
   confirmación. Esto no desactiva TLS.
7. Solicita App Secret y código de activación sin mostrarlos ni escribirlos en
   texto plano.
8. Instala el Connector, ejecuta `doctor`, registra la tarea y muestra el
   estado final.

Elige la opción **BÁSICA** en la primera ejecución. Deja vacíos los Access
Levels si todavía no los tienes; el diagnóstico de conectividad puede hacerse
sin mutar permisos. Para comprobar altas/bajas reales, vuelve a ejecutar el
asistente cuando tengas sus IDs.

## Después de instalar

En GymFort confirma que la conexión tenga heartbeat reciente. Luego:

1. vincula el torniquete descubierto con el punto `Acceso General Centro`;
2. deja `El proveedor decide; GymFort recibe el registro`;
3. crea o localiza una persona de prueba en HikCentral y captura su rostro;
4. vincula el candidato con el socio exacto en GymFort;
5. fuerza una reconciliación y revisa que HikCentral reciba sólo el Access
   Level esperado.

No habilites `GymFort valida en línea y el Connector abre` durante esta primera
etapa. Ese modo requiere completar el perfil OpenAPI y probar eventos/apertura
remota con recepción presente.

## Diagnóstico

Desde la carpeta extraída:

```powershell
.\scripts\connector-control.ps1 -Action Status
.\scripts\connector-control.ps1 -Action Doctor
.\scripts\connector-control.ps1 -Action Logs
```

Si el asistente falla, no deja una instalación nueva a medias; restaura la
versión/configuración anterior cuando exista.

## Qué no puede automatizar

- crear una App Key/App Secret dentro de HikCentral sin credenciales y permisos
  administrativos del producto;
- adivinar endpoints OpenAPI que cambian entre paquetes instalados;
- confirmar por sí solo que el certificado presentado pertenece realmente al
  servidor del gimnasio;
- enrolar rostros sin la intervención y consentimiento del operador.

El asistente automatiza el resto y pospone esas tareas hasta que sean necesarias
para la prueba física avanzada.
