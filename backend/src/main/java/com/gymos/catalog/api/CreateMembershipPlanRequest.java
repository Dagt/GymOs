package com.gymos.catalog.api;

import com.gymos.catalog.application.command.CreateMembershipPlanCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateMembershipPlanRequest(
		@NotBlank @Size(max = 64) String sku,
		@NotBlank @Size(max = 160) String name,
		@Size(max = 2000) String description,
		@Min(0) long priceCents,
		@NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
		@Min(1) int durationDays,
		@Min(0) int graceDays,
		@Size(max = 4000) String benefits,
		boolean multiBranchAccess,
		@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status
) {
	CreateMembershipPlanCommand toCommand() {
		return new CreateMembershipPlanCommand(
				sku, name, description, priceCents, currency, durationDays, graceDays,
				benefits, multiBranchAccess, status
		);
	}
}
