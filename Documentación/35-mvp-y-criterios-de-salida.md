# 35. MVP piloto de GymFort y criterios de salida

**Corte documental:** 29 de octubre de 2026. **Base de código:** `main` = `136ce72b4613b0a59c2b37d5734a6bb0b0ef3a92`, 29/08/2026. El desarrollo más reciente del Connector está fuera de `main`, en `feature/local-access-connector` = `8955cdb7682376bd66fbc733610e84d24ebfe9c9`, 30/08/2026. **No es una certificación de ejecución actual ni de producción.**

## 1. Qué producto se está cerrando

SaaS multi-tenant y multi-sucursal para gimnasios independientes y cadenas pequeñas. El MVP **no significa volver a programar todo**; significa convertir lo ya desarrollado en recorridos seguros, coherentes y utilizables en un **piloto comercial controlado**. Separa capacidades codificadas, probadas con mocks, verificadas en integración y aceptadas con servicios/equipos reales.

### Alcance de piloto cloud básico

| Capacidad | Presencia en `main` | Gate restante de aceptación |
|---|---|---|
| Plataforma SaaS, alta del gimnasio, primera sucursal, propietario invitado, RBAC | Código y documentación presentes | #28, #29, #45, seguridad y recuperación de invitaciones |
| Catálogo, variantes, códigos de barras y POS | Código presente | #33, #44, ventas concurrentes, impresión/comprobante y volumen |
| Socios y ciclo de vida, inscripción | Código presente | #30, #45, privacidad, deduplicación y reversibilidad |
| Planes, membresías y beneficios multi-sucursal | Código presente | #29, #33, #37 y pruebas reales de contratos |
| Paquetes de visitas de saldo compartido | V98–V103 en `main` | #35 y validación de pagos/consumos reales |
| Pagos manuales, Stripe Connect/Checkout, Mercado Pago Point | Contratos/implementación presentes | #33 y #34; no operar live antes de aprobar sandbox y reconciliación |
| Kiosco iniciado por Recepción | Código presente | #34, #44, #50, sesión temporal, recuperación y hardware de pantalla |
| Agenda, inventario, coaches, wellness | Código presente | #36, #44, #54, #48 y matriz por roles |
| Automatizaciones y correo ZeptoMail | Código presente; entregabilidad real histórica 22/08 | Confirmación en el entorno piloto, métricas/dead letters, rebotes #56 posterior |
| Reportes y auditoría | Código presente | #31 y #53 para KPI, filtros, inmutabilidad y permisos |
| Operación, backup, despliegue, alertas | Runbooks y CI presentes | #42, #43, #44, #46 y #59 |

**Piloto con control de acceso físico**: añade obligatoriamente #38, #39, #40 y matriz HikCentral real; no se debe prometer torniquete conectado mientras esté pendiente. Un piloto **sin acceso físico** puede habilitarse con control físico desactivado y contrato comercial que lo declare explícitamente. El SaaS no depende del Connector para probar recepción/catálogo; no ofrecer capacidades no probadas.

## 2. Fuera del MVP cloud inicial

Portal móvil remoto completo del socio, otros fabricantes de torniquetes sin caso real, biometría procesada por GymFort, WhatsApp/push, campañas, marketplace, facturación CFDI SaaS avanzada y cobro SaaS recurrente automático. El multibranding por sucursal (#51) es requisito del **piloto con dos marcas** pero no bloquea un gimnasio de una sola identidad, sujeto a acuerdo. La mejora visual integral (#47–50) se divide en usabilidad bloqueante y pulido incremental; no bloquear seguridad/pagos por animaciones.

## 3. Estados verificables

- **CODIFICADO:** se observa implementación, sin afirmar que funcione en entorno real.
- **TEST LOCAL HISTÓRICO:** el repositorio registra pruebas en una fecha/commit anterior; no autoriza afirmar CI del HEAD.
- **CI HEAD PASS:** jobs exigidos del mismo SHA aprueban, evidencias accesibles.
- **INTEGRACIÓN VALIDADA:** Stripe/Mercado Pago/SMTP/DB real y datos sintéticos, operaciones con trazabilidad.
- **PILOTO ACEPTADO:** recorridos completos, operador, responsable, evidencias, riesgos y aceptación de sitio.
- **PRODUCCIÓN APROBADA:** añade restauración, seguridad, observabilidad, capacidad y respuesta a incidentes.

En la auditoría del 29/10/2026 no se ha verificado ningún gate CI completo del HEAD; los runs reportaron fallo y sus logs no estuvieron disponibles. No reemplazar estas categorías con «terminado» genérico.

## 4. Puertas de salida obligatorias (go/no-go)

1. **Seguridad P0:** #28 y #29 con amenazas/controles revisados, test IDOR multi-tenant, protección refresh y no PII cross-tenant.
2. **Comercio P0:** #33 y #34 con Stripe test o proveedor relevante: webhooks, duplicados, devoluciones, mandatos, importes, moneda, comisiones y no doble efecto.
3. **Datos P0:** #42 base limpia, upgrade desde versiones históricas, backup/restauración ensayada, datos de prueba preservados.
4. **Operación P0:** #43 y #46 en staging seguro, métricas, alertas, secreto gestionado, acceso de soporte con caducidad y respuesta a incidentes.
5. **Pruebas:** #44 y #59 CI verde del mismo SHA, Playwright representativo y evidencia de límites (mocks vs real).
6. **UX mínima:** completar navegación, errores comprensibles, acciones frecuentes, registros sin pérdidas, permisos y contraste en 390/768/1440 px más kiosco.
7. **Físico condicional:** #38–40, toda fila de aceptación de sitio aplicable; denegación segura en cortes de red y no abrir por default.
8. **Validación comercial:** un operador real recorre altas, cambios, cobros, socio, reportes y recuperación, deja observaciones firmadas.
9. **Sin P0 abiertos críticos para alcance habilitado.** Un P0 físico puede quedar aparcado **sólo** si el hardware está fuera del piloto y se comunica esa exclusión.

## 5. Demostración piloto mínima

Administrador SaaS crea gimnasio/sucursal y envía invitación; dueño entra, configura sucursal, catálogo, plan/beneficio; Recepción registra socio, factura inscripción como partida, vende un producto, cobra, renueva; socio usa kiosco, ve sus derechos, realiza Checkout **de prueba**; gerente consulta métricas, inventario/reservas; coach asigna rutina y entrega PDF; se comprueba la separación entre dos tenants y dos sedes. Repetir rutas de error, red caída y recuperación. Para acceso físico usar la matriz de sitio sólo cuando exista instalación real.

## 6. Métricas de aceptación

Definir y capturar tiempos objetivo antes de firmar: registro de socio, venta con código de barras, renovación, apertura de kiosco, disponibilidad, tasa de pago conciliado, doble cobro (cero), balance negativo de visitas (cero), fugas cross-tenant (cero), éxito de backup-restauración, errores por rol y accesibilidad. No inventar mediciones ni objetivos productivos sin acuerdo.

## 7. Dependencias y orden

**Ola 0:** cerrar CI, estado y docs #57/#59; investigar fallos antes de tomar métricas. **Ola 1:** seguridad #28/#29, pagos #33/#34, datos #42, E2E #44. **Ola 2:** piloto/staging #43/#46, onboarding #45, regresión reservas/paquetes #35/#36. **Ola 3:** conector #38–40 si contrato exige torniquete, multibranding #51 para dos marcas. **Ola 4:** DS/UX #47–50 y producto pos-MVP #52–56; UX bloqueante se adelanta por impacto.

Enlazar decisiones a `36-epicas-backlog-codex.md`. El Product Owner aprueba cada gate; Codex propone y desarrolla, no certifica por sí mismo.
## SAL-001

Para abrir caja con cobros integrados, comprueba recuperación ante timeout, conciliación y expiración del proveedor, y stock reservado bajo concurrencia. La simulación del proveedor sólo acredita el comportamiento local.
