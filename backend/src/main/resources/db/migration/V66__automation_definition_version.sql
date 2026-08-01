ALTER TABLE automation_rule
    ADD COLUMN definition_version BIGINT NOT NULL DEFAULT 1;

CREATE INDEX ix_automation_rule_definition_version
    ON automation_rule (id, definition_version);
