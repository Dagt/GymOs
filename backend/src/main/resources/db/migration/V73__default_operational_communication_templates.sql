WITH template_seed(code, channel, subject, body, variables) AS (
    VALUES
      ('MEMBERSHIP_EXPIRING','EMAIL','Tu membresía vence pronto','Hola {{clientName}}, tu membresía vence el {{endsOn}}. Faltan {{daysBeforeExpiry}} días.','["clientName","endsOn","daysBeforeExpiry"]'),
      ('MEMBERSHIP_EXPIRING','WHATSAPP',NULL,'Hola {{clientName}}, tu membresía vence el {{endsOn}}. Faltan {{daysBeforeExpiry}} días.','["clientName","endsOn","daysBeforeExpiry"]'),
      ('MEMBERSHIP_EXPIRED','EMAIL','Tu membresía venció','Hola {{clientName}}, tu membresía venció el {{endsOn}}. Acude a recepción para conocer tus opciones.','["clientName","endsOn"]'),
      ('MEMBERSHIP_EXPIRED','WHATSAPP',NULL,'Hola {{clientName}}, tu membresía venció el {{endsOn}}. Acude a recepción para conocer tus opciones.','["clientName","endsOn"]'),
      ('MEMBERSHIP_CREATED','EMAIL','Tu membresía fue creada','Hola {{clientName}}, tu membresía quedó registrada correctamente.','["clientName"]'),
      ('MEMBERSHIP_CREATED','WHATSAPP',NULL,'Hola {{clientName}}, tu membresía quedó registrada correctamente.','["clientName"]'),
      ('MEMBERSHIP_RENEWED','EMAIL','Tu membresía fue renovada','Hola {{clientName}}, tu membresía fue renovada correctamente y vence el {{endsOn}}.','["clientName","endsOn"]'),
      ('MEMBERSHIP_RENEWED','WHATSAPP',NULL,'Hola {{clientName}}, tu membresía fue renovada correctamente y vence el {{endsOn}}.','["clientName","endsOn"]'),
      ('MEMBERSHIP_PLAN_CHANGED','EMAIL','Tu plan fue actualizado','Hola {{clientName}}, tu plan de membresía fue actualizado correctamente.','["clientName"]'),
      ('MEMBERSHIP_PLAN_CHANGED','WHATSAPP',NULL,'Hola {{clientName}}, tu plan de membresía fue actualizado correctamente.','["clientName"]'),
      ('MEMBERSHIP_CANCELLED','EMAIL','Tu membresía fue cancelada','Hola {{clientName}}, tu membresía fue cancelada. Si necesitas ayuda, comunícate con recepción.','["clientName"]'),
      ('MEMBERSHIP_CANCELLED','WHATSAPP',NULL,'Hola {{clientName}}, tu membresía fue cancelada. Si necesitas ayuda, comunícate con recepción.','["clientName"]'),
      ('BOOKING_CREATED','EMAIL','Reserva confirmada','Hola {{clientName}}, tu reserva quedó registrada para el {{startsAt}}.','["clientName","startsAt"]'),
      ('BOOKING_CREATED','WHATSAPP',NULL,'Hola {{clientName}}, tu reserva quedó registrada para el {{startsAt}}.','["clientName","startsAt"]'),
      ('BOOKING_CANCELLED','EMAIL','Reserva cancelada','Hola {{clientName}}, tu reserva del {{startsAt}} fue cancelada.','["clientName","startsAt"]'),
      ('BOOKING_CANCELLED','WHATSAPP',NULL,'Hola {{clientName}}, tu reserva del {{startsAt}} fue cancelada.','["clientName","startsAt"]'),
      ('WELLNESS_APPOINTMENT_CREATED','EMAIL','Cita confirmada','Hola {{clientName}}, tu cita de bienestar quedó registrada para el {{startsAt}}.','["clientName","startsAt"]'),
      ('WELLNESS_APPOINTMENT_CREATED','WHATSAPP',NULL,'Hola {{clientName}}, tu cita de bienestar quedó registrada para el {{startsAt}}.','["clientName","startsAt"]'),
      ('WELLNESS_APPOINTMENT_CANCELLED','EMAIL','Cita cancelada','Hola {{clientName}}, tu cita de bienestar del {{startsAt}} fue cancelada.','["clientName","startsAt"]'),
      ('WELLNESS_APPOINTMENT_CANCELLED','WHATSAPP',NULL,'Hola {{clientName}}, tu cita de bienestar del {{startsAt}} fue cancelada.','["clientName","startsAt"]'),
      ('PAYMENT_COMPLETED','EMAIL','Pago confirmado','Hola {{clientName}}, recibimos correctamente tu pago.','["clientName"]'),
      ('PAYMENT_COMPLETED','WHATSAPP',NULL,'Hola {{clientName}}, recibimos correctamente tu pago.','["clientName"]'),
      ('PAYMENT_FAILED','EMAIL','No fue posible procesar tu pago','Hola {{clientName}}, no fue posible procesar tu pago. Comunícate con recepción para recibir apoyo.','["clientName"]'),
      ('PAYMENT_FAILED','WHATSAPP',NULL,'Hola {{clientName}}, no fue posible procesar tu pago. Comunícate con recepción para recibir apoyo.','["clientName"]')
)
INSERT INTO communication_template (
    id, organization_id, branch_id, code, channel, locale, subject, body,
    allowed_variables_json, version, status, created_by, created_at, updated_at
)
SELECT gen_random_uuid(), o.id, NULL, s.code, s.channel, 'es-MX', s.subject, s.body,
       s.variables, 1, 'ACTIVE', NULL, now(), now()
FROM organization o
CROSS JOIN template_seed s
WHERE o.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM communication_template existing
      WHERE existing.organization_id = o.id
        AND existing.branch_id IS NULL
        AND existing.code = s.code
        AND existing.channel = s.channel
        AND existing.locale = 'es-MX'
        AND existing.version = 1
  );
