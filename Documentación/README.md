# GymFort — documentación maestra

Versión documental: 0.9
Estado: FUENTE CANÓNICA
Corte: 22 de agosto de 2026

GymFort es la identidad comercial vigente. `GymOS`/`gymos` permanece como nombre técnico histórico en paquetes Java, variables, base de datos y algunos artefactos; una migración de marca no debe romper contratos ni datos existentes.

## Fuentes canónicas

1. `01-constitution.md`: principios no negociables.
2. `02-prd.md`: requisitos de producto y actores.
3. `03-sds.md`: arquitectura del monolito modular.
4. `04-domain-model.md`: agregados, invariantes y eventos.
5. `05-data-model.md`: tablas, relaciones, índices y migraciones.
6. `06-api-contracts.md`: contratos HTTP y permisos.
7. `07-use-cases.md`: recorridos y criterios de aceptación.
8. `08-frontend-spec.md`: experiencia web, marca y feedback.
9. `09-integrations-infrastructure.md`: proveedores, despliegue y observabilidad.
10. `10-ai-development.md`: reglas de desarrollo asistido.
11. `11-roadmap.md`: implementado, en curso y siguiente.
12. `12-decisiones-fundacionales.md`: decisiones arquitectónicas y de producto.
13. `13-estado-desarrollo.md`: resumen vigente de todo el proyecto.
14. `modulos/`: estado técnico y funcional por módulo.
15. `28-registro-vivo-deuda-tecnica.md`: único registro de deuda activa.
16. `29-cronologia-ramas.md`: integración y situación Git.
17. `30-rediseño-pos-membresias-beneficios.md`: diseño consolidado de comercio.
18. `31-diseño-automatizaciones-kiosco.md`: diseño consolidado de autoservicio, automatizaciones y correo transaccional.
19. `modulos/22-platform-billing.md`: operación comercial de GymFort por cliente y sucursal.

Para **Automatizaciones y Notificaciones**, las fichas `modulos/16-automations.md`, `modulos/17-notifications.md` y el diseño rector `31-diseño-automatizaciones-kiosco.md` representan el corte funcional vigente. Las notas etiquetadas como hitos históricos dentro de documentos generales como `06-api-contracts.md` u `08-frontend-spec.md` describen etapas de evolución y no deben prevalecer sobre esas fichas cuando exista una diferencia de detalle.

Fuentes operativas complementarias:

- `../docs/operations/`: despliegue, respaldo, incidentes, correo, kiosco y Stripe.
- `../docs/security/`: permisos y verificación multi-tenant.
- `../docs/testing/`: aceptación funcional y Stripe sandbox.
- `../infra/README.md`: ejecución local y perfil Docker de Stripe CLI.

## Alcance implementado

La versión piloto cubre organizaciones y sucursales, identidad/RBAC, clientes, catálogo, ventas, pagos manuales y por proveedor, membresías, beneficios, acceso, reservas, inventario, coaches, wellness, automatizaciones, notificaciones, reportes, auditoría, configuración y kiosco web.

Stripe Checkout y la domiciliación ya forman parte del producto. Los cambios de membresía y demás efectos financieros sólo se aplican después del webhook firmado y conciliado. Los planes multi-sucursal conservan la sucursal de origen de la membresía, pero habilitan consulta, acceso, reservas y kiosco en otras sucursales activas del mismo gimnasio.

### Automatizaciones

El alcance V1 está funcionalmente cerrado:

- reglas manuales, programadas y por eventos;
- inbox durable, leases, idempotencia, retries y dead letters;
- interfaz simplificada en **Automatizaciones / Actividad / Problemas**;
- creación desde cero sin plantillas recomendadas;
- prueba manual directa con un socio real;
- Gerencia autorizada para crear/administrar/probar reglas mediante V80.

### Correo transaccional

ZeptoMail es el proveedor operativo actual mediante `EmailDeliveryPort` + SMTP. `gymfort.mx` quedó verificado con DKIM y CNAME/return-path y se confirmaron entregas reales el 22 de agosto de 2026.

Patrón V1 recomendado:

```text
From: Nombre del gimnasio <notificaciones@gymfort.mx>
Reply-To: bandeja real del gimnasio
```

La plantilla toma nombre, colores y logo del tenant. La URL pública del logo depende de `GYMOS_FRONTEND_BASE_URL`; localhost no es accesible desde clientes externos de correo.

Quedan como mejoras posteriores, no como bloqueo del módulo: feedback de rebotes/quejas, reputación productiva/DMARC final, trigger específico de domiciliación activada y canales WhatsApp/push.

Quedan fuera de V1: aplicación móvil/portal remoto completo del socio, cobros entre coach y socio, integración productiva del hardware de acceso hasta completar el piloto HikCentral y WhatsApp/push productivos.

## Reglas de mantenimiento

1. El código y las pruebas verificadas son la evidencia; la documentación debe corregirse cuando discrepe.
2. Todo cambio material actualiza la ficha de módulo, este índice si cambia el mapa, el documento 13 y la deuda 28 cuando corresponda.
3. Cambios de endpoint, permiso o migración actualizan 05, 06 y la matriz de permisos.
4. Cambios operativos actualizan `docs/operations`, `docs/testing` o `infra`.
5. No crear cierres, prompts o listas de pendientes paralelas; Git conserva el historial y el documento 28 conserva la deuda activa.
