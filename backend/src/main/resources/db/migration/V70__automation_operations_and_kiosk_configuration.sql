CREATE TABLE automation_scheduler_control (
    organization_id UUID PRIMARY KEY REFERENCES organization(id),
    paused BOOLEAN NOT NULL DEFAULT FALSE,
    reason VARCHAR(500),
    paused_at TIMESTAMPTZ,
    paused_by UUID REFERENCES user_account(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_automation_trigger_fair_scheduler
    ON automation_trigger_event (organization_id, available_at, created_at)
    WHERE status IN ('PENDING', 'RETRY_SCHEDULED', 'PROCESSING');

CREATE INDEX ix_automation_rule_fair_scheduler
    ON automation_rule (organization_id, next_run_at, id)
    WHERE status = 'ENABLED' AND trigger_type = 'SCHEDULED' AND deleted_at IS NULL;

INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES (
    '8a000000-0000-0000-0000-000000000070',
    'automation.scheduler.manage',
    'Pausar y reanudar el procesamiento de automatizaciones del gimnasio',
    'automations', now(), now(), 0
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES (
    '8a000000-0000-0000-0000-000000000071',
    'selfservice.settings.manage',
    'Configurar las capacidades del kiosco por sucursal',
    'selfservice', now(), now(), 0
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('automation.scheduler.manage', 'selfservice.settings.manage')
WHERE r.name IN ('Administrador', 'Administrador GymOS', 'Dueno del gimnasio')
ON CONFLICT DO NOTHING;

-- Paid wellness and paid resource checkout are deliberately outside the kiosk scope.
UPDATE self_service_terminal
SET allowed_actions_json = allowed_actions_json
        || jsonb_build_object('wellnessBooking', false),
    updated_at = now(),
    version = version + 1
WHERE deleted_at IS NULL;
