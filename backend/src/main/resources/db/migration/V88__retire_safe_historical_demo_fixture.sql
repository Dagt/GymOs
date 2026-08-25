CREATE TEMPORARY TABLE gymos_demo_allowed_reference_v88 (
    table_name TEXT NOT NULL,
    row_id UUID NOT NULL,
    PRIMARY KEY (table_name, row_id)
) ON COMMIT DROP;

INSERT INTO gymos_demo_allowed_reference_v88 (table_name, row_id)
VALUES
    ('branch', '22222222-2222-2222-2222-222222222222'),
    ('catalog_product', '66666666-6666-6666-6666-666666666601'),
    ('catalog_product', '66666666-6666-6666-6666-666666666602'),
    ('catalog_product', '66666666-6666-6666-6666-666666666603'),
    ('membership_plan', '77777777-7777-7777-7777-777777777701'),
    ('membership_plan', '77777777-7777-7777-7777-777777777702'),
    ('self_service_terminal', '99999999-9999-9999-9999-999999999901'),
    ('access_device', '99999999-9999-9999-9999-999999999911'),
    ('inventory_stock', '99999999-9999-9999-9999-999999999921'),
    ('inventory_movement', '99999999-9999-9999-9999-999999999922'),
    ('resource', '99999999-9999-9999-9999-999999999941'),
    ('coach', '99999999-9999-9999-9999-999999999951'),
    ('workout', '99999999-9999-9999-9999-999999999952'),
    ('workout_version', '99999999-9999-9999-9999-999999999953'),
    ('professional', '99999999-9999-9999-9999-999999999961'),
    ('professional_service', '99999999-9999-9999-9999-999999999962'),
    ('professional_availability', '99999999-9999-9999-9999-999999999963'),
    ('automation_rule', '99999999-9999-9999-9999-999999999964');

DO $$
DECLARE
    demo_organization_id CONSTANT UUID := '11111111-1111-1111-1111-111111111111';
    demo_branch_id CONSTANT UUID := '22222222-2222-2222-2222-222222222222';
    referenced_id UUID;
    dependency RECORD;
    dependency_count BIGINT;
    unexpected_dependencies TEXT[] := ARRAY[]::TEXT[];
BEGIN
    IF NOT EXISTS (SELECT 1 FROM organization WHERE id = demo_organization_id)
       AND NOT EXISTS (SELECT 1 FROM branch WHERE id = demo_branch_id) THEN
        RAISE NOTICE 'V88: historical demo fixture is already absent';
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM organization
        WHERE id = demo_organization_id
          AND legal_name = 'GymOS Demo S.A. de C.V.'
          AND trade_name = 'GymOS Demo'
          AND tax_id = 'GYMOS-DEMO'
          AND data_origin = 'DEMO'
          AND timezone = 'America/Mexico_City'
          AND default_currency = 'MXN'
          AND contact_email = 'admin@gymos.local'
          AND contact_phone = '+520000000000'
    ) OR NOT EXISTS (
        SELECT 1
        FROM branch
        WHERE id = demo_branch_id
          AND organization_id = demo_organization_id
          AND name = 'Sucursal Principal'
          AND code = 'MAIN'
          AND timezone = 'America/Mexico_City'
          AND phone = '+520000000000'
    ) THEN
        RAISE WARNING 'V88 retained the historical demo fixture because its identity no longer matches the exact technical fixture';
        RETURN;
    END IF;

    FOR dependency IN
        SELECT DISTINCT
               source_namespace.nspname AS source_schema,
               source_table.relname AS source_table,
               source_column.attname AS source_column,
               target_table.relname AS target_table
        FROM pg_constraint foreign_key
        JOIN pg_class source_table ON source_table.oid = foreign_key.conrelid
        JOIN pg_namespace source_namespace ON source_namespace.oid = source_table.relnamespace
        JOIN pg_class target_table ON target_table.oid = foreign_key.confrelid
        JOIN LATERAL unnest(foreign_key.conkey) WITH ORDINALITY source_key(attnum, position) ON TRUE
        JOIN LATERAL unnest(foreign_key.confkey) WITH ORDINALITY target_key(attnum, position)
          ON target_key.position = source_key.position
        JOIN pg_attribute source_column
          ON source_column.attrelid = source_table.oid
         AND source_column.attnum = source_key.attnum
        JOIN pg_attribute target_column
          ON target_column.attrelid = target_table.oid
         AND target_column.attnum = target_key.attnum
        WHERE foreign_key.contype = 'f'
          AND source_namespace.nspname = current_schema()
          AND target_table.oid IN ('organization'::regclass, 'branch'::regclass)
          AND target_column.attname = 'id'
    LOOP
        referenced_id := CASE dependency.target_table
            WHEN 'organization' THEN demo_organization_id
            ELSE demo_branch_id
        END;

        IF dependency.source_table = 'catalog_product_branch' THEN
            EXECUTE format(
                'SELECT count(*) FROM %I.catalog_product_branch referenced '
                'WHERE referenced.%I = $1 '
                'AND NOT ('
                '  referenced.organization_id = $2 '
                '  AND referenced.branch_id = $3 '
                '  AND referenced.product_id IN ($4, $5, $6)'
                ')',
                dependency.source_schema,
                dependency.source_column
            ) INTO dependency_count USING
                referenced_id,
                demo_organization_id,
                demo_branch_id,
                '66666666-6666-6666-6666-666666666601'::UUID,
                '66666666-6666-6666-6666-666666666602'::UUID,
                '66666666-6666-6666-6666-666666666603'::UUID;
        ELSIF dependency.source_table = 'organization_payment_configuration' THEN
            EXECUTE format(
                'SELECT count(*) FROM %I.organization_payment_configuration referenced '
                'WHERE referenced.%I = $1 '
                'AND NOT ('
                '  referenced.organization_id = $2 '
                '  AND referenced.reception_manual_terminal_enabled '
                '  AND NOT referenced.reception_mercado_pago_enabled '
                '  AND NOT referenced.stripe_self_checkout_enabled '
                '  AND NOT referenced.stripe_recurring_enabled '
                '  AND referenced.mercado_pago_account_id IS NULL '
                '  AND referenced.mercado_pago_terminal_id IS NULL '
                '  AND referenced.stripe_account_id IS NULL '
                '  AND referenced.version = 0'
                ')',
                dependency.source_schema,
                dependency.source_column
            ) INTO dependency_count USING referenced_id, demo_organization_id;
        ELSIF EXISTS (
            SELECT 1
            FROM gymos_demo_allowed_reference_v88 allowed
            WHERE allowed.table_name = dependency.source_table
        ) THEN
            EXECUTE format(
                'SELECT count(*) FROM %I.%I referenced '
                'WHERE referenced.%I = $1 '
                'AND NOT EXISTS ('
                '  SELECT 1 FROM pg_temp.gymos_demo_allowed_reference_v88 allowed '
                '  WHERE allowed.table_name = $2 AND allowed.row_id = referenced.id'
                ')',
                dependency.source_schema,
                dependency.source_table,
                dependency.source_column
            ) INTO dependency_count USING referenced_id, dependency.source_table;
        ELSE
            EXECUTE format(
                'SELECT count(*) FROM %I.%I WHERE %I = $1',
                dependency.source_schema,
                dependency.source_table,
                dependency.source_column
            ) INTO dependency_count USING referenced_id;
        END IF;

        IF dependency_count > 0 THEN
            unexpected_dependencies := array_append(
                unexpected_dependencies,
                format('%I.%I=%s (%s rows)', dependency.source_table, dependency.source_column,
                       dependency.target_table, dependency_count)
            );
        END IF;
    END LOOP;

    IF cardinality(unexpected_dependencies) > 0 THEN
        RAISE WARNING 'V88 retained the historical demo fixture because it has non-technical dependencies: %',
            array_to_string(unexpected_dependencies, ', ');
        RETURN;
    END IF;

    BEGIN
        DELETE FROM catalog_product_branch
        WHERE organization_id = demo_organization_id
          AND branch_id = demo_branch_id
          AND product_id IN (
              '66666666-6666-6666-6666-666666666601',
              '66666666-6666-6666-6666-666666666602',
              '66666666-6666-6666-6666-666666666603'
          );

        DELETE FROM organization_payment_configuration
        WHERE organization_id = demo_organization_id
          AND reception_manual_terminal_enabled
          AND NOT reception_mercado_pago_enabled
          AND NOT stripe_self_checkout_enabled
          AND NOT stripe_recurring_enabled
          AND mercado_pago_account_id IS NULL
          AND mercado_pago_terminal_id IS NULL
          AND stripe_account_id IS NULL
          AND version = 0;

        DELETE FROM automation_rule
        WHERE id = '99999999-9999-9999-9999-999999999964'
          AND organization_id = demo_organization_id
          AND branch_id = demo_branch_id
          AND name = 'Recordatorio de membresia por vencer';

        DELETE FROM professional_availability
        WHERE id = '99999999-9999-9999-9999-999999999963'
          AND organization_id = demo_organization_id
          AND branch_id = demo_branch_id
          AND professional_id = '99999999-9999-9999-9999-999999999961';

        DELETE FROM professional_service
        WHERE id = '99999999-9999-9999-9999-999999999962'
          AND organization_id = demo_organization_id
          AND branch_id = demo_branch_id
          AND professional_id = '99999999-9999-9999-9999-999999999961'
          AND name = 'Consulta nutricional inicial';

        DELETE FROM professional
        WHERE id = '99999999-9999-9999-9999-999999999961'
          AND organization_id = demo_organization_id
          AND branch_id = demo_branch_id
          AND display_name = 'Nutriologa Demo';

        DELETE FROM workout_version
        WHERE id = '99999999-9999-9999-9999-999999999953'
          AND organization_id = demo_organization_id
          AND workout_id = '99999999-9999-9999-9999-999999999952'
          AND version_number = 1;

        DELETE FROM workout
        WHERE id = '99999999-9999-9999-9999-999999999952'
          AND organization_id = demo_organization_id
          AND branch_id = demo_branch_id
          AND coach_id = '99999999-9999-9999-9999-999999999951'
          AND title = 'Fuerza base 4 semanas';

        DELETE FROM coach
        WHERE id = '99999999-9999-9999-9999-999999999951'
          AND organization_id = demo_organization_id
          AND branch_id = demo_branch_id
          AND display_name = 'Coach Demo';

        DELETE FROM resource
        WHERE id = '99999999-9999-9999-9999-999999999941'
          AND organization_id = demo_organization_id
          AND branch_id = demo_branch_id
          AND name = 'Consultorio evaluacion';

        DELETE FROM inventory_movement
        WHERE id = '99999999-9999-9999-9999-999999999922'
          AND organization_id = demo_organization_id
          AND branch_id = demo_branch_id
          AND stock_id = '99999999-9999-9999-9999-999999999921'
          AND reference_type = 'SEED';

        DELETE FROM inventory_stock
        WHERE id = '99999999-9999-9999-9999-999999999921'
          AND organization_id = demo_organization_id
          AND branch_id = demo_branch_id
          AND product_id = '66666666-6666-6666-6666-666666666602';

        DELETE FROM access_device
        WHERE id = '99999999-9999-9999-9999-999999999911'
          AND organization_id = demo_organization_id
          AND branch_id = demo_branch_id
          AND device_code = 'MAIN-DOOR'
          AND provider = 'SIMULATED';

        DELETE FROM self_service_terminal
        WHERE id = '99999999-9999-9999-9999-999999999901'
          AND organization_id = demo_organization_id
          AND branch_id = demo_branch_id
          AND device_code = 'KIOSK-MAIN';

        DELETE FROM membership_plan
        WHERE id = '77777777-7777-7777-7777-777777777701'
          AND organization_id = demo_organization_id
          AND product_id = '66666666-6666-6666-6666-666666666601'
          AND duration_days = 30;

        DELETE FROM membership_plan
        WHERE id = '77777777-7777-7777-7777-777777777702'
          AND organization_id = demo_organization_id
          AND product_id = '66666666-6666-6666-6666-666666666603'
          AND duration_days = 90;

        DELETE FROM catalog_product
        WHERE id = '66666666-6666-6666-6666-666666666601'
          AND organization_id = demo_organization_id
          AND sku = 'PLAN-MENSUAL';

        DELETE FROM catalog_product
        WHERE id = '66666666-6666-6666-6666-666666666602'
          AND organization_id = demo_organization_id
          AND sku = 'BOTELLA-AGUA';

        DELETE FROM catalog_product
        WHERE id = '66666666-6666-6666-6666-666666666603'
          AND organization_id = demo_organization_id
          AND sku = 'PLAN-TRIMESTRAL';

        DELETE FROM branch
        WHERE id = demo_branch_id
          AND organization_id = demo_organization_id
          AND name = 'Sucursal Principal'
          AND code = 'MAIN';

        DELETE FROM organization
        WHERE id = demo_organization_id
          AND legal_name = 'GymOS Demo S.A. de C.V.'
          AND trade_name = 'GymOS Demo'
          AND tax_id = 'GYMOS-DEMO'
          AND data_origin = 'DEMO';

        IF EXISTS (SELECT 1 FROM organization WHERE id = demo_organization_id)
           OR EXISTS (SELECT 1 FROM branch WHERE id = demo_branch_id) THEN
            RAISE foreign_key_violation USING
                MESSAGE = 'exact demo fixture could not be retired completely';
        END IF;

        RAISE NOTICE 'V88 removed the exact empty historical demo fixture';
    EXCEPTION
        WHEN foreign_key_violation THEN
            RAISE WARNING 'V88 retained the historical demo fixture because a protected dependency was detected during deletion';
    END;
END $$;

DROP TABLE gymos_demo_allowed_reference_v88;
