-- Two pilot builds reached test installations before their migrations were
-- finalized. Recognize only those exact predecessor checksums; this is not a
-- general Flyway repair. The canonical V84 plus idempotent V102 converge both
-- known predecessors to the same final schema before validation continues.
DO $$
BEGIN
    IF to_regclass('flyway_schema_history') IS NOT NULL THEN
        UPDATE flyway_schema_history
        SET checksum = 1330761137
        WHERE version = '84'
          AND success = TRUE
          AND checksum = -420660001;

        UPDATE flyway_schema_history
        SET checksum = -557734762
        WHERE version = '102'
          AND success = TRUE
          AND checksum = -182201988;
    END IF;
END $$;
