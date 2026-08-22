# 9. Integraciones e infraestructura

Corte: 22 de agosto de 2026

## 9.1 Arquitectura de proveedores

Los proveedores externos se aíslan detrás de puertos de aplicación. Los
dominios no almacenan secretos ni confían en datos devueltos por el navegador.

- Pagos: terminal manual, Mercado Pago OAuth/Point y Stripe
  Connect/Checkout/Setup/off-session.
- Correo: `EmailDeliveryPort` con adaptador SMTP, cola durable y configuración
  de remitente por tenant. El perfil Zoho/SMTP, remitentes autorizados, prueba
  inmediata y evidencia de última verificación forman parte del trabajo vigente
  de notificaciones.
- Almacenamiento: MinIO/S3 para logos, medios, comprobantes y documentos.
- Acceso físico: modelo funcional de dispositivos/beneficios ya implementado;
  integración productiva de hardware definida para iniciar con HikCentral
  Professional en el primer gimnasio piloto.
- WhatsApp, push y facturación: puertos previstos, adaptadores productivos fuera
  del corte.

## 9.2 Acceso físico y HikCentral Professional

### Fuente de verdad y ejecutor físico

GymFort será la fuente de verdad para:

```text
Client -> Membership -> MembershipPlan -> Benefit ACCESS
```

El sistema físico existente será responsable de ejecutar localmente esos
derechos. La lógica de membresías/elegibilidad no debe depender de una marca.

El primer gimnasio piloto utiliza terminal/torniquete Hikvision administrado por
**HikCentral Professional** dentro de la red local. El objetivo es integrar con
HikCentral, no saltarlo para hablar directamente con el torniquete mientras
HikCentral siga siendo el administrador del dispositivo.

Arquitectura objetivo del primer piloto:

```text
GymFort Cloud
  -> puerto de integración Access
  -> GymFort Local Connector
  -> HikCentral Professional (LAN)
  -> terminal/controlador Hikvision
  -> torniquete/puerta
```

### Modo de integración

Para HikCentral se prefiere **sincronización de autorizaciones** frente a una
consulta a GymFort Cloud en cada intento de paso.

GymFort calcula el estado deseado de acceso y el conector sincroniza con
HikCentral las personas/referencias y niveles de acceso necesarios. HikCentral y
los terminales continúan tomando la decisión local con sus capacidades propias.
Esto evita convertir la conexión a Internet de GymFort en requisito para abrir
cada puerta.

Ejemplo conceptual:

```text
GymFort benefit GYM_ACCESS
  <-> mapping de integración
  <-> HikCentral Access Level acceso general

GymFort benefit SAUNA_ACCESS
  <-> mapping de integración
  <-> HikCentral Access Level sauna
```

Los identificadores concretos de `Person`, `Door`, `Access Level` o recurso del
proveedor deben quedar en configuración de integración por tenant/sucursal, no
en el dominio de Benefits ni en `EligibilityPolicy`.

### Descubrimiento obligatorio antes de implementar

La instalación real debe validarse antes de fijar contratos del adaptador:

- versión de HikCentral Professional;
- modelo/firmware de terminal y controlador;
- disponibilidad y licencia de OpenAPI/Open Platform o SDK soportado por esa
  instalación;
- endpoints, puertos y autenticación oficiales;
- operaciones soportadas para persona, fotografía/rostro, Access Levels,
  apertura remota y eventos;
- topología actual de red y relación de puertas.

No se documenta como hecho un endpoint/puerto concreto de HikCentral hasta
confirmarlo en el ambiente del gimnasio piloto.

### GymFort Local Connector

El conector será un proceso ligero dentro de la red del gimnasio, idealmente en
la misma PC o LAN donde opera HikCentral.

Responsabilidades:

- establecer comunicación segura con GymFort;
- invocar únicamente interfaces oficiales/soportadas de HikCentral;
- aplicar sincronizaciones idempotentes;
- reportar éxito, error y salud de integración;
- permitir reconciliación del estado deseado contra el estado real;
- posteriormente reenviar/consultar eventos físicos para `access_log`.

No debe:

- contener reglas de membresía;
- calcular beneficios;
- decidir qué plan corresponde al cliente;
- almacenar secretos en frontend;
- exigir exposición pública de HikCentral o de puertos del torniquete.

Se prioriza un patrón de conexión saliente desde la red local cuando la
tecnología elegida lo permita.

### Biometría

GymFort no debe implementar reconocimiento facial. HikCentral/Hikvision conserva
el matching biométrico y la operación local. GymFort debe almacenar como máximo
las referencias opacas indispensables para vincular un `Client` con la persona
del proveedor.

La automatización posterior del alta de persona/fotografía sólo se implementará
si la interfaz oficial disponible en la instalación real lo soporta y después
de revisar seguridad, privacidad y cumplimiento. No se copiarán plantillas
biométricas a GymFort por defecto.

### Fases

1. **Descubrimiento:** inventario técnico y capacidades reales de HikCentral.
2. **Sincronización mínima:** el enrolamiento facial puede seguir siendo manual
   una vez; GymFort concede/revoca Access Levels según membresía y beneficios.
3. **Enrolamiento asistido:** crear/actualizar persona/fotografía desde GymFort
   si el API/licencia lo soporta.
4. **Eventos:** conciliar eventos de HikCentral con la bitácora de GymFort.
5. **Resiliencia:** heartbeat, cola, reintentos y reconciliación tras desconexión.
6. **Segundo proveedor:** abstraer otra marca sólo cuando exista un caso real.

## 9.3 Stripe y Mercado Pago

- Las credenciales permanecen en secretos del backend; nunca en
  `NEXT_PUBLIC_*`, imágenes o Git.
- Stripe usa cuenta Express conectada por gimnasio, Account Links y captura
  alojada. GymFort conserva sólo identificadores opacos y máscara.
- Mercado Pago usa OAuth/PKCE, selección de Point y órdenes idempotentes.
- Todo pago se persiste pendiente antes de invocar al proveedor.
- Webhook firmado más consulta autoritativa concilian monto, moneda, tenant,
  sucursal, venta, pago y método antes de publicar efectos.
- Los workers recuperan operaciones no terminales y preservan idempotencia.

## 9.4 Correo

El backend recibe proveedor visible, host, puerto, usuario, contraseña,
remitentes autorizados, TLS/SSL y timeouts SMTP por entorno. Cada gimnasio
configura nombre, `From`, `Reply-To` y habilitación, no las credenciales. Los
remitentes que no pertenezcan al dominio central de GymFort deben estar
verificados y asociados al proveedor antes de autorizarse. La cola usa lease,
backoff, dead letter, preferencias y destinatarios enmascarados/HMAC.

La prueba administrativa intenta la entrega inmediatamente, clasifica
autenticación/configuración como fallo permanente y conserva la última entrega
de prueba `SENT`. SPF/DKIM/DMARC, reputación y credenciales reales siguen siendo
responsabilidades del ambiente.

## 9.5 Despliegue local

Docker Compose levanta frontend, backend, PostgreSQL 18, Redis 8 y MinIO. El
perfil opcional `stripe` usa `stripe/stripe-cli:v1.50.3` para reenviar eventos
Connect. El primer arranque entrega un `whsec_...` que debe inyectarse al backend
antes de validar firmas.

El futuro `GymFort Local Connector` de acceso físico es un componente distinto
del stack local de desarrollo. Su empaquetado, actualización y observabilidad se
definirán durante el piloto HikCentral; no debe requerir instalar el backend
completo de GymFort dentro de cada gimnasio.

## 9.6 CI/CD vigente

El pipeline ejecuta pruebas backend, pruebas/lint/typecheck/build frontend,
validación y build de Compose. También descarga la imagen fijada de Stripe CLI
y ejecuta su comando de versión. Permanecen pendientes despliegue automático a
staging, escaneo formal de imágenes, simulacro restore y `stripeTest` con
secretos sandbox protegidos.

Cuando inicie el conector HikCentral se debe añadir una suite contractual con un
fake/simulador del proveedor; las pruebas de CI no deben depender del torniquete
físico del gimnasio.

## 9.7 Observabilidad y resiliencia

- Correlation ID y errores sanitizados.
- Métricas Prometheus de pagos, recurrencia, webhooks, automatizaciones y correo
  con etiquetas acotadas por tenant/sucursal.
- Idempotencia, timeouts, backoff, leasing recuperable y dead letters.
- Alertas para backlog, lease vencido, fallos de proveedor y correo definitivo.
- Auditoría separada de logs técnicos; nunca PAN, CVV, secretos o payloads
  sensibles.

Para acceso físico deberán añadirse métricas acotadas de sincronización,
conector disponible/no disponible, último sync exitoso, backlog y errores del
proveedor, sin incluir datos biométricos.

## 9.8 Kiosco

El kiosco es un perfil lógico por sucursal, no una computadora registrada.
Recepción crea el handoff de navegador; la pestaña elimina tokens
administrativos y usa una sesión temporal con `no-store`, rate limit,
inactividad, verificación del socio y capacidades cerradas por defecto.

Las capacidades de compra/renovación/cambio de plan sólo pueden habilitarse
cuando Stripe self checkout está listo. La domiciliación del kiosco exige además
Stripe recurring listo; backend y frontend aplican la misma dependencia.

## 9.9 Operación

Runbooks vigentes: `docs/operations/` para despliegue, rollback, backup, restore,
incidentes, secretos, correo, kiosco, Stripe y mantenimiento. RPO piloto: 24
horas. RTO piloto: 4 horas. Ambos deben validarse mediante simulacro antes de
producción.

Antes de poner el control físico en producción se añadirá un runbook específico
para pérdida de conectividad GymFort/HikCentral, reconciliación posterior,
revocaciones pendientes, apertura manual y recuperación del conector local.
