package com.gymos.automations.application.command;

public record CreateAutomationRuleCommand(
		String name,
		String triggerType,
		String conditionJson,
		String actionType,
		String actionConfigJson,
		int retryLimit
) {
}
