# Automatizaciones

Estado funcional: `PARCIAL`

## Propósito del módulo

Automatizaciones detecta eventos del negocio y orquesta acciones durables e idempotentes. Para comunicaciones, su objetivo principal es enviar información a los socios por canales externos, no mostrar al personal cada recordatorio generado.

Canales objetivo:

- fase 1: correo electrónico y WhatsApp;
- fase 2: notificaciones push mediante la aplicación móvil;
- las alertas internas se reservan para incidencias operativas que requieren intervención del personal.

Ejemplos:

- aviso de membresía próxima a vencer;
- confirmación de creación, renovación o cambio de membresía;
- confirmación o fallo de pago;
- confirmación o cancelación de reserva;
- confirmación o cancelación de cita Wellness.

## Modelo funcional

```text
evento de dominio
→ regla y condiciones
→ ocurrencia durable
→ resolución del socio y preferencias
→ plantilla por canal
→ mensaje saliente EMAIL / WHATSAPP / PUSH
→ proveedor
→ resultado o webhook
```

La ocurrencia de automatización y cada entrega por canal tienen ciclos de vida independientes. Un correo puede entregarse mientras WhatsApp queda en reintento.

## Acciones objetivo

- `SEND_CUSTOMER_MESSAGE`: crea una o varias entregas salientes dirigidas al socio;
- `CREATE_INTERNAL_ALERT`: genera una alerta operativa para personal únicamente ante incidencias relevantes;
- `CREATE_STAFF_TASK`: crea seguimiento para personal cuando exista la bandeja correspondiente;
- acciones sobre otros módulos siempre se invocan mediante puertos públicos y nunca modificando sus tablas directamente.

`CREATE_NOTIFICATION` y `notification_log` son compatibilidad transitoria y una ayuda de inspección técnica. No representan el mecanismo final de correo, WhatsApp o push y no deben convertirse en una bandeja global de recordatorios para administradores.

## Requisitos de comunicaciones

- plantillas versionadas por canal e idioma;
- resolución de destinatarios desde `clientId` mediante el módulo de socios;
- correo normalizado y teléfono E.164;
- preferencias y consentimiento por canal;
- separación entre comunicación operativa y marketing;
- idempotencia por regla, evento, destinatario, canal y versión de plantilla;
- estados de entrega, identificador externo, reintentos y webhooks;
- adaptadores desacoplados para correo, WhatsApp y push;
- métricas agregadas por regla y canal;
- detalle individual sólo para auditoría, diagnóstico y soporte.

## Experiencia administrativa esperada

El administrador configura reglas, plantillas, canales y alcance. La pantalla debe mostrar resultados agregados, por ejemplo:

```text
Regla: membresía vence en 7 días
42 socios detectados
40 correos entregados
37 WhatsApp entregados
2 sin correo válido
3 números inválidos
1 entrega en reintento
```

No debe recibir una alerta en la plataforma por cada socio cuya membresía vaya a vencer. Las alertas internas se limitan a problemas como integración deshabilitada, credenciales inválidas o entregas que agotaron reintentos.

## Diseño rector

- Diseño general de automatizaciones y kiosco: `../31-diseño-automatizaciones-kiosco.md`.
- Diseño específico de comunicaciones salientes: `../33-diseno-comunicaciones-automatizadas.md`.

Este documento define comportamiento funcional y no se utiliza como bitácora de avances de implementación.
