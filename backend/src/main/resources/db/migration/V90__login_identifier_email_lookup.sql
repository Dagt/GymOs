CREATE INDEX ix_person_login_email_active
    ON person (LOWER(email))
    WHERE email IS NOT NULL
      AND deleted_at IS NULL;
