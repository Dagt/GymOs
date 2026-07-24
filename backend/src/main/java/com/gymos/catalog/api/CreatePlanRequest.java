package com.gymos.catalog.api;

import com.gymos.catalog.application.command.CreatePlanCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreatePlanRequest(
		@NotNull UUID productId,
		@Min(1) int durationDays,
		@Min(0) int graceDays,
		@Size(max = 4000) String benefitsJson,
		boolean multiBranchAccess,
		@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status
) {
	CreatePlanCommand toCommand() {
		return new CreatePlanCommand(productId, durationDays, graceDays, benefitsJson, multiBranchAccess, status);
	}
}
