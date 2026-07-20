ALTER TABLE branch
    ADD COLUMN IF NOT EXISTS brand_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS logo_url VARCHAR(512),
    ADD COLUMN IF NOT EXISTS primary_color VARCHAR(16),
    ADD COLUMN IF NOT EXISTS accent_color VARCHAR(16),
    ADD COLUMN IF NOT EXISTS surface_color VARCHAR(16);

UPDATE branch b
SET brand_name = COALESCE(b.brand_name, b.name)
WHERE b.brand_name IS NULL;
