# Matriz integral de pruebas — Centro y Tecnológica

Topología de aceptación:

- **Centro**: Acceso General, Sauna, Tina de Crioterapia y Lockers Premium; HikCentral y torniquete facial.
- **Tecnológica**: únicamente Acceso General; sin áreas premium.
- **Modos físicos**: `PROVIDER_MANAGED` para derechos estáticos y `GYMFORT_ONLINE` cuando GymFort debe decidir/consumir antes de abrir.
- **Regla inviolable**: ningún paquete, plan o vínculo crea Sauna/Crioterapia/Locker en Tecnológica.

Estados: `AUTO` se cubre sin hardware; `SITE` exige HikCentral/torniquete reales; `BOTH` requiere ambas evidencias.

| ID | Preparación/acción | Resultado obligatorio | Evidencia |
|---|---|---|---|
| CFG-01 | Connector Centro activado con secreto válido | Heartbeat sano y tenant/sucursal Centro | AUTO |
| CFG-02 | Mismo secreto usado con otra connector key | 401/denegado, sin datos filtrados | AUTO |
| CFG-03 | Dispositivo externo descubierto pero no vinculado | Visible como candidato; no puede autorizar ni abrir | AUTO |
| CFG-04 | Vincular torniquete Centro a punto General | Evento resuelve exactamente ese punto | BOTH |
| CFG-05 | Referencia de puerta ya ligada a otro punto | Rechazo por unicidad | AUTO |
| CFG-06 | Intentar vínculo cruzado a Tecnológica | Rechazo por FK/tenant-branch | AUTO |
| CFG-07 | Punto `PROVIDER_MANAGED` | HikCentral decide; GymFort sólo audita; no consume paquetes | AUTO |
| CFG-08 | Punto `GYMFORT_ONLINE` sin EVENT_POLLING/REMOTE_OPEN reportados | Backend y UI impiden habilitarlo; diagnóstico incompleto | AUTO |
| CFG-09 | Heartbeat posterior reporta EVENT_POLLING y REMOTE_OPEN | UI muestra capacidades y backend permite configurar `GYMFORT_ONLINE` | AUTO |
| CFG-10 | Mismo beneficio ligado a un punto proveedor y otro en línea | Configuración rechazada; no se permite una vía local que evada GymFort | AUTO |
| CFG-11 | Cambiar un beneficio de proveedor a sólo en línea | Reconciliación retira su Access Level autónomo de socios vinculados | BOTH |
| CFG-12 | HikCentral reconoce a socio de punto en línea | El rostro identifica, pero ningún Access Level local abre esa puerta; sólo `remoteOpen` de una decisión GymFort concedida actúa | SITE |
| ID-01 | Persona HCP con email exacto único | Candidato `SUGGESTED`, nunca vínculo automático | AUTO |
| ID-02 | Dos socios comparten dato de contacto | Candidato `CONFLICT` | AUTO |
| ID-03 | Sólo coincide el nombre | `UNMATCHED`; no fuzzy matching | AUTO |
| ID-04 | Dueño confirma candidato | Se crea vínculo opaco y se reconcilian derechos | AUTO |
| ID-05 | Rostro capturado en HCP | Plantilla nunca sale de HikCentral | SITE |
| ID-06 | Socio creado/vendido en Tecnológica con cobertura Centro | Puede ligarse en Centro dentro de la misma organización | BOTH |
| MEM-01 | Plan Centro General activo | General asignado/permitido en Centro | BOTH |
| MEM-02 | Plan Tecnológica-only creado en Tecnológica | General sólo Tecnológica; no acceso Centro | AUTO |
| MEM-03 | Plan multisucursal creado en Tecnológica con General Centro+Tecnológica | Socio visible y autorizado en Centro | BOTH |
| MEM-04 | Plan multisucursal incluye Sauna LOCAL Centro | Sauna sólo Centro | BOTH |
| MEM-05 | Renovación sin cambio de derechos | Sin duplicar persona ni Access Levels | BOTH |
| MEM-06 | Cambio pierde Sauna | Retira sólo Sauna administrado; conserva derechos ajenos | BOTH |
| MEM-07 | Membresía vencida/inactiva | Revoca derechos GymFort administrados | BOTH |
| PKG-01 | 5 sesiones Sauna, Sauna `CONSUMES_VISIT`, Centro | Cada autorización concede y descuenta exactamente 1 | BOTH |
| PKG-02 | Mismo evento PKG-01 repetido | Una decisión, una visita, una apertura lógica | AUTO |
| PKG-03 | 10 visitas: General consume, Sauna incluida; General Centro | Saldo 10→9; abre General | BOTH |
| PKG-04 | PKG-03: Sauna posterior, mismo día/sucursal/entitlement | Concede; saldo permanece 9 | BOTH |
| PKG-05 | PKG-03: Sauna antes de General | Deniega; no abre; saldo intacto | AUTO |
| PKG-06 | Consumo base en Tecnológica, Sauna en Centro | Deniega Sauna por distinta sucursal | AUTO |
| PKG-07 | Consumo base ayer, Sauna hoy | Deniega por distinta business date | AUTO |
| PKG-08 | Consumo base de otro entitlement | Deniega | AUTO |
| PKG-09 | Paquete Centro+Tecnológica: General ambas, Sauna Centro | General funciona ambas; Sauna sólo Centro | BOTH |
| PKG-10 | Paquete creado en Tecnológica sólo para Tecnológica intentando Sauna Centro | Configuración inválida/no ofrece Sauna efectivo | AUTO |
| PKG-11 | Paquete creado en Tecnológica para Tecnológica+Centro con Sauna Centro | Válido; Sauna sólo Centro | AUTO |
| PKG-12 | Membresía concede General y paquete tiene General consume + Sauna incluida | General usa membresía, paquete no consume; Sauna incluida se deniega sin consumo base del mismo entitlement | AUTO |
| PKG-13 | Membresía concede también Sauna | Membresía gana; paquete intacto | AUTO |
| PKG-14 | Última visita, dos eventos concurrentes | Uno consume/concede; otro no consume; nunca saldo negativo | AUTO PostgreSQL |
| EVT-01 | HCP reporta evento ya decidido en modo proveedor | Un `access_log`, sin decisión comercial retroactiva | AUTO |
| EVT-02 | Evento online con persona no ligada | Deniega y no abre | AUTO |
| EVT-03 | Evento online con dispositivo no ligado | Rechazo controlado; no abre | AUTO |
| EVT-04 | Cloud cae antes de decidir | No abre; cursor/evento queda recuperable | AUTO |
| EVT-05 | Cloud cae después de abrir y antes del acuse | Ledger evita repetir apertura; resultado queda `UNCERTAIN` | AUTO |
| EVT-06 | Reinicio antes de apertura | Reintenta autorización idempotente y puede abrir una vez | AUTO |
| EVT-07 | Reinicio durante apertura | No repite; `UNCERTAIN` | AUTO |
| EVT-08 | HCP rechaza apertura antes de ejecutar | `FAILED`, auditable; política de retry controlada | BOTH |
| EVT-09 | HCP timeout con resultado desconocido | `UNCERTAIN`; sin retry automático de apertura | BOTH |
| EVT-10 | Primer arranque devuelve eventos históricos o una autorización se reintenta tarde | Evento online anterior a la ventana segura (2 min por defecto) se deniega/expira; nunca abre ni consume | AUTO |
| EVT-11 | Acuse `SUCCEEDED` duplicado | Idempotente; conserva éxito | AUTO PostgreSQL |
| EVT-12 | Acuse contradictorio después de `SUCCEEDED` | Rechazado; un éxito no puede degradarse a fallo | AUTO PostgreSQL |
| SEC-01 | Inspección de config/log/SQLite | Sin App Secret, connector secret ni biometría en claro | BOTH |
| SEC-02 | Certificado HCP no confiable | Conexión rechazada; nunca modo insecure | AUTO |
| SEC-03 | Evento intenta person/device de otra organización | No resuelve ni filtra datos | AUTO PostgreSQL |
| SEC-04 | Perfil contiene path fuera de `/artemis/` o `..` | Arranque/doctor lo rechaza | AUTO |
| OPS-01 | Backup y restauración del runtime | Conserva secretos DPAPI/ledger bajo la misma cuenta | SITE |
| OPS-02 | Upgrade falla | Rollback restaura versión/estado previo | AUTO paquete |
| OPS-03 | Equipo reinicia | Tarea piloto vuelve a iniciar bajo cuenta instalada | SITE |
| OPS-04 | ZIP alterado | SHA-256 falla | AUTO |

## Orden de simulación recomendado

1. Ejecutar migraciones y suites PostgreSQL.
2. Ejecutar Connector con `SIMULATOR` y cubrir CFG, ID, PKG y EVT automáticos.
3. Instalar paquete Windows en un equipo aislado; validar doctor, reinicio, backup y rollback.
4. Generar el perfil usando exclusivamente la documentación OpenAPI de la instalación.
5. Probar `PROVIDER_MANAGED` con un socio de prueba.
6. Probar `GYMFORT_ONLINE` primero sin relay conectado, después con apertura supervisada.
7. Ejecutar todos los casos `BOTH` y `SITE`; conservar IDs de evento, `access_log`, autorización y operación HCP.

La aprobación productiva exige cero casos fallidos, cero aperturas duplicadas, cero saldos negativos y firma del responsable del gimnasio sobre los casos físicos.
