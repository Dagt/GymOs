ALTER TABLE professional ADD COLUMN office_location VARCHAR(255);

CREATE TABLE wellness_appointment (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    professional_id UUID NOT NULL REFERENCES professional(id),
    service_id UUID REFERENCES professional_service(id),
    client_id UUID NOT NULL REFERENCES client(id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    notes TEXT,
    cancellation_reason TEXT,
    confirmed_by_user_id UUID,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX ix_wellness_appointment_prof_time
    ON wellness_appointment (organization_id, branch_id, professional_id, starts_at, ends_at);

CREATE INDEX ix_wellness_appointment_client
    ON wellness_appointment (organization_id, client_id, starts_at);

CREATE INDEX ix_wellness_appointment_status_time
    ON wellness_appointment (organization_id, branch_id, status, starts_at);
