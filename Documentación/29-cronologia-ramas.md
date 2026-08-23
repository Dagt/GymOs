# 29. Cronología y situación de ramas

Estado: AUDITADO
Corte: 23 de agosto de 2026
Rama de trabajo: `feature/pricing-and-billing`
Base integrada: `main` con Automatizaciones/ZeptoMail y visualización de métricas cerradas

## 1. Situación vigente

`fix/kiosk-memberships-stripe`, Automatizaciones/ZeptoMail y la visualización de métricas ya fueron cerradas e integradas a `main`. La rama actual parte de esa base para administrar la relación comercial de GymFort con sus gimnasios clientes.

El alcance actual inicia con la configuración de planes por sucursal, la agregación por cliente y un registro manual controlado de cobros. No representa todavía facturación fiscal ni cobro automático de la suscripción SaaS.

| Rama | Estado | Acción |
|---|---|---|
| `main` | Base estable con cierres de kiosco, Automatizaciones/ZeptoMail y métricas integrados | Recibir cambios sólo por PR verde |
| `fix/kiosk-memberships-stripe` | Integrada | Histórica; no fusionar otra vez |
| `feature/automations-zoho-email` | Integrada | Histórica; no fusionar otra vez |
| `feature/pricing-and-billing` | Activa | Completar cobranza comercial, validación, PR y merge a `main` |
| `feature/automations-self-service-kiosk` | Ancestro funcional integrado | Referencia histórica |
| `feature/coaches-workouts` | Coaches ya incorporado en la evolución del árbol | Referencia histórica; no merge lateral |
| `feature/wellness-services` | Funcionalidad reaplicada en historia vigente | Referencia; no fusionar de nuevo |

## 2. Hitos integrados

| Periodo | Hito |
|---|---|
| 13–25 jul 2026 | Fundación, multi-tenant, identidad, CRM, catálogo, POS, membresías, reservas, inventario, consolas y operación por sucursal |
| 26–27 jul | Coaches/rutinas, perfiles, catálogo de ejercicios, métricas, PDF y Wellness |
| 30 jul–3 ago | Automatizaciones durables, kiosco lógico, proveedores, Stripe Checkout/recurrencia, correo durable y observabilidad |
| agosto | Integración/corrección de membresías Stripe, idempotencia, beneficios, retornos, pagos y multi-sucursal |
| 20 ago | PR #10 integra `fix/kiosk-memberships-stripe` en `main` |
| 21 ago | Inicio de `feature/automations-zoho-email`: estilo administrativo, GymFort, feedback, permisos Stripe y Stripe CLI |
| 21–22 ago | Configuración unificada de pagos/kiosco, restricción de apertura sólo a Recepción, UI administrativa refinada y eliminación de Operación diaria |
| 22 ago | ZeptoMail sustituye el planteamiento previo de Zoho Mail: SMTP real, allowlist de remitentes, DKIM/CNAME de `gymfort.mx`, entrega real y runbooks |
| 22 ago | Automatizaciones simplificadas a **Automatizaciones / Actividad / Problemas**, creación desde cero, prueba con socio real y búsqueda incremental |
| 22 ago | V80 concede administración/ejecución de Automatizaciones a Gerencia |
| 22 ago | V79 reconcilia perfiles Coach históricos faltantes después de detectar drift durante pruebas |
| 22 ago | Cierre documental de Automatizaciones + ZeptoMail |
| 23 ago | Inicio de `feature/pricing-and-billing`: V81 configura contratos por sucursal y V82 agrega historial inmutable de cobros comerciales |

## 3. Cambios materiales del cierre

### Automatizaciones

- motor durable existente conservado;
- UI del cliente simplificada;
- recetas/plantillas recomendadas eliminadas;
- toggle + `Probar ahora` directo;
- prueba email con socio real;
- búsqueda por nombre/correo;
- Gerencia con permisos de gestión;
- distinción documentada entre activación de mandato y `RECURRING_CHARGE_DUE`.

### ZeptoMail

- integración mediante `EmailDeliveryPort` + SMTP existente;
- credenciales globales por ambiente;
- `From` central recomendado `notificaciones@gymfort.mx`;
- nombre y `Reply-To` por tenant;
- branding HTML con colores/logo del gimnasio;
- resolución pública del logo mediante `GYMOS_FRONTEND_BASE_URL`;
- validación DKIM + CNAME/return-path;
- entregas reales confirmadas.

### Deuda técnica adyacente

- V79 evita perfiles Coach históricos sin fila operativa correspondiente cuando existe rol/acceso válido;
- la modificación de kiosco V78 quedó confirmada como independiente del problema de Coaches.

## 4. Evidencia del corte

- dominio `gymfort.mx` verificado en ZeptoMail;
- correo de prueba recibido en bandeja;
- prueba de regla EMAIL usando socio real;
- V80 aplicada como reparación de permisos;
- documentación operativa/funcional actualizada;
- la rama se mantiene sin commits por detrás de `main` al momento del cierre documental.

Los checks CI deben revisarse sobre el commit final antes de abrir/fusionar PR; ausencia de un status publicado no se interpreta como pipeline verde.

## 5. Regla futura

1. Una rama representa un objetivo claro.
2. Antes de integrar: fetch/comparación contra `origin/main`, pruebas, revisión documental y PR.
3. No hacer merges laterales de ramas históricas.
4. Todo cierre material actualiza documentos 13, 28, 29 y fichas afectadas.
5. Las ramas remotas sólo se eliminan por decisión explícita.
