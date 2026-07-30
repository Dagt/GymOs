CREATE UNIQUE INDEX ux_access_device_id_organization
    ON access_device (id, organization_id);

CREATE TABLE access_device_benefit (
    access_device_id UUID NOT NULL,
    benefit_id UUID NOT NULL,
    organization_id UUID NOT NULL REFERENCES organization(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES user_account(id),
    PRIMARY KEY (access_device_id, benefit_id),
    CONSTRAINT fk_access_device_benefit_device
        FOREIGN KEY (access_device_id, organization_id)
        REFERENCES access_device(id, organization_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_access_device_benefit_benefit
        FOREIGN KEY (benefit_id, organization_id)
        REFERENCES benefit(id, organization_id)
        ON DELETE CASCADE
);

CREATE INDEX ix_access_device_benefit_org_benefit
    ON access_device_benefit (organization_id, benefit_id);
