
ALTER TABLE access_log
    ADD COLUMN manual_request_id UUID,
    ADD COLUMN manual_business_date DATE,
    ADD COLUMN manual_benefit_id UUID,
    ADD COLUMN manual_duplicate_of UUID,
    ADD COLUMN manual_override_of UUID,
    ADD COLUMN manual_override_reason TEXT,
    ADD CONSTRAINT ck_access_log_manual_context CHECK (
        source <> 'MANUAL' OR (
            manual_request_id IS NOT NULL AND manual_business_date IS NOT NULL
            AND created_by IS NOT NULL AND client_id IS NOT NULL AND device_id IS NULL
            AND decision IN ('GRANTED', 'DENIED', 'DUPLICATE')
            AND (decision = 'DUPLICATE') = (manual_duplicate_of IS NOT NULL)
            AND (manual_override_of IS NULL) = (manual_override_reason IS NULL)
        )
    ),
    ADD CONSTRAINT uq_access_log_id_scope UNIQUE (id, organization_id, branch_id),
    -- Requested benefits can be stale or unknown on denied attempts.
    ADD CONSTRAINT fk_access_log_manual_duplicate FOREIGN KEY (manual_duplicate_of, organization_id, branch_id)
        REFERENCES access_log(id, organization_id, branch_id),
    ADD CONSTRAINT fk_access_log_manual_override FOREIGN KEY (manual_override_of, organization_id, branch_id)
        REFERENCES access_log(id, organization_id, branch_id);

CREATE UNIQUE INDEX ux_access_log_manual_request
    ON access_log (organization_id, manual_request_id)
    WHERE source = 'MANUAL';

CREATE INDEX ix_access_log_manual_history
    ON access_log (organization_id, branch_id, occurred_at DESC, id DESC)
    WHERE source = 'MANUAL';

CREATE INDEX ix_access_log_manual_recent_grant
    ON access_log (organization_id, branch_id, client_id, occurred_at DESC, id DESC)
    WHERE source = 'MANUAL' AND decision = 'GRANTED';

CREATE INDEX ix_visit_package_consumption_access_log
    ON visit_package_consumption (organization_id, access_log_id)
    WHERE access_log_id IS NOT NULL;

CREATE TABLE access_manual_reversal (
    access_log_id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    reason TEXT NOT NULL CHECK (length(btrim(reason)) BETWEEN 1 AND 300),
    remaining_before INTEGER,
    remaining_after INTEGER,
    CONSTRAINT fk_manual_reversal_log FOREIGN KEY (access_log_id, organization_id, branch_id)
        REFERENCES access_log(id, organization_id, branch_id),
    CONSTRAINT ck_manual_reversal_balance CHECK (
        (remaining_before IS NULL AND remaining_after IS NULL) OR
        (remaining_before IS NOT NULL AND remaining_after IS NOT NULL
         AND remaining_before >= 0 AND remaining_after = remaining_before + 1)
    )
);

INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES
    ('88888888-8888-8888-8888-888888888905', 'access.check-in', 'Registrar y consultar entradas en recepción', 'access', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888906', 'access.check-in.override', 'Confirmar una entrada distinta dentro de la ventana de duplicados', 'access', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888907', 'access.check-in.reverse', 'Revertir una entrada manual con motivo', 'access', now(), now(), 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('access.check-in', 'access.check-in.override', 'access.check-in.reverse')
WHERE r.deleted_at IS NULL AND r.name IN ('Dueno del gimnasio', 'Gerente')
UNION ALL
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code = 'access.check-in'
WHERE r.deleted_at IS NULL AND r.name = 'Recepcion'
ON CONFLICT DO NOTHING;
