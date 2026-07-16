CREATE TABLE resource (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    name VARCHAR(140) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    capacity INTEGER NOT NULL DEFAULT 1,
    slot_minutes INTEGER NOT NULL DEFAULT 60,
    status VARCHAR(32) NOT NULL,
    opens_at TIME NOT NULL DEFAULT '06:00',
    closes_at TIME NOT NULL DEFAULT '22:00',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_resource_org_branch_name
    ON resource (organization_id, branch_id, lower(name))
    WHERE deleted_at IS NULL;
CREATE INDEX ix_resource_org_branch_status
    ON resource (organization_id, branch_id, status);

CREATE TABLE booking (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    resource_id UUID NOT NULL REFERENCES resource(id),
    client_id UUID NOT NULL REFERENCES client(id),
    status VARCHAR(32) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX ix_booking_resource_time
    ON booking (organization_id, branch_id, resource_id, starts_at, ends_at);
CREATE INDEX ix_booking_client_time
    ON booking (organization_id, client_id, starts_at);
CREATE INDEX ix_booking_status_time
    ON booking (organization_id, branch_id, status, starts_at);

INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES
    ('88888888-8888-8888-8888-888888888941', 'resource.read', 'Permiso resource.read', 'bookings', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888942', 'resource.manage', 'Permiso resource.manage', 'bookings', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888943', 'booking.read', 'Permiso booking.read', 'bookings', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888944', 'booking.manage', 'Permiso booking.manage', 'bookings', now(), now(), 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('resource.read', 'resource.manage', 'booking.read', 'booking.manage')
WHERE r.name = 'Administrador'
ON CONFLICT DO NOTHING;

INSERT INTO resource (
    id, organization_id, branch_id, name, resource_type, capacity, slot_minutes,
    status, opens_at, closes_at, notes, created_at, updated_at, version
)
VALUES (
    '99999999-9999-9999-9999-999999999941',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'Consultorio evaluacion',
    'ROOM',
    1,
    60,
    'ACTIVE',
    '08:00',
    '20:00',
    'Recurso demo para reservas de evaluacion.',
    now(),
    now(),
    0
)
ON CONFLICT DO NOTHING;
