UPDATE self_service_terminal
SET allowed_actions_json = jsonb_build_object(
        'membershipRenewal',
        COALESCE(
            allowed_actions_json -> 'membershipRenewal',
            allowed_actions_json -> 'renewal',
            allowed_actions_json -> 'renew',
            'true'::jsonb
        ),
        'membershipPlanChange',
        COALESCE(
            allowed_actions_json -> 'membershipPlanChange',
            allowed_actions_json -> 'planChange',
            allowed_actions_json -> 'changePlan',
            'true'::jsonb
        ),
        'resourceBooking',
        COALESCE(
            allowed_actions_json -> 'resourceBooking',
            allowed_actions_json -> 'booking',
            allowed_actions_json -> 'bookings',
            'true'::jsonb
        ),
        'wellnessBooking',
        COALESCE(
            allowed_actions_json -> 'wellnessBooking',
            allowed_actions_json -> 'wellness',
            'true'::jsonb
        ),
        'profileUpdate',
        COALESCE(
            allowed_actions_json -> 'profileUpdate',
            allowed_actions_json -> 'contactUpdate',
            'true'::jsonb
        ),
        'autopay',
        COALESCE(
            allowed_actions_json -> 'autopay',
            allowed_actions_json -> 'recurringPayment',
            'true'::jsonb
        )
    ),
    updated_at = now(),
    version = version + 1
WHERE deleted_at IS NULL;

ALTER TABLE self_service_terminal
    ALTER COLUMN allowed_actions_json SET DEFAULT
    '{"membershipRenewal":true,"membershipPlanChange":true,"resourceBooking":true,"wellnessBooking":true,"profileUpdate":true,"autopay":true}'::jsonb;
