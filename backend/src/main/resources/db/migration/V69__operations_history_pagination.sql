CREATE INDEX ix_recurring_mandate_org_created
    ON recurring_payment_mandate (organization_id, created_at DESC, id DESC);

CREATE INDEX ix_recurring_charge_org_created
    ON recurring_charge_occurrence (organization_id, created_at DESC, id DESC);

CREATE INDEX ix_provider_webhook_org_received
    ON payment_provider_webhook_event (organization_id, received_at DESC, id DESC);

CREATE INDEX ix_automation_execution_org_rule_occurred
    ON automation_execution (organization_id, rule_id, occurred_at DESC, id DESC);
