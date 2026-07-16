# 5. Modelo Físico de Datos

## 5.1 Convenciones

Todas las tablas principales deben incluir:

- `id UUID PRIMARY KEY`
- `organization_id UUID`
- `branch_id UUID NULL` cuando el dato pertenezca a una sucursal específica
- `created_at TIMESTAMP`
- `updated_at TIMESTAMP`
- `created_by UUID`
- `updated_by UUID`
- `version BIGINT`
- `deleted_at TIMESTAMP NULL` cuando aplique

## 5.2 Tablas principales

### organization
- legal_name
- trade_name
- tax_id
- status
- timezone
- default_currency
- contact_email
- contact_phone

Índices:
- lower(trade_name)
- tax_id

### branch
- organization_id
- name
- code
- address_json
- timezone
- status
- phone

Índices:
- organization_id + code
- organization_id + status

### person
- first_name
- last_name
- birth_date
- gender
- phone
- email
- photo_reference
- status

Índices:
- organization_id + lower(email)
- organization_id + phone
- organization_id + last_name

### user_account
- person_id
- username
- password_hash
- status
- last_login_at
- failed_attempts
- locked_until

### role
- name
- description
- system_role

### permission
- code
- description
- module

### user_role
- user_id
- role_id

### role_permission
- role_id
- permission_id

### user_branch_access
- user_id
- branch_id
- access_scope

Regla:
- Un usuario puede tener acceso a una o varias sucursales dentro de su organización.
- Los administradores de organización pueden tener alcance global de organización sin sucursal específica.

### self_service_terminal
- organization_id
- branch_id
- name
- device_code
- status
- allowed_actions_json
- last_seen_at

Índices:
- organization_id + branch_id
- organization_id + device_code

### self_service_session
- terminal_id
- client_id
- status
- started_at
- expires_at
- ended_at
- verification_method
- origin_ip

Índices:
- terminal_id + status
- client_id + started_at

### client
- person_id
- status
- joined_at
- notes

### product
- sku
- name
- description
- type
- base_price
- currency
- active
- inventory_tracked
- behavior_key

### plan
- product_id
- duration_unit
- duration_value
- access_policy_json
- reservation_policy_json
- auto_renew_allowed
- evidence_requirement_json

### membership
- client_id
- plan_id
- sale_item_id
- start_date
- end_date
- status
- auto_renew
- grace_period_until

Índices:
- client_id + status
- end_date + status
- plan_id

### benefit
- name
- type
- value
- eligibility_rule_json
- evidence_required
- valid_from
- valid_until
- active

### client_benefit
- client_id
- benefit_id
- evidence_reference
- verified_at
- valid_until
- verified_by

### sale
- client_id
- status
- subtotal
- discount_total
- tax_total
- grand_total
- currency
- idempotency_key

### sale_item
- sale_id
- product_id
- quantity
- unit_price
- discount_amount
- total_amount
- behavior_snapshot_json

### payment
- sale_id
- method
- status
- amount
- external_reference
- proof_reference
- reviewed_by
- reviewed_at
- idempotency_key

### inventory_item
- product_id
- quantity_on_hand
- minimum_threshold
- average_cost

### inventory_movement
- inventory_item_id
- type
- quantity
- unit_cost
- reference_type
- reference_id
- reason
- Los movimientos automaticos por venta usan `reference_type = SALE`, `reference_id = sale.id` y `product_id`.
- La restriccion `ux_inventory_movement_sale_product_effect` evita descontar dos veces el mismo producto por la misma venta.

### resource
- name
- type
- capacity
- active
- schedule_json
- booking_rules_json

### booking
- resource_id
- client_id
- product_id
- sale_item_id
- starts_at
- ends_at
- status
- notes

Índices:
- resource_id + starts_at + ends_at
- client_id + starts_at
- status + starts_at

### access_device
- name
- provider
- external_id
- status
- configuration_reference

### access_attempt
- client_id
- device_id
- occurred_at
- decision
- reason
- membership_id
- raw_event_reference

### coach
- organization_id
- branch_id
- display_name
- email
- phone
- specialties_json
- bio
- status

### workout
- coach_id
- title
- goal
- level
- status

### workout_version
- workout_id
- version_number
- summary
- content_json
- created_by

### workout_assignment
- workout_id
- workout_version_id
- client_id
- coach_id
- status
- assigned_on
- starts_on
- ends_on
- notes

### professional
- organization_id
- branch_id
- display_name
- professional_type
- email
- phone
- bio
- status
- version
- deleted_at

### professional_service
- organization_id
- branch_id
- professional_id
- name
- service_type
- duration_minutes
- price_cents
- currency
- status
- version
- deleted_at

### professional_availability
- organization_id
- branch_id
- professional_id
- day_of_week
- opens_at
- closes_at
- status
- version
- deleted_at

### notification_log
- organization_id
- branch_id
- channel
- recipient
- subject
- body
- status
- trigger_type
- reference_type
- reference_id
- scheduled_at
- sent_at
- error_message
- version

### automation_rule
- organization_id
- branch_id
- name
- trigger_type
- condition_json
- action_type
- action_config_json
- status
- retry_limit
- version
- deleted_at

### automation_execution
- organization_id
- branch_id
- rule_id
- status
- trigger_reference
- error_message
- occurred_at

### client_import_batch
- organization_id
- branch_id
- file_name
- dry_run
- status
- total_rows
- created_count
- skipped_count
- error_count
- completed_at

### client_import_row
- batch_id
- organization_id
- row_number
- status
- reason
- client_id
- first_name
- last_name
- email
- phone

### audit_log
- actor_user_id
- action
- entity_type
- entity_id
- before_json
- after_json
- reason
- correlation_id
- occurred_at

## 5.3 Integridad

- FKs obligatorias.
- Restricciones únicas por organización.
- Restricciones únicas por sucursal cuando el dato sea local a una sucursal.
- Check constraints para montos no negativos.
- Exclusión o validación transaccional para evitar traslapes.
- Nunca borrar auditoría.
- Nunca borrar auditorÃ­a.

## 5.4 Cambios Hito 3.5

Columnas agregadas:
- `sale.idempotency_key`
- `sale.idempotency_request_hash`
- `sale.business_context`
- `sale.context_membership_id`
- `sale.context_target_plan_id`
- `sale.payment_completed_at`
- `payment.idempotency_key`
- `payment.idempotency_request_hash`
- `self_service_session.session_token_hash`
- `self_service_session.verification_attempts`
- `self_service_session.blocked_until`
- `self_service_session.last_request_at`
- `membership.source_membership_id`
- `membership.source_payment_event_key`

Tabla nueva:
- `processed_domain_event`: registra `organization_id`, `event_key`, `event_type` y `processed_at` para consumo idempotente.

Restricciones nuevas:
- `ux_sale_org_idempotency_key`
- `ux_payment_org_idempotency_key`
- `ux_self_service_session_token_hash`
- `ux_membership_org_payment_event_key`

Migraciones nuevas:
- `V2_1__demo_organization_prerequisite.sql`: garantiza que los datos demo historicos de catalogo y kiosco tengan organizacion/sucursal al migrar desde una base vacia.
- `V6__hito3_5_architectural_stabilization.sql`: agrega idempotencia, contexto comercial, eventos procesados, seguridad de kiosco y trazabilidad de membresias.
- `V7__permissions_and_kiosk_security.sql`: registra permisos granulares faltantes y los asigna al rol administrador.
- `V8__idempotency_request_hashes.sql`: agrega hash de solicitud a ventas y pagos para detectar reutilizacion de idempotency key con payload distinto.
- `V9__hito4_access_control.sql`: agrega dispositivos de acceso, pases temporales y bitacora de accesos.
- `V10__hito4_inventory.sql`: agrega existencias, movimientos de inventario y stock semilla.
- `V11__hito4_resources_bookings.sql`: agrega recursos reservables, reservas, permisos y recurso demo.
- `V12__hito4_inventory_sale_idempotency.sql`: agrega indice unico para movimientos automaticos de inventario por venta y producto.
- `V13__hito5_coaches_workouts.sql`: agrega coaches, rutinas versionadas, asignaciones a clientes, permisos y datos demo de coaching.
- `V14__hito5_wellness_notifications_automations.sql`: agrega profesionales de bienestar, servicios, disponibilidad, notificaciones, automatizaciones y dashboard del dueno.
- `V15__hito6_productization_onboarding_imports.sql`: agrega bitacora de importacion inicial de clientes y permisos `onboarding.manage` y `client.import`.
