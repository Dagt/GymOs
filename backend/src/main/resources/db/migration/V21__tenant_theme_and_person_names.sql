ALTER TABLE person
    ADD COLUMN IF NOT EXISTS maternal_last_name VARCHAR(120);

UPDATE person
SET maternal_last_name = ''
WHERE maternal_last_name IS NULL;
