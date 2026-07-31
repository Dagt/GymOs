CREATE INDEX ix_automation_work_item_expired_lease
    ON automation_work_item (locked_until)
    WHERE status = 'RUNNING' AND locked_until IS NOT NULL;
