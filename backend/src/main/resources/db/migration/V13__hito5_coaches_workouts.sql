CREATE TABLE coach (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    display_name VARCHAR(160) NOT NULL,
    email VARCHAR(180),
    phone VARCHAR(32),
    specialties_json JSONB,
    bio TEXT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_coach_org_branch_email
    ON coach (organization_id, branch_id, lower(email))
    WHERE email IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX ix_coach_org_branch_status
    ON coach (organization_id, branch_id, status);

CREATE TABLE workout (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    coach_id UUID REFERENCES coach(id),
    title VARCHAR(180) NOT NULL,
    goal VARCHAR(160),
    level VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX ix_workout_org_branch_status
    ON workout (organization_id, branch_id, status);
CREATE INDEX ix_workout_coach
    ON workout (coach_id);

CREATE TABLE workout_version (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    workout_id UUID NOT NULL REFERENCES workout(id),
    version_number INTEGER NOT NULL,
    summary TEXT,
    content_json JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX ux_workout_version_number
    ON workout_version (workout_id, version_number)
    WHERE deleted_at IS NULL;

CREATE TABLE workout_assignment (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    branch_id UUID NOT NULL REFERENCES branch(id),
    workout_id UUID NOT NULL REFERENCES workout(id),
    workout_version_id UUID NOT NULL REFERENCES workout_version(id),
    client_id UUID NOT NULL REFERENCES client(id),
    coach_id UUID REFERENCES coach(id),
    status VARCHAR(32) NOT NULL,
    assigned_on DATE NOT NULL,
    starts_on DATE,
    ends_on DATE,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX ix_workout_assignment_client
    ON workout_assignment (organization_id, client_id, status, assigned_on);
CREATE INDEX ix_workout_assignment_coach
    ON workout_assignment (organization_id, branch_id, coach_id, assigned_on);

INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES
    ('88888888-8888-8888-8888-888888888951', 'coach.read', 'Permiso coach.read', 'coaching', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888952', 'coach.manage', 'Permiso coach.manage', 'coaching', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888953', 'workout.read', 'Permiso workout.read', 'coaching', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888954', 'workout.manage', 'Permiso workout.manage', 'coaching', now(), now(), 0),
    ('88888888-8888-8888-8888-888888888955', 'report.coaches.read', 'Permiso report.coaches.read', 'coaching', now(), now(), 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN ('coach.read', 'coach.manage', 'workout.read', 'workout.manage', 'report.coaches.read')
WHERE r.name = 'Administrador'
ON CONFLICT DO NOTHING;

INSERT INTO coach (
    id, organization_id, branch_id, display_name, email, phone, specialties_json,
    bio, status, created_at, updated_at, version
)
VALUES (
    '99999999-9999-9999-9999-999999999951',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    'Coach Demo',
    'coach@gymos.local',
    '+520000000010',
    '["fuerza","hipertrofia","principiantes"]'::jsonb,
    'Coach demo para planes de entrenamiento iniciales.',
    'ACTIVE',
    now(),
    now(),
    0
)
ON CONFLICT DO NOTHING;

INSERT INTO workout (
    id, organization_id, branch_id, coach_id, title, goal, level,
    status, created_at, updated_at, version
)
VALUES (
    '99999999-9999-9999-9999-999999999952',
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '99999999-9999-9999-9999-999999999951',
    'Fuerza base 4 semanas',
    'Fuerza general',
    'BEGINNER',
    'ACTIVE',
    now(),
    now(),
    0
)
ON CONFLICT DO NOTHING;

INSERT INTO workout_version (
    id, organization_id, workout_id, version_number, summary, content_json,
    status, created_at
)
VALUES (
    '99999999-9999-9999-9999-999999999953',
    '11111111-1111-1111-1111-111111111111',
    '99999999-9999-9999-9999-999999999952',
    1,
    'Rutina demo de cuerpo completo tres dias por semana.',
    '{"days":[{"name":"Dia A","blocks":[{"exercise":"Sentadilla goblet","sets":3,"reps":"10"},{"exercise":"Press banca","sets":3,"reps":"8"},{"exercise":"Remo con mancuerna","sets":3,"reps":"10"}]}]}'::jsonb,
    'PUBLISHED',
    now()
)
ON CONFLICT DO NOTHING;
