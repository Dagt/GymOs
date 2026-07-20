package com.gymos.organizations.application.command;

public record CreateBranchCommand(
		String name,
		String code,
		String timezone,
		String phone
) {
}
