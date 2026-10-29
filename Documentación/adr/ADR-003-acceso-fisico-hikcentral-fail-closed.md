# ADR-003 — Modos de acceso físico, presencia de Cloud y fail-closed

**Estado:** PROPOSED (29/10/2026). **Issues:** #38 #39 #40. **OQ:** OQ-001, 004, 010.

## Contexto
`main` tiene control plane y API Connector, sin ejecutable integrado. La rama `feature/local-access-connector` añade ejecución Windows, drivers, SIMULATOR y autorización en línea (V104). HikCentral y el torniquete no han pasado certificación SITE documentada. Hay dos semánticas distintas:

1. `PROVIDER_MANAGED`: permisos estáticos administrados desde Cloud y aplicados por HikCentral; un corte de Internet no debe desactivar los derechos locales conocidos.
2. `GYMFORT_ONLINE`: evento físico solicita decisión de GymFort para consumos de visitas, solo abre si autorización positiva y actúa una vez. No es seguro conceder nivel estático a visitas de saldo limitado.

## Opciones
A. Lanzar piloto solo con permisos estáticos, sin descuento físico de visitas.
B. Lanzar mixto por punto, con contrato de capacidades y fail-closed online; pruebas de latencia/fallo y presencia operativa.
C. Diferir toda apertura física del primer MVP y seguir control manual temporal.

## Obligaciones
No alterar derechos no administrados del proveedor. No guardar biometría. Vincular identidad por ID opaco validado, nunca fuzzy match. TLS con verificación de hostname/certificado. Claves/secretos DPAPI; no enlazar logs a rostros. Caída o respuesta incierta no abre por default, no reintenta a ciegas una apertura incierta.

## Certificación
Inspeccionar versión/licencia/OpenAPI real de HCP. Aprobar matriz `connector/docs/hikcentral-site-acceptance-matrix.md` (rama experimental) + pruebas de relé con operador y controles de privacidad. Empaquetar firmando en producción. **No fusionar rama por el mero hecho de compilar ni afirmar hardware operativo.**

## Decisión
Pendiente de evidencia del sitio y contrato comercial sobre modo físico del piloto.