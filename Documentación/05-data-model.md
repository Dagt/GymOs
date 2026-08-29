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
- last_name (apellido paterno)
- maternal_last_name
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
- inactivated_at
- inactivation_reason
- notes

Regla:
- El socio pertenece a la organizacion/tenant, no se duplica por sucursal. Las ventas, membresias, accesos, reservas y pases conservan `branch_id` cuando la operacion es local a una sucursal.
- La baja automática usa `status=INACTIVE` e
  `inactivation_reason=MEMBERSHIP_INACTIVITY`; no elimina el expediente ni sus
  relaciones financieras.

### client_lifecycle_configuration
- organization_id
- registration_fee_enabled
- registration_fee_cents
- registration_fee_label
- auto_inactivation_enabled
- inactivity_months
- updated_at
- updated_by
- version

Regla:
- `inactivity_months` es el único umbral temporal para determinar periodo de
  inactividad y reinscripción.
- La inscripción corresponde en la primera membresía o cuando
  `latestMembershipGraceEnd + inactivity_months <= effectiveDate`, aunque la
  baja automática esté desactivada.
- La inactividad se mide desde el fin de la última gracia de membresía o, si el
  socio nunca tuvo una, desde `joined_at`.

### product
- `catalog_product` sigue representando la unidad vendible y conserva su `id`:
  ventas, movimientos, existencias y snapshots no se migran a otra entidad.
- sku
- name
- description
- type
- base_price
- currency
- active
- inventory_tracked
- barcode (espejo temporal del código principal para compatibilidad)
- family_id
- variant_name
- brand
- presentation
- unit_of_measure
- display_order
- image_object_key
- archived_at
- archived_by
- categoria principal mediante `catalog_product_category`
- etiquetas mediante `catalog_product_tag`
- behavior_key

### catalog_product_family
- id
- organization_id
- name
- brand
- description
- version

Regla: la familia describe el producto base, por ejemplo `Psychotic`; no tiene
precio, SKU ni existencias. Cada sabor/presentación permanece como
`catalog_product` vendible, con precio, costo, SKU e inventario propios. La
familia es de la organización y las variantes conservan disponibilidad por
sucursal mediante `catalog_product_branch`.

Los datos comunes del alta masiva son valores por defecto del lote; una vez
creada, cada variante sigue siendo un producto vendible independiente. No hay
herencia dinámica persistida: presentación, precio y costo efectivos se copian
al `catalog_product` de cada variante durante la creación.

### catalog_product_barcode
- organization_id
- product_id
- barcode
- is_primary
- active

Reglas:
- una variante acepta hasta 20 códigos;
- un código activo identifica una sola variante dentro de la organización;
- todos los alias resuelven el mismo `catalog_product` en POS e inventario;
- los códigos adicionales son aliases de la misma variante, no sabores
  diferentes;
- archivar desactiva sus alias y restaurar vuelve a validar su unicidad.

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

### membership_plan
- product_id
- duration_days
- grace_days
- benefits_json
- multi_branch_access
- status

Regla:
- `multi_branch_access=false`: la membresia da acceso en la sucursal donde se vendio/activo.
- `multi_branch_access=true`: la membresia se considera valida en todas las sucursales activas del mismo gimnasio. Renovaciones y cambios de plan conservan una sola membresia origen/destino; la agregacion o remocion de acceso multi-sucursal se resuelve por el plan vigente.
- La busqueda y ficha de socios se filtran por sucursal activa: un socio normal se ve en su `home_branch_id`; si tiene membresia activa multi-sucursal se ve en todas las sucursales del gimnasio.

### resource_allowed_membership_plan
- resource_id
- plan_id
- organization_id
- created_at

Regla:
- Si un recurso no tiene filas en esta tabla, cualquier socio activo puede reservarlo.
- Si existen planes permitidos, el socio necesita una membresia activa de alguno de esos planes. La validacion respeta `membership_plan.multi_branch_access`.

### benefit
- name
- type
- value
- eligibility_rule_json
- evidence_required
- valid_from
- valid_until
- active

Regla objetivo multi-sucursal:
- Un beneficio ligado a un plan multi-sucursal puede tener alcance
  `PLAN_BRANCHES`; en ese caso la elegibilidad se deriva de las sucursales donde
  el plan es válido y no de la sucursal desde la que se creó la definición.
- Los beneficios deliberadamente locales conservan alcance `LOCAL`. Una futura
  selección parcial de sedes se representa con una relación de alcance, no con
  copias del mismo beneficio.
- La organización continúa siendo la frontera tenant en todos los casos.

### client_benefit
- client_id
- benefit_id
- evidence_reference
- verified_at
- valid_until
- verified_by

### visit_package_entitlement (objetivo)
- organization_id
- client_id
- sale_id
- total_visits
- remaining_visits
- valid_from
- valid_until
- branch_scope (`ALL_BRANCHES` o `SELECTED_BRANCHES`)
- status

### visit_package_branch (objetivo)
- entitlement_id
- branch_id

### visit_package_consumption (objetivo)
- entitlement_id
- branch_id
- access_id
- consumed_at
- idempotency_key

Regla:
- El saldo es único para el socio dentro del tenant. Dos sucursales nunca crean
  saldos independientes para la misma compra; el consumo atómico registra la
  sede donde ocurrió.

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
- item_type
- quantity
- unit_price
- discount_amount
- total_amount
- behavior_snapshot_json

Regla:
- Una alta con inscripción genera una partida `MEMBERSHIP_PLAN` con producto y
  una partida `REGISTRATION_FEE` sin producto. Ambas integran subtotal, total y
  pago, pero los beneficios de membresía sólo descuentan el plan.

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
- user_account_id
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
- printable_notes
- idempotency_key
- request_hash
- status_reason
- replaced_assignment_id

### Modelo implementado para Coaches y rutinas

Los cierres funcionales `84a5fd3` y `8162814` conservan las tablas base y
agregan:

- `coach.user_account_id` para resolver propiedad del perfil;
- una fila `coach` por usuario y sucursal, con índice único parcial. Las
  especialidades y biografía se sincronizan entre las filas del mismo usuario;
  el estado y la pertenencia se administran por sucursal;
- `exercise` y `exercise_alias`, con origen SYSTEM/ORGANIZATION/COACH y referencia
  de medio en almacenamiento de objetos;
- `workout`, `workout_version`, `workout_session`, `workout_session_block` y
  `workout_exercise`;
- `workout_assignment` con versión obligatoria, llave idempotente, actor, notas
  privadas, observaciones imprimibles, `status_reason` y
  `replaced_assignment_id`;
- auditoría de asignación, reemplazo, cancelación, finalización, catálogo,
  aliases, medios y actualización del perfil del coach.

Todas las tablas incluyen `organization_id`; las dependientes de sede incluyen
`branch_id`. Los históricos conservan nombre, prescripción e indicaciones aunque un
ejercicio se archive. `content_json` se mantiene como instantánea compatible,
pero ya no es la única representación editable. Coaching no referencia ventas,
pagos, comisiones ni caja.

### professional
- organization_id
- branch_id
- display_name
- professional_type
- email
- phone
- bio
- office_location
- engagement_type (`STAFF`, `EXTERNAL`)
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
- next_run_at
- locked_until
- current_trigger_reference
- version
- deleted_at

### automation_execution
- organization_id
- branch_id
- rule_id
- status
- trigger_reference
- occurrence_reference
- error_message
- idempotency_key
- attempt_number
- rule_version
- started_at
- finished_at
- occurred_at

### automation_trigger_event
- organization_id
- branch_id
- trigger_type
- trigger_reference
- payload_json
- status
- attempt_number
- available_at
- locked_until
- last_error
- completed_at
- version

Regla:
- una ocurrencia de dominio es única por organización, tipo y referencia;
- el worker usa `locked_until` como lease recuperable y conserva la misma
  referencia al reintentar;
- una ejecución es única por organización, regla y llave idempotente.
- `occurrence_reference` desacopla la identidad funcional de la referencia de
  entrega; por ejemplo, varios escaneos diarios de una membresía conservan una
  sola ocurrencia por fecha de vencimiento.

### membership_lifecycle_occurrence
- organization_id
- branch_id
- membership_id
- event_type
- effective_on
- created_at

Regla: el marcador único por membresía, tipo y fecha efectiva impide publicar
dos veces el aviso diario de membresía por vencer. `membership` conserva
`expired_event_published_at` para reclamar exactamente una vez la publicación
de vencimiento después de aplicar el periodo de gracia.

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
- Nunca borrar auditoría.

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
- `V61__browser_kiosk_security.sql`: registra quién abrió el modo kiosco,
  incorpora `selfservice.session.launch`, permite un perfil lógico equivalente
  por sucursal y deja capacidades futuras cerradas por defecto.
- `V62__organization_payment_configuration.sql`: separa modalidades de cobro de
  recepción, Mercado Pago Point y Stripe por organización.
- `V63__automation_notification_idempotency.sql`: agrega llaves idempotentes a
  notificaciones y ejecuciones de automatización, junto con intento, inicio/fin
  e identidad persistente del trigger en reintento.
- `V64__payment_provider_integrations.sql`: agrega
  `payment_provider_connection`, `payment_provider_oauth_request`,
  `payment_provider_operation` y `payment_provider_webhook_event`; separa
  credenciales cifradas, autorización efímera, intención local idempotente y
  deduplicación/reintento de notificaciones de proveedor.
- `V65__kiosk_membership_enrollment.sql`: habilita explícitamente la capacidad
  lógica `membershipEnrollment` en los perfiles de kiosco existentes y en el
  valor por defecto, sin registrar ni asignar computadoras.
- `V66__automation_domain_event_inbox.sql`: agrega la bandeja durable de
  ocurrencias de pago/reserva, su payload tipado y conserva `rule_version` en
  cada ejecución para que reintentos y diagnóstico usen una identidad estable.
- `V67__automation_membership_lifecycle_and_recovery.sql`: agrega los
  marcadores de publicación del ciclo de membresía y
  `automation_execution.occurrence_reference` para deduplicar una ocurrencia
  funcional aunque llegue mediante distintas entregas diarias.
- `V68__stripe_recurring_billing.sql`: agrega `payment_customer`,
  `stored_payment_method`, `recurring_payment_mandate` y
  `recurring_charge_occurrence`; relaciona operaciones de proveedor con
  mandato/ocurrencia y conserva únicamente referencias tokenizadas y máscara
  de tarjeta.
- `V69__operations_history_pagination.sql`: agrega índices tenant/fecha/ID para
  paginar de forma estable mandatos, cargos recurrentes, webhooks y ejecuciones
  de automatización sin escanear registros de otras organizaciones.
- `V70__automation_operations_and_kiosk_configuration.sql`: agrega control de
  pausa del scheduler, permisos operativos y configuración de capacidades del
  kiosco por perfil lógico de sucursal.
- `V71__provider_payment_receipts.sql`: conserva la referencia segura del
  comprobante emitido por el proveedor.
- `V72__durable_email_delivery.sql`: agrega configuración por tenant y cola
  durable de entrega de correo.
- `V73__manager_operational_configuration_permissions.sql`: permite al gerente
  configurar cobros, kiosco y correo sin otorgarle edición del gimnasio ni de
  sucursales.
- `V74__manager_catalog_kiosk_and_client_integrity.sql`: concede al gerente la
  administración de beneficios y recursos reservables; la fecha de nacimiento
  obligatoria se valida en los contratos de socio para no imponerla a otros
  tipos de persona.
- `V75__repair_manager_configuration_access.sql`: repara en bases existentes los
  permisos operativos de configuración de cobros, kiosco y correo del gerente.
- `V76__restrict_kiosk_launch_to_reception.sql`: retira
  `selfservice.session.launch` del gerente; sólo Recepción y soporte de
  plataforma pueden abrir una sesión aislada para socios.
- `V77__separate_stripe_account_management.sql`: crea
  `payment.provider.stripe.manage`, lo concede a dueño/soporte y lo separa de
  `payment.settings.manage`, que el gerente conserva para activar o desactivar
  una conexión existente.
- `V78__restrict_kiosk_launch_strictly_to_reception.sql`: conserva
  `selfservice.session.launch` únicamente en los roles de Recepción y elimina
  concesiones heredadas de dueño, administrador, gerente o soporte.
- `V15__hito6_productization_onboarding_imports.sql`: agrega bitacora de importacion inicial de clientes y permisos `onboarding.manage` y `client.import`.
- `V23__membership_plan_multi_branch_access.sql`: agrega `membership_plan.multi_branch_access` para planes con acceso a todas las sucursales del tenant.
- `V24__resource_allowed_membership_plans.sql`: agrega planes permitidos por recurso para restringir reservas segun membresia vigente.
- `V25__manager_permissions_and_client_branch_visibility.sql`: agrega `client.home_branch_id`, filtra socios por sucursal/plan multi-sucursal y revoca permisos de administracion de datos/sucursales al rol `Gerente`.
- `V26__branch_branding_and_owner_branch_selection.sql`: historicamente agrego marca opcional por sucursal (`brand_name`, `logo_url`, colores) y soportaba que el dueño entrara sin sucursal operativa por defecto.
- `V27__reception_booking_scope_and_gym_branding.sql`: elimina marca visual de `branch`, deja logo/colores exclusivamente en `organization` y revoca `resource.manage` a recepcion.
- La decisión de V27 describe el estado implementado, pero queda superada como
  objetivo de producto por `CFG-003`: un mismo tenant puede operar marcas
  distintas por sucursal.
- `V47__advanced_coach_profiles.sql`: registra `coach.profile.manage` y lo
  asigna a los roles administrativos que pueden editar el perfil y las
  sucursales de un coach.
- `V48__owner_coaching_metrics_scope.sql`: retira al dueño los permisos
  operativos de rutinas, ejercicios, asignaciones y PDF, conserva métricas y
  repara perfiles faltantes para usuarios existentes con rol `Coach`.
 
## Hito 7 - Tablas V16

Migracion: `V16__hito7_pilot_readiness_foundations.sql`.

Tablas nuevas:

- `benefit`
- `client_benefit`
- `benefit_evidence`
- `visit_pass`
- `sale_refund`
- `booking_payment`
- `booking_policy`
- `auto_renewal_attempt`
- `notification_template`

Columnas nuevas:

- `membership.auto_renew`
- `membership.cancelled_at`
- `membership.cancellation_reason`
- `automation_rule.trigger_type`
- `automation_rule.schedule_cron`
- `automation_rule.next_run_at`
- `automation_rule.locked_until`

Restricciones e indices destacados:

- `ux_benefit_org_code`
- `ux_client_benefit_active`
- `ux_sale_refund_org_idempotency`
- `ux_booking_payment_booking`
- `ux_auto_renewal_attempt_idempotency`
- `ux_notification_template_org_code_channel`

Todas las tablas operativas incluyen `organization_id`; las tablas localizables incluyen `branch_id`.

## Hito 7.7 - Marca por tenant y administrador de plataforma

Migraciones:

- `V18__organization_branding.sql`
- `V19__platform_admin_legacy_role.sql`

Columnas agregadas a `organization`:

- `brand_name`: nombre visible de marca para el tenant.
- `logo_url`: URL opcional del logo del gimnasio.
- `primary_color`: color principal de interfaz en formato hexadecimal.
- `accent_color`: color de acento en formato hexadecimal.
- `surface_color`: color de superficie/fondo en formato hexadecimal.

Permisos:

- `platform.admin`: identifica al Administrador GymOS de plataforma. Permite administrar gimnasios/tenants y regresar a `/platform` desde cualquier pantalla.

Notas de integridad:

- Los colores deben validarse como `#RRGGBB` desde API/frontend.
- La organización conserva una marca corporativa de fallback. El objetivo
  `CFG-003` agrega una marca operativa por sucursal, resuelta desde el
  `branch_id` de la sesión u operación, no desde datos locales del navegador.
- El panel de plataforma no toma la marca del tenant; usa identidad neutral de GymFort para evitar confusion entre administracion SaaS y operacion del gimnasio.

### branch_brand_profile (objetivo CFG-003)

- organization_id
- branch_id (PK)
- brand_name
- logo_object_key
- primary_color
- accent_color
- surface_color
- reply_to_email
- updated_at
- updated_by
- version

Si un atributo de la sucursal no está configurado se usa el valor de
`organization`. Consola, kiosco, correo, recibos y PDFs deben recibir el mismo
perfil efectivo para evitar mezclar marcas del mismo tenant.

## Hito 7.8 - Separacion de roles plataforma/tenant

Migracion:

- `V20__platform_and_tenant_role_separation.sql`

Cambios:

- Revoca `platform.admin` del rol legacy `Administrador`/`Dueno del gimnasio`.
- Mantiene `platform.admin` solo para `Administrador GymOS`.
- Normaliza roles operativos:
  - `Administrador GymOS`: dueno/operador SaaS.
  - `Dueno del gimnasio`: cliente directo; administra su tenant.
  - `Gerente`: operacion diaria y administracion limitada.
  - `Recepcion`: mostrador por sucursal.
  - `Coach`: entrenamiento, rutinas y seguimiento.
- La cuenta local `admin@gymos.local` recibe rol `Administrador GymOS` desde seed local para bases nuevas.
- `Gerente` conserva operacion diaria, reportes, inventario, agenda, equipo autorizado y lectura de sucursal para contexto operativo, pero no puede actualizar datos del gimnasio ni crear/modificar sucursales.
- `Dueno del gimnasio` inicia con la primera sucursal activa permitida cuando existe. Puede cambiar el contexto operativo desde el selector de sucursal antes de crear recursos, socios u operaciones locales.

Notas:

- Los roles de tenant nunca deben otorgar `platform.admin`.
- El acceso de soporte no depende de `user_branch_access`; se controla por `platform.admin` y se marca en el token como `supportMode`.

## Extensión V72 - Entrega durable de correo

`V72__durable_email_delivery.sql` amplía `notification_log` con categoría,
destinatario tipado, cuerpo HTML opcional, proveedor, identificador externo,
intentos, disponibilidad y lease. Agrega:

- `notification_channel_configuration`: habilitación, proveedor y remitente por
  organización, sin credenciales;
- `notification_recipient_preference`: preferencias por tenant identificadas
  mediante HMAC-SHA-256 de la dirección;
- `ix_notification_delivery_queue`: selección de correo vencido sin recorrer el
  histórico;
- `notification.settings.manage`: permiso exclusivo para dueño, administración
  tenant y administración GymOS.

La cola conserva `organization_id`; tanto la selección justa como el claim y
las consultas de preferencia incluyen el tenant. Los secretos SMTP permanecen
fuera del modelo relacional.

## Hito 8 - Cobranza comercial de GymFort

Migraciones:

- `V81__platform_branch_billing.sql`
- `V82__platform_billing_payment_history.sql`
- `V85__platform_customer_management_and_billing_hardening.sql`

### platform_customer_account

Expediente interno de la relación de GymFort con cada `Organization` cliente.
Conserva etapa (`ONBOARDING`, `ACTIVE`, `AT_RISK`, `OFFBOARDING`, `FORMER`),
responsable, contacto de cobranza, inicio/renovación, plazo de pago, siguiente
seguimiento y notas. `version` implementa concurrencia optimista; las fechas y
datos no sustituyen los estados operativos del tenant ni su historial de cobros.

### platform_branch_billing

Configura la relación comercial de GymFort por sucursal contratada. Conserva
`branch_id` como clave primaria, `organization_id` para agregación por cliente,
estado de suscripción, día de corte, siguiente cobro, plan contratado, importe
Enterprise pactado, cargo de implementación, notas y la última mensualidad
registrada como dato de consulta rápida.

V85 agrega `version`: toda edición debe enviar la versión leída y la actualización
la incrementa de forma atómica. Los resúmenes de último pago no son editables por
este contrato; sólo cambian al registrar una mensualidad real.

Los planes estándar permitidos son `GYMFORT_150`, `GYMFORT_500`,
`GYMFORT_1000` y `GYMFORT_2000`; `ENTERPRISE` requiere precio mensual pactado.
El estado comercial se limita a `ACTIVE`, `PENDING`, `PAUSED` y `CANCELLED`.

### platform_billing_payment

Es el historial inmutable de los cobros que recibe GymFort, no los pagos que
procesa un gimnasio a sus propios socios. Guarda sucursal, organización, tipo
(`SUBSCRIPTION`, `IMPLEMENTATION`, `OTHER`), medio
(`TRANSFER`, `CASH`, `CARD`, `STRIPE`, `OTHER`), monto positivo, fecha efectiva,
referencia, nota, usuario que lo registró y creación.

V85 agrega `idempotency_key`, obligatorio y único por sucursal. Repetir una clave
con el mismo contenido devuelve el registro original; usarla con datos distintos
se rechaza. El backfill `legacy-{id}` mantiene compatible el historial V82.

Índices por sucursal/fecha y organización/fecha permiten consultar el
expediente comercial sin recorrer la operación tenant. Al registrar una
mensualidad se actualiza el resumen de `platform_branch_billing` y el siguiente
corte; al acumular pagos de implementación por el importe acordado, la
implementación queda liquidada. No hay borrado ni actualización operativa del
historial de pagos.

## Extensión V93 — Alta y ciclo de vida del socio

`V93__client_onboarding_lifecycle.sql` agrega `client.inactivated_at`,
`client.inactivation_reason`, la configuración por organización
`client_lifecycle_configuration` y el índice parcial
`ix_client_automatic_inactivation`. También incorpora la capacidad opt-in
`clientRegistration=false` a los perfiles de kiosco existentes.

La baja automática es lógica y reversible. Una inscripción cobrada se conserva
como partida `sale_item` de tipo `REGISTRATION_FEE`; no requiere convertir el
cargo en un producto físico ni provoca movimientos de inventario.

## Extensión V94 — Umbral canónico de inactividad

`V94__unify_client_inactivity_period.sql` conserva `inactivity_months` como el
único umbral temporal y elimina `registration_recharge_months`. La migración no
reescribe V93 ni modifica su checksum. En una base donde V93 ya fue aplicada se
conserva el valor existente de `inactivity_months`; desde V94 ese mismo valor
gobierna tanto la baja automática como el nuevo cobro de inscripción al volver.

## Extensiones V96–V100 — Planes, beneficios y paquetes de visitas

V96 separa la política comercial del plan de su cobertura: `membership_plan`
incorpora `self_service_acquisition_enabled` y `registration_fee_policy`; la
tabla `pricing_benefit_membership_plan` limita `SPECIAL_PRICE` a planes
explícitos. V97 agrega `benefit.access_scope` (`LOCAL` o `PLAN_COVERAGE`) sin
duplicar beneficios por sucursal.

V98 introduce `visit_package_plan` como definición vendible ligada a un
`catalog_product` `SERVICE/VISIT_PASS`, sus sucursales y accesos; cada compra se
materializa en `visit_package_entitlement` con snapshots de cobertura. El saldo
se descuenta en el ledger idempotente `visit_package_consumption`.

V99 materializa además `validity_days` en cada entitlement. Así, un Checkout
pendiente conserva la vigencia cotizada y su activación no depende de una
edición posterior de la definición comercial.

V100 agrega `visit_package_entitlement_benefit_branch`. Esta tabla materializa
el snapshot de la intersección entre las sucursales del paquete y el alcance
efectivo de cada beneficio `ACCESS`. No crea otro saldo: el consumo exige la
combinación exacta `entitlement + benefit + branch`. Un paquete A+B con acceso
General válido en ambas y Sauna `LOCAL` de A conserva saldo compartido, pero
Sauna sólo lo descuenta en A. La definición no acepta B si ningún acceso
seleccionado puede consumir allí.

Sólo puede existir un entitlement abierto (`PENDING_PAYMENT` o `ACTIVE`) por
`organization_id + client_id`. La restricción incluye pagos pendientes para que
dos Checkouts simultáneos no terminen activando dos paquetes. Una membresía
vigente y un paquete sí pueden coexistir; un acceso concedido por la membresía
no consume visitas.

La tabla histórica `visit_pass` permanece por compatibilidad y no se reinterpreta
por nombre. Su alta y consumo manuales rechazan nuevas escrituras; mientras
exista un pase histórico activo, la compra nueva deberá bloquearse y solicitar
regularización explícita. Arquitectura completa: documento 33.
