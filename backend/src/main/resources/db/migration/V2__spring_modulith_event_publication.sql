CREATE TABLE event_publication (
    id UUID PRIMARY KEY,
    publication_date TIMESTAMPTZ NOT NULL,
    listener_id VARCHAR(512) NOT NULL,
    serialized_event TEXT NOT NULL,
    event_type VARCHAR(512) NOT NULL,
    completion_date TIMESTAMPTZ,
    completion_attempts INTEGER NOT NULL DEFAULT 0,
    last_resubmission_date TIMESTAMPTZ,
    status VARCHAR(64) NOT NULL
);

CREATE INDEX ix_event_publication_incomplete
    ON event_publication (completion_date, publication_date)
    WHERE completion_date IS NULL;

CREATE INDEX ix_event_publication_listener
    ON event_publication (listener_id);
