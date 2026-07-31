# Transición del kiosco a modo web derivado de recepción

Estado: DECISIÓN FUNCIONAL VIGENTE  
Rama de referencia: `feature/automations-self-service-kiosk`

## 1. Decisión

El kiosco de GymOS no requiere que cada computadora o tablet se registre como
una terminal física independiente. El producto es web y el recorrido de
autoservicio se inicia desde una sesión autenticada de recepción con una
organización y una sucursal activas.

La secuencia objetivo es:

```text
cuenta de recepción autenticada
  -> organización autorizada
  -> sucursal activa
  -> iniciar modo kiosco
  -> sesión efímera de autoservicio
  -> operaciones del socio con token restringido
```

Una misma sucursal puede abrir el kiosco en varios equipos al mismo tiempo.
Cada navegador obtiene su propia sesión efímera, pero los equipos no necesitan
registros administrativos separados.

## 2. Frontera de seguridad

La sesión de recepción se utiliza únicamente para autorizar el inicio del modo
kiosco y determinar su contexto multi-tenant.

Después de crear la sesión de autoservicio:

- el frontend del kiosco opera con un token efímero específico;
- el token administrativo no se utiliza en los endpoints del socio;
- las operaciones quedan limitadas por las capacidades del kiosco;
- la sesión administrativa no se expone en la pantalla de autoservicio;
- organización y sucursal no se aceptan desde encabezados públicos como fuente
  confiable cuando el flujo parte de recepción.

El backend debe obtener organización y sucursal del `GymOsPrincipal` validado.

## 3. Uso por sucursal

El kiosco se configura a nivel de sucursal, no a nivel de dispositivo físico.
La configuración debe permitir definir:

- disponibilidad del modo autoservicio;
- capacidades habilitadas;
- marca visual heredada de la organización;
- políticas operativas aplicables a esa sucursal.

Una sucursal puede no utilizar el kiosco o puede abrirlo en uno o varios equipos.
El botón de recepción sólo debe iniciar una sesión para la sucursal actualmente
seleccionada y autorizada.

## 4. Compatibilidad con el modelo existente

El diseño inicial introdujo `self_service_terminal`, el código `KIOSK-MAIN` y
la ruta `/kiosk/{terminalCode}`. Esos elementos pueden mantenerse de forma
transitoria para compatibilidad de datos y configuración, pero dejan de ser la
identidad funcional del recorrido iniciado desde recepción.

`KIOSK-MAIN`:

- no debe determinar el tenant;
- no debe seleccionar globalmente una organización;
- no debe ser requerido para abrir el kiosco desde recepción;
- no representa una computadora o tablet;
- no es una credencial;
- no debe condicionar el soporte multisucursal.

La migración V61 corrigió temporalmente una falla que enviaba usuarios al tenant
demo y asignó un registro a la primera sucursal activa. Esa migración sólo
mantiene compatibilidad con el modelo previo; no expresa el comportamiento
objetivo.

Durante la transición, la tabla existente podrá actuar como configuración
interna de autoservicio por sucursal. Posteriormente podrá renombrarse,
simplificarse o sustituirse por una entidad explícita de configuración de
kiosco de sucursal.

## 5. Contrato objetivo

La creación de una sesión iniciada desde recepción debe exponerse mediante un
endpoint autenticado equivalente a:

```http
POST /api/v1/self-service/reception/sessions
Authorization: Bearer <token de recepción>
```

El backend debe:

1. exigir usuario autenticado;
2. exigir organización y sucursal activas;
3. validar que el usuario tenga acceso operativo a esa sucursal;
4. resolver la configuración de autoservicio de la sucursal;
5. crear un token aleatorio y efímero de kiosco;
6. devolver únicamente marca, expiración y capacidades necesarias;
7. auditar el inicio con usuario iniciador, organización y sucursal.

El resto de endpoints del socio conserva el token propio de autoservicio.

## 6. Identidad física opcional futura

La identidad individual de dispositivos sólo se incorporará si existe una
necesidad de producto concreta, por ejemplo:

- revocar una tablet específica;
- aplicar capacidades distintas por equipo;
- operar kioscos sin iniciar sesión en recepción;
- conocer último contacto y versión instalada;
- recopilar telemetría por dispositivo;
- impedir que una URL se utilice fuera de una instalación autorizada.

En ese escenario podrá añadirse un modelo opcional con identificador público,
credencial de activación, estado y telemetría. No es un requisito del kiosco web
actual.

## 7. Criterios de cumplimiento

La transición queda completa cuando:

- recepción inicia el kiosco sin depender de `KIOSK-MAIN`;
- organización y sucursal proceden del principal autenticado;
- abrirlo desde dos o más equipos genera sesiones independientes válidas;
- ninguna búsqueda puede degradarse a otra organización;
- una sucursal sin configuración recibe un error explícito;
- el socio nunca opera con el token administrativo;
- existen pruebas positivas y negativas para tenant, sucursal y ausencia de
  contexto;
- la ruta anterior por código queda eliminada o señalada únicamente como
  compatibilidad temporal.
