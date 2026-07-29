ALTER TABLE coach ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;

CREATE TABLE exercise (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organization(id),
    name VARCHAR(180) NOT NULL,
    normalized_name VARCHAR(180) NOT NULL,
    description TEXT,
    instructions TEXT,
    primary_muscle_group VARCHAR(80),
    secondary_muscle_groups VARCHAR(400),
    movement_pattern VARCHAR(80),
    exercise_type VARCHAR(40) NOT NULL,
    equipment VARCHAR(120),
    suggested_level VARCHAR(40),
    laterality VARCHAR(20),
    media_url VARCHAR(1000),
    warnings TEXT,
    origin VARCHAR(32) NOT NULL,
    created_by UUID,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    archived_at TIMESTAMPTZ,
    CONSTRAINT ck_exercise_origin CHECK (origin IN ('SYSTEM', 'ORGANIZATION', 'COACH')),
    CONSTRAINT ck_exercise_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE UNIQUE INDEX ux_exercise_system_name
    ON exercise (normalized_name) WHERE organization_id IS NULL AND status = 'ACTIVE';
CREATE UNIQUE INDEX ux_exercise_org_name
    ON exercise (organization_id, normalized_name) WHERE organization_id IS NOT NULL AND status = 'ACTIVE';
CREATE INDEX ix_exercise_search
    ON exercise (organization_id, exercise_type, primary_muscle_group, equipment, normalized_name);

CREATE TABLE exercise_alias (
    id UUID PRIMARY KEY,
    exercise_id UUID NOT NULL REFERENCES exercise(id),
    alias VARCHAR(180) NOT NULL,
    normalized_alias VARCHAR(180) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (exercise_id, normalized_alias)
);
CREATE INDEX ix_exercise_alias_search ON exercise_alias (normalized_alias);

CREATE TABLE workout_session (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    workout_version_id UUID NOT NULL REFERENCES workout_version(id) ON DELETE CASCADE,
    name VARCHAR(180) NOT NULL,
    focus VARCHAR(180),
    notes TEXT,
    position INTEGER NOT NULL,
    UNIQUE (workout_version_id, position)
);

CREATE TABLE workout_session_block (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    workout_session_id UUID NOT NULL REFERENCES workout_session(id) ON DELETE CASCADE,
    name VARCHAR(180) NOT NULL,
    block_type VARCHAR(40) NOT NULL,
    notes TEXT,
    position INTEGER NOT NULL,
    UNIQUE (workout_session_id, position)
);

CREATE TABLE workout_exercise (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id),
    workout_session_block_id UUID NOT NULL REFERENCES workout_session_block(id) ON DELETE CASCADE,
    exercise_id UUID REFERENCES exercise(id),
    exercise_name_snapshot VARCHAR(180) NOT NULL,
    prescription_text VARCHAR(500),
    sets INTEGER,
    reps VARCHAR(120),
    load_text VARCHAR(160),
    rest_seconds INTEGER,
    tempo VARCHAR(80),
    duration_text VARCHAR(120),
    distance_text VARCHAR(120),
    notes TEXT,
    group_key VARCHAR(80),
    optional BOOLEAN NOT NULL DEFAULT FALSE,
    position INTEGER NOT NULL,
    UNIQUE (workout_session_block_id, position)
);

ALTER TABLE workout ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE workout ADD COLUMN IF NOT EXISTS duration_weeks INTEGER;
ALTER TABLE workout ADD COLUMN IF NOT EXISTS owner_user_id UUID REFERENCES user_account(id);

ALTER TABLE workout_assignment ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(160);
ALTER TABLE workout_assignment ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);
ALTER TABLE workout_assignment ADD COLUMN IF NOT EXISTS printable_notes TEXT;
ALTER TABLE workout_assignment ADD COLUMN IF NOT EXISTS replaced_assignment_id UUID REFERENCES workout_assignment(id);
ALTER TABLE workout_assignment ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;
ALTER TABLE workout_assignment ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;
CREATE UNIQUE INDEX ux_workout_assignment_idempotency
    ON workout_assignment (organization_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX ix_workout_assignment_metrics
    ON workout_assignment (organization_id, branch_id, assigned_on, coach_id, client_id, status)
    WHERE deleted_at IS NULL;

INSERT INTO permission (id, code, description, module, created_at, updated_at, version)
VALUES
    (gen_random_uuid(), 'exercise.read', 'Consultar catálogo de ejercicios', 'coaching', now(), now(), 0),
    (gen_random_uuid(), 'exercise.create', 'Crear ejercicios', 'coaching', now(), now(), 0),
    (gen_random_uuid(), 'exercise.manage', 'Administrar catálogo de ejercicios', 'coaching', now(), now(), 0),
    (gen_random_uuid(), 'workout.assign', 'Asignar rutinas', 'coaching', now(), now(), 0),
    (gen_random_uuid(), 'workout.pdf.download', 'Descargar rutina en PDF', 'coaching', now(), now(), 0),
    (gen_random_uuid(), 'coach.metrics.read', 'Consultar métricas de coaches', 'coaching', now(), now(), 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'exercise.read', 'exercise.create', 'exercise.manage', 'workout.assign',
    'workout.pdf.download', 'coach.metrics.read'
)
WHERE r.name IN ('Administrador', 'Administrador GymOS', 'Dueno del gimnasio', 'Gerente')
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON p.code IN (
    'exercise.read', 'exercise.create', 'workout.assign', 'workout.pdf.download'
)
WHERE r.name = 'Coach'
ON CONFLICT DO NOTHING;

WITH seed(name, muscle, pattern, type, equipment) AS (
    VALUES
    ('Sentadilla libre','Cuádriceps','Sentadilla','STRENGTH','Barra'),
    ('Sentadilla goblet','Cuádriceps','Sentadilla','STRENGTH','Mancuerna'),
    ('Sentadilla sissy','Cuádriceps','Sentadilla','STRENGTH','Peso corporal'),
    ('Prensa articulada','Cuádriceps','Empuje de pierna','STRENGTH','Máquina'),
    ('Extensión de cuádriceps','Cuádriceps','Extensión de rodilla','STRENGTH','Máquina'),
    ('Desplantes cortos','Cuádriceps','Zancada','STRENGTH','Peso corporal'),
    ('Desplantes caminando','Glúteos','Zancada','STRENGTH','Mancuernas'),
    ('Búlgaras','Glúteos','Zancada','STRENGTH','Mancuernas'),
    ('Hip thrust','Glúteos','Extensión de cadera','STRENGTH','Barra'),
    ('Puente de glúteo','Glúteos','Extensión de cadera','STRENGTH','Peso corporal'),
    ('Peso muerto rumano','Femoral','Bisagra de cadera','STRENGTH','Barra'),
    ('Peso muerto convencional','Espalda','Bisagra de cadera','STRENGTH','Barra'),
    ('Femoral tumbado','Femoral','Flexión de rodilla','STRENGTH','Máquina'),
    ('Femoral sentado','Femoral','Flexión de rodilla','STRENGTH','Máquina'),
    ('Aductores en máquina','Aductores','Aducción de cadera','STRENGTH','Máquina'),
    ('Abductores en máquina','Glúteos','Abducción de cadera','STRENGTH','Máquina'),
    ('Elevación de pantorrilla','Pantorrilla','Flexión plantar','STRENGTH','Máquina'),
    ('Elevación de sóleo','Sóleo','Flexión plantar','STRENGTH','Máquina'),
    ('Press banca','Pecho','Empuje horizontal','STRENGTH','Barra'),
    ('Press inclinado','Pecho','Empuje horizontal','STRENGTH','Barra'),
    ('Chest press','Pecho','Empuje horizontal','STRENGTH','Máquina'),
    ('Press con mancuernas','Pecho','Empuje horizontal','STRENGTH','Mancuernas'),
    ('Cross over en polea','Pecho','Aducción horizontal','STRENGTH','Polea'),
    ('Aperturas con mancuernas','Pecho','Aducción horizontal','STRENGTH','Mancuernas'),
    ('Flexiones','Pecho','Empuje horizontal','STRENGTH','Peso corporal'),
    ('Press militar con mancuerna','Hombro','Empuje vertical','STRENGTH','Mancuernas'),
    ('Press militar con barra','Hombro','Empuje vertical','STRENGTH','Barra'),
    ('Laterales con mancuerna','Hombro','Abducción de hombro','STRENGTH','Mancuernas'),
    ('Laterales en polea','Hombro','Abducción de hombro','STRENGTH','Polea'),
    ('Frontales con disco','Hombro','Flexión de hombro','STRENGTH','Disco'),
    ('Posteriores en polea','Hombro','Abducción horizontal','STRENGTH','Polea'),
    ('Face pull en polea alta','Hombro','Jalón horizontal','STRENGTH','Polea'),
    ('Jalón supino','Espalda','Jalón vertical','STRENGTH','Polea'),
    ('Jalón al pecho','Espalda','Jalón vertical','STRENGTH','Polea'),
    ('Dominadas','Espalda','Jalón vertical','STRENGTH','Peso corporal'),
    ('Remo con barra','Espalda','Jalón horizontal','STRENGTH','Barra'),
    ('Remo con mancuerna','Espalda','Jalón horizontal','STRENGTH','Mancuerna'),
    ('Remo sentado en polea','Espalda','Jalón horizontal','STRENGTH','Polea'),
    ('Pullover en polea','Espalda','Extensión de hombro','STRENGTH','Polea'),
    ('Curl con barra','Bíceps','Flexión de codo','STRENGTH','Barra'),
    ('Curl con mancuerna','Bíceps','Flexión de codo','STRENGTH','Mancuernas'),
    ('Curl banco inclinado','Bíceps','Flexión de codo','STRENGTH','Mancuernas'),
    ('Curl banco Scott','Bíceps','Flexión de codo','STRENGTH','Banco Scott'),
    ('Curl martillo','Bíceps','Flexión de codo','STRENGTH','Mancuernas'),
    ('Curl 21','Bíceps','Flexión de codo','STRENGTH','Barra'),
    ('Press francés','Tríceps','Extensión de codo','STRENGTH','Barra'),
    ('Extensión de tríceps en polea','Tríceps','Extensión de codo','STRENGTH','Polea'),
    ('Fondos en paralelas','Tríceps','Empuje vertical','STRENGTH','Peso corporal'),
    ('Plancha frontal','Core','Anti-extensión','CORE','Peso corporal'),
    ('Plancha lateral','Core','Anti-flexión lateral','CORE','Peso corporal'),
    ('Dead bug','Core','Estabilidad','CORE','Peso corporal'),
    ('Bird dog','Core','Estabilidad','CORE','Peso corporal'),
    ('Crunch en polea','Core','Flexión de tronco','CORE','Polea'),
    ('Elevación de piernas','Core','Flexión de cadera','CORE','Peso corporal'),
    ('Caminata del granjero','Antebrazo','Acarreo','STRENGTH','Mancuernas'),
    ('Caminadora','Cardio','Locomoción','CARDIO','Caminadora'),
    ('Bicicleta estática','Cardio','Locomoción','CARDIO','Bicicleta'),
    ('Remo ergómetro','Cardio','Jalón','CARDIO','Remo'),
    ('Elíptica','Cardio','Locomoción','CARDIO','Elíptica'),
    ('Jumping jacks','Cardio','Locomoción','WARMUP','Peso corporal'),
    ('Movilidad de cadera','Cadera','Movilidad','MOBILITY','Peso corporal'),
    ('Movilidad de hombro','Hombro','Movilidad','MOBILITY','Banda'),
    ('Movilidad torácica','Espalda','Movilidad','MOBILITY','Peso corporal'),
    ('Estiramiento de cuádriceps','Cuádriceps','Estiramiento','STRETCHING','Peso corporal'),
    ('Estiramiento de femoral','Femoral','Estiramiento','STRETCHING','Peso corporal'),
    ('Estiramiento de glúteo','Glúteos','Estiramiento','STRETCHING','Peso corporal'),
    ('Estiramiento de pecho','Pecho','Estiramiento','STRETCHING','Peso corporal'),
    ('Estiramiento de dorsal','Espalda','Estiramiento','STRETCHING','Peso corporal')
)
INSERT INTO exercise (
    id, organization_id, name, normalized_name, primary_muscle_group,
    movement_pattern, exercise_type, equipment, origin, status
)
SELECT gen_random_uuid(), NULL, name,
       translate(lower(name), 'áéíóúüñ', 'aeiouun'),
       muscle, pattern, type, equipment, 'SYSTEM', 'ACTIVE'
FROM seed
ON CONFLICT DO NOTHING;
