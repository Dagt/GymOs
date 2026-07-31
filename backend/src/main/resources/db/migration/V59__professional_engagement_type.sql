ALTER TABLE professional
    ADD COLUMN engagement_type VARCHAR(16);

UPDATE professional p
SET engagement_type = CASE
    WHEN EXISTS (
        SELECT 1
        FROM professional_availability pa
        WHERE pa.professional_id = p.id
          AND pa.status = 'ACTIVE'
          AND pa.deleted_at IS NULL
    ) THEN 'STAFF'
    ELSE 'EXTERNAL'
END;

ALTER TABLE professional
    ALTER COLUMN engagement_type SET DEFAULT 'STAFF',
    ALTER COLUMN engagement_type SET NOT NULL,
    ADD CONSTRAINT ck_professional_engagement_type
        CHECK (engagement_type IN ('STAFF', 'EXTERNAL'));
