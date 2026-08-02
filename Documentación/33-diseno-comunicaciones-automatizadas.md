# Diseño rector de comunicaciones automatizadas

## 1. Propósito

Automatizaciones detecta eventos del negocio y orquesta comunicaciones salientes dirigidas a los socios. Su objetivo principal no es mostrar al personal cada recordatorio generado, sino entregar información útil por los canales habilitados.

Canales objetivo:

- fase 1: `EMAIL` y `WHATSAPP`;
- fase 2, con la aplicación móvil: `PUSH`;
- `IN_APP` se reserva para alertas internas reales del personal y no representa una comunicación al socio.

Ejemplos de comunicaciones al socio:

- membresía próxima a vencer o vencida;
- membresía creada, renovada o cambiada de plan;
- pago confirmado o rechazado;
- reserva creada, confirmada o cancelada;
- cita de Wellness creada, confirmada o cancelada.

## 2. Separación conceptual

### 2.1 Evento de dominio

Representa un hecho ocurrido en un módulo propietario, por ejemplo `MEMBERSHIP_RENEWED` o `BOOKING_CREATED`. Debe incluir identidad estable, versión, organización, sucursal, referencia de negocio y variables mínimas.

### 2.2 Regla

Relaciona un disparador y condiciones con una acción. Para comunicaciones a socios la acción canónica será `SEND_CUSTOMER_MESSAGE`.

Ejemplo lógico:

```text
MEMBERSHIP_EXPIRING + daysBeforeExpiry=7
→ SEND_CUSTOMER_MESSAGE
→ EMAIL y WHATSAPP
→ plantilla MEMBERSHIP_EXPIRING_7_DAYS
```

### 2.3 Ocurrencia durable

Cada coincidencia crea un trabajo idempotente. La cola durable controla lease, intentos, backoff, recuperación y `DEAD_LETTER`. La ocurrencia indica que la regla fue procesada; no sustituye el estado individual de cada canal.

### 2.4 Mensaje saliente

Cada canal produce una entrega independiente en `outbound_message`.

Estados canónicos:

- `PENDING`;
- `SENDING`;
- `ACCEPTED`;
- `DELIVERED`;
- `FAILED`;
- `UNDELIVERABLE`;
- `CANCELLED`.

Un mismo trabajo puede terminar con correo entregado y WhatsApp fallido. Los identificadores y webhooks del proveedor se registran en la entrega, no en la regla.

### 2.5 Alerta interna

`CREATE_INTERNAL_ALERT` se reserva para incidencias que requieren intervención del personal, por ejemplo credenciales inválidas, integración deshabilitada o entregas agotadas. No debe crearse una alerta administrativa por cada membresía próxima a vencer.

## 3. Plantillas

Las plantillas se separan de las reglas y se versionan por canal e idioma.

Campos mínimos:

- código funcional;
- canal;
- idioma;
- asunto, cuando aplique;
- contenido;
- variables permitidas;
- versión;
- estado.

Una regla referencia el código de plantilla y los canales. Para WhatsApp, la plantilla puede almacenar además el identificador aprobado por el proveedor.

## 4. Destinatarios y preferencias

El destinatario se resuelve desde `clientId` mediante un puerto público del módulo de socios. Nunca se confía en una dirección enviada por el navegador o incluida libremente en el evento.

Deben considerarse:

- correo normalizado;
- teléfono en formato E.164;
- preferencias por canal;
- consentimiento y revocación;
- diferencia entre mensajes operativos y marketing;
- ausencia o invalidez del dato de contacto.

Los mensajes operativos y las campañas comerciales tendrán políticas independientes.

## 5. Adaptadores

Automatizaciones no contiene código específico de proveedores. Utiliza contratos:

```text
EmailSender
WhatsAppSender
PushSender
```

Las implementaciones concretas podrán utilizar SMTP, SES, SendGrid, Resend, Meta WhatsApp Cloud API, Twilio, Firebase Cloud Messaging u otros proveedores sin cambiar las reglas ni la cola.

## 6. Flujo objetivo

```text
módulo propietario publica evento
→ regla compatible
→ work item idempotente
→ snapshot de regla
→ resolución del socio y preferencias
→ renderizado de plantilla
→ una entrega por canal
→ dispatcher del canal
→ proveedor externo
→ webhook/resultado
→ estado final y métricas
```

El trabajo se considera correctamente materializado cuando las entregas fueron creadas de forma idempotente. Los reintentos de envío se controlan por entrega para no reenviar los canales ya aceptados o entregados.

## 7. Experiencia administrativa

El administrador configura reglas, plantillas y canales y consulta resultados agregados. No necesita una bandeja global con cada mensaje individual.

La vista operativa debe mostrar, por regla y periodo:

- socios detectados;
- entregas creadas por canal;
- aceptadas y entregadas;
- sin destinatario válido;
- fallidas y en reintento;
- agotadas o bloqueadas por preferencias.

El detalle individual sólo se consulta para diagnóstico, auditoría y soporte.

## 8. Alcance por fases

### Fase 1

- `EMAIL` y `WHATSAPP`;
- plantillas por canal;
- preferencias operativas;
- cola de mensajes salientes;
- adaptadores y webhooks;
- métricas y diagnóstico.

### Fase 2

- `PUSH`;
- registro de dispositivos FCM/APNS;
- deep links hacia la aplicación;
- invalidación de tokens;
- bandeja y badges dentro de la app.

## 9. Compatibilidad transitoria

`CREATE_NOTIFICATION` y `notification_log` pueden conservarse temporalmente para pruebas del motor y alertas internas existentes. No se consideran el mecanismo final de comunicación al socio y no deben ampliarse como centro global de avisos administrativos.
