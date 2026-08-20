UPDATE self_service_terminal
SET allowed_actions_json = allowed_actions_json
        || jsonb_build_object('membershipEnrollment', true),
    updated_at = now(),
    version = version + 1
WHERE deleted_at IS NULL;

ALTER TABLE self_service_terminal
    ALTER COLUMN allowed_actions_json SET DEFAULT
    '{"membershipEnrollment":true,"membershipRenewal":true,"membershipPlanChange":true,"resourceBooking":true,"wellnessBooking":false,"profileUpdate":true,"autopay":false}'::jsonb;
